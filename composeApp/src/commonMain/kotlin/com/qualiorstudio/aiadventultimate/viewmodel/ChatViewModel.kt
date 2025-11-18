package com.qualiorstudio.aiadventultimate.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qualiorstudio.aiadventultimate.ai.AIAgent
import com.qualiorstudio.aiadventultimate.api.DeepSeek
import com.qualiorstudio.aiadventultimate.api.DeepSeekMessage
import com.qualiorstudio.aiadventultimate.mcp.TodoistClient
import com.qualiorstudio.aiadventultimate.model.ChatMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ChatViewModel : ViewModel() {
    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val deepSeekApiKey = "API_KEY"
    private val todoistApiToken = "TOKEN"
    
    private val deepSeek = DeepSeek(apiKey = deepSeekApiKey, model = "deepseek-chat")
    private val todoistClient = TodoistClient(apiToken = todoistApiToken)
    private val aiAgent = AIAgent(deepSeek = deepSeek, todoistClient = todoistClient)

    fun sendMessage(userMessage: String) {
        if (userMessage.isBlank()) return

        val userChatMessage = ChatMessage(text = userMessage, isUser = true)
        _messages.value = _messages.value + userChatMessage

        _isLoading.value = true

        viewModelScope.launch {
            try {
                val conversationHistory = _messages.value
                    .filter { !it.isUser || it != userChatMessage }
                    .map { message ->
                        DeepSeekMessage(
                            role = if (message.isUser) "user" else "assistant",
                            content = message.text
                        )
                    }

                val response = aiAgent.processMessage(userMessage, conversationHistory)

                val assistantMessage = ChatMessage(text = response, isUser = false)
                _messages.value = _messages.value + assistantMessage
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
    }
}

