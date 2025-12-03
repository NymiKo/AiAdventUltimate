package com.qualiorstudio.aiadventultimate.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qualiorstudio.aiadventultimate.api.DeepSeek
import com.qualiorstudio.aiadventultimate.api.DeepSeekMessage
import com.qualiorstudio.aiadventultimate.model.ChatMessage
import com.qualiorstudio.aiadventultimate.model.Ticket
import com.qualiorstudio.aiadventultimate.service.CRMService
import com.qualiorstudio.aiadventultimate.service.SupportRAGService
import com.qualiorstudio.aiadventultimate.service.createCRMService
import com.qualiorstudio.aiadventultimate.service.createSupportRAGService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SupportViewModel(
    private val settingsViewModel: SettingsViewModel,
    private val crmService: CRMService = createCRMService(),
    private val supportRAGService: SupportRAGService = createSupportRAGService()
) : ViewModel() {
    
    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private var deepSeek: DeepSeek? = null
    private var lastApiKey: String? = null
    
    init {
        viewModelScope.launch {
            settingsViewModel.settings.collect { settings ->
                if (settings.deepSeekApiKey != lastApiKey) {
                    lastApiKey = settings.deepSeekApiKey
                    if (settings.deepSeekApiKey.isNotBlank()) {
                        deepSeek = DeepSeek(apiKey = settings.deepSeekApiKey)
                    } else {
                        deepSeek = null
                    }
                }
            }
        }
    }
    
    fun clearChat() {
        _messages.value = emptyList()
    }
    
    fun sendMessage(userQuestion: String) {
        if (userQuestion.isBlank() || _isLoading.value) return
        
        val userMessage = ChatMessage(
            text = userQuestion,
            isUser = true
        )
        
        _messages.value = _messages.value + userMessage
        _isLoading.value = true
        
        viewModelScope.launch {
            try {
                val response = generateSupportResponse(userQuestion)
                val assistantMessage = ChatMessage(
                    text = response,
                    isUser = false,
                    agentName = "Поддержка"
                )
                _messages.value = _messages.value + assistantMessage
            } catch (e: Exception) {
                println("Ошибка при генерации ответа поддержки: ${e.message}")
                e.printStackTrace()
                val errorMessage = ChatMessage(
                    text = "Извините, произошла ошибка при обработке вашего запроса. Пожалуйста, попробуйте еще раз или свяжитесь с поддержкой.",
                    isUser = false,
                    agentName = "Поддержка"
                )
                _messages.value = _messages.value + errorMessage
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    private suspend fun generateSupportResponse(userQuestion: String): String {
        val currentDeepSeek = deepSeek
        if (currentDeepSeek == null) {
            return "Ошибка: API ключ DeepSeek не настроен. Пожалуйста, настройте API ключ в настройках приложения."
        }
        
        val relevantTickets = crmService.searchTickets(userQuestion)
        val docContext = supportRAGService.buildSupportContext(userQuestion, relevantTickets)
        
        val ticketContext = if (relevantTickets.isNotEmpty()) {
            buildTicketContext(relevantTickets)
        } else {
            null
        }
        
        val prompt = supportRAGService.buildSupportPrompt(
            userQuestion = userQuestion,
            context = docContext,
            ticketContext = ticketContext
        )
        
        val messages = listOf(
            DeepSeekMessage(
                role = "system",
                content = "Ты - ассистент поддержки. Отвечай на вопросы четко и по делу. НЕ используй вводные фразы типа 'На основе документации', 'Согласно FAQ', 'Привет'. Начинай сразу с ответа. Будь лаконичным, но информативным. Если нужны шаги настройки - опиши их последовательно."
            ),
            DeepSeekMessage(
                role = "user",
                content = prompt
            )
        )
        
        val response = currentDeepSeek.sendMessage(
            messages = messages,
            temperature = 0.3,
            maxTokens = 1500
        )
        
        return response.choices.firstOrNull()?.message?.content 
            ?: "Не удалось получить ответ от AI. Пожалуйста, попробуйте еще раз."
    }
    
    private fun buildTicketContext(tickets: List<Ticket>): String {
        if (tickets.isEmpty()) return ""
        
        val contextBuilder = StringBuilder()
        contextBuilder.append("Найдено ${tickets.size} похожих тикетов:\n\n")
        
        tickets.take(5).forEachIndexed { index, ticket ->
            contextBuilder.append("Тикет #${index + 1}:\n")
            contextBuilder.append("ID: ${ticket.id}\n")
            contextBuilder.append("Пользователь: ${ticket.userName ?: ticket.userId}\n")
            contextBuilder.append("Статус: ${ticket.status}\n")
            contextBuilder.append("Заголовок: ${ticket.title}\n")
            contextBuilder.append("Описание: ${ticket.description}\n")
            if (ticket.tags.isNotEmpty()) {
                contextBuilder.append("Теги: ${ticket.tags.joinToString(", ")}\n")
            }
            contextBuilder.append("\n")
        }
        
        return contextBuilder.toString().trim()
    }
}

