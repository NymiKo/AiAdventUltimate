# Схемы данных

Подробное описание всех моделей данных, используемых в проекте AI Advent Ultimate.

## 📋 Содержание

- [Модели чата](#модели-чата)
- [Модели агентов](#модели-агентов)
- [Модели RAG](#модели-rag)
- [Модели настроек](#модели-настроек)
- [Модели API](#модели-api)
- [Диаграммы связей](#диаграммы-связей)

## 💬 Модели чата

### Chat

Основная модель чата, содержащая историю сообщений.

**Расположение**: `com.qualiorstudio.aiadventultimate.model.Chat`

**Схема**:
```kotlin
@Serializable
data class Chat(
    val id: String,                    // Уникальный идентификатор чата
    val title: String,                 // Заголовок чата
    val messages: List<ChatMessage>,   // Список сообщений
    val createdAt: Long,               // Время создания (timestamp)
    val updatedAt: Long                // Время последнего обновления (timestamp)
)
```

**Поля**:
- `id` — уникальный идентификатор чата (UUID)
- `title` — заголовок чата (обычно первое сообщение или автоматически сгенерированный)
- `messages` — список сообщений в чате
- `createdAt` — время создания чата в миллисекундах
- `updatedAt` — время последнего обновления в миллисекундах

**Пример**:
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "title": "Вопрос о Kotlin",
  "messages": [
    {
      "text": "Что такое Kotlin?",
      "isUser": true,
      "variants": [],
      "agentId": null,
      "agentName": null
    }
  ],
  "createdAt": 1704067200000,
  "updatedAt": 1704067200000
}
```

### ChatMessage

Модель сообщения в чате.

**Расположение**: `com.qualiorstudio.aiadventultimate.model.ChatMessage`

**Схема**:
```kotlin
@Serializable
data class ChatMessage(
    val text: String,                              // Текст сообщения
    val isUser: Boolean,                           // Флаг: сообщение от пользователя
    val variants: List<ChatResponseVariant> = emptyList(), // Варианты ответа (для AI)
    val agentId: String? = null,                  // ID агента, который ответил
    val agentName: String? = null                 // Имя агента, который ответил
)
```

**Поля**:
- `text` — текст сообщения
- `isUser` — `true` если сообщение от пользователя, `false` если от AI
- `variants` — список вариантов ответа (используется для сравнения RAG результатов)
- `agentId` — идентификатор агента, который сгенерировал ответ (если применимо)
- `agentName` — имя агента, который сгенерировал ответ (если применимо)

**Пример**:
```json
{
  "text": "Kotlin — это современный язык программирования...",
  "isUser": false,
  "variants": [
    {
      "id": "baseline",
      "title": "Без reranker",
      "body": "Kotlin — это язык программирования...",
      "metadata": "Чанки: 5 | Sim ≥ 0.65",
      "isPreferred": false
    }
  ],
  "agentId": "agent-123",
  "agentName": "Программист"
}
```

### ChatResponseVariant

Вариант ответа AI (используется для сравнения разных подходов RAG).

**Расположение**: `com.qualiorstudio.aiadventultimate.model.ChatResponseVariant`

**Схема**:
```kotlin
@Serializable
data class ChatResponseVariant(
    val id: String,                    // Уникальный идентификатор варианта
    val title: String,                  // Заголовок варианта
    val body: String,                   // Текст варианта ответа
    val metadata: String? = null,       // Метаданные (метрики, статистика)
    val isPreferred: Boolean = false    // Флаг предпочтительного варианта
)
```

**Поля**:
- `id` — идентификатор варианта (например, "baseline", "reranked")
- `title` — название варианта (например, "Без reranker", "С reranker")
- `body` — полный текст варианта ответа
- `metadata` — метаданные в виде строки (например, "Чанки: 5 | Sim ≥ 0.65 | Avg sim: 0.72")
- `isPreferred` — `true` если это предпочтительный вариант

**Пример**:
```json
{
  "id": "reranked",
  "title": "С reranker",
  "body": "Kotlin — это современный язык программирования...",
  "metadata": "Чанки: 3 | Отфильтровано: 2 | Score ≥ 0.58 | Avg sim: 0.75 | Avg score: 0.68",
  "isPreferred": true
}
```

## 🤖 Модели агентов

### Agent

Модель AI агента с кастомным промптом.

**Расположение**: `com.qualiorstudio.aiadventultimate.model.Agent`

**Схема**:
```kotlin
@Serializable
data class Agent(
    val id: String,                    // Уникальный идентификатор агента
    val name: String,                   // Имя агента
    val role: String,                   // Роль/специализация агента
    val systemPrompt: String,          // Системный промпт агента
    val createdAt: Long,               // Время создания (timestamp)
    val updatedAt: Long                // Время последнего обновления (timestamp)
)
```

**Поля**:
- `id` — уникальный идентификатор агента (UUID)
- `name` — отображаемое имя агента
- `role` — краткое описание роли/специализации
- `systemPrompt` — системный промпт, определяющий поведение агента
- `createdAt` — время создания в миллисекундах
- `updatedAt` — время последнего обновления в миллисекундах

**Пример**:
```json
{
  "id": "agent-123",
  "name": "Программист",
  "role": "Помощник по программированию",
  "systemPrompt": "Ты опытный программист, специализирующийся на Kotlin и Android разработке...",
  "createdAt": 1704067200000,
  "updatedAt": 1704067200000
}
```

### AgentConnection

Связь между двумя агентами.

**Расположение**: `com.qualiorstudio.aiadventultimate.model.AgentConnection`

**Схема**:
```kotlin
@Serializable
data class AgentConnection(
    val id: String,                          // Уникальный идентификатор связи
    val sourceAgentId: String,               // ID исходного агента
    val targetAgentId: String,              // ID целевого агента
    val description: String,                 // Описание связи
    val connectionType: ConnectionType = ConnectionType.REVIEW, // Тип связи
    val createdAt: Long,                    // Время создания (timestamp)
    val updatedAt: Long                     // Время последнего обновления (timestamp)
)
```

**Поля**:
- `id` — уникальный идентификатор связи (UUID)
- `sourceAgentId` — идентификатор исходного агента
- `targetAgentId` — идентификатор целевого агента
- `description` — текстовое описание связи
- `connectionType` — тип связи (см. `ConnectionType`)
- `createdAt` — время создания в миллисекундах
- `updatedAt` — время последнего обновления в миллисекундах

**Пример**:
```json
{
  "id": "connection-123",
  "sourceAgentId": "agent-1",
  "targetAgentId": "agent-2",
  "description": "Агент-2 проверяет код, написанный агентом-1",
  "connectionType": "REVIEW",
  "createdAt": 1704067200000,
  "updatedAt": 1704067200000
}
```

### ConnectionType

Тип связи между агентами.

**Расположение**: `com.qualiorstudio.aiadventultimate.model.ConnectionType`

**Значения**:
```kotlin
@Serializable
enum class ConnectionType {
    REVIEW,        // Просмотр/проверка работы другого агента
    VALIDATE,      // Валидация результатов другого агента
    ENHANCE,       // Улучшение результатов другого агента
    COLLABORATE    // Совместная работа агентов
}
```

**Описание**:
- `REVIEW` — агент просматривает и проверяет работу другого агента
- `VALIDATE` — агент валидирует результаты другого агента
- `ENHANCE` — агент улучшает результаты другого агента
- `COLLABORATE` — агенты работают совместно над задачей

## 🔍 Модели RAG

### EmbeddingChunk

Фрагмент текста с эмбеддингом.

**Расположение**: `com.qualiorstudio.aiadventultimate.ai.EmbeddingChunk`

**Схема**:
```kotlin
@Serializable
data class EmbeddingChunk(
    val id: String,                           // Уникальный идентификатор фрагмента
    val text: String,                         // Текст фрагмента
    val embedding: List<Double>,              // Вектор эмбеддинга
    val metadata: Map<String, String> = emptyMap() // Метаданные фрагмента
)
```

**Поля**:
- `id` — уникальный идентификатор фрагмента (UUID)
- `text` — текст фрагмента (чанк документа)
- `embedding` — вектор эмбеддинга (список чисел с плавающей точкой)
- `metadata` — метаданные в виде ключ-значение (например, `title`, `fileName`, `url`, `source`)

**Пример**:
```json
{
  "id": "chunk-123",
  "text": "Kotlin — это статически типизированный язык программирования...",
  "embedding": [0.123, -0.456, 0.789, ...],
  "metadata": {
    "title": "Введение в Kotlin",
    "fileName": "kotlin-intro.html",
    "url": "https://example.com/kotlin-intro",
    "source": "documentation"
  }
}
```

### ScoredEmbeddingChunk

Фрагмент с оценкой схожести.

**Расположение**: `com.qualiorstudio.aiadventultimate.ai.ScoredEmbeddingChunk`

**Схема**:
```kotlin
@Serializable
data class ScoredEmbeddingChunk(
    val chunk: EmbeddingChunk,    // Фрагмент текста
    val similarity: Double         // Оценка схожести (0.0 - 1.0)
)
```

**Поля**:
- `chunk` — фрагмент текста с эмбеддингом
- `similarity` — оценка схожести с запросом (косинусное сходство, от 0.0 до 1.0)

**Пример**:
```json
{
  "chunk": {
    "id": "chunk-123",
    "text": "Kotlin — это статически типизированный язык...",
    "embedding": [0.123, -0.456, ...],
    "metadata": {}
  },
  "similarity": 0.85
}
```

### RankedChunk

Фрагмент с расширенными метриками ранжирования.

**Расположение**: `com.qualiorstudio.aiadventultimate.ai.RankedChunk`

**Схема**:
```kotlin
data class RankedChunk(
    val chunk: EmbeddingChunk,      // Фрагмент текста
    val similarity: Double,          // Оценка схожести (эмбеддинг)
    val rerankScore: Double? = null, // Оценка reranker (лексическое сходство)
    val combinedScore: Double? = null // Комбинированная оценка
)
```

**Поля**:
- `chunk` — фрагмент текста с эмбеддингом
- `similarity` — оценка схожести по эмбеддингу (0.0 - 1.0)
- `rerankScore` — оценка reranker на основе лексического сходства (0.0 - 1.0)
- `combinedScore` — комбинированная оценка (weighted sum of similarity and rerankScore)

**Пример**:
```json
{
  "chunk": {
    "id": "chunk-123",
    "text": "Kotlin — это статически типизированный язык...",
    "embedding": [0.123, -0.456, ...],
    "metadata": {}
  },
  "similarity": 0.85,
  "rerankScore": 0.72,
  "combinedScore": 0.80
}
```

### EmbeddingIndexData

Данные индекса эмбеддингов.

**Расположение**: `com.qualiorstudio.aiadventultimate.ai.EmbeddingIndexData`

**Схема**:
```kotlin
@Serializable
data class EmbeddingIndexData(
    val chunks: List<EmbeddingChunk>,           // Список всех фрагментов
    val createdAt: Long = System.currentTimeMillis(), // Время создания индекса
    val model: String = "local-model"           // Модель, использованная для эмбеддингов
)
```

**Поля**:
- `chunks` — список всех фрагментов с эмбеддингами
- `createdAt` — время создания индекса в миллисекундах
- `model` — название модели, использованной для генерации эмбеддингов

**Пример**:
```json
{
  "chunks": [
    {
      "id": "chunk-1",
      "text": "Текст фрагмента 1...",
      "embedding": [0.123, -0.456, ...],
      "metadata": {}
    },
    {
      "id": "chunk-2",
      "text": "Текст фрагмента 2...",
      "embedding": [0.789, -0.321, ...],
      "metadata": {}
    }
  ],
  "createdAt": 1704067200000,
  "model": "nomic-embed-text-v1"
}
```

### RAGVariantContext

Контекст для варианта RAG ответа.

**Расположение**: `com.qualiorstudio.aiadventultimate.ai.RAGVariantContext`

**Схема**:
```kotlin
data class RAGVariantContext(
    val id: String,                              // Идентификатор варианта
    val title: String,                           // Заголовок варианта
    val prompt: String,                          // Промпт с контекстом
    val context: String,                         // Извлеченный контекст
    val chunks: List<RankedChunk>,               // Использованные фрагменты
    val similarityThreshold: Double?,             // Порог схожести
    val totalCandidates: Int,                    // Всего кандидатов
    val averageSimilarity: Double?,               // Средняя схожесть
    val averageCombinedScore: Double?             // Средняя комбинированная оценка
)
```

**Поля**:
- `id` — идентификатор варианта ("baseline" или "reranked")
- `title` — название варианта
- `prompt` — полный промпт с контекстом для AI модели
- `context` — извлеченный контекст из базы знаний
- `chunks` — список использованных фрагментов с метриками
- `similarityThreshold` — минимальный порог схожести для фильтрации
- `totalCandidates` — общее количество кандидатов до фильтрации
- `averageSimilarity` — средняя оценка схожести использованных фрагментов
- `averageCombinedScore` — средняя комбинированная оценка

## ⚙️ Модели настроек

### AppSettings

Настройки приложения.

**Расположение**: `com.qualiorstudio.aiadventultimate.model.AppSettings`

**Схема**:
```kotlin
@Serializable
data class AppSettings(
    val darkTheme: Boolean = true,                    // Темная тема
    val useRAG: Boolean = true,                      // Использовать RAG
    val enableVoiceInput: Boolean = true,            // Включить голосовой ввод
    val enableVoiceOutput: Boolean = true,            // Включить голосовой вывод
    val deepSeekApiKey: String = "...",              // API ключ DeepSeek
    val temperature: Double = 0.7,                    // Температура генерации
    val maxTokens: Int = 8000,                       // Максимум токенов
    val ragTopK: Int = 12,                           // Количество фрагментов для RAG
    val rerankMinScore: Double = 0.58,                // Минимальный score для reranker
    val rerankedRetentionRatio: Double = 0.5,         // Доля сохраненных после rerank
    val lmStudioBaseUrl: String = "http://localhost:1234", // URL LM Studio
    val maxIterations: Int = 10                       // Максимум итераций для tool calls
)
```

**Поля**:

**UI настройки**:
- `darkTheme` — использовать темную тему интерфейса
- `enableVoiceInput` — включить голосовой ввод (Desktop)
- `enableVoiceOutput` — включить голосовой вывод (Desktop)

**AI настройки**:
- `deepSeekApiKey` — API ключ для DeepSeek
- `temperature` — температура генерации (0.0 - 1.0), влияет на креативность
- `maxTokens` — максимальное количество токенов в ответе

**RAG настройки**:
- `useRAG` — использовать ли RAG для поиска контекста
- `ragTopK` — количество фрагментов для извлечения (top-K)
- `rerankMinScore` — минимальный комбинированный score для фильтрации после rerank
- `rerankedRetentionRatio` — доля фрагментов, сохраняемых после rerank (0.0 - 1.0)
- `lmStudioBaseUrl` — базовый URL для LM Studio сервера

**Agent настройки**:
- `maxIterations` — максимальное количество итераций для цепочки tool calls

**Пример**:
```json
{
  "darkTheme": true,
  "useRAG": true,
  "enableVoiceInput": true,
  "enableVoiceOutput": true,
  "deepSeekApiKey": "sk-...",
  "temperature": 0.7,
  "maxTokens": 8000,
  "ragTopK": 12,
  "rerankMinScore": 0.58,
  "rerankedRetentionRatio": 0.5,
  "lmStudioBaseUrl": "http://localhost:1234",
  "maxIterations": 10
}
```

## 📡 Модели API

### DeepSeekMessage

Сообщение для DeepSeek API.

**Расположение**: `com.qualiorstudio.aiadventultimate.api.DeepSeekMessage`

**Схема**:
```kotlin
@Serializable
data class DeepSeekMessage(
    val role: String,                        // Роль: "system", "user", "assistant", "tool"
    val content: String? = null,            // Содержимое сообщения
    val toolCalls: List<DeepSeekToolCall>? = null, // Вызовы инструментов
    val toolCallId: String? = null,         // ID вызова инструмента (для role="tool")
    val type: String? = null                // Тип сообщения
)
```

### DeepSeekResponse

Ответ от DeepSeek API.

**Расположение**: `com.qualiorstudio.aiadventultimate.api.DeepSeekResponse`

**Схема**:
```kotlin
@Serializable
data class DeepSeekResponse(
    val id: String,                         // ID запроса
    val `object`: String,                   // Тип объекта ("chat.completion")
    val created: Long,                      // Время создания (timestamp)
    val model: String,                      // Использованная модель
    val choices: List<DeepSeekChoice>,     // Варианты ответа
    val usage: DeepSeekUsage                // Статистика использования токенов
)
```

### Yandex GPT Models

#### ChatRequest
```kotlin
@Serializable
data class ChatRequest(
    val modelUri: String,                   // URI модели
    val completionOptions: CompletionOptions, // Опции генерации
    val messages: List<MessageInfo>         // Список сообщений
)
```

#### ChatResponse
```kotlin
@Serializable
data class ChatResponse(
    val result: Result                      // Результат генерации
)
```

### LM Studio Models

#### EmbeddingRequest
```kotlin
@Serializable
data class EmbeddingRequest(
    val input: String,                      // Текст для эмбеддинга
    val model: String                       // Модель для использования
)
```

#### EmbeddingResponse
```kotlin
@Serializable
data class EmbeddingResponse(
    val data: List<EmbeddingData>,          // Данные эмбеддингов
    val model: String,                      // Использованная модель
    val usage: EmbeddingUsage,              // Статистика использования
    val obj: String = "list"                // Тип объекта
)
```

## 🔗 Диаграммы связей

### Связи между моделями

```
Chat
├── ChatMessage (1:N)
    ├── ChatResponseVariant (1:N)
    └── Agent (N:1, через agentId)

Agent
├── AgentConnection (1:N, как sourceAgent)
├── AgentConnection (1:N, как targetAgent)
└── ChatMessage (1:N, через agentId)

EmbeddingIndexData
└── EmbeddingChunk (1:N)

ScoredEmbeddingChunk
└── EmbeddingChunk (1:1)

RankedChunk
└── EmbeddingChunk (1:1)
```

### Поток данных RAG

```
Документ → TextChunker → Chunks → LMStudio.generateEmbedding() 
→ EmbeddingChunk → EmbeddingIndex.saveIndex()

Запрос → LMStudio.generateEmbedding() → Query Embedding 
→ EmbeddingIndex.searchSimilar() → ScoredEmbeddingChunk 
→ RAGReranker.rerank() → RankedChunk → RAGService.buildContext() 
→ Context → AIAgent.processMessage()
```

### Поток данных чата

```
User Input → ChatViewModel.sendMessage() → AIAgent.processMessage() 
→ DeepSeek.sendMessage() → DeepSeekResponse → ChatMessage 
→ ChatRepository.saveChat() → Storage → JSON File
```

---

**Примечание**: Все модели данных используют `kotlinx.serialization` для сериализации/десериализации в JSON формат. Модели сохраняются в локальные файлы в формате JSON.

