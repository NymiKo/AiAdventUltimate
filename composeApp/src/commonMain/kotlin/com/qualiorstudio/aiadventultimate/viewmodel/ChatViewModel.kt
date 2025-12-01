package com.qualiorstudio.aiadventultimate.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qualiorstudio.aiadventultimate.ai.AIAgent
import com.qualiorstudio.aiadventultimate.ai.RAGService
import com.qualiorstudio.aiadventultimate.api.DeepSeek
import com.qualiorstudio.aiadventultimate.api.DeepSeekMessage
import com.qualiorstudio.aiadventultimate.model.Agent
import com.qualiorstudio.aiadventultimate.model.AgentConnection
import com.qualiorstudio.aiadventultimate.model.Chat
import com.qualiorstudio.aiadventultimate.model.ChatMessage
import com.qualiorstudio.aiadventultimate.repository.AgentConnectionRepository
import com.qualiorstudio.aiadventultimate.repository.AgentConnectionRepositoryImpl
import com.qualiorstudio.aiadventultimate.repository.ChatRepository
import com.qualiorstudio.aiadventultimate.repository.ChatRepositoryImpl
import com.qualiorstudio.aiadventultimate.utils.currentTimeMillis
import com.qualiorstudio.aiadventultimate.voice.createVoiceOutputService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

