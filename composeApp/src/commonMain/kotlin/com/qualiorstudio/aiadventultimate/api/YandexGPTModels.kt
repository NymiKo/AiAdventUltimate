package com.qualiorstudio.aiadventultimate.api

import kotlinx.serialization.Serializable

@Serializable
data class MessageInfo(
    val role: String,
    val text: String
)

@Serializable
data class CompletionOptions(
    val stream: Boolean = false,
    val temperature: Double = 0.7,
    val maxTokens: Int = 2000,
)

@Serializable
data class ChatRequest(
    val modelUri: String,
    val completionOptions: CompletionOptions = CompletionOptions(),
    val messages: List<MessageInfo>
)

@Serializable
data class Message(
    val role: String,
    val text: String
)

@Serializable
data class Alternative(
    val message: Message,
    val status: String
)

@Serializable
data class Result(
    val alternatives: List<Alternative>,
    val usage: Usage,
    val modelVersion: String
)

@Serializable
data class Usage(
    val inputTextTokens: String,
    val completionTokens: String,
    val totalTokens: String
)

@Serializable
data class ChatResponse(
    val result: Result
)

@Serializable
data class StructuredResponse(
    val title: String,
    val answer: String,
    val question: String,
    val tokens: Int
)

@Serializable
data class TokenizeRequest(
    val modelUri: String,
    val text: String,
)

@Serializable
data class TokenInfo(
    val id: Int,
    val text: String,
    val special: Boolean = false,
)

@Serializable
data class TokenizeResponse(
    val tokens: List<TokenInfo> = emptyList(),
    val length: Int = 0,
)

