package com.qualiorstudio.aiadventultimate.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qualiorstudio.aiadventultimate.ai.AIAgent
import com.qualiorstudio.aiadventultimate.ai.RAGService
import com.qualiorstudio.aiadventultimate.api.DeepSeek
import com.qualiorstudio.aiadventultimate.api.DeepSeekMessage
import com.qualiorstudio.aiadventultimate.model.ChatMessage
import com.qualiorstudio.aiadventultimate.voice.createVoiceOutputService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ChatViewModel(
    private val settingsViewModel: SettingsViewModel? = null
) : ViewModel() {
    private val voiceOutputService = createVoiceOutputService()
    
    private var deepSeek: DeepSeek? = null
    private var ragService: RAGService? = null
    private var aiAgent: AIAgent? = null
    private var lastApiKey: String? = null
    private var lastLmStudioUrl: String? = null
    private var lastTopK: Int? = null
    private var lastRerankMinScore: Double? = null
    private var lastRerankedRetentionRatio: Double? = null
    private var lastMaxIterations: Int? = null
    
    private fun getSettings() = settingsViewModel?.settings?.value ?: com.qualiorstudio.aiadventultimate.model.AppSettings()
    
    private fun initializeServices() {
        val settings = getSettings()
        
        // Проверяем, нужно ли пересоздавать сервисы
        val needsRecreation = deepSeek == null || 
            ragService == null || 
            aiAgent == null ||
            lastApiKey != settings.deepSeekApiKey ||
            lastLmStudioUrl != settings.lmStudioBaseUrl ||
            lastTopK != settings.ragTopK ||
            lastRerankMinScore != settings.rerankMinScore ||
            lastRerankedRetentionRatio != settings.rerankedRetentionRatio ||
            lastMaxIterations != settings.maxIterations
        
        if (!needsRecreation) {
            return
        }
        
        // Закрываем старые сервисы
        aiAgent?.close()
        ragService?.close()
        deepSeek = null
        
        // Сохраняем текущие настройки
        lastApiKey = settings.deepSeekApiKey
        lastLmStudioUrl = settings.lmStudioBaseUrl
        lastTopK = settings.ragTopK
        lastRerankMinScore = settings.rerankMinScore
        lastRerankedRetentionRatio = settings.rerankedRetentionRatio
        lastMaxIterations = settings.maxIterations
        
        // Создаем новые сервисы с настройками
        deepSeek = DeepSeek(apiKey = settings.deepSeekApiKey)
        ragService = RAGService(
            lmStudioBaseUrl = settings.lmStudioBaseUrl,
            topK = settings.ragTopK,
            rerankMinScore = settings.rerankMinScore,
            rerankedRetentionRatio = settings.rerankedRetentionRatio
        )
        aiAgent = AIAgent(
            deepSeek = deepSeek!!,
            ragService = ragService,
            maxIterations = settings.maxIterations
        )
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
    
    init {
        initializeServices()
        viewModelScope.launch {
            try {
                aiAgent?.initialize()
            } catch (e: Exception) {
                println("Failed to initialize AIAgent: ${e.message}")
                e.printStackTrace()
            }
        }
        
        // Подписываемся на изменения настроек
        settingsViewModel?.settings?.let { settingsFlow ->
            viewModelScope.launch {
                settingsFlow.collect { settings ->
                    // Пересоздаем сервисы при изменении критических настроек
                    initializeServices()
                    aiAgent?.initialize()
                }
            }
        }
    }
    
    fun sendMessage(text: String) {
        if (text.isBlank() || _isLoading.value) return
        
        val userMessage = ChatMessage(text = text, isUser = true)
        _messages.value = _messages.value + userMessage
        conversationHistory.add(DeepSeekMessage(role = "user", content = text))
        
        _isLoading.value = true
        
        viewModelScope.launch {
            try {
                val settings = getSettings()
                val result = aiAgent?.processMessage(
                    userMessage = text,
                    conversationHistory = conversationHistory.toList(),
                    useRAG = _useRAG.value,
                    temperature = settings.temperature,
                    maxTokens = settings.maxTokens
                ) ?: throw Exception("AI Agent не инициализирован")
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
    }
    
    fun clearChat() {
        _messages.value = emptyList()
        conversationHistory.clear()
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
    
    override fun onCleared() {
        super.onCleared()
        voiceOutputService.stopSpeaking()
        aiAgent?.close()
        ragService?.close()
    }
}

