package com.qualiorstudio.aiadventultimate.model

import com.qualiorstudio.aiadventultimate.utils.currentTimeMillis
import kotlinx.serialization.Serializable

@Serializable
data class AgentConnection(
    val id: String,
    val sourceAgentId: String,
    val targetAgentId: String,
    val description: String,
    val connectionType: ConnectionType = ConnectionType.REVIEW,
    val createdAt: Long = currentTimeMillis(),
    val updatedAt: Long = currentTimeMillis()
)

@Serializable
enum class ConnectionType {
    REVIEW,
    VALIDATE,
    ENHANCE,
    COLLABORATE
}

