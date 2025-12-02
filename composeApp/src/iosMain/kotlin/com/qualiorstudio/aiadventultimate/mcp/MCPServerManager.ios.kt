package com.qualiorstudio.aiadventultimate.mcp

import com.qualiorstudio.aiadventultimate.api.DeepSeekTool
import com.qualiorstudio.aiadventultimate.model.MCPServerStatus
import com.qualiorstudio.aiadventultimate.repository.MCPServerRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.JsonObject

actual class MCPServerManagerImpl : MCPServerManager {
    override suspend fun initializeServers(repository: MCPServerRepository) {}
    
    override suspend fun getAvailableTools(): List<DeepSeekTool> = emptyList()
    
    override suspend fun callTool(toolName: String, arguments: JsonObject): kotlinx.serialization.json.JsonElement {
        throw UnsupportedOperationException("MCP не поддерживается на iOS")
    }
    
    override fun hasTools(toolName: String): Boolean = false
    
    override fun getServerStatus(serverId: String): StateFlow<MCPServerStatus?> {
        return MutableStateFlow(null)
    }
    
    override suspend fun shutdown() {}
}

actual fun createMCPServerManager(): MCPServerManager {
    return MCPServerManagerImpl()
}

