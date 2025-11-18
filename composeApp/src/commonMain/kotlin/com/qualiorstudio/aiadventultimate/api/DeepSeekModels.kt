package com.qualiorstudio.aiadventultimate.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DeepSeekMessage(
    val role: String,
    val content: String
)

@Serializable
data class DeepSeekRequest(
    val model: String,
    val messages: List<DeepSeekMessage>,
    val temperature: Double = 0.7,
    @SerialName("max_tokens")
    val maxTokens: Int = 2000,
    val stream: Boolean = false
)

@Serializable
data class DeepSeekChoice(
    val index: Int,
    val message: DeepSeekMessage,
    @SerialName("finish_reason")
    val finishReason: String? = null
)

@Serializable
data class DeepSeekUsage(
    @SerialName("prompt_tokens")
    val promptTokens: Int,
    @SerialName("completion_tokens")
    val completionTokens: Int,
    @SerialName("total_tokens")
    val totalTokens: Int
)

@Serializable
data class DeepSeekResponse(
    val id: String,
    val `object`: String,
    val created: Long,
    val model: String,
    val choices: List<DeepSeekChoice>,
    val usage: DeepSeekUsage
)

