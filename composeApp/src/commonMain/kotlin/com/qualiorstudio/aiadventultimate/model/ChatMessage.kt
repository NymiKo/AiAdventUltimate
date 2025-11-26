package com.qualiorstudio.aiadventultimate.model

import kotlinx.serialization.Serializable

@Serializable
data class ChatMessage(
    val text: String,
    val isUser: Boolean,
    val variants: List<ChatResponseVariant> = emptyList()
)

@Serializable
data class ChatResponseVariant(
    val id: String,
    val title: String,
    val body: String,
    val metadata: String? = null,
    val isPreferred: Boolean = false
)

