package com.qualiorstudio.aiadventultimate.api

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

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
                })
            }
        }
    }

    suspend fun sendMessage(
        messages: List<DeepSeekMessage>,
        temperature: Double = 0.7,
        maxTokens: Int = 2000
    ): String {
        return try {
            val request = DeepSeekRequest(
                model = model,
                messages = messages,
                temperature = temperature,
                maxTokens = maxTokens
            )

            val response = client.post(baseUrl) {
                header("Authorization", "Bearer $apiKey")
                contentType(ContentType.Application.Json)
                setBody(request)
            }

            if (response.status != HttpStatusCode.OK) {
                val errorBody = response.bodyAsText()
                return "API Error: ${response.status.value} - $errorBody"
            }

            val responseBody = response.body<DeepSeekResponse>()
            responseBody.choices.firstOrNull()?.message?.content?.trim() 
                ?: "No response generated"
        } catch (e: Exception) {
            "Request failed: ${e.message ?: "Unknown error"}"
        }
    }
}