class ChatViewModel(
    private val settingsViewModel: SettingsViewModel? = null,
    private val chatRepository: ChatRepository? = null,
    private val connectionRepository: AgentConnectionRepository? = null
) : ViewModel() {
    private val repository = chatRepository ?: ChatRepositoryImpl()
    private val connectionRepo = connectionRepository ?: AgentConnectionRepositoryImpl()
    private val voiceOutputService = createVoiceOutputService()
    
    private var deepSeek: DeepSeek? = null
    private var ragService: RAGService? = null
    private val agentInstances = mutableMapOf<String, AIAgent>()
    private var coordinatorAgent: AIAgent? = null
    private var lastApiKey: String? = null
    private var lastLmStudioUrl: String? = null
    private var lastTopK: Int? = null
    private var lastRerankMinScore: Double? = null
    private var lastRerankedRetentionRatio: Double? = null
    private var lastMaxIterations: Int? = null
    
    private val _selectedAgents = MutableStateFlow<List<Agent>>(emptyList())
    val selectedAgents: StateFlow<List<Agent>> = _selectedAgents.asStateFlow()
    
    private val _useCoordinator = MutableStateFlow(true)
    val useCoordinator: StateFlow<Boolean> = _useCoordinator.asStateFlow()
    
    private val coordinatorSystemPrompt = """
Ты агент-координатор в мультиагентной системе. Твоя задача - анализировать запросы пользователя и выбирать наиболее подходящего специализированного агента из доступных для ответа.

ДОСТУПНЫЕ АГЕНТЫ:
{available_agents}

ИНСТРУКЦИИ:
1. Внимательно проанализируй запрос пользователя
2. Определи, какой из доступных агентов лучше всего подходит для ответа на основе его специализации и роли
3. Верни ТОЛЬКО ID выбранного агента в формате: AGENT_ID: <id_агента>
4. Если запрос требует знаний нескольких агентов, выбери наиболее подходящего основного агента
5. Если ни один агент не подходит идеально, выбери наиболее близкого по специализации

ФОРМАТ ОТВЕТА:
AGENT_ID: <id_агента>

Примеры:
- Запрос: "Как написать функцию на Kotlin?" → AGENT_ID: <id_агента_программиста>
- Запрос: "Объясни концепцию машинного обучения" → AGENT_ID: <id_агента_по_ML>
- Запрос: "Помоги с дизайном интерфейса" → AGENT_ID: <id_агента_дизайнера>

ВАЖНО: Верни ТОЛЬКО строку в формате AGENT_ID: <id>, без дополнительных пояснений.
    """.trimIndent()
    
    private fun getSettings() = settingsViewModel?.settings?.value ?: com.qualiorstudio.aiadventultimate.model.AppSettings()
    
    private fun initializeServices() {
        val settings = getSettings()
        
        // Проверяем, нужно ли пересоздавать базовые сервисы
        val needsRecreation = deepSeek == null || 
            ragService == null ||
            lastApiKey != settings.deepSeekApiKey ||
            lastLmStudioUrl != settings.lmStudioBaseUrl ||
            lastTopK != settings.ragTopK ||
            lastRerankMinScore != settings.rerankMinScore ||
            lastRerankedRetentionRatio != settings.rerankedRetentionRatio ||
            lastMaxIterations != settings.maxIterations
        
        if (needsRecreation) {
            // Закрываем старые сервисы
            agentInstances.values.forEach { it.close() }
            ragService?.close()
            deepSeek = null
            agentInstances.clear()
            
            // Сохраняем текущие настройки
            lastApiKey = settings.deepSeekApiKey
            lastLmStudioUrl = settings.lmStudioBaseUrl
            lastTopK = settings.ragTopK
            lastRerankMinScore = settings.rerankMinScore
            lastRerankedRetentionRatio = settings.rerankedRetentionRatio
            lastMaxIterations = settings.maxIterations
            
            // Создаем новые базовые сервисы
            deepSeek = DeepSeek(apiKey = settings.deepSeekApiKey)
            ragService = RAGService(
                lmStudioBaseUrl = settings.lmStudioBaseUrl,
                topK = settings.ragTopK,
                rerankMinScore = settings.rerankMinScore,
                rerankedRetentionRatio = settings.rerankedRetentionRatio
            )
        }
        
        // Создаем экземпляр координатора, если есть выбранные агенты и координатор включен
        val selectedAgents = _selectedAgents.value
        val useCoordinator = _useCoordinator.value
        if (selectedAgents.isNotEmpty() && useCoordinator && coordinatorAgent == null) {
            coordinatorAgent = AIAgent(
                deepSeek = deepSeek!!,
                ragService = null,
                maxIterations = settings.maxIterations,
                customSystemPrompt = coordinatorSystemPrompt
            )
        } else if (selectedAgents.isEmpty() || !useCoordinator) {
            coordinatorAgent?.close()
            coordinatorAgent = null
        }
        
        // Создаем экземпляры AIAgent для каждого выбранного агента
        val currentAgentIds = agentInstances.keys.toSet()
        val newAgentIds = selectedAgents.map { it.id }.toSet()
        
        // Удаляем экземпляры для агентов, которые больше не выбраны
        val agentsToRemove = currentAgentIds - newAgentIds
        agentsToRemove.forEach { agentId ->
            agentInstances[agentId]?.close()
            agentInstances.remove(agentId)
        }
        
        // Создаем экземпляры для новых агентов
        selectedAgents.forEach { agent ->
            if (!agentInstances.containsKey(agent.id)) {
                agentInstances[agent.id] = AIAgent(
                    deepSeek = deepSeek!!,
                    ragService = ragService,
                    maxIterations = settings.maxIterations,
                    customSystemPrompt = agent.systemPrompt
                )
            }
        }
    }
    
    private suspend fun selectBestAgent(userMessage: String, availableAgents: List<Agent>): Agent? {
        if (availableAgents.isEmpty() || coordinatorAgent == null) {
            return null
        }
        
        if (availableAgents.size == 1) {
            return availableAgents.first()
        }
        
        val agentsDescription = availableAgents.joinToString("\n") { agent ->
            "- ID: ${agent.id}, Имя: ${agent.name}, Роль: ${agent.role}"
        }
        
        val coordinatorPrompt = coordinatorSystemPrompt.replace("{available_agents}", agentsDescription)
        
        val messages = listOf(
            DeepSeekMessage(role = "system", content = coordinatorPrompt),
            DeepSeekMessage(role = "user", content = userMessage)
        )
        
        try {
            val response = deepSeek!!.sendMessage(messages, null, temperature = 0.3, maxTokens = 100)
            val coordinatorResponse = response.choices.firstOrNull()?.message?.content?.trim() ?: return availableAgents.first()
            
            val agentIdMatch = Regex("AGENT_ID:\\s*([^\\s]+)").find(coordinatorResponse)
            val selectedAgentId = agentIdMatch?.groupValues?.get(1)?.trim()
            
            if (selectedAgentId != null) {
                val selectedAgent = availableAgents.find { it.id == selectedAgentId }
                if (selectedAgent != null) {
                    println("Координатор выбрал агента: ${selectedAgent.name} (ID: ${selectedAgentId})")
                    return selectedAgent
                }
            }
            
            println("Координатор не смог выбрать агента, используем первого: ${coordinatorResponse}")
            return availableAgents.first()
        } catch (e: Exception) {
            println("Ошибка при выборе агента координатором: ${e.message}")
            return availableAgents.first()
        }
    }
    
    private fun getAgentInstance(agentId: String): AIAgent? {
        return agentInstances[agentId]
    }
    
    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val _useRAG = MutableStateFlow(true)
    val useRAG: StateFlow<Boolean> = _useRAG.asStateFlow()
    
    private val _enableVoiceOutput = MutableStateFlow(true)
    val enableVoiceOutput: StateFlow<Boolean> = _enableVoiceOutput.asStateFlow()
    
    private var conversationHistory = mutableListOf<DeepSeekMessage>()
    
    private var currentChatId: String? = null
    
    fun loadChat(chatId: String) {
        viewModelScope.launch {
            val chat = repository.getChatById(chatId)
            if (chat != null) {
                currentChatId = chatId
                _messages.value = chat.messages
                conversationHistory.clear()
                chat.messages.forEach { message ->
                    if (message.isUser) {
                        conversationHistory.add(DeepSeekMessage(role = "user", content = message.text))
                    } else {
                        conversationHistory.add(DeepSeekMessage(role = "assistant", content = message.text))
                    }
                }
            }
        }
    }
    
    fun createNewChat() {
        currentChatId = null
        _messages.value = emptyList()
        conversationHistory.clear()
    }
    
    private fun generateChatTitle(firstMessage: String): String {
        val maxLength = 50
        val trimmed = firstMessage.trim()
        return if (trimmed.length <= maxLength) {
            trimmed
        } else {
            trimmed.take(maxLength - 3) + "..."
        }
    }
    
    private suspend fun saveChat() {
        val messages = _messages.value
        if (messages.isEmpty()) return
        
        val chatId = currentChatId ?: UUID.randomUUID().toString()
        val title = if (currentChatId == null) {
            val firstUserMessage = messages.firstOrNull { it.isUser }?.text ?: "Новый чат"
            generateChatTitle(firstUserMessage)
        } else {
            val existingChat = repository.getChatById(chatId)
            existingChat?.title ?: "Новый чат"
        }
        
        val chat = Chat(
            id = chatId,
            title = title,
            messages = messages,
            updatedAt = currentTimeMillis()
        )
        
        repository.saveChat(chat)
        currentChatId = chatId
    }
    
        init {
        initializeServices()
        viewModelScope.launch {
            try {
                coordinatorAgent?.initialize()
                agentInstances.values.forEach { it.initialize() }
            } catch (e: Exception) {
                println("Failed to initialize AIAgents: ${e.message}")
                e.printStackTrace()
            }
        }
        
        // Подписываемся на изменения настроек
        settingsViewModel?.settings?.let { settingsFlow ->
            viewModelScope.launch {
                settingsFlow.collect { settings ->
                    // Пересоздаем сервисы при изменении критических настроек
                    initializeServices()
                    coordinatorAgent?.initialize()
                    agentInstances.values.forEach { it.initialize() }
                }
            }
        }
        
        // Подписываемся на изменения выбранных агентов
        viewModelScope.launch {
            _selectedAgents.collect {
                initializeServices()
                coordinatorAgent?.initialize()
                agentInstances.values.forEach { agent ->
                    try {
                        agent.initialize()
                    } catch (e: Exception) {
                        println("Failed to initialize agent: ${e.message}")
                        e.printStackTrace()
                    }
                }
            }
        }
        
        // Подписываемся на изменения состояния координатора
        viewModelScope.launch {
            _useCoordinator.collect {
                initializeServices()
                coordinatorAgent?.initialize()
            }
        }
    }
    
    fun sendMessage(text: String) {
        if (text.isBlank() || _isLoading.value) return
        
        val userMessage = ChatMessage(text = text, isUser = true)
        _messages.value = _messages.value + userMessage
        conversationHistory.add(DeepSeekMessage(role = "user", content = text))
        
        val selectedAgents = _selectedAgents.value
        if (selectedAgents.isEmpty()) {
            _isLoading.value = true
            viewModelScope.launch {
                try {
                    val settings = getSettings()
                    val defaultAgent = AIAgent(
                        deepSeek = deepSeek ?: DeepSeek(apiKey = settings.deepSeekApiKey),
                        ragService = ragService,
                        maxIterations = settings.maxIterations
                    )
                    defaultAgent.initialize()
                    val result = defaultAgent.processMessage(
                        userMessage = text,
                        conversationHistory = conversationHistory.toList(),
                        useRAG = _useRAG.value,
                        temperature = settings.temperature,
                        maxTokens = settings.maxTokens
                    )
                    val metricsSuffix = result.variants.firstOrNull()?.metadata?.let {
                        "\n\n[Reranker] $it"
                    } ?: ""
                    val messageText = result.response + metricsSuffix
                    val aiMessage = ChatMessage(
                        text = messageText,
                        isUser = false
                    )
                    _messages.value = _messages.value + aiMessage
                    conversationHistory.clear()
                    conversationHistory.addAll(result.updatedHistory)
                    
                    saveChat()
                    
                    if (_enableVoiceOutput.value && voiceOutputService.isSupported() && result.shortPhrase.isNotBlank()) {
                        launch {
                            voiceOutputService.speak(result.shortPhrase).onFailure { error ->
                                println("Voice output error: ${error.message}")
                            }
                        }
                    }
                } catch (e: Exception) {
                    val errorMessage = ChatMessage(
                        text = "Ошибка: ${e.message}",
                        isUser = false
                    )
                    _messages.value = _messages.value + errorMessage
                } finally {
                    _isLoading.value = false
                }
            }
            return
        }
        
        _isLoading.value = true
        
        viewModelScope.launch {
            try {
                val settings = getSettings()
                val conversationHistoryList = conversationHistory.toList()
                val useCoordinator = _useCoordinator.value
                
                val selectedAgent = if (useCoordinator) {
                    // Координатор выбирает наиболее подходящего агента
                    selectBestAgent(text, selectedAgents)
                } else {
                    // Если координатор отключен, используем первого агента
                    selectedAgents.firstOrNull()
                }
                
                if (selectedAgent == null) {
                    val errorMessage = ChatMessage(
                        text = "Ошибка: не удалось выбрать агента",
                        isUser = false
                    )
                    _messages.value = _messages.value + errorMessage
                    _isLoading.value = false
                    return@launch
                }
                
                // Обрабатываем сообщение выбранным агентом
                val agentInstance = getAgentInstance(selectedAgent.id)
                if (agentInstance != null) {
                    try {
                        val result = agentInstance.processMessage(
                            userMessage = text,
                            conversationHistory = conversationHistoryList,
                            useRAG = _useRAG.value,
                            temperature = settings.temperature,
                            maxTokens = settings.maxTokens
                        )
                        
                        val metricsSuffix = result.variants.firstOrNull()?.metadata?.let {
                            "\n\n[Reranker] $it"
                        } ?: ""
                        val messageText = result.response + metricsSuffix
                        val aiMessage = ChatMessage(
                            text = messageText,
                            isUser = false,
                            agentId = selectedAgent.id,
                            agentName = selectedAgent.name
                        )
                        _messages.value = _messages.value + aiMessage
                        
                        // Проверяем связи с другими агентами
                        val connections = connectionRepo.getConnectionsByAgent(selectedAgent.id)
                        if (connections.isNotEmpty()) {
                            connections.forEach { connection ->
                                val connectedAgent = selectedAgents.find { it.id == connection.targetAgentId }
                                if (connectedAgent != null) {
                                    val connectedAgentInstance = getAgentInstance(connectedAgent.id)
                                    if (connectedAgentInstance != null) {
                                        try {
                                            val reviewPrompt = when (connection.connectionType) {
                                                com.qualiorstudio.aiadventultimate.model.ConnectionType.REVIEW -> {
                                                    """
${connection.description}

Исходный запрос пользователя:
$text

Ответ агента "${selectedAgent.name}":
$messageText

Проанализируй ответ и предоставь свою оценку или улучшения.
                                                    """.trimIndent()
                                                }
                                                com.qualiorstudio.aiadventultimate.model.ConnectionType.VALIDATE -> {
                                                    """
${connection.description}

Исходный запрос пользователя:
$text

Ответ агента "${selectedAgent.name}":
$messageText

Проверь правильность и корректность ответа.
                                                    """.trimIndent()
                                                }
                                                com.qualiorstudio.aiadventultimate.model.ConnectionType.ENHANCE -> {
                                                    """
${connection.description}

Исходный запрос пользователя:
$text

Ответ агента "${selectedAgent.name}":
$messageText

Улучши или дополни этот ответ.
                                                    """.trimIndent()
                                                }
                                                com.qualiorstudio.aiadventultimate.model.ConnectionType.COLLABORATE -> {
                                                    """
${connection.description}

Исходный запрос пользователя:
$text

Ответ агента "${selectedAgent.name}":
$messageText

Добавь свой вклад к этому ответу.
                                                    """.trimIndent()
                                                }
                                            }
                                            
                                            val reviewResult = connectedAgentInstance.processMessage(
                                                userMessage = reviewPrompt,
                                                conversationHistory = emptyList(),
                                                useRAG = false,
                                                temperature = settings.temperature,
                                                maxTokens = settings.maxTokens
                                            )
                                            
                                            val reviewMessage = ChatMessage(
                                                text = reviewResult.response,
                                                isUser = false,
                                                agentId = connectedAgent.id,
                                                agentName = "${connectedAgent.name} (${connection.connectionType.name.lowercase()})"
                                            )
                                            _messages.value = _messages.value + reviewMessage
                                        } catch (e: Exception) {
                                            println("Error processing connection review: ${e.message}")
                                        }
                                    }
                                }
                            }
                        }
                        
                        conversationHistory.clear()
                        conversationHistory.addAll(result.updatedHistory)
                        
                        saveChat()
                        
                        if (_enableVoiceOutput.value && voiceOutputService.isSupported() && result.shortPhrase.isNotBlank()) {
                            launch {
                                voiceOutputService.speak(result.shortPhrase).onFailure { error ->
                                    println("Voice output error: ${error.message}")
                                }
                            }
                        }
                    } catch (e: Exception) {
                        println("Error processing message for agent ${selectedAgent.name}: ${e.message}")
                        val errorMessage = ChatMessage(
                            text = "Ошибка при обработке сообщения агентом ${selectedAgent.name}: ${e.message}",
                            isUser = false,
                            agentId = selectedAgent.id,
                            agentName = selectedAgent.name
                        )
                        _messages.value = _messages.value + errorMessage
                    }
                } else {
                    val errorMessage = ChatMessage(
                        text = "Ошибка: агент ${selectedAgent.name} не инициализирован",
                        isUser = false,
                        agentId = selectedAgent.id,
                        agentName = selectedAgent.name
                    )
                    _messages.value = _messages.value + errorMessage
                }
            } catch (e: Exception) {
                val errorMessage = ChatMessage(
                    text = "Ошибка: ${e.message}",
                    isUser = false
                )
                _messages.value = _messages.value + errorMessage
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    fun clearChat() {
        viewModelScope.launch {
            currentChatId = null
            _messages.value = emptyList()
            conversationHistory.clear()
        }
    }
    
    fun toggleRAG() {
        _useRAG.value = !_useRAG.value
    }
    
    fun setUseRAG(enabled: Boolean) {
        _useRAG.value = enabled
    }
    
    fun setEnableVoiceOutput(enabled: Boolean) {
        _enableVoiceOutput.value = enabled
    }
    
    fun setSelectedAgents(agents: List<Agent>) {
        _selectedAgents.value = agents
    }
    
    fun addSelectedAgent(agent: Agent) {
        val currentAgents = _selectedAgents.value.toMutableList()
        if (!currentAgents.any { it.id == agent.id }) {
            currentAgents.add(agent)
            _selectedAgents.value = currentAgents
        }
    }
    
    fun removeSelectedAgent(agentId: String) {
        val currentAgents = _selectedAgents.value.toMutableList()
        currentAgents.removeAll { it.id == agentId }
        _selectedAgents.value = currentAgents
    }
    
    fun clearSelectedAgents() {
        _selectedAgents.value = emptyList()
    }
    
    fun setUseCoordinator(enabled: Boolean) {
        _useCoordinator.value = enabled
    }
    
    fun toggleCoordinator() {
        _useCoordinator.value = !_useCoordinator.value
    }
    
    override fun onCleared() {
        super.onCleared()
        voiceOutputService.stopSpeaking()
        coordinatorAgent?.close()
        agentInstances.values.forEach { it.close() }
        ragService?.close()
    }
}

