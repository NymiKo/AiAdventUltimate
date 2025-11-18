package com.qualiorstudio.aiadventultimate.model

import kotlinx.serialization.Serializable

@Serializable
data class ChatMessage(
    val text: String,
    val isUser: Boolean
)

