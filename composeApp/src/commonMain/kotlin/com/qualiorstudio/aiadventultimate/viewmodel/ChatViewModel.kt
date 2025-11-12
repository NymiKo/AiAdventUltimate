package com.qualiorstudio.aiadventultimate.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qualiorstudio.aiadventultimate.api.MessageInfo
import com.qualiorstudio.aiadventultimate.api.YandexGPT
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

    private val apiKey = "API_KEY"
    private val folderId = "FOLDER_ID"
    private val yandexGPT = YandexGPT(apiKey, folderId)

    fun sendMessage(userMessage: String) {
        if (userMessage.isBlank()) return

        _isLoading.value = true

        viewModelScope.launch {
            try {
                val userChatMessage = ChatMessage(text = userMessage, isUser = true)
                _messages.value = _messages.value + userChatMessage

                val userMessageIndex = _messages.value.lastIndex
                val userTokens = runCatching { yandexGPT.countTokens(userMessage) }.getOrNull()
                if (userTokens != null && userTokens > 0) {
                    val updatedList = _messages.value.toMutableList()
                    if (userMessageIndex in updatedList.indices) {
                        updatedList[userMessageIndex] =
                            updatedList[userMessageIndex].copy(tokensUsed = userTokens)
                        _messages.value = updatedList
                    }
                }

                val apiMessages = _messages.value.map { message ->
                    MessageInfo(
                        role = if (message.isUser) "user" else "assistant",
                        text = message.text
                    )
                }

                val response = yandexGPT.sendMessage(apiMessages)

                val assistantTokens =
                    runCatching { yandexGPT.countTokens(response.answer) }.getOrElse { 0 }

                val assistantMessage = ChatMessage(
                    text = response.answer,
                    isUser = false,
                    originalQuestion = response.question.ifBlank { userMessage },
                    tokensUsed = assistantTokens
                )
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

