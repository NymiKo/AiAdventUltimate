package com.qualiorstudio.aiadventultimate.mcp

import kotlinx.serialization.json.JsonObject

actual class McpClient actual constructor(
    private val command: String,
    private val args: List<String>,
    private val env: Map<String, String>
) {
    actual suspend fun start() {
        throw UnsupportedOperationException("MCP Client is not supported on iOS")
    }

    actual suspend fun listTools(): List<McpTool> {
        return emptyList()
    }

    actual suspend fun callTool(name: String, arguments: JsonObject): String {
        throw UnsupportedOperationException("MCP Client is not supported on iOS")
    }

    actual fun close() {
    }
}

