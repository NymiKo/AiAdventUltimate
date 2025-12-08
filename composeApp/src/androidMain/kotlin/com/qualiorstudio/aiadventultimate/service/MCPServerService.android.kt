package com.qualiorstudio.aiadventultimate.service

import com.qualiorstudio.aiadventultimate.model.MCPServer
import com.qualiorstudio.aiadventultimate.model.MCPServerConnectionStatus
import com.qualiorstudio.aiadventultimate.model.MCPServerStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

actual class MCPServerServiceImpl : MCPServerService {
    private val statuses = ConcurrentHashMap<String, MutableStateFlow<MCPServerStatus?>>()
    
    override fun getServerStatus(serverId: String): StateFlow<MCPServerStatus?> {
        return statuses.getOrPut(serverId) {
            MutableStateFlow(MCPServerStatus(serverId, MCPServerConnectionStatus.DISCONNECTED, "MCP серверы не поддерживаются на Android"))
        }.asStateFlow()
    }
    
    override fun startServer(server: MCPServer) {
        updateStatus(server.id, MCPServerConnectionStatus.ERROR, "MCP серверы не поддерживаются на Android")
    }
    
    override fun stopServer(serverId: String) {
        updateStatus(serverId, MCPServerConnectionStatus.DISCONNECTED)
    }
    
    override fun checkServerStatus(server: MCPServer) {
        updateStatus(server.id, MCPServerConnectionStatus.ERROR, "MCP серверы не поддерживаются на Android")
    }
    
    override fun getAllStatuses(): Map<String, StateFlow<MCPServerStatus?>> {
        return statuses.mapValues { it.value.asStateFlow() }
    }
    
    private fun updateStatus(serverId: String, status: MCPServerConnectionStatus, errorMessage: String? = null) {
        statuses.getOrPut(serverId) {
            MutableStateFlow(MCPServerStatus(serverId, MCPServerConnectionStatus.DISCONNECTED))
        }.value = MCPServerStatus(serverId, status, errorMessage)
    }
}


