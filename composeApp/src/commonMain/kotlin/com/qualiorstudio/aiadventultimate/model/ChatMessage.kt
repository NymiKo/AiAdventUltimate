package com.qualiorstudio.aiadventultimate.model

data class ChatMessage(
    val text: String,
    val isUser: Boolean,
    val temperature: Double? = null
)

