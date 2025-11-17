package com.qualiorstudio.aiadventultimate.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qualiorstudio.aiadventultimate.api.MessageInfo
import com.qualiorstudio.aiadventultimate.api.YandexGPT
import com.qualiorstudio.aiadventultimate.model.Chat
import com.qualiorstudio.aiadventultimate.model.ChatMessage
import com.qualiorstudio.aiadventultimate.repository.ChatRepository
import com.qualiorstudio.aiadventultimate.repository.ChatRepositoryImpl
import com.qualiorstudio.aiadventultimate.utils.currentTimeMillis
import com.qualiorstudio.aiadventultimate.utils.generateUUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ChatViewModel(
    private val chatRepository: ChatRepository = ChatRepositoryImpl(),
    chatId: String? = null
) : ViewModel() {
    private val currentChatId = chatId ?: generateUUID()
    
    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val _chatTitle = MutableStateFlow<String?>(null)
    val chatTitle: StateFlow<String?> = _chatTitle.asStateFlow()
    
    private var existingChat: Chat? = null

    private val apiKey = "API_KEY"
    private val folderId = "FOLDER_ID"
    private val yandexGPT = YandexGPT(apiKey, folderId)

    init {
        loadChat()
    }
    
    private fun loadChat() {
        viewModelScope.launch {
            val chat = chatRepository.getChatById(currentChatId)
            if (chat != null) {
                existingChat = chat
                _messages.value = chat.messages
                _chatTitle.value = chat.title
            }
        }
    }
    
    private suspend fun saveChat() {
        val title = _chatTitle.value ?: generateTitle()
        _chatTitle.value = title
        
        val now = currentTimeMillis()
        val createdAt = existingChat?.createdAt ?: now
        
        val chat = Chat(
            id = currentChatId,
            title = title,
            messages = _messages.value,
            createdAt = createdAt,
            updatedAt = now
        )
        existingChat = chat
        chatRepository.saveChat(chat)
    }
    
    private fun generateTitle(): String {
        val firstUserMessage = _messages.value.firstOrNull { it.isUser }?.text
        return if (firstUserMessage != null && firstUserMessage.length > 50) {
            firstUserMessage.take(50) + "..."
        } else {
            firstUserMessage ?: "Новый чат"
        }
    }

    fun sendMessage(userMessage: String) {
        if (userMessage.isBlank()) return

        val userChatMessage = ChatMessage(text = userMessage, isUser = true)
        _messages.value = _messages.value + userChatMessage

        _isLoading.value = true

        viewModelScope.launch {
            try {
                saveChat()
                
                val apiMessages = _messages.value.map { message ->
                    MessageInfo(
                        role = if (message.isUser) "user" else "assistant",
                        text = message.text
                    )
                }

                val response = yandexGPT.sendMessage(apiMessages)

                val assistantMessage = ChatMessage(text = response, isUser = false)
                _messages.value = _messages.value + assistantMessage
                
                saveChat()
            } catch (e: Exception) {
                val errorMessage = ChatMessage(
                    text = "Ошибка: ${e.message}",
                    isUser = false
                )
                _messages.value = _messages.value + errorMessage
                saveChat()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun clearChat() {
        _messages.value = emptyList()
        _chatTitle.value = null
        viewModelScope.launch {
            chatRepository.deleteChat(currentChatId)
        }
    }
    
    fun saveChatOnExit() {
        if (_messages.value.isNotEmpty()) {
            viewModelScope.launch {
                saveChat()
            }
        }
    }
    
    fun getCurrentChatId(): String = currentChatId
}

