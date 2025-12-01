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
    private val customSystemPrompt: String? = null,
    private val mcpServerManager: com.qualiorstudio.aiadventultimate.mcp.MCPServerManager? = null,
    private val projectTools: ProjectTools? = null
) {
    private val json = Json { ignoreUnknownKeys = true }
    private var tools: List<DeepSeekTool> = emptyList()
    
    private val defaultSystemPrompt = """
You are a helpful AI assistant.
Be friendly, helpful, and proactive.

CRITICAL INSTRUCTIONS FOR INFORMATION USAGE:
- When context or information from the knowledge base is provided in the user's message, you MUST use ONLY that information to answer.
- DO NOT make up, invent, or hallucinate any information that is not explicitly provided in the context.
- DO NOT use your general knowledge if context from the knowledge base is provided - rely exclusively on the provided context.
- If the provided context does not contain enough information to fully answer the question, clearly state that the available information is insufficient, rather than inventing details.
- If no context is provided in the user's message, you may use your general knowledge as usual.
- When context is present, base your answer strictly on what is stated in that context, without adding supplementary information from your training data.

CITATIONS AND REFERENCES:
- When using information from the knowledge base context, ALWAYS cite your sources by referencing the source numbers (e.g., [Источник 1], [Источник 2])
- Use direct quotes in quotation marks when citing specific text from the sources
- Include a "Sources:" or "Источники:" section at the end of your response listing all sources used with their metadata (title, file, URL if available)
- Make citations clear and visible in your response - every factual claim should be backed by a source reference

The context from the knowledge base will be clearly marked in the user's message. Pay close attention to it and use it as your primary and only source of information.
    """.trimIndent()
    
    private val systemPrompt: String
        get() = customSystemPrompt ?: defaultSystemPrompt

    suspend fun initialize() {
        val mcpTools = mcpServerManager?.getAvailableTools() ?: emptyList()
        val projectToolsList = projectTools?.getTools() ?: emptyList()
        tools = mcpTools + projectToolsList
        println("=== AIAgent.initialize() ===")
        println("Загружено MCP инструментов: ${mcpTools.size}")
        println("Загружено инструментов проекта: ${projectToolsList.size}")
        println("Всего инструментов: ${tools.size}")
        if (tools.isEmpty()) {
            println("⚠️ ВНИМАНИЕ: Нет доступных инструментов!")
            if (mcpServerManager == null) {
                println("  Причина: mcpServerManager = null")
            }
            if (projectTools == null) {
                println("  Причина: projectTools = null")
            }
        } else {
            tools.forEachIndexed { index, tool ->
                println("  [$index] ${tool.function.name}: ${tool.function.description.take(60)}...")
            }
        }
    }

    suspend fun processMessage(
        userMessage: String,
        conversationHistory: List<DeepSeekMessage>,
        useRAG: Boolean = true,
        temperature: Double = 0.7,
        maxTokens: Int = 8000
    ): ProcessMessageResult {
        val ragEnabled = useRAG && ragService != null && ragService.isAvailable()
        return try {
            if (ragEnabled) {
                val comparison = ragService?.buildComparison(userMessage)
                if (comparison != null) {
                    println("=== RAG: Генерирую ответ только по reranker-контексту ===")
                    val rerankedCompletion = requestCompletion(
                        comparison.reranked.prompt,
                        conversationHistory,
                        temperature = temperature,
                        maxTokens = maxTokens
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
                maxTokens = maxTokens
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
        maxTokens: Int = 8000
    ): CompletionOutput {
        val messages = mutableListOf(
            DeepSeekMessage(role = "system", content = systemPrompt)
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
                                mcpServerManager.callTool(functionName, argumentsJson)
                            }
                            projectTools != null && isProjectTool(functionName) -> {
                                projectTools.executeTool(functionName, argumentsJson)
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
    
    fun close() {
        ragService?.close()
    }
}
