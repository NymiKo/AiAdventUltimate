package com.qualiorstudio.aiadventultimate.api

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

class YandexGPT(
    var apiKey: String,
    private val folderId: String,
) {
    private val client: HttpClient by lazy { createHttpClient() }
    private val baseUrl = "https://llm.api.cloud.yandex.net/foundationModels/v1/completion"
    private val tokenizerUrl = "https://llm.api.cloud.yandex.net/foundationModels/v1/tokenize"
    private val defaultModelUri = "gpt://$folderId/yandexgpt-lite/latest"

    private fun createHttpClient(): HttpClient {
        return HttpClient {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    prettyPrint = true
                    isLenient = true
                })
            }
        }
    }

    suspend fun sendMessage(
        messages: List<MessageInfo>,
        modelUri: String = defaultModelUri,
    ): StructuredResponse {
        return try {
            val systemInstruction = """Ты должен всегда отвечать в строгом JSON формате. 
                |Формат ответа:
                |{
                |  "title": "краткий заголовок ответа (2-5 слов)",
                |  "answer": "твой ответ на вопрос пользователя",
                |  "question": "исходный вопрос пользователя",
                |  "tokens": 0
                |}
                |Поле tokens всегда устанавливай в 0. Не добавляй никакого другого текста, только JSON.
                |
                |""".trimMargin()

            val modifiedMessages = messages.toMutableList()
            if (modifiedMessages.isNotEmpty() && modifiedMessages.last().role == "user") {
                val lastIndex = modifiedMessages.lastIndex
                modifiedMessages[lastIndex] = MessageInfo(
                    role = modifiedMessages[lastIndex].role,
                    text = systemInstruction + modifiedMessages[lastIndex].text
                )
            }

            val request = ChatRequest(
                modelUri = modelUri,
                messages = modifiedMessages,
            )

            val response = client.post(baseUrl) {
                header("Authorization", "Bearer $apiKey")
                contentType(ContentType.Application.Json)
                setBody(request)
            }

            if (response.status == HttpStatusCode.UnprocessableEntity) {
                val errorBody = response.bodyAsText()
                return StructuredResponse(
                    title = "Ошибка",
                    answer = "API Validation Error: $errorBody",
                    question = "",
                    tokens = 0
                )
            }

            if (!response.status.isSuccess()) {
                val errorBody = response.bodyAsText()
                return StructuredResponse(
                    title = "Ошибка",
                    answer = "API Error (${response.status.value}): $errorBody",
                    question = "",
                    tokens = 0
                )
            }

            val responseBody = response.body<ChatResponse>()
            val rawText = responseBody.result.alternatives.first().message.text
            val actualTokens = responseBody.result.usage.totalTokens.toIntOrNull() ?: 0
            
            parseStructuredResponse(rawText, actualTokens)
        } catch (e: Exception) {
            StructuredResponse(
                title = "Ошибка",
                answer = "Request failed: ${e.message ?: "Unknown error"}",
                question = "",
                tokens = 0
            )
        }
    }

    suspend fun countTokens(
        text: String,
        modelUri: String = defaultModelUri,
    ): Int {
        if (text.isBlank()) return 0

        return try {
            val response = client.post(tokenizerUrl) {
                header("Authorization", "Bearer $apiKey")
                contentType(ContentType.Application.Json)
                setBody(TokenizeRequest(modelUri = modelUri, text = text))
            }

            if (!response.status.isSuccess()) return 0

            val body = response.body<TokenizeResponse>()
            print(body.tokens.size)
            when {
                body.length > 0 -> body.length
                body.tokens.isNotEmpty() -> body.tokens.size
                else -> 0
            }
        } catch (_: Exception) {
            0
        }
    }

    private fun parseStructuredResponse(text: String, actualTokens: Int): StructuredResponse {
        return try {
            val cleanText = text
                .removeSurrounding("```json", "```")
                .removeSurrounding("```", "```")
                .trim()

            val parsed = Json { ignoreUnknownKeys = true }.decodeFromString<StructuredResponse>(cleanText)
            parsed.copy(tokens = actualTokens)
        } catch (e: Exception) {
            StructuredResponse(
                title = "Ответ",
                answer = text,
                question = "",
                tokens = actualTokens
            )
        }
    }
}

