package com.qualiorstudio.aiadventultimate.api

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString

class DeepSeek(
    private val apiKey: String,
    private val model: String = "deepseek-chat"
) {
    private val client: HttpClient by lazy { createHttpClient() }
    private val baseUrl = "https://api.deepseek.com/v1/chat/completions"

    private fun createHttpClient(): HttpClient {
        return HttpClient {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    prettyPrint = true
                    isLenient = true
                    encodeDefaults = true
                })
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
            val request = DeepSeekRequest(
                model = model,
                messages = messages,
                temperature = temperature,
                maxTokens = maxTokens,
                tools = tools,
                toolChoice = if (tools != null) "auto" else null
            )

            println("=== DeepSeek Request ===")
            println("Model: $model")
            println("Messages count: ${messages.size}")
            println("Tools: ${tools?.size ?: 0}")
            
            val requestJson = Json {
                encodeDefaults = true
                ignoreUnknownKeys = true
            }.encodeToString(DeepSeekRequest.serializer(), request)
            println("Request JSON: $requestJson")
            println("Messages in request:")
            request.messages.forEachIndexed { index, msg ->
                println("  Message[$index]: role=${msg.role}, type=${msg.type}, toolCallId=${msg.toolCallId}, content=${msg.content?.take(50)}")
            }
            
            val response = client.post(baseUrl) {
                header("Authorization", "Bearer $apiKey")
                contentType(ContentType.Application.Json)
                setBody(request)
            }

            println("=== DeepSeek Response ===")
            println("Status: ${response.status}")

            if (response.status != HttpStatusCode.OK) {
                val errorBody = response.bodyAsText()
                println("Error body: $errorBody")
                throw Exception("API Error: ${response.status.value} - $errorBody")
            }

            response.body<DeepSeekResponse>()
        } catch (e: Exception) {
            println("Exception in DeepSeek.sendMessage: ${e.message}")
            e.printStackTrace()
            throw Exception("Request failed: ${e.message ?: "Unknown error"}")
        }
    }
}

