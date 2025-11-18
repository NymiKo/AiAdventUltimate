package com.qualiorstudio.aiadventultimate.mcp

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

@Serializable
data class McpRequest(
    val jsonrpc: String = "2.0",
    val id: Int,
    val method: String,
    val params: JsonObject? = null
)

@Serializable
data class McpResponse(
    val jsonrpc: String,
    val id: Int,
    val result: JsonElement? = null,
    val error: McpError? = null
)

@Serializable
data class McpError(
    val code: Int,
    val message: String,
    val data: JsonElement? = null
)

@Serializable
data class McpToolCall(
    val name: String,
    val arguments: Map<String, String>
)

@Serializable
data class McpToolResult(
    val content: String,
    val isError: Boolean = false
)

