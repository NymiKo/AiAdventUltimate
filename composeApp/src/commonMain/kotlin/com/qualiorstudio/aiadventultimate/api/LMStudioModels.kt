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

