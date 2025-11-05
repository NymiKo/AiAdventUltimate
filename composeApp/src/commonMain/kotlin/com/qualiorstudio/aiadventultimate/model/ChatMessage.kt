package com.qualiorstudio.aiadventultimate.model

data class ChatMessage(
    val text: String,
    val isUser: Boolean,
    val title: String? = null,
    val originalQuestion: String? = null,
    val tokensUsed: Int? = null
)

