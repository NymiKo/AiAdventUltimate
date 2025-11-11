package com.qualiorstudio.aiadventultimate.api

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

class YandexGPT(
    var apiKey: String,
    private val folderId: String,
) {
    private val client: HttpClient by lazy { createHttpClient() }
    private val baseUrl = "https://llm.api.cloud.yandex.net/foundationModels/v1/completion"

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
        modelUri: String = "gpt://$folderId/yandexgpt-lite/latest",
        temperature: Double = 0.7,
    ): String {
        return try {
            val request = ChatRequest(
                modelUri = modelUri,
                completionOptions = CompletionOptions(temperature = temperature),
                messages = messages,
            )

            val response = client.post(baseUrl) {
                header("Authorization", "Bearer $apiKey")
                contentType(ContentType.Application.Json)
                setBody(request)
            }

            if (response.status == HttpStatusCode.UnprocessableEntity) {
                val errorBody = response.bodyAsText()
                return "API Validation Error: $errorBody"
            }

            val responseBody = response.body<ChatResponse>()
            responseBody.result.alternatives.first().message.text.removeSurrounding("```").trim()
        } catch (e: Exception) {
            "Request failed: ${e.message ?: "Unknown error"}"
        }
    }
}

