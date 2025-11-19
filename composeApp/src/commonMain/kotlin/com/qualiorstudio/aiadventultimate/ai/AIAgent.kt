package com.qualiorstudio.aiadventultimate.ai

import com.qualiorstudio.aiadventultimate.api.*
import com.qualiorstudio.aiadventultimate.mcp.McpClient
import kotlinx.serialization.json.*

class AIAgent(
    private val deepSeek: DeepSeek,
    private val mcpClient: McpClient
) {
    private val json = Json { ignoreUnknownKeys = true }
    private var tools: List<DeepSeekTool> = emptyList()
    
    private val systemPrompt = """
You are a helpful AI assistant with access to task management through Todoist.

When users ask you to manage tasks, use the available tools to help them.
Be friendly, helpful, and proactive in suggesting ways to organize their tasks.
    """.trimIndent()

    suspend fun initialize() {
        try {
            mcpClient.start()
            val mcpTools = mcpClient.listTools()
            
            tools = mcpTools.map { mcpTool ->
                val normalizedSchema = normalizeSchema(mcpTool.inputSchema)
                DeepSeekTool(
                    type = "function",
                    function = DeepSeekFunction(
                        name = mcpTool.name,
                        description = mcpTool.description,
                        parameters = normalizedSchema
                    )
                )
            }
            println("Initialized ${tools.size} tools: ${tools.map { it.function.name }}")
        } catch (e: Exception) {
            println("Failed to initialize MCP client: ${e.message}")
            e.printStackTrace()
        }
    }

    private fun normalizeSchema(schema: JsonObject): JsonObject {
        return if (schema.containsKey("type")) {
            schema
        } else {
            buildJsonObject {
                put("type", "object")
                if (schema.containsKey("properties")) {
                    put("properties", schema["properties"]!!)
                } else {
                    put("properties", buildJsonObject {})
                }
                if (schema.containsKey("required")) {
                    put("required", schema["required"]!!)
                }
                if (schema.containsKey("description")) {
                    put("description", schema["description"]!!)
                }
            }
        }
    }

    suspend fun processMessage(
        userMessage: String,
        conversationHistory: List<DeepSeekMessage>
    ): String {
        val messages = mutableListOf(
            DeepSeekMessage(role = "system", content = systemPrompt)
        )
        messages.addAll(conversationHistory)
        messages.add(DeepSeekMessage(role = "user", content = userMessage))

        return try {
            println("=== Sending request to DeepSeek ===")
            println("Tools count: ${tools.size}")
            tools.forEachIndexed { index, tool ->
                println("Tool $index: ${tool.function.name}")
                println("  Parameters: ${tool.function.parameters}")
            }
            
            var response = deepSeek.sendMessage(messages, tools)
            var currentMessages = messages.toMutableList()
            
            println("Response finish_reason: ${response.choices.firstOrNull()?.finishReason}")
            
            while (response.choices.firstOrNull()?.finishReason == "tool_calls") {
                val assistantMessage = response.choices.first().message
                currentMessages.add(assistantMessage)
                
                val toolCalls = assistantMessage.toolCalls ?: break
                
                for (toolCall in toolCalls) {
                    val functionName = toolCall.function.name
                    val argumentsStr = toolCall.function.arguments
                    
                    try {
                        val arguments = json.parseToJsonElement(argumentsStr).jsonObject
                        val result = mcpClient.callTool(functionName, arguments)
                        
                        currentMessages.add(
                            DeepSeekMessage(
                                role = "tool",
                                content = result,
                                toolCallId = toolCall.id,
                                type = "tool"
                            )
                        )
                    } catch (e: Exception) {
                        currentMessages.add(
                            DeepSeekMessage(
                                role = "tool",
                                content = "Error executing tool: ${e.message}",
                                toolCallId = toolCall.id,
                                type = "tool"
                            )
                        )
                    }
                }
                
                response = deepSeek.sendMessage(currentMessages, tools)
            }
            
            response.choices.firstOrNull()?.message?.content?.trim() 
                ?: "Sorry, I couldn't generate a response."
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }

    fun close() {
        mcpClient.close()
    }
}
