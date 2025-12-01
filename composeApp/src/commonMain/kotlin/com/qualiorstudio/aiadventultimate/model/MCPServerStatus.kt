package com.qualiorstudio.aiadventultimate.model

import com.qualiorstudio.aiadventultimate.utils.currentTimeMillis
import kotlinx.serialization.Serializable

enum class MCPServerConnectionStatus {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    ERROR
}

data class MCPServerStatus(
    val serverId: String,
    val status: MCPServerConnectionStatus,
    val errorMessage: String? = null,
    val lastChecked: Long = currentTimeMillis()
)

