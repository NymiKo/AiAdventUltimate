package com.qualiorstudio.aiadventultimate.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

@Serializable
data class DeepSeekMessage(
    val role: String,
    val content: String? = null,
    @SerialName("tool_calls")
    val toolCalls: List<DeepSeekToolCall>? = null,
    @SerialName("tool_call_id")
    val toolCallId: String? = null,
    val type: String? = null
)

@Serializable
data class DeepSeekToolCall(
    val id: String,
    val type: String = "function",
    val function: DeepSeekFunctionCall
)

@Serializable
data class DeepSeekFunctionCall(
    val name: String,
    val arguments: String
)

@Serializable
data class DeepSeekTool(
    val type: String,
    val function: DeepSeekFunction
)

@Serializable
data class DeepSeekFunction(
    val name: String,
    val description: String,
    val parameters: JsonObject
)

@Serializable
data class DeepSeekRequest(
    val model: String,
    val messages: List<DeepSeekMessage>,
    val temperature: Double = 0.7,
    @SerialName("max_tokens")
    val maxTokens: Int = 4000,
    val stream: Boolean = false,
    val tools: List<DeepSeekTool>? = null,
    @SerialName("tool_choice")
    val toolChoice: String? = null
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
