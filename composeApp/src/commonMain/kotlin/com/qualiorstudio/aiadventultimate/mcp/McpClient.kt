package com.qualiorstudio.aiadventultimate.mcp

import kotlinx.serialization.json.JsonObject

expect class McpClient(
    command: String,
    env: Map<String, String> = emptyMap()
) {
    suspend fun start()
    suspend fun listTools(): List<McpTool>
    suspend fun callTool(name: String, arguments: JsonObject): String
    fun close()
}

data class McpTool(
    val name: String,
    val description: String,
    val inputSchema: JsonObject
)

