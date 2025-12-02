package com.qualiorstudio.aiadventultimate.mcp

import com.qualiorstudio.aiadventultimate.api.DeepSeekTool
import com.qualiorstudio.aiadventultimate.api.DeepSeekFunction
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import kotlinx.serialization.encodeToString

interface MCPClient {
    suspend fun connect()
    suspend fun disconnect()
    suspend fun listTools(): List<MCPTool>
    suspend fun callTool(name: String, arguments: JsonObject): JsonElement
    fun isConnected(): Boolean
}

@Serializable
data class MCPRequest(
    @SerialName("jsonrpc")
    val jsonrpc: String = "2.0",
    val id: Int? = null,
    val method: String,
    val params: JsonObject? = null
)

@Serializable
data class MCPResponse(
    val jsonrpc: String = "2.0",
    val id: Int? = null,
    val result: JsonElement? = null,
    val error: MCPError? = null
)

@Serializable
data class MCPError(
    val code: Int,
    val message: String,
    val data: JsonElement? = null
)

@Serializable
data class MCPTool(
    val name: String,
    val description: String,
    val inputSchema: JsonObject
)

fun MCPTool.toDeepSeekTool(): DeepSeekTool {
    return DeepSeekTool(
        type = "function",
        function = DeepSeekFunction(
            name = this.name,
            description = this.description,
            parameters = this.inputSchema
        )
    )
}

expect class MCPClientImpl(
    command: String,
    args: List<String>,
    env: Map<String, String>
) : MCPClient

