package com.qualiorstudio.aiadventultimate.ai

import com.qualiorstudio.aiadventultimate.api.*
import com.qualiorstudio.aiadventultimate.model.ChatResponseVariant
import kotlinx.serialization.json.*
import kotlin.math.roundToInt

data class ProcessMessageResult(
    val response: String,
    val shortPhrase: String,
    val updatedHistory: List<DeepSeekMessage>,
    val variants: List<ChatResponseVariant> = emptyList()
)

class AIAgent(
    private val deepSeek: DeepSeek,
    private val ragService: RAGService? = null,
    private val maxIterations: Int = 10,
    private var customSystemPrompt: String? = null,
    private val mcpServerManager: com.qualiorstudio.aiadventultimate.mcp.MCPServerManager? = null,
    private val projectTools: ProjectTools? = null,
    private val taskBreakdownTools: TaskBreakdownTools? = null,
    private val onTodoistTaskCreated: (() -> Unit)? = null,
    private val onProgressMessage: ((String) -> Unit)? = null
) {
    private val json = Json { ignoreUnknownKeys = true }
    private var tools: List<DeepSeekTool> = emptyList()
    private var projectContext: String = ""
    private var currentRagContext: String? = null
    
    private val defaultSystemPrompt = """
You are a helpful AI assistant.
Be friendly, helpful, and proactive.

CRITICAL INSTRUCTIONS FOR INFORMATION USAGE - HIERARCHY:
Follow this priority order when answering questions:

1. RAG (Retrieval-Augmented Generation) - PRIMARY SOURCE:
   - If context from the knowledge base (RAG) is provided in the user's message, use it as your PRIMARY source
   - Prioritize information from RAG sources over all other sources
   - When using RAG information, ALWAYS cite sources by referencing source numbers (e.g., [Источник 1], [Источник 2])
   - Use direct quotes in quotation marks when citing specific text from RAG sources
   - Include a "Sources:" or "Источники:" section at the end listing all RAG sources used with their metadata

2. MCP (Model Context Protocol) Tools - SECONDARY SOURCE:
   - If RAG context is missing, insufficient, or doesn't contain the needed information, use available MCP tools
   - MCP tools can provide real-time data, documentation, or external resources
   - Call MCP tools when you need information that is not in the RAG context
   - After using MCP tools, cite them appropriately in your response
   
SPECIAL TOOL: breakdown_task
   - Use this tool when the user asks to create a task that requires breaking down into smaller subtasks
   - Examples: "Add feature to display ad details", "Implement authentication", "Create notification system"
   - This tool automatically creates/finds a project in Todoist and breaks down the task into subtasks
   - The tool uses RAG context from the knowledge base to better understand the project structure
   - IMPORTANT: The tool returns ONLY subtasks - DO NOT create a main task, only subtasks!
   - CRITICAL: After calling breakdown_task, you MUST create each subtask in Todoist using create_task tool
   - MANDATORY RULE: projectId is ALWAYS provided in the breakdown_task response - you MUST include it in EVERY create_task call
   - Example with projectId: create_task({"content": "Task title", "description": "Task description", "priority": 2, "projectId": "12345678"})
   - CRITICAL: If projectId is provided but you don't include it in create_task, tasks will go to Inbox - this is WRONG!
   - DO NOT create tasks in Inbox - ALWAYS use projectId from the breakdown_task response!
   - DO NOT create a main task - only create subtasks!
   - DO NOT skip any subtasks - create ALL of them!
   - For simple tasks (like "Buy milk"), use the regular create_task from Todoist MCP instead

AUTOMATIC TASK EXECUTION: execute_tasks
   - AFTER creating ALL subtasks using create_task, you MUST call execute_tasks tool
   - This tool automatically executes all tasks from the project one by one
   - It uses RAG context from the project knowledge base to understand the project structure
   - For each task: gets task details, uses RAG for context, executes the task, marks as completed
   - The tool works automatically - you only need to call it ONCE after creating all subtasks
   - Format: execute_tasks({"project_id": "12345678"}) - use the project_id from breakdown_task response
   - CRITICAL: Call execute_tasks ONLY after ALL subtasks have been created!
   - CRITICAL: You MUST call execute_tasks in the SAME tool call chain as create_task calls!
   - CRITICAL: Do NOT finish the conversation without calling execute_tasks!
   - The tool will show progress messages in chat for each step
   - Example workflow: breakdown_task -> create_task (for each subtask) -> execute_tasks

3. General Knowledge - FALLBACK:
   - Only use your general knowledge if:
     a) No RAG context is provided, OR
     b) RAG context doesn't contain relevant information, AND
     c) MCP tools are not available or don't provide the needed information
   - When using general knowledge, clearly indicate that you're using your training data
   - Be transparent about the source of your information

IMPORTANT RULES:
- Always try RAG first if context is provided
- If RAG information is insufficient, supplement with MCP tools, then general knowledge
- Never invent or hallucinate information - if you don't know something, say so
- Be clear about which source you're using (RAG, MCP, or general knowledge)
- When combining sources, prioritize RAG > MCP > General Knowledge

The context from the knowledge base (RAG) will be clearly marked in the user's message. Pay close attention to it and use it as your primary source when available.
    """.trimIndent()
    
    private val systemPrompt: String
        get() {
            val basePrompt = customSystemPrompt ?: defaultSystemPrompt
            return if (projectContext.isNotEmpty()) {
                "$basePrompt\n\n$projectContext"
            } else {
                basePrompt
            }
        }
    
    fun updateProjectContext(context: String) {
        projectContext = context
    }

    suspend fun initialize() {
        val mcpTools = mcpServerManager?.getAvailableTools() ?: emptyList()
        val projectToolsList = projectTools?.getTools() ?: emptyList()
        val taskBreakdownToolsList = taskBreakdownTools?.getTools() ?: emptyList()
        tools = mcpTools + projectToolsList + taskBreakdownToolsList
        println("=== AIAgent.initialize() ===")
        println("Загружено MCP инструментов: ${mcpTools.size}")
        println("Загружено инструментов проекта: ${projectToolsList.size}")
        println("Загружено инструментов разбиения задач: ${taskBreakdownToolsList.size}")
        println("Всего инструментов: ${tools.size}")
        if (tools.isEmpty()) {
            println("⚠️ ВНИМАНИЕ: Нет доступных инструментов!")
            if (mcpServerManager == null) {
                println("  Причина: mcpServerManager = null")
            }
            if (projectTools == null) {
                println("  Причина: projectTools = null")
            }
            if (taskBreakdownTools == null) {
                println("  Причина: taskBreakdownTools = null")
            }
        } else {
            tools.forEachIndexed { index, tool ->
                println("  [$index] ${tool.function.name}: ${tool.function.description.take(60)}...")
            }
        }
    }
    
    fun updateRagContext(context: String?) {
        currentRagContext = context
    }

    suspend fun processMessage(
        userMessage: String,
        conversationHistory: List<DeepSeekMessage>,
        useRAG: Boolean = true,
        temperature: Double = 0.7,
        maxTokens: Int = 8000
    ): ProcessMessageResult {
        val ragEnabled = useRAG && ragService != null && ragService.isAvailable()
        var ragContextForTools: String? = null
        
        return try {
            if (ragEnabled) {
                val comparison = ragService?.buildComparison(userMessage)
                if (comparison != null) {
                    println("=== RAG: Генерирую ответ только по reranker-контексту ===")
                    ragContextForTools = comparison.reranked.context
                    updateRagContext(ragContextForTools)
                    val rerankedCompletion = requestCompletion(
                        comparison.reranked.prompt,
                        conversationHistory,
                        temperature = temperature,
                        maxTokens = maxTokens,
                        ragContext = ragContextForTools
                    )
                    val variants = listOf(
                        createVariant(
                            context = comparison.reranked,
                            content = rerankedCompletion.content,
                            isPreferred = true
                        )
                    )
                    val shortPhrase = generateShortPhrase(rerankedCompletion.content, temperature, maxTokens)
                    return ProcessMessageResult(
                        response = rerankedCompletion.content,
                        shortPhrase = shortPhrase,
                        updatedHistory = rerankedCompletion.updatedHistory,
                        variants = variants
                    )
                }
            } else if (!useRAG) {
                println("=== RAG: Отключен, используем исходный вопрос ===")
            }

            val completion = requestCompletion(
                userMessage, 
                conversationHistory,
                temperature = temperature,
                maxTokens = maxTokens,
                ragContext = ragContextForTools
            )
            val shortPhrase = generateShortPhrase(completion.content, temperature, maxTokens)

            ProcessMessageResult(
                response = completion.content,
                shortPhrase = shortPhrase,
                updatedHistory = completion.updatedHistory
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
    
    private suspend fun requestCompletion(
        userContent: String,
        conversationHistory: List<DeepSeekMessage>,
        temperature: Double = 0.7,
        maxTokens: Int = 8000,
        ragContext: String? = null
    ): CompletionOutput {
        val finalSystemPrompt = systemPrompt
        println("=== AIAgent System Prompt ===")
        println("System prompt length: ${finalSystemPrompt.length}")
        println("Has project context: ${projectContext.isNotEmpty()}")
        if (projectContext.isNotEmpty()) {
            println("Project context: ${projectContext.take(200)}...")
        }
        
        val messages = mutableListOf(
            DeepSeekMessage(role = "system", content = finalSystemPrompt)
        )
        messages.addAll(conversationHistory)
        val userMsg = DeepSeekMessage(role = "user", content = userContent)
        messages.add(userMsg)

        return try {
            println("=== Sending request to DeepSeek ===")
            println("Tools count: ${tools.size}")
            if (tools.isEmpty()) {
                println("⚠️ ВНИМАНИЕ: Инструменты не передаются в запрос!")
                println("  Проверьте, что MCP серверы инициализированы и возвращают инструменты")
            } else {
                println("✓ Инструменты будут переданы в запрос:")
                tools.forEachIndexed { index, tool ->
                    println("  [$index] ${tool.function.name}: ${tool.function.description.take(80)}...")
                }
            }

            var response = deepSeek.sendMessage(messages, tools.ifEmpty { null }, temperature = temperature, maxTokens = maxTokens)
            var currentMessages = messages.toMutableList()

            var iterationCount = 0

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
                        val argumentsJson = json.parseToJsonElement(argumentsStr).jsonObject
                        
                        val result = when {
                            mcpServerManager?.hasTools(functionName) == true -> {
                                val toolResult = mcpServerManager.callTool(functionName, argumentsJson)
                                // Отслеживаем успешное создание задачи в Todoist
                                if (functionName == "create_task") {
                                    println("✓ Задача создана в Todoist, уведомляем об обновлении списка")
                                    onTodoistTaskCreated?.invoke()
                                }
                                toolResult
                            }
                            projectTools != null && isProjectTool(functionName) -> {
                                projectTools.executeTool(functionName, argumentsJson)
                            }
                            taskBreakdownTools != null && (functionName == "breakdown_task" || functionName == "breakdown_and_create_task" || functionName == "execute_tasks") -> {
                                taskBreakdownTools.executeTool(functionName, argumentsJson, currentRagContext)
                            }
                            else -> null
                        }
                        
                        val resultContent = result?.let {
                            when {
                                it is JsonObject && it.containsKey("text") -> it["text"]?.jsonPrimitive?.content ?: it.toString()
                                it is JsonObject && it.containsKey("content") -> {
                                    val content = it["content"]
                                    if (content is JsonArray) {
                                        content.joinToString("\n") { item ->
                                            (item as? JsonObject)?.get("text")?.jsonPrimitive?.content ?: item.toString()
                                        }
                                    } else {
                                        content.toString()
                                    }
                                }
                                else -> it.toString()
                            }
                        } ?: "Tool $functionName executed successfully"
                        
                        println("  Tool result: ${resultContent.take(200)}")
                        currentMessages.add(
                            DeepSeekMessage(
                                role = "tool",
                                content = resultContent,
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
                                content = "Error executing tool $functionName: ${e.message}",
                                toolCallId = toolCall.id,
                                type = "tool"
                            )
                        )
                    }
                }

                println("Sending follow-up request with ${currentMessages.size} messages")
                println("Tools will be included: ${tools.isNotEmpty()}")
                response = deepSeek.sendMessage(currentMessages, tools, temperature = temperature, maxTokens = maxTokens)
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

            println("=== Final Response ===")
            println("Total iterations: $iterationCount")
            println("Final content length: ${finalContent.length}")
            println("Updated history size: ${updatedHistory.size}")

            CompletionOutput(
                content = finalContent,
                updatedHistory = updatedHistory
            )
        } catch (e: Exception) {
            println("Error while requesting completion: ${e.message}")
            throw e
        }
    }

    private fun createVariant(
        context: RAGVariantContext,
        content: String,
        isPreferred: Boolean
    ): ChatResponseVariant {
        return ChatResponseVariant(
            id = context.id,
            title = context.title,
            body = content,
            metadata = buildVariantMetadata(context),
            isPreferred = isPreferred
        )
    }

    private fun buildVariantMetadata(context: RAGVariantContext): String {
        val usedChunks = context.chunks.size
        val filteredOut = (context.totalCandidates - usedChunks).coerceAtLeast(0)
        val parts = mutableListOf<String>()
        parts.add("Чанки: $usedChunks")
        if (filteredOut > 0) {
            parts.add("Отфильтровано: $filteredOut")
        }
        val thresholdLabel = if (context.chunks.any { it.rerankScore != null }) "Score ≥" else "Sim ≥"
        val thresholdPart = context.similarityThreshold?.let { "$thresholdLabel ${formatScore(it)}" } ?: "Без порога"
        parts.add(thresholdPart)
        context.averageSimilarity?.let {
            parts.add("Avg sim: ${formatScore(it)}")
        }
        context.averageCombinedScore?.let {
            parts.add("Avg score: ${formatScore(it)}")
        }
        return parts.joinToString(" | ")
    }

    private fun formatScore(value: Double): String {
        val rounded = (value * 100).roundToInt() / 100.0
        return rounded.toString()
    }

    private suspend fun generateShortPhrase(
        fullResponse: String,
        temperature: Double = 0.7,
        maxTokens: Int = 8000
    ): String {
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
            
            val response = deepSeek.sendMessage(messages, null, temperature = temperature, maxTokens = maxTokens)
            response.choices.firstOrNull()?.message?.content?.trim()
                ?.take(200)
                ?: "Готово"
        } catch (e: Exception) {
            println("Failed to generate short phrase: ${e.message}")
            "Готово"
        }
    }

    private data class CompletionOutput(
        val content: String,
        val updatedHistory: List<DeepSeekMessage>
    )

    private fun isProjectTool(functionName: String): Boolean {
        return functionName in listOf(
            "list_files",
            "read_file",
            "write_file",
            "search_in_files",
            "get_file_info"
        )
    }
    
    private fun isTaskBreakdownTool(functionName: String): Boolean {
        return functionName == "breakdown_and_create_task"
    }
    
    fun close() {
        ragService?.close()
    }
}
