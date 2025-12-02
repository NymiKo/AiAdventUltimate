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
import com.qualiorstudio.aiadventultimate.model.Commands
import com.qualiorstudio.aiadventultimate.repository.AgentConnectionRepository
import com.qualiorstudio.aiadventultimate.repository.AgentConnectionRepositoryImpl
import com.qualiorstudio.aiadventultimate.repository.ChatRepository
import com.qualiorstudio.aiadventultimate.repository.ChatRepositoryImpl
import com.qualiorstudio.aiadventultimate.repository.MCPServerRepository
import com.qualiorstudio.aiadventultimate.repository.MCPServerRepositoryImpl
import com.qualiorstudio.aiadventultimate.repository.DEFAULT_GITHUB_MCP_SERVER_ID
import com.qualiorstudio.aiadventultimate.mcp.createMCPServerManager
import com.qualiorstudio.aiadventultimate.utils.currentTimeMillis
import com.qualiorstudio.aiadventultimate.voice.createVoiceOutputService
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID

class ChatViewModel(
    private val settingsViewModel: SettingsViewModel? = null,
    private val chatRepository: ChatRepository? = null,
    private val connectionRepository: AgentConnectionRepository? = null,
    private val mcpServerRepository: MCPServerRepository? = null,
    private val projectRepository: com.qualiorstudio.aiadventultimate.repository.ProjectRepository? = null,
    private val embeddingViewModel: EmbeddingViewModel? = null
) : ViewModel() {
    private val repository = chatRepository ?: ChatRepositoryImpl()
    private val connectionRepo = connectionRepository ?: AgentConnectionRepositoryImpl()
    private val projectRepo = projectRepository ?: com.qualiorstudio.aiadventultimate.repository.ProjectRepositoryImpl()
    private val voiceOutputService = createVoiceOutputService()
    private val mcpManager = createMCPServerManager()
    
    private var deepSeek: DeepSeek? = null
    private var ragService: RAGService? = null
    private val agentInstances = mutableMapOf<String, AIAgent>()
    private var coordinatorAgent: AIAgent? = null
    private var mcpInitialized = false
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
    
    val currentProject: StateFlow<com.qualiorstudio.aiadventultimate.model.Project?> = projectRepo.currentProject
    
    private val _currentBranch = MutableStateFlow<String?>(null)
    val currentBranch: StateFlow<String?> = _currentBranch.asStateFlow()
    
    private val _githubBranchInfo = MutableStateFlow<com.qualiorstudio.aiadventultimate.service.GitHubBranchInfo?>(null)
    val githubBranchInfo: StateFlow<com.qualiorstudio.aiadventultimate.service.GitHubBranchInfo?> = _githubBranchInfo.asStateFlow()
    
    private val gitBranchService = com.qualiorstudio.aiadventultimate.service.createGitBranchService()
    private var lastHeadModified: Long? = null
    private var branchCheckJob: Job? = null
    
    init {
        viewModelScope.launch {
            currentProject.collect { project ->
                branchCheckJob?.cancel()
                if (project != null) {
                    updateCurrentBranch(project.path)
                    startBranchMonitoring(project.path)
                } else {
                    _currentBranch.value = null
                    lastHeadModified = null
                }
            }
        }
    }
    
    private fun startBranchMonitoring(projectPath: String) {
        branchCheckJob = viewModelScope.launch {
            while (isActive) {
                try {
                    val currentModified = gitBranchService.getHeadFileLastModified(projectPath)
                    if (currentModified != null && currentModified != lastHeadModified) {
                        lastHeadModified = currentModified
                        updateCurrentBranch(projectPath)
                    }
                } catch (e: Exception) {
                    println("Ошибка при проверке изменений ветки: ${e.message}")
                }
                kotlinx.coroutines.delay(1000)
            }
        }
    }
    
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
            
            // Инициализируем MCP серверы
            val mcpRepo = mcpServerRepository ?: MCPServerRepositoryImpl()
            viewModelScope.launch {
                try {
                    mcpManager.initializeServers(mcpRepo)
                    mcpInitialized = true
                    val tools = mcpManager.getAvailableTools()
                    println("✓ MCP серверы инициализированы. Доступно инструментов: ${tools.size}")
                    tools.forEach { tool ->
                        println("  - ${tool.function.name}: ${tool.function.description}")
                    }
                    // Переинициализируем всех агентов после загрузки MCP инструментов
                    coordinatorAgent?.initialize()
                    agentInstances.values.forEach { it.initialize() }
                } catch (e: Exception) {
                    println("Ошибка инициализации MCP серверов: ${e.message}")
                    e.printStackTrace()
                    mcpInitialized = true
                }
            }
        }
        
        // Создаем ProjectTools если есть открытый проект
        val projectTools = currentProject.value?.let { 
            com.qualiorstudio.aiadventultimate.ai.ProjectTools(it)
        }
        
        // Создаем экземпляр координатора, если есть выбранные агенты и координатор включен
        val selectedAgents = _selectedAgents.value
        val useCoordinator = _useCoordinator.value
        val currentProjectContext = if (_githubBranchInfo.value?.isGitHubRepo == true && 
            _githubBranchInfo.value?.owner != null && 
            _githubBranchInfo.value?.repo != null) {
            val branchInfo = _githubBranchInfo.value!!
            """
PROJECT CONTEXT - GITHUB REPOSITORY:
The current project is connected to a GitHub repository:
- Repository owner: ${branchInfo.owner}
- Repository name: ${branchInfo.repo}
- Current branch: ${branchInfo.branch}
- Full repository path: ${branchInfo.owner}/${branchInfo.repo}

IMPORTANT: When the user asks questions about the project, repository, code, pull requests, issues, or any GitHub-related information, you MUST use the following repository information:
- Repository owner: ${branchInfo.owner}
- Repository name: ${branchInfo.repo}

When calling GitHub MCP tools, ALWAYS use these exact values for the "owner" and "repo" parameters. Do NOT ask the user for this information - it is already provided in this context.

You can use GitHub MCP tools to:
- Search for files, issues, pull requests, or discussions in this repository
- Read repository files, issues, or pull requests
- Get information about the repository structure
- Search for code, commits, or other repository content
- List open pull requests, issues, etc.

Example: If the user asks "What open PRs does this project have?", you should immediately use GitHub MCP tools with owner="${branchInfo.owner}" and repo="${branchInfo.repo}" to get the information.
            """.trimIndent()
        } else {
            ""
        }
        
        if (selectedAgents.isNotEmpty() && useCoordinator && coordinatorAgent == null) {
            val newCoordinator = AIAgent(
                deepSeek = deepSeek!!,
                ragService = null,
                maxIterations = settings.maxIterations,
                customSystemPrompt = coordinatorSystemPrompt,
                mcpServerManager = mcpManager,
                projectTools = projectTools
            )
            newCoordinator.updateProjectContext(currentProjectContext)
            coordinatorAgent = newCoordinator
        } else if (selectedAgents.isEmpty() || !useCoordinator) {
            coordinatorAgent?.close()
            coordinatorAgent = null
        } else if (coordinatorAgent != null) {
            coordinatorAgent?.updateProjectContext(currentProjectContext)
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
                val newAgent = AIAgent(
                    deepSeek = deepSeek!!,
                    ragService = ragService,
                    maxIterations = settings.maxIterations,
                    customSystemPrompt = agent.systemPrompt,
                    mcpServerManager = mcpManager,
                    projectTools = projectTools
                )
                newAgent.updateProjectContext(currentProjectContext)
                agentInstances[agent.id] = newAgent
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
                // Ждем инициализации MCP серверов перед инициализацией агентов
                delay(2000)
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
        
        if (text.startsWith("/")) {
            handleCommand(text)
            return
        }
        
        val userMessage = ChatMessage(text = text, isUser = true)
        _messages.value = _messages.value + userMessage
        conversationHistory.add(DeepSeekMessage(role = "user", content = text))
        
        val selectedAgents = _selectedAgents.value
        if (selectedAgents.isEmpty()) {
            _isLoading.value = true
            viewModelScope.launch {
                try {
                    val settings = getSettings()
                    val projectTools = currentProject.value?.let { 
                        com.qualiorstudio.aiadventultimate.ai.ProjectTools(it)
                    }
                    val defaultAgent = AIAgent(
                        deepSeek = deepSeek ?: DeepSeek(apiKey = settings.deepSeekApiKey),
                        ragService = ragService,
                        maxIterations = settings.maxIterations,
                        mcpServerManager = mcpManager,
                        projectTools = projectTools
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
    
    private fun handleCommand(commandText: String) {
        val parts = commandText.substring(1).trim().split(" ", limit = 2)
        val commandName = parts[0].lowercase()
        val commandArgs = parts.getOrNull(1) ?: ""
        
        when (commandName) {
            "help" -> handleHelpCommand(commandArgs)
            else -> {
                val errorMessage = ChatMessage(
                    text = "Неизвестная команда: /$commandName. Доступные команды: /help",
                    isUser = false
                )
                _messages.value = _messages.value + errorMessage
            }
        }
    }
    
    private fun handleHelpCommand(query: String) {
        val userMessage = ChatMessage(text = "/help${if (query.isNotBlank()) " $query" else ""}", isUser = true)
        _messages.value = _messages.value + userMessage
        
        _isLoading.value = true
        viewModelScope.launch {
            try {
                val settings = getSettings()
                val projectTools = currentProject.value?.let { 
                    com.qualiorstudio.aiadventultimate.ai.ProjectTools(it)
                }
                
                val helpSystemPrompt = """
Ты помощник по проекту AI Advent Ultimate. Твоя задача - помогать пользователям разобраться в проекте и отвечать на их вопросы.

ИНФОРМАЦИЯ О ПРОЕКТЕ:

AI Advent Ultimate — это кросс-платформенное приложение AI чат-бота с поддержкой:
- Мультиагентной системы — создание и управление специализированными AI агентами
- RAG (Retrieval-Augmented Generation) — поиск релевантной информации из базы знаний
- Голосового ввода и вывода — распознавание речи и синтез голоса (Desktop)
- Кросс-платформенности — Android, iOS, Desktop (JVM)

ОСНОВНЫЕ ВОЗМОЖНОСТИ:

1. Текстовый чат с AI
   - Поддержка нескольких AI провайдеров (DeepSeek, Yandex GPT)
   - Настройка параметров генерации (temperature, maxTokens)
   - История сообщений с сохранением в локальное хранилище

2. Мультиагентная система
   - Создание специализированных AI агентов с кастомными промптами
   - Агент-координатор для автоматического выбора подходящего агента
   - Связи между агентами (REVIEW, VALIDATE, ENHANCE, COLLABORATE)
   - Параллельная работа нескольких агентов

3. RAG (Retrieval-Augmented Generation)
   - Индексация документов (HTML, PDF) в векторное хранилище
   - Поиск релевантных фрагментов по семантическому сходству
   - Reranking для улучшения качества результатов
   - Интеграция с LM Studio для генерации эмбеддингов

4. Голосовой ввод (Desktop)
   - Распознавание речи через Yandex SpeechKit STT
   - Поддержка русского языка
   - Запись аудио с микрофона

5. Голосовой вывод (Desktop)
   - Синтез речи голосом Джарвиса через Yandex SpeechKit TTS
   - Автоматическое озвучивание ответов AI
   - Генерация кратких фраз для озвучивания

ТЕХНОЛОГИЧЕСКИЙ СТЕК:
- Kotlin Multiplatform — кроссплатформенная разработка
- Compose Multiplatform — декларативный UI фреймворк
- Ktor — HTTP клиент и сервер
- Kotlinx Serialization — сериализация данных
- Coroutines — асинхронное программирование
- StateFlow — реактивное управление состоянием

ВНЕШНИЕ СЕРВИСЫ:
- DeepSeek API — основной AI провайдер
- Yandex SpeechKit — распознавание и синтез речи
- Yandex GPT — альтернативный AI провайдер
- LM Studio — локальный сервер для эмбеддингов

ИСПОЛЬЗОВАНИЕ:

Работа с агентами:
1. Нажмите на иконку "Агенты" в верхней панели
2. Создайте нового агента с кастомным промптом
3. Выберите агентов для работы
4. Включите/выключите координатор

Использование RAG:
1. Перейдите в раздел "Эмбеддинги"
2. Загрузите документы (HTML, PDF)
3. Дождитесь индексации
4. Используйте RAG в чате (включите в настройках)

Голосовой ввод (Desktop):
1. Убедитесь, что микрофон подключен
2. Нажмите на иконку микрофона
3. Говорите в микрофон
4. Нажмите красную кнопку для остановки
5. Текст автоматически появится в поле ввода

НАСТРОЙКИ:
- Темная тема — переключение темы интерфейса
- RAG — включение/выключение поиска по базе знаний
- Голосовой ввод — включение/выключение распознавания речи
- Голосовой вывод — включение/выключение синтеза речи
- API ключи — настройка ключей для внешних сервисов
- Параметры модели — temperature, maxTokens
- Параметры RAG — topK, rerankMinScore, rerankedRetentionRatio

${if (query.isNotBlank()) {
    """
    
ПОЛЬЗОВАТЕЛЬ ЗАДАЛ ВОПРОС: $query

Ответь на вопрос пользователя, используя информацию о проекте выше. Будь полезным и конкретным.
    """.trimIndent()
} else {
    """
    
Пользователь запросил помощь. Предоставь краткое описание проекта и основных возможностей. Если пользователь задаст конкретный вопрос, ответь на него подробно.
    """.trimIndent()
}}
                """.trimIndent()
                
                val defaultAgent = AIAgent(
                    deepSeek = deepSeek ?: DeepSeek(apiKey = settings.deepSeekApiKey),
                    ragService = null,
                    maxIterations = 5,
                    customSystemPrompt = helpSystemPrompt,
                    mcpServerManager = null,
                    projectTools = null
                )
                defaultAgent.initialize()
                
                val userQuery = if (query.isNotBlank()) {
                    query
                } else {
                    "Расскажи о проекте AI Advent Ultimate и его основных возможностях"
                }
                
                val result = defaultAgent.processMessage(
                    userMessage = userQuery,
                    conversationHistory = emptyList(),
                    useRAG = false,
                    temperature = settings.temperature,
                    maxTokens = settings.maxTokens
                )
                
                val aiMessage = ChatMessage(
                    text = result.response,
                    isUser = false
                )
                _messages.value = _messages.value + aiMessage
                
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
                    text = "Ошибка при обработке команды /help: ${e.message}",
                    isUser = false
                )
                _messages.value = _messages.value + errorMessage
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    fun openProject(path: String) {
        viewModelScope.launch {
            try {
                projectRepo.openProject(path)
                
                currentProject.value?.let { project ->
                    val mcpRepo = mcpServerRepository ?: MCPServerRepositoryImpl()
                    val servers = mcpRepo.getAllServers()
                    val hasGitHubServer = servers.any { 
                        it.id == DEFAULT_GITHUB_MCP_SERVER_ID && it.enabled 
                    }
                    
                    if (hasGitHubServer) {
                        updateCurrentBranch(project.path)
                    }
                    
                    initializeServices()
                    coordinatorAgent?.initialize()
                    agentInstances.values.forEach { it.initialize() }
                    
                    indexProjectMarkdownFiles(project)
                    
                    if (hasGitHubServer) {
                        checkGitHubConnection()
                    }
                }
            } catch (e: Exception) {
                println("Error opening project: ${e.message}")
                e.printStackTrace()
            }
        }
    }
    
    private suspend fun updateCurrentBranch(projectPath: String) {
        try {
            val branchInfo = gitBranchService.getGitHubBranchInfo(projectPath, mcpManager)
            _currentBranch.value = branchInfo?.branch
            _githubBranchInfo.value = branchInfo
            
            if (branchInfo?.isGitHubRepo == true && branchInfo.owner != null && branchInfo.repo != null) {
                updateAgentsProjectContext(branchInfo.owner, branchInfo.repo, branchInfo.branch)
            } else {
                clearAgentsProjectContext()
            }
        } catch (e: Exception) {
            println("Ошибка при получении текущей ветки: ${e.message}")
            _currentBranch.value = null
            _githubBranchInfo.value = null
            clearAgentsProjectContext()
        }
    }
    
    private fun updateAgentsProjectContext(owner: String, repo: String, branch: String) {
        val context = """
PROJECT CONTEXT - GITHUB REPOSITORY:
The current project is connected to a GitHub repository:
- Repository owner: $owner
- Repository name: $repo
- Current branch: $branch
- Full repository path: $owner/$repo

IMPORTANT: When the user asks questions about the project, repository, code, pull requests, issues, or any GitHub-related information, you MUST use the following repository information:
- Repository owner: $owner
- Repository name: $repo

When calling GitHub MCP tools, ALWAYS use these exact values for the "owner" and "repo" parameters. Do NOT ask the user for this information - it is already provided in this context.

You can use GitHub MCP tools to:
- Search for files, issues, pull requests, or discussions in this repository
- Read repository files, issues, or pull requests
- Get information about the repository structure
- Search for code, commits, or other repository content
- List open pull requests, issues, etc.

Example: If the user asks "What open PRs does this project have?", you should immediately use GitHub MCP tools with owner="$owner" and repo="$repo" to get the information.
        """.trimIndent()
        
        println("=== Updating agents project context ===")
        println("Owner: $owner, Repo: $repo, Branch: $branch")
        println("Coordinator agent exists: ${coordinatorAgent != null}")
        println("Agent instances count: ${agentInstances.size}")
        
        coordinatorAgent?.updateProjectContext(context)
        agentInstances.values.forEach { it.updateProjectContext(context) }
        
        println("Project context updated for all agents")
    }
    
    private fun clearAgentsProjectContext() {
        coordinatorAgent?.updateProjectContext("")
        agentInstances.values.forEach { it.updateProjectContext("") }
    }
    
    suspend fun getBranches(): com.qualiorstudio.aiadventultimate.service.BranchList? {
        val project = currentProject.value ?: return null
        return gitBranchService.getBranches(project.path, mcpManager)
    }
    
    suspend fun checkGitHubConnection() {
        val project = currentProject.value ?: run {
            _githubBranchInfo.value = null
            _currentBranch.value = null
            clearAgentsProjectContext()
            return
        }
        viewModelScope.launch {
            try {
                val branchInfo = gitBranchService.getGitHubBranchInfo(project.path, mcpManager)
                _githubBranchInfo.value = branchInfo
                _currentBranch.value = branchInfo?.branch
                
                if (branchInfo?.isGitHubRepo == true && branchInfo.owner != null && branchInfo.repo != null) {
                    updateAgentsProjectContext(branchInfo.owner, branchInfo.repo, branchInfo.branch)
                } else {
                    clearAgentsProjectContext()
                }
            } catch (e: Exception) {
                println("Ошибка при проверке подключения к GitHub: ${e.message}")
                _githubBranchInfo.value = null
                clearAgentsProjectContext()
            }
        }
    }
    
    fun clearGitHubInfo() {
        _githubBranchInfo.value = null
    }
    
    private fun indexProjectMarkdownFiles(project: com.qualiorstudio.aiadventultimate.model.Project) {
        viewModelScope.launch {
            try {
                println("🔍 Поиск файлов в проекте...")
                val projectFiles = com.qualiorstudio.aiadventultimate.utils.ProjectScanner.findProjectFiles(project)
                
                if (projectFiles.isEmpty()) {
                    println("📭 Файлы не найдены")
                    return@launch
                }
                
                println("📚 Найдено ${projectFiles.size} файлов. Начинаю индексацию...")
                
                embeddingViewModel?.let { vm ->
                    val result = vm.processHtmlFiles(projectFiles)
                    result.onSuccess { chunksCount ->
                        println("✅ Успешно проиндексировано ${projectFiles.size} файлов ($chunksCount чанков)")
                    }.onFailure { error ->
                        println("❌ Ошибка индексации: ${error.message}")
                    }
                } ?: run {
                    println("⚠️ EmbeddingViewModel не доступна для индексации")
                }
            } catch (e: Exception) {
                println("❌ Ошибка при индексации файлов проекта: ${e.message}")
                e.printStackTrace()
            }
        }
    }
    
    fun closeProject() {
        viewModelScope.launch {
            try {
                projectRepo.closeProject()
                _currentBranch.value = null
                _githubBranchInfo.value = null
                clearAgentsProjectContext()
                initializeServices()
                coordinatorAgent?.initialize()
                agentInstances.values.forEach { it.initialize() }
            } catch (e: Exception) {
                println("Error closing project: ${e.message}")
                e.printStackTrace()
            }
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        voiceOutputService.stopSpeaking()
        coordinatorAgent?.close()
        agentInstances.values.forEach { it.close() }
        ragService?.close()
    }
}

