package com.qualiorstudio.aiadventultimate.ai

import com.qualiorstudio.aiadventultimate.api.*
import com.qualiorstudio.aiadventultimate.mcp.McpClient
import kotlinx.serialization.json.*

data class ProcessMessageResult(
    val response: String,
    val shortPhrase: String,
    val updatedHistory: List<DeepSeekMessage>
)

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
    ): ProcessMessageResult {
        val messages = mutableListOf(
            DeepSeekMessage(role = "system", content = systemPrompt)
        )
        messages.addAll(conversationHistory)
        val userMsg = DeepSeekMessage(role = "user", content = userMessage)
        messages.add(userMsg)

        return try {
            println("=== Sending request to DeepSeek ===")
            println("Tools count: ${tools.size}")
            tools.forEachIndexed { index, tool ->
                println("Tool $index: ${tool.function.name}")
            }
            
            var response = deepSeek.sendMessage(messages, tools)
            var currentMessages = messages.toMutableList()
            
            var iterationCount = 0
            val maxIterations = 10
            
            println("Initial response finish_reason: ${response.choices.firstOrNull()?.finishReason}")
            
            while (response.choices.firstOrNull()?.finishReason == "tool_calls" && iterationCount < maxIterations) {
                iterationCount++
                println("=== Tool Call Chain Iteration $iterationCount ===")
                
                val assistantMessage = response.choices.first().message
                currentMessages.add(assistantMessage)
                
                val toolCalls = assistantMessage.toolCalls ?: break
                
                println("Processing ${toolCalls.size} tool call(s)")
                
                for (toolCall in toolCalls) {
                    val functionName = toolCall.function.name
                    val argumentsStr = toolCall.function.arguments
                    
                    println("  Calling tool: $functionName")
                    println("  Arguments: $argumentsStr")
                    
                    try {
                        val arguments = json.parseToJsonElement(argumentsStr).jsonObject
                        val result = mcpClient.callTool(functionName, arguments)
                        
                        println("  Tool result: ${result.take(200)}...")
                        
                        currentMessages.add(
                            DeepSeekMessage(
                                role = "tool",
                                content = result,
                                toolCallId = toolCall.id,
                                type = "tool"
                            )
                        )
                    } catch (e: Exception) {
                        println("  Tool error: ${e.message}")
                        e.printStackTrace()
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
                
                println("Sending follow-up request with ${currentMessages.size} messages")
                println("Tools will be included: ${tools.isNotEmpty()}")
                response = deepSeek.sendMessage(currentMessages, tools)
                println("Follow-up response finish_reason: ${response.choices.firstOrNull()?.finishReason}")
            }
            
            if (iterationCount >= maxIterations) {
                println("WARNING: Reached maximum iterations ($maxIterations) in tool call chain")
            }
            
            val finalAssistantMessage = response.choices.firstOrNull()?.message
            val finalContent = finalAssistantMessage?.content?.trim() 
                ?: "Sorry, I couldn't generate a response."
            
            if (finalAssistantMessage != null && finalAssistantMessage.content != null) {
                currentMessages.add(finalAssistantMessage)
            }
            
            val updatedHistory = currentMessages
                .drop(1)
                .filter { it.role != "system" }
            
            val shortPhrase = generateShortPhrase(finalContent)
            
            println("=== Final Response ===")
            println("Total iterations: $iterationCount")
            println("Final content length: ${finalContent.length}")
            println("Short phrase: $shortPhrase")
            println("Updated history size: ${updatedHistory.size}")
            
            ProcessMessageResult(
                response = finalContent,
                shortPhrase = shortPhrase,
                updatedHistory = updatedHistory
            )
        } catch (e: Exception) {
            println("Error in processMessage: ${e.message}")
            e.printStackTrace()
            ProcessMessageResult(
                response = "Error: ${e.message}",
                shortPhrase = "Произошла ошибка",
                updatedHistory = conversationHistory
            )
        }
    }
    
    private suspend fun generateShortPhrase(fullResponse: String): String {
        return try {
            val prompt = """
                На основе следующего ответа создай краткую фразу (максимум 1-2 предложения) для озвучивания голосом AI ассистента в стиле Джарвиса из фильмов Iron Man.
                
                Требования к стилю:
                - Максимально формальный и вежливый тон
                - Используй обращение "сэр" или "sir" где уместно
                - Говори от первого лица ("Я выполнил", "Я нашел")
                - Избегай сокращений и разговорных выражений
                - Будь профессионален и сдержан
                - Подчеркивай завершенность действия ("успешно", "выполнено", "готово")
                
                Примеры хороших фраз:
                - "Задача успешно добавлена в ваш список, сэр"
                - "Я нашел пять задач на сегодня. Две из них имеют высокий приоритет"
                - "Выполнено, сэр. Задача отмечена как завершенная"
                - "Информация обновлена. Могу ли я еще чем-то помочь?"
                
                Полный ответ:
                $fullResponse
                
                Верни только краткую фразу без дополнительных пояснений и кавычек.
            """.trimIndent()
            
            val messages = listOf(
                DeepSeekMessage(role = "system", content = "Ты профессиональный AI ассистент в стиле Джарвиса - формальный, вежливый, эффективный."),
                DeepSeekMessage(role = "user", content = prompt)
            )
            
            val response = deepSeek.sendMessage(messages, null)
            response.choices.firstOrNull()?.message?.content?.trim()
                ?.take(200)
                ?: "Готово"
        } catch (e: Exception) {
            println("Failed to generate short phrase: ${e.message}")
            "Готово"
        }
    }

    suspend fun getTodayTaskSummary(): String {
        return try {
            val getTasksTool = tools.find { it.function.name == "get_tasks" }
            if (getTasksTool == null) {
                return "Tool get_tasks not available"
            }
            
            val arguments = buildJsonObject {
                put("filter", "today")
            }
            
            val tasksResult = mcpClient.callTool("get_tasks", arguments)
            
            val summaryPrompt = """
                Проанализируй следующие задачи на сегодня и предоставь краткие итоги:
                - Общее количество задач
                - Количество выполненных задач
                - Количество невыполненных задач
                - Приоритетные задачи (если есть)
                - Краткое резюме
                
                Данные задач:
                $tasksResult
                
                Ответ должен быть кратким и структурированным, на русском языке.
            """.trimIndent()
            
            val messages = listOf(
                DeepSeekMessage(role = "system", content = systemPrompt),
                DeepSeekMessage(role = "user", content = summaryPrompt)
            )
            
            val response = deepSeek.sendMessage(messages, null)
            response.choices.firstOrNull()?.message?.content?.trim() 
                ?: "Не удалось получить итоги по задачам"
        } catch (e: Exception) {
            "Ошибка при получении итогов: ${e.message}"
        }
    }

    fun close() {
        mcpClient.close()
    }
}
