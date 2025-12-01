package com.qualiorstudio.aiadventultimate.model

import com.qualiorstudio.aiadventultimate.utils.currentTimeMillis
import kotlinx.serialization.Serializable

@Serializable
data class Agent(
    val id: String,
    val name: String,
    val role: String,
    val systemPrompt: String,
    val createdAt: Long = currentTimeMillis(),
    val updatedAt: Long = currentTimeMillis()
)

