package com.qualiorstudio.aiadventultimate.api

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

class LMStudio(
    private val baseUrl: String = "http://localhost:1234",
    private val defaultModel: String? = null
) {
    private val client: HttpClient by lazy { createHttpClient() }
    private val embeddingsUrl = "$baseUrl/v1/embeddings"
    private val modelsUrl = "$baseUrl/v1/models"

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
        }
    }

    suspend fun getAvailableModels(): List<String> {
        return try {
            val response = client.get(modelsUrl)
            
            if (response.status != HttpStatusCode.OK) {
                println("Failed to get models list: ${response.status.value}")
                return emptyList()
            }

            val modelsResponse = response.body<ModelsResponse>()
            modelsResponse.data.map { it.id }
        } catch (e: Exception) {
            println("Exception getting models list: ${e.message}")
            e.printStackTrace()
            emptyList()
        }
    }

    suspend fun generateEmbedding(
        text: String,
        model: String? = null
    ): List<Double> {
        return try {
            val modelToUse = model ?: defaultModel ?: getDefaultModel()
            
            val request = EmbeddingRequest(
                input = text,
                model = modelToUse
            )

            println("Generating embedding with model: $modelToUse")

            val response = client.post(embeddingsUrl) {
                contentType(ContentType.Application.Json)
                setBody(request)
            }

            if (response.status != HttpStatusCode.OK) {
                val errorBody = response.bodyAsText()
                throw Exception("LM Studio API Error: ${response.status.value} - $errorBody")
            }

            val embeddingResponse = response.body<EmbeddingResponse>()
            embeddingResponse.data.firstOrNull()?.embedding
                ?: throw Exception("No embedding data in response")
        } catch (e: Exception) {
            println("Exception in LMStudio.generateEmbedding: ${e.message}")
            e.printStackTrace()
            throw Exception("Failed to generate embedding: ${e.message ?: "Unknown error"}")
        }
    }

    suspend fun generateEmbeddings(
        texts: List<String>,
        model: String? = null
    ): List<List<Double>> {
        return texts.map { text ->
            generateEmbedding(text, model)
        }
    }

    private suspend fun getDefaultModel(): String {
        val availableModels = getAvailableModels()
        return if (availableModels.isNotEmpty()) {
            availableModels.first()
        } else {
            "local-model"
        }
    }

    fun close() {
        client.close()
    }
}

