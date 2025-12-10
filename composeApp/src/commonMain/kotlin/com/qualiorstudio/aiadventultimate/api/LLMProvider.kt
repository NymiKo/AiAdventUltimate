package com.qualiorstudio.aiadventultimate.api

interface LLMProvider {
    suspend fun sendMessage(
        messages: List<DeepSeekMessage>,
        tools: List<DeepSeekTool>? = null,
        temperature: Double = 0.7,
        maxTokens: Int = 8000
    ): DeepSeekResponse
}

class OllamaLLMProvider(
    private val ollamaChat: OllamaChat
) : LLMProvider {
    override suspend fun sendMessage(
        messages: List<DeepSeekMessage>,
        tools: List<DeepSeekTool>?,
        temperature: Double,
        maxTokens: Int
    ): DeepSeekResponse {
        if (tools != null && tools.isNotEmpty()) {
            println("Warning: Ollama does not support tool calls, ignoring tools")
        }
        return ollamaChat.sendMessage(messages, tools, temperature, maxTokens)
    }
}

