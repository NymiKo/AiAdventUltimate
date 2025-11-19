package com.qualiorstudio.aiadventultimate.mcp

import kotlinx.serialization.json.JsonObject

actual class McpClient actual constructor(
    private val command: String,
    private val env: Map<String, String>
) {
    actual suspend fun start() {
        throw UnsupportedOperationException("MCP Client is not supported on Android")
    }

    actual suspend fun listTools(): List<McpTool> {
        return emptyList()
    }

    actual suspend fun callTool(name: String, arguments: JsonObject): String {
        throw UnsupportedOperationException("MCP Client is not supported on Android")
    }

    actual fun close() {
    }
}

