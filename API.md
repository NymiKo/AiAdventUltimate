# API Документация

Документация по внутренним API и внешним интеграциям проекта AI Advent Ultimate.

## 📋 Содержание

- [Внутренние API](#внутренние-api)
- [Внешние API](#внешние-api)
- [Модели данных API](#модели-данных-api)
- [Примеры использования](#примеры-использования)

## 🔌 Внутренние API

### AI Agent API

#### AIAgent

Основной класс для работы с AI моделями.

**Расположение**: `com.qualiorstudio.aiadventultimate.ai.AIAgent`

**Конструктор**:
```kotlin
AIAgent(
    deepSeek: DeepSeek,
    ragService: RAGService? = null,
    maxIterations: Int = 10,
    customSystemPrompt: String? = null
)
```

**Методы**:

##### `initialize()`
Инициализация агента.

```kotlin
suspend fun initialize()
```

##### `processMessage()`
Обработка сообщения пользователя с поддержкой RAG.

```kotlin
suspend fun processMessage(
    userMessage: String,
    conversationHistory: List<DeepSeekMessage>,
    useRAG: Boolean = true,
    temperature: Double = 0.7,
    maxTokens: Int = 8000
): ProcessMessageResult
```

**Параметры**:
- `userMessage` — сообщение пользователя
- `conversationHistory` — история разговора
- `useRAG` — использовать ли RAG для поиска контекста
- `temperature` — температура генерации (0.0-1.0)
- `maxTokens` — максимальное количество токенов

**Возвращает**: `ProcessMessageResult` с ответом, краткой фразой и обновленной историей

**Пример**:
```kotlin
val agent = AIAgent(deepSeek, ragService)
val result = agent.processMessage(
    userMessage = "Что такое Kotlin?",
    conversationHistory = emptyList(),
    useRAG = true
)
println(result.response)
```

##### `close()`
Закрытие агента и освобождение ресурсов.

```kotlin
fun close()
```

### RAG Service API

#### RAGService

Сервис для поиска релевантной информации из базы знаний.

**Расположение**: `com.qualiorstudio.aiadventultimate.ai.RAGService`

**Конструктор**:
```kotlin
RAGService(
    lmStudioBaseUrl: String = "http://localhost:1234",
    indexFilePath: String = getEmbeddingsIndexPath(),
    topK: Int = 12,
    rerankMinScore: Double = 0.58,
    rerankedRetentionRatio: Double = 0.5,
    reranker: RAGReranker = RAGReranker()
)
```

**Методы**:

##### `searchRelevantChunks()`
Поиск релевантных фрагментов по запросу.

```kotlin
suspend fun searchRelevantChunks(query: String): List<ScoredEmbeddingChunk>
```

**Параметры**:
- `query` — поисковый запрос

**Возвращает**: Список релевантных фрагментов с оценками схожести

##### `buildContext()`
Построение контекста из найденных фрагментов.

```kotlin
fun buildContext(chunks: List<RankedChunk>): String
```

**Параметры**:
- `chunks` — список ранжированных фрагментов

**Возвращает**: Текстовый контекст для промпта

##### `buildRAGPrompt()`
Построение промпта с контекстом для AI модели.

```kotlin
fun buildRAGPrompt(userQuestion: String, context: String): String
```

**Параметры**:
- `userQuestion` — вопрос пользователя
- `context` — контекст из базы знаний

**Возвращает**: Промпт с инструкциями и контекстом

##### `buildComparison()`
Построение сравнения базового и улучшенного поиска.

```kotlin
suspend fun buildComparison(userQuestion: String): RAGComparisonResult?
```

**Параметры**:
- `userQuestion` — вопрос пользователя

**Возвращает**: Результат сравнения двух вариантов поиска

##### `isAvailable()`
Проверка доступности RAG сервиса.

```kotlin
fun isAvailable(): Boolean
```

**Возвращает**: `true` если сервис доступен

##### `close()`
Закрытие сервиса.

```kotlin
fun close()
```

### Embedding Pipeline API

#### EmbeddingPipeline

Пайплайн для обработки документов и создания эмбеддингов.

**Расположение**: `com.qualiorstudio.aiadventultimate.ai.EmbeddingPipeline`

**Конструктор**:
```kotlin
EmbeddingPipeline(
    lmStudio: LMStudio,
    index: EmbeddingIndex,
    chunker: TextChunker = TextChunker(),
    model: String? = null
)
```

**Методы**:

##### `processText()`
Обработка текста и создание эмбеддингов.

```kotlin
suspend fun processText(
    text: String,
    metadata: Map<String, String> = emptyMap(),
    model: String? = null
): List<EmbeddingChunk>
```

**Параметры**:
- `text` — текст для обработки
- `metadata` — метаданные для фрагментов
- `model` — модель для генерации эмбеддингов

**Возвращает**: Список фрагментов с эмбеддингами

##### `processTexts()`
Обработка нескольких текстов.

```kotlin
suspend fun processTexts(
    texts: List<String>,
    metadata: Map<String, String> = emptyMap(),
    model: String? = null
): List<EmbeddingChunk>
```

##### `search()`
Поиск похожих фрагментов по запросу.

```kotlin
suspend fun search(
    query: String,
    topK: Int = 5,
    model: String? = null
): List<ScoredEmbeddingChunk>
```

**Параметры**:
- `query` — поисковый запрос
- `topK` — количество результатов
- `model` — модель для генерации эмбеддинга запроса

**Возвращает**: Список похожих фрагментов с оценками

##### `getAvailableModels()`
Получение списка доступных моделей.

```kotlin
suspend fun getAvailableModels(): List<String>
```

##### `close()`
Закрытие пайплайна.

```kotlin
fun close()
```

### Repository API

#### ChatRepository

Репозиторий для работы с чатами.

**Расположение**: `com.qualiorstudio.aiadventultimate.repository.ChatRepository`

**Интерфейс**:
```kotlin
interface ChatRepository {
    suspend fun getAllChats(): List<Chat>
    suspend fun getChatById(id: String): Chat?
    suspend fun saveChat(chat: Chat)
    suspend fun deleteChat(id: String)
    suspend fun reloadChats()
    fun observeAllChats(): Flow<List<Chat>>
}
```

**Методы**:
- `getAllChats()` — получить все чаты
- `getChatById(id)` — получить чат по ID
- `saveChat(chat)` — сохранить чат
- `deleteChat(id)` — удалить чат
- `reloadChats()` — перезагрузить чаты из хранилища
- `observeAllChats()` — наблюдать за изменениями чатов

#### AgentRepository

Репозиторий для работы с агентами.

**Расположение**: `com.qualiorstudio.aiadventultimate.repository.AgentRepository`

**Интерфейс**:
```kotlin
interface AgentRepository {
    suspend fun getAllAgents(): List<Agent>
    suspend fun getAgentById(id: String): Agent?
    suspend fun saveAgent(agent: Agent)
    suspend fun deleteAgent(id: String)
    suspend fun updateAgent(agent: Agent)
    suspend fun reloadAgents()
    fun observeAllAgents(): Flow<List<Agent>>
}
```

#### AgentConnectionRepository

Репозиторий для работы со связями между агентами.

**Расположение**: `com.qualiorstudio.aiadventultimate.repository.AgentConnectionRepository`

**Интерфейс**:
```kotlin
interface AgentConnectionRepository {
    suspend fun getAllConnections(): List<AgentConnection>
    suspend fun getConnectionsBySourceAgent(sourceAgentId: String): List<AgentConnection>
    suspend fun getConnectionsByTargetAgent(targetAgentId: String): List<AgentConnection>
    suspend fun saveConnection(connection: AgentConnection)
    suspend fun deleteConnection(id: String)
    suspend fun reloadConnections()
    fun observeAllConnections(): Flow<List<AgentConnection>>
}
```

## 🌐 Внешние API

### DeepSeek API

#### DeepSeek

Клиент для работы с DeepSeek API.

**Расположение**: `com.qualiorstudio.aiadventultimate.api.DeepSeek`

**Конструктор**:
```kotlin
DeepSeek(
    apiKey: String,
    model: String = "deepseek-chat"
)
```

**Методы**:

##### `sendMessage()`
Отправка сообщения в DeepSeek API.

```kotlin
suspend fun sendMessage(
    messages: List<DeepSeekMessage>,
    tools: List<DeepSeekTool>? = null,
    temperature: Double = 0.7,
    maxTokens: Int = 8000
): DeepSeekResponse
```

**Параметры**:
- `messages` — список сообщений
- `tools` — список инструментов (опционально)
- `temperature` — температура генерации
- `maxTokens` — максимальное количество токенов

**Возвращает**: `DeepSeekResponse` с ответом модели

**Endpoint**: `POST https://api.deepseek.com/v1/chat/completions`

**Заголовки**:
- `Authorization: Bearer {apiKey}`
- `Content-Type: application/json`

**Пример запроса**:
```json
{
  "model": "deepseek-chat",
  "messages": [
    {
      "role": "user",
      "content": "Привет!"
    }
  ],
  "temperature": 0.7,
  "max_tokens": 8000
}
```

### Yandex GPT API

#### YandexGPT

Клиент для работы с Yandex GPT API.

**Расположение**: `com.qualiorstudio.aiadventultimate.api.YandexGPT`

**Конструктор**:
```kotlin
YandexGPT(
    apiKey: String,
    folderId: String
)
```

**Методы**:

##### `sendMessage()`
Отправка сообщения в Yandex GPT API.

```kotlin
suspend fun sendMessage(
    messages: List<MessageInfo>,
    modelUri: String = "gpt://$folderId/yandexgpt-lite/latest"
): String
```

**Параметры**:
- `messages` — список сообщений
- `modelUri` — URI модели

**Возвращает**: Текст ответа

**Endpoint**: `POST https://llm.api.cloud.yandex.net/foundationModels/v1/completion`

**Заголовки**:
- `Authorization: Bearer {apiKey}`
- `Content-Type: application/json`

### LM Studio API

#### LMStudio

Клиент для работы с локальным LM Studio сервером.

**Расположение**: `com.qualiorstudio.aiadventultimate.api.LMStudio`

**Конструктор**:
```kotlin
LMStudio(
    baseUrl: String = "http://localhost:1234",
    defaultModel: String? = null
)
```

**Методы**:

##### `getAvailableModels()`
Получение списка доступных моделей.

```kotlin
suspend fun getAvailableModels(): List<String>
```

**Endpoint**: `GET {baseUrl}/v1/models`

##### `generateEmbedding()`
Генерация эмбеддинга для текста.

```kotlin
suspend fun generateEmbedding(
    text: String,
    model: String? = null
): List<Double>
```

**Параметры**:
- `text` — текст для генерации эмбеддинга
- `model` — модель для использования

**Возвращает**: Вектор эмбеддинга

**Endpoint**: `POST {baseUrl}/v1/embeddings`

**Пример запроса**:
```json
{
  "input": "Текст для эмбеддинга",
  "model": "model-name"
}
```

##### `generateEmbeddings()`
Генерация эмбеддингов для нескольких текстов.

```kotlin
suspend fun generateEmbeddings(
    texts: List<String>,
    model: String? = null
): List<List<Double>>
```

### Voice Services API

#### VoiceInputService

Сервис для голосового ввода.

**Расположение**: `com.qualiorstudio.aiadventultimate.voice.VoiceInputService`

**Методы**:

##### `isSupported()`
Проверка поддержки голосового ввода на платформе.

```kotlin
fun isSupported(): Boolean
```

##### `startRecording()`
Начало записи аудио.

```kotlin
suspend fun startRecording()
```

##### `stopRecording()`
Остановка записи и распознавание речи.

```kotlin
suspend fun stopRecording(): Result<String>
```

**Возвращает**: `Result<String>` с распознанным текстом

##### `isRecording()`
Проверка состояния записи.

```kotlin
fun isRecording(): Boolean
```

**Интеграция с Yandex SpeechKit STT**:
- Endpoint: `https://stt.api.cloud.yandex.net/speech/v1/stt:recognize`
- Формат аудио: PCM, 16kHz, 16-bit, mono
- Язык: `ru-RU`

#### VoiceOutputService

Сервис для голосового вывода.

**Расположение**: `com.qualiorstudio.aiadventultimate.voice.VoiceOutputService`

**Методы**:

##### `isSupported()`
Проверка поддержки голосового вывода на платформе.

```kotlin
fun isSupported(): Boolean
```

##### `speak()`
Озвучивание текста.

```kotlin
suspend fun speak(text: String): Result<Unit>
```

**Параметры**:
- `text` — текст для озвучивания

**Возвращает**: `Result<Unit>`

**Интеграция с Yandex SpeechKit TTS**:
- Endpoint: `https://tts.api.cloud.yandex.net/speech/v1/tts:synthesize`
- Голос: `jane` (женский голос Джарвиса)
- Формат: `lpcm` (PCM)
- Частота: 16000 Hz

## 📊 Модели данных API

### DeepSeek Models

#### DeepSeekMessage
```kotlin
data class DeepSeekMessage(
    val role: String,
    val content: String? = null,
    val toolCalls: List<DeepSeekToolCall>? = null,
    val toolCallId: String? = null,
    val type: String? = null
)
```

#### DeepSeekRequest
```kotlin
data class DeepSeekRequest(
    val model: String,
    val messages: List<DeepSeekMessage>,
    val temperature: Double = 0.7,
    val maxTokens: Int = 8000,
    val stream: Boolean = false,
    val tools: List<DeepSeekTool>? = null,
    val toolChoice: String? = null
)
```

#### DeepSeekResponse
```kotlin
data class DeepSeekResponse(
    val id: String,
    val `object`: String,
    val created: Long,
    val model: String,
    val choices: List<DeepSeekChoice>,
    val usage: DeepSeekUsage
)
```

### Yandex GPT Models

#### ChatRequest
```kotlin
data class ChatRequest(
    val modelUri: String,
    val completionOptions: CompletionOptions = CompletionOptions(),
    val messages: List<MessageInfo>
)
```

#### ChatResponse
```kotlin
data class ChatResponse(
    val result: Result
)
```

### LM Studio Models

#### EmbeddingRequest
```kotlin
data class EmbeddingRequest(
    val input: String,
    val model: String
)
```

#### EmbeddingResponse
```kotlin
data class EmbeddingResponse(
    val data: List<EmbeddingData>,
    val model: String,
    val usage: EmbeddingUsage,
    val obj: String = "list"
)
```

## 💡 Примеры использования

### Пример 1: Базовое использование AI Agent

```kotlin
val deepSeek = DeepSeek(apiKey = "your-api-key")
val ragService = RAGService(
    lmStudioBaseUrl = "http://localhost:1234"
)
val agent = AIAgent(deepSeek, ragService)

val result = agent.processMessage(
    userMessage = "Что такое Kotlin Multiplatform?",
    conversationHistory = emptyList(),
    useRAG = true
)

println(result.response)
```

### Пример 2: Использование RAG Service

```kotlin
val ragService = RAGService(
    lmStudioBaseUrl = "http://localhost:1234",
    topK = 10
)

val chunks = ragService.searchRelevantChunks("Kotlin coroutines")
val context = ragService.buildContext(
    chunks.map { RankedChunk(it.chunk, it.similarity) }
)
val prompt = ragService.buildRAGPrompt("Что такое корутины?", context)
```

### Пример 3: Голосовой ввод

```kotlin
val voiceService = createVoiceInputService()

if (voiceService.isSupported()) {
    voiceService.startRecording()
    delay(5000)
    val result = voiceService.stopRecording()
    result.onSuccess { text ->
        println("Распознанный текст: $text")
    }
}
```

### Пример 4: Голосовой вывод

```kotlin
val voiceOutputService = createVoiceOutputService()

if (voiceOutputService.isSupported()) {
    voiceOutputService.speak("Привет, я Джарвис")
}
```

### Пример 5: Работа с репозиториями

```kotlin
val chatRepository = ChatRepositoryImpl()
val agentRepository = AgentRepositoryImpl()

chatRepository.observeAllChats().collect { chats ->
    println("Всего чатов: ${chats.size}")
}

val agent = Agent(
    id = UUID.randomUUID().toString(),
    name = "Программист",
    role = "Помощник по программированию",
    systemPrompt = "Ты опытный программист..."
)

agentRepository.saveAgent(agent)
```

---

**Примечание**: Для работы с внешними API требуется настройка соответствующих ключей в файле `.env` или переменных окружения.

