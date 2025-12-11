package com.qualiorstudio.aiadventultimate.api

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

class OllamaChat(
    private val baseUrl: String = "http://38.180.222.56:11434",
    private val model: String = "qwen2.5:0.5b"
) {
    private val client: HttpClient by lazy { createHttpClient() }
    private val chatUrl = "$baseUrl/api/chat"

    private fun createHttpClient(): HttpClient {
        return HttpClient {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    prettyPrint = false
                    isLenient = true
                    encodeDefaults = true
                })
            }
            install(HttpTimeout) {
                connectTimeoutMillis = 30_000
                socketTimeoutMillis = 600_000
                requestTimeoutMillis = 600_000
            }
        }
    }

    suspend fun sendMessage(
        messages: List<DeepSeekMessage>,
        tools: List<DeepSeekTool>? = null,
        temperature: Double = 0.7,
        maxTokens: Int = 8000
    ): DeepSeekResponse {
        return try {
            val ollamaMessages = messages.map { msg ->
                OllamaMessage(
                    role = msg.role,
                    content = msg.content ?: ""
                )
            }

            val request = OllamaChatRequest(
                model = model,
                messages = ollamaMessages,
                options = OllamaOptions(
                    temperature = temperature,
                    numPredict = maxTokens
                ),
                stream = false
            )

            val response = client.post(chatUrl) {
                contentType(ContentType.Application.Json)
                setBody(request)
            }

            if (response.status != HttpStatusCode.OK) {
                val errorBody = response.bodyAsText()
                throw Exception("Ollama API Error: ${response.status.value} - $errorBody")
            }

            val chatResponse = response.body<OllamaChatResponse>()
            
            DeepSeekResponse(
                id = chatResponse.id ?: "chat-${System.currentTimeMillis()}",
                `object` = "chat.completion",
                created = chatResponse.createdAt?.let { parseTimestamp(it) } ?: System.currentTimeMillis() / 1000,
                model = chatResponse.model ?: model,
                choices = listOf(
                    DeepSeekChoice(
                        index = 0,
                        message = DeepSeekMessage(
                            role = chatResponse.message?.role ?: "assistant",
                            content = chatResponse.message?.content ?: ""
                        ),
                        finishReason = if (chatResponse.done == true) "stop" else null
                    )
                ),
                usage = DeepSeekUsage(
                    promptTokens = chatResponse.promptEvalCount ?: 0,
                    completionTokens = chatResponse.evalCount ?: 0,
                    totalTokens = (chatResponse.promptEvalCount ?: 0) + (chatResponse.evalCount ?: 0)
                )
            )
        } catch (e: Exception) {
            println("Exception in OllamaChat.sendMessage: ${e.message}")
            e.printStackTrace()
            throw Exception("Failed to send message: ${e.message ?: "Unknown error"}")
        }
    }

    private fun parseTimestamp(timestamp: String): Long {
        return try {
            timestamp.toLongOrNull() ?: System.currentTimeMillis() / 1000
        } catch (e: Exception) {
            System.currentTimeMillis() / 1000
        }
    }

    fun close() {
        client.close()
    }
}
