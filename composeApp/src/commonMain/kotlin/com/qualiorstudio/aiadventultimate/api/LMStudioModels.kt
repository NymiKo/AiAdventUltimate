package com.qualiorstudio.aiadventultimate.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class EmbeddingRequest(
    val input: String,
    val model: String
)

@Serializable
data class EmbeddingData(
    val embedding: List<Double>,
    val index: Int,
    @SerialName("object")
    val obj: String = "embedding"
)

@Serializable
data class EmbeddingUsage(
    val prompt_tokens: Int,
    val total_tokens: Int
)

@Serializable
data class EmbeddingResponse(
    val data: List<EmbeddingData>,
    val model: String,
    val usage: EmbeddingUsage,
    @SerialName("object")
    val obj: String = "list"
)

@Serializable
data class ModelInfo(
    val id: String,
    @SerialName("object")
    val obj: String = "model",
    val created: Long = 0,
    val owned_by: String = "local"
)

@Serializable
data class ModelsResponse(
    val data: List<ModelInfo>,
    @SerialName("object")
    val obj: String = "list"
)

@Serializable
data class LMStudioChatMessage(
    val role: String,
    val content: String
)

@Serializable
data class LMStudioChatRequest(
    val model: String,
    val messages: List<LMStudioChatMessage>,
    val temperature: Double = 0.7,
    @SerialName("max_tokens")
    val maxTokens: Int = 8000,
    val stream: Boolean = false
)

@Serializable
data class LMStudioChatChoice(
    val index: Int,
    val message: LMStudioChatMessage,
    @SerialName("finish_reason")
    val finishReason: String?
)

@Serializable
data class LMStudioChatUsage(
    @SerialName("prompt_tokens")
    val promptTokens: Int,
    @SerialName("completion_tokens")
    val completionTokens: Int,
    @SerialName("total_tokens")
    val totalTokens: Int
)

@Serializable
data class LMStudioChatResponse(
    val id: String,
    val `object`: String,
    val created: Long,
    val model: String,
    val choices: List<LMStudioChatChoice>,
    val usage: LMStudioChatUsage
)

