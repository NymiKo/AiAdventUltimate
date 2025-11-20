package com.qualiorstudio.aiadventultimate.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qualiorstudio.aiadventultimate.ai.AIAgent
import com.qualiorstudio.aiadventultimate.api.DeepSeek
import com.qualiorstudio.aiadventultimate.api.DeepSeekMessage
import com.qualiorstudio.aiadventultimate.mcp.McpClient
import com.qualiorstudio.aiadventultimate.model.ChatMessage
import com.qualiorstudio.aiadventultimate.model.Notification
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class ChatViewModel : ViewModel() {
    private val deepSeekApiKey = "sk-b21fe1a6400b4757840a27ebc1a5de2a"
    private val todoistApiToken = "76ac119dd13a2876d041926445ff1156fda25419"
    
    private val mcpClient = McpClient(
        command = "/Users/dmitry/IdeaProjects/MCP-Tick-Tick/run-mcp-server.sh",
        env = mapOf("TODOIST_API_TOKEN" to todoistApiToken)
    )
    
    private val deepSeek = DeepSeek(apiKey = deepSeekApiKey)
    private val aiAgent = AIAgent(deepSeek, mcpClient)
    
    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val _notifications = MutableStateFlow<List<Notification>>(emptyList())
    val notifications: StateFlow<List<Notification>> = _notifications.asStateFlow()
    
    private var conversationHistory = mutableListOf<DeepSeekMessage>()
    private var periodicJob: Job? = null
    
    init {
        viewModelScope.launch {
            try {
                aiAgent.initialize()
                startPeriodicTaskSummary()
            } catch (e: Exception) {
                println("Failed to initialize AIAgent: ${e.message}")
                e.printStackTrace()
            }
        }
    }
    
    private fun startPeriodicTaskSummary() {
        periodicJob?.cancel()
        periodicJob = viewModelScope.launch {
            while (true) {
                delay(100_000)
                fetchTodayTaskSummary()
            }
        }
    }
    
    private suspend fun fetchTodayTaskSummary() {
        try {
            val summary = aiAgent.getTodayTaskSummary()
            if (summary.isNotBlank()) {
                val existingNotification = _notifications.value.find { 
                    it.title == "Итоги по задачам на сегодня" 
                }
                
                val notification = if (existingNotification != null) {
                    existingNotification.copy(content = summary)
                } else {
                    Notification(
                        id = System.currentTimeMillis().toString(),
                        title = "Итоги по задачам на сегодня",
                        content = summary,
                        isExpanded = false
                    )
                }
                
                _notifications.value = if (existingNotification != null) {
                    _notifications.value.map { if (it.id == existingNotification.id) notification else it }
                } else {
                    _notifications.value + notification
                }
            }
        } catch (e: Exception) {
            println("Failed to fetch today task summary: ${e.message}")
            e.printStackTrace()
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
                val result = aiAgent.processMessage(text, conversationHistory.toList())
                val aiMessage = ChatMessage(text = result.response, isUser = false)
                _messages.value = _messages.value + aiMessage
                conversationHistory.clear()
                conversationHistory.addAll(result.updatedHistory)
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
    
    fun toggleNotificationExpanded(notificationId: String) {
        _notifications.value = _notifications.value.map { notification ->
            if (notification.id == notificationId) {
                notification.copy(isExpanded = !notification.isExpanded)
            } else {
                notification
            }
        }
    }
    
    fun dismissNotification(notificationId: String) {
        _notifications.value = _notifications.value.filter { it.id != notificationId }
    }
    
    override fun onCleared() {
        super.onCleared()
        periodicJob?.cancel()
        aiAgent.close()
    }
}

