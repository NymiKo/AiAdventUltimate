package com.qualiorstudio.aiadventultimate.model

import kotlinx.serialization.Serializable

@Serializable
data class AppSettings(
    val darkTheme: Boolean = true,
    val useRAG: Boolean = true,
    val enableVoiceInput: Boolean = true,
    val enableVoiceOutput: Boolean = true,
    // API настройки
    val deepSeekApiKey: String = "sk-b21fe1a6400b4757840a27ebc1a5de2a",
    // Параметры модели
    val temperature: Double = 0.7,
    val maxTokens: Int = 8000,
    // Параметры RAG
    val ragTopK: Int = 12,
    val rerankMinScore: Double = 0.58,
    val rerankedRetentionRatio: Double = 0.5,
    val lmStudioBaseUrl: String = "http://localhost:1234",
    // Параметры AI Agent
    val maxIterations: Int = 10
)

