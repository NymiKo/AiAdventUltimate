package com.qualiorstudio.aiadventultimate.mcp

import com.qualiorstudio.aiadventultimate.api.DeepSeekTool
import com.qualiorstudio.aiadventultimate.model.MCPServer
import com.qualiorstudio.aiadventultimate.repository.MCPServerRepository
import kotlinx.coroutines.flow.StateFlow

interface MCPServerManager {
    suspend fun initializeServers(repository: MCPServerRepository)
    suspend fun getAvailableTools(): List<DeepSeekTool>
    suspend fun callTool(toolName: String, arguments: kotlinx.serialization.json.JsonObject): kotlinx.serialization.json.JsonElement
    fun hasTools(toolName: String): Boolean
    fun getServerStatus(serverId: String): StateFlow<com.qualiorstudio.aiadventultimate.model.MCPServerStatus?>
    suspend fun shutdown()
}

expect class MCPServerManagerImpl : MCPServerManager

expect fun createMCPServerManager(): MCPServerManager

