package com.qualiorstudio.aiadventultimate.mcp

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.util.concurrent.atomic.AtomicInteger

actual class McpClient actual constructor(
    private val command: String,
    private val args: List<String>,
    private val env: Map<String, String>
) {
    private var process: Process? = null
    private var reader: BufferedReader? = null
    private var writer: BufferedWriter? = null
    private val requestId = AtomicInteger(0)
    private val json = Json { 
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    actual suspend fun start() = withContext(Dispatchers.IO) {
        val processBuilder = if (args.isEmpty()) {
            ProcessBuilder("sh", command)
        } else {
            ProcessBuilder(listOf(command) + args)
        }
        processBuilder.environment().putAll(env)
        
        process = processBuilder.start()
        reader = BufferedReader(InputStreamReader(process!!.inputStream))
        writer = BufferedWriter(OutputStreamWriter(process!!.outputStream))
        
        initialize()
    }

    private suspend fun initialize() = withContext(Dispatchers.IO) {
        val initRequest = buildJsonObject {
            put("jsonrpc", "2.0")
            put("id", requestId.incrementAndGet())
            put("method", "initialize")
            put("params", buildJsonObject {
                put("protocolVersion", "2024-11-05")
                put("capabilities", buildJsonObject {})
                put("clientInfo", buildJsonObject {
                    put("name", "AiAdventUltimate")
                    put("version", "1.0.0")
                })
            })
        }
        
        sendRequest(initRequest)
        val response = readResponse()
        
        val initializedNotification = buildJsonObject {
            put("jsonrpc", "2.0")
            put("method", "notifications/initialized")
        }
        sendRequest(initializedNotification)
    }

    actual suspend fun listTools(): List<McpTool> = withContext(Dispatchers.IO) {
        val request = buildJsonObject {
            put("jsonrpc", "2.0")
            put("id", requestId.incrementAndGet())
            put("method", "tools/list")
        }
        
        sendRequest(request)
        val response = readResponse()
        
        val tools = response["result"]?.jsonObject?.get("tools")?.jsonArray ?: return@withContext emptyList()
        
        tools.mapNotNull { toolElement ->
            try {
                val tool = toolElement.jsonObject
                val name = tool["name"]?.jsonPrimitive?.content ?: return@mapNotNull null
                val description = tool["description"]?.jsonPrimitive?.content ?: ""
                val inputSchema = tool["inputSchema"]?.jsonObject ?: buildJsonObject {}
                
                McpTool(name, description, inputSchema)
            } catch (e: Exception) {
                null
            }
        }
    }

    actual suspend fun callTool(name: String, arguments: JsonObject): String = withContext(Dispatchers.IO) {
        val request = buildJsonObject {
            put("jsonrpc", "2.0")
            put("id", requestId.incrementAndGet())
            put("method", "tools/call")
            put("params", buildJsonObject {
                put("name", name)
                put("arguments", arguments)
            })
        }
        
        sendRequest(request)
        val response = readResponse()
        
        val result = response["result"]?.jsonObject
        val content = result?.get("content")?.jsonArray
        
        content?.joinToString("\n") { item ->
            item.jsonObject["text"]?.jsonPrimitive?.content ?: ""
        } ?: "No response"
    }

    private fun sendRequest(request: JsonObject) {
        val requestStr = request.toString()
        writer?.write(requestStr)
        writer?.newLine()
        writer?.flush()
    }

    private fun readResponse(): JsonObject {
        val line = reader?.readLine() ?: throw Exception("Failed to read response")
        return json.parseToJsonElement(line).jsonObject
    }

    actual fun close() {
        try {
            writer?.close()
            reader?.close()
            process?.destroy()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
