package com.qualiorstudio.aiadventultimate.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class OllamaMessage(
    val role: String,
    val content: String
)

@Serializable
data class OllamaOptions(
    val temperature: Double = 0.3,
    @SerialName("num_predict")
    val numPredict: Int = 400
)

@Serializable
data class OllamaChatRequest(
    val model: String,
    val messages: List<OllamaMessage>,
    val options: OllamaOptions? = null,
    val stream: Boolean = false
)

@Serializable
data class OllamaChatResponse(
    val model: String? = null,
    @SerialName("created_at")
    val createdAt: String? = null,
    val message: OllamaMessage? = null,
    val done: Boolean? = null,
    @SerialName("prompt_eval_count")
    val promptEvalCount: Int? = null,
    @SerialName("eval_count")
    val evalCount: Int? = null,
    val id: String? = null
)
