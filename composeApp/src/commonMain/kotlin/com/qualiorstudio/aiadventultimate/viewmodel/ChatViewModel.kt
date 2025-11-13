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
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class ChatViewModel : ViewModel() {
    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val apiKey = "API_KEY"
    private val folderId = "FOLDER_ID"
    private val yandexGPT = YandexGPT(apiKey, folderId)
    private var systemPrompt: String? = null
    private var lastUserKeywords: Set<String> = emptySet()
    private val _contextSummary = MutableStateFlow<String?>(null)
    val contextSummary: StateFlow<String?> = _contextSummary.asStateFlow()

    fun sendMessage(userMessage: String) {
        if (userMessage.isBlank()) return

        val newKeywords = extractKeywords(userMessage)
        val contextChanged = hasTaskContextChanged(newKeywords)
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

                if (_messages.value.size > 5 || contextChanged) {
                    val summary = compressContext(_messages.value)
                    if (!summary.isNullOrBlank()) {
                        _contextSummary.value = summary
                        systemPrompt = buildSystemPrompt(summary)
                    } else if (contextChanged) {
                        _contextSummary.value = null
                        systemPrompt = null
                    }
                }

                val apiMessages = buildMessagesForRequest(_messages.value)

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
                lastUserKeywords = newKeywords
            } catch (e: Exception) {
                val errorMessage = ChatMessage(
                    text = "Ошибка: ${e.message}",
                    isUser = false
                )
                _messages.value = _messages.value + errorMessage
                lastUserKeywords = newKeywords
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun clearChat() {
        _messages.value = emptyList()
        systemPrompt = null
        lastUserKeywords = emptySet()
        _contextSummary.value = null
    }

    private suspend fun compressContext(messages: List<ChatMessage>): String? {
        val summaryMessages = listOf(
            MessageInfo(
                role = "system",
                text = "Ты помогаешь сжимать историю переписки. Ответ должен быть в формате JSON с полями title, answer, question. В поле answer верни краткое описание текущего контекста. Поле title установи в context, поле question оставь пустым."
            ),
            MessageInfo(
                role = "user",
                text = buildSummaryPrompt(messages, systemPrompt)
            )
        )
        val response = yandexGPT.sendMessage(summaryMessages)
        if (response.title.lowercase() == "ошибка") return null
        return extractContextText(response.answer)
    }

    private fun buildMessagesForRequest(
        messages: List<ChatMessage>
    ): List<MessageInfo> {
        val result = mutableListOf<MessageInfo>()
        val prompt = systemPrompt
        if (!prompt.isNullOrBlank()) {
            result += MessageInfo(
                role = "system",
                text = prompt
            )
        }
        val recentMessages = if (messages.size <= 8) messages else messages.takeLast(8)
        recentMessages.forEach { message ->
            result += MessageInfo(
                role = if (message.isUser) "user" else "assistant",
                text = message.text
            )
        }
        return result
    }

    private fun buildSummaryPrompt(
        messages: List<ChatMessage>,
        currentPrompt: String?
    ): String {
        return buildString {
            appendLine(
                "Сожми весь диалог и текущий контекст. Зафиксируй ключевые факты, договоренности, цели, ограничения и другую важную информацию, чтобы ассистент мог продолжить работу без потери деталей."
            )
            if (!currentPrompt.isNullOrBlank()) {
                appendLine()
                appendLine("Текущий системный контекст:")
                appendLine(currentPrompt)
            }
            appendLine()
            appendLine("История сообщений (от начала до конца):")
            messages.forEachIndexed { index, message ->
                append(index + 1)
                append(". ")
                append(if (message.isUser) "Пользователь: " else "Ассистент: ")
                appendLine(message.text)
            }
        }
    }

    private fun extractKeywords(text: String): Set<String> {
        val regex = Regex("[\\p{L}\\d]{4,}")
        return regex.findAll(text.lowercase()).map { it.value }.toSet()
    }

    private fun hasTaskContextChanged(newKeywords: Set<String>): Boolean {
        if (lastUserKeywords.isEmpty() || newKeywords.isEmpty()) return false
        val intersection = lastUserKeywords.intersect(newKeywords).size
        val union = lastUserKeywords.union(newKeywords).size
        if (union == 0) return false
        val similarity = intersection.toDouble() / union.toDouble()
        return similarity < 0.3
    }

    private fun extractContextText(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val cleaned = raw
            .replace("```json", "")
            .replace("```", "")
            .trim()
        val parsed = runCatching {
            Json { ignoreUnknownKeys = true }.decodeFromString<ContextSummaryPayload>(cleaned)
        }.getOrNull()
        return parsed?.answer?.takeIf { it.isNotBlank() } ?: cleaned
    }

    private fun buildSystemPrompt(summary: String): String {
        return "Краткий контекст задачи: $summary"
    }

    @Serializable
    private data class ContextSummaryPayload(
        val title: String? = null,
        val answer: String? = null,
        val question: String? = null,
        val tokens: Int? = null
    )
}

