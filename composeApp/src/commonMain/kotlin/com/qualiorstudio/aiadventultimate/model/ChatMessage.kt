package com.qualiorstudio.aiadventultimate.model

data class ExpertResponse(
    val expertName: String,
    val expertType: ExpertType,
    val answer: String,
    val tokensUsed: Int = 0
)

data class ChatMessage(
    val text: String,
    val isUser: Boolean,
    val title: String? = null,
    val originalQuestion: String? = null,
    val tokensUsed: Int? = null,
    val expertResponses: List<ExpertResponse>? = null
)

