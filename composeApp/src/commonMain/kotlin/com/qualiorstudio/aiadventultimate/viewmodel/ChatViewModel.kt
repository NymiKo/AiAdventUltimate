package com.qualiorstudio.aiadventultimate.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qualiorstudio.aiadventultimate.ai.AIAgent
import com.qualiorstudio.aiadventultimate.api.DeepSeek
import com.qualiorstudio.aiadventultimate.api.DeepSeekMessage
import com.qualiorstudio.aiadventultimate.model.ChatMessage
import com.qualiorstudio.aiadventultimate.voice.createVoiceOutputService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ChatViewModel : ViewModel() {
    private val deepSeekApiKey = "sk-b21fe1a6400b4757840a27ebc1a5de2a"
    
    private val deepSeek = DeepSeek(apiKey = deepSeekApiKey)
    private val aiAgent = AIAgent(deepSeek)
    private val voiceOutputService = createVoiceOutputService()
    
    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private var conversationHistory = mutableListOf<DeepSeekMessage>()
    
    init {
        viewModelScope.launch {
            try {
                aiAgent.initialize()
            } catch (e: Exception) {
                println("Failed to initialize AIAgent: ${e.message}")
                e.printStackTrace()
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
                val result = aiAgent.processMessage(text, conversationHistory.toList())
                val aiMessage = ChatMessage(text = result.response, isUser = false)
                _messages.value = _messages.value + aiMessage
                conversationHistory.clear()
                conversationHistory.addAll(result.updatedHistory)
                
                if (voiceOutputService.isSupported() && result.shortPhrase.isNotBlank()) {
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
    
    override fun onCleared() {
        super.onCleared()
        voiceOutputService.stopSpeaking()
        aiAgent.close()
    }
}

