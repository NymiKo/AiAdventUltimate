package com.qualiorstudio.aiadventultimate.mcp

import kotlinx.serialization.json.JsonObject

actual class MCPClientImpl(
    private val command: String,
    private val args: List<String>,
    private val env: Map<String, String>
) : MCPClient {
    override suspend fun connect() {
        throw UnsupportedOperationException("MCP клиенты не поддерживаются на Android")
    }
    
    override suspend fun disconnect() {}
    
    override suspend fun listTools(): List<MCPTool> = emptyList()
    
    override suspend fun callTool(name: String, arguments: JsonObject): kotlinx.serialization.json.JsonElement {
        throw UnsupportedOperationException("MCP клиенты не поддерживаются на Android")
    }
    
    override fun isConnected(): Boolean = false
}


