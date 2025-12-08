package com.qualiorstudio.aiadventultimate.api

interface LLMProvider {
    suspend fun sendMessage(
        messages: List<DeepSeekMessage>,
        tools: List<DeepSeekTool>? = null,
        temperature: Double = 0.7,
        maxTokens: Int = 8000
    ): DeepSeekResponse
}

class DeepSeekLLMProvider(
    val deepSeek: DeepSeek
) : LLMProvider {
    override suspend fun sendMessage(
        messages: List<DeepSeekMessage>,
        tools: List<DeepSeekTool>?,
        temperature: Double,
        maxTokens: Int
    ): DeepSeekResponse {
        return deepSeek.sendMessage(messages, tools, temperature, maxTokens)
    }
}

class LMStudioLLMProvider(
    private val lmStudio: LMStudio,
    private val model: String? = null
) : LLMProvider {
    override suspend fun sendMessage(
        messages: List<DeepSeekMessage>,
        tools: List<DeepSeekTool>?,
        temperature: Double,
        maxTokens: Int
    ): DeepSeekResponse {
        if (tools != null && tools.isNotEmpty()) {
            println("Warning: LM Studio does not support tool calls, ignoring tools")
        }
        return lmStudio.sendMessage(messages, model, temperature, maxTokens)
    }
}

