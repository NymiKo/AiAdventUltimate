package com.qualiorstudio.aiadventultimate.model

import com.qualiorstudio.aiadventultimate.utils.currentTimeMillis
import kotlinx.serialization.Serializable

@Serializable
data class MCPServer(
    val id: String,
    val name: String,
    val command: String,
    val args: List<String> = emptyList(),
    val env: Map<String, String> = emptyMap(),
    val enabled: Boolean = true,
    val createdAt: Long = currentTimeMillis(),
    val updatedAt: Long = currentTimeMillis()
)




