package com.qualiorstudio.aiadventultimate.service

import com.qualiorstudio.aiadventultimate.model.MCPServer
import com.qualiorstudio.aiadventultimate.model.MCPServerConnectionStatus
import com.qualiorstudio.aiadventultimate.model.MCPServerStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

interface MCPServerService {
    fun getServerStatus(serverId: String): StateFlow<MCPServerStatus?>
    fun startServer(server: MCPServer)
    fun stopServer(serverId: String)
    fun checkServerStatus(server: MCPServer)
    fun getAllStatuses(): Map<String, StateFlow<MCPServerStatus?>>
}

expect class MCPServerServiceImpl() : MCPServerService


