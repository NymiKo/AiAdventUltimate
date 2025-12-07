package com.qualiorstudio.aiadventultimate.ai

import com.qualiorstudio.aiadventultimate.api.DeepSeekFunction
import com.qualiorstudio.aiadventultimate.api.DeepSeekTool
import com.qualiorstudio.aiadventultimate.api.DeepSeek
import com.qualiorstudio.aiadventultimate.api.DeepSeekMessage
import com.qualiorstudio.aiadventultimate.service.TaskBreakdownService
import com.qualiorstudio.aiadventultimate.ai.RAGService
import com.qualiorstudio.aiadventultimate.mcp.MCPServerManager
import kotlinx.serialization.json.*
import kotlinx.serialization.Serializable

class TaskBreakdownTools(
    private val taskBreakdownService: TaskBreakdownService?,
    private val onProgressMessage: ((String) -> Unit)? = null,
    private val deepSeek: DeepSeek? = null,
    private val ragService: RAGService? = null,
    private val mcpManager: MCPServerManager? = null,
    private val projectTools: ProjectTools? = null
) {
    private val json = Json { ignoreUnknownKeys = true }
    
    fun getTools(): List<DeepSeekTool> {
        if (taskBreakdownService == null) {
            return emptyList()
        }
        
        val tools = mutableListOf<DeepSeekTool>()
        tools.add(createBreakdownTaskTool())
        
        // Добавляем инструмент выполнения задач только если есть все необходимые зависимости
        if (deepSeek != null && mcpManager != null) {
            val executeTasksTool = createExecuteTasksTool()
            tools.add(executeTasksTool)
            println("✓ Инструмент execute_tasks добавлен в список доступных инструментов")
        } else {
            println("⚠️ Инструмент execute_tasks НЕ добавлен: deepSeek=${deepSeek != null}, mcpManager=${mcpManager != null}")
        }
        
        return tools
    }
    
    private fun createBreakdownTaskTool(): DeepSeekTool {
        return DeepSeekTool(
            type = "function",
            function = DeepSeekFunction(
                name = "breakdown_task",
                description = """
Разбивает задачу на подзадачи и автоматически создает/находит проект в Todoist. 
Используй этот инструмент, когда пользователь просит создать задачу, которая требует разбиения на более мелкие подзадачи.
Например: "Добавить фичу с отображением деталей объявления", "Реализовать авторизацию", "Создать систему уведомлений".

ВАЖНО: После получения списка подзадач, ты ДОЛЖЕН создать КАЖДУЮ подзадачу в Todoist, используя инструмент create_task из Todoist MCP.

Инструмент автоматически:
1. Создает/находит проект в Todoist с именем проекта из приложения
2. Разбивает задачу на подзадачи (UI, логика, данные, тесты)
3. Возвращает список подзадач с projectId

Инструмент возвращает:
1. Список подзадач (subtasks) с полями: title, description, priority, order, projectId
2. ID проекта (project_id) - ОБЯЗАТЕЛЬНО используй его при создании задач!

После получения результата:
- НЕ создавай главную задачу - создавай ТОЛЬКО подзадачи!
- Для КАЖДОЙ подзадачи из списка subtasks вызови create_task с:
   - content = subtask.title
   - description = subtask.description (если не пустое)
   - priority = subtask.priority
   - projectId = project_id (ОБЯЗАТЕЛЬНО! Без этого задачи попадут во Входящие!)
   
КРИТИЧЕСКИ ВАЖНО: project_id ВСЕГДА указан в ответе - ты ОБЯЗАН использовать его при КАЖДОМ вызове create_task!
Без projectId задачи попадут во Входящие - это ошибка!

Используй этот инструмент ТОЛЬКО если задача действительно требует разбиения на подзадачи. 
Для простых задач (например, "Купить молоко") используй обычный create_task из Todoist MCP.
                """.trimIndent(),
                parameters = buildJsonObject {
                    put("type", "object")
                    putJsonObject("properties") {
                        putJsonObject("task_description") {
                            put("type", "string")
                            put("description", "Описание задачи, которую нужно разбить на подзадачи")
                        }
                        putJsonObject("project_id") {
                            put("type", "string")
                            put("description", "ID проекта в Todoist (опционально)")
                        }
                    }
                    putJsonArray("required") {
                        add("task_description")
                    }
                }
            )
        )
    }
    
    suspend fun executeTool(functionName: String, arguments: JsonObject, ragContext: String?): JsonElement {
        if (taskBreakdownService == null) {
            return buildJsonObject {
                put("error", "TaskBreakdownService не доступен")
            }
        }
        
        return when (functionName) {
            "breakdown_task" -> breakdownTask(arguments, ragContext)
            "breakdown_and_create_task" -> breakdownTask(arguments, ragContext)
            "execute_tasks" -> executeTasks(arguments, ragContext)
            else -> buildJsonObject {
                put("error", "Неизвестная функция: $functionName")
            }
        }
    }
    
    private suspend fun breakdownTask(arguments: JsonObject, ragContext: String?): JsonElement {
        val taskDescription = arguments["task_description"]?.jsonPrimitive?.content
            ?: return buildJsonObject {
                put("error", "task_description обязателен")
            }
        
        val service = taskBreakdownService ?: return buildJsonObject {
            put("error", "TaskBreakdownService не доступен")
        }
        
        return try {
            onProgressMessage?.invoke("Получаю ID проекта из Todoist...")
            println("=== Шаг 1: Создание/поиск проекта ===")
            val projectId = service.getOrCreateProjectId()
            
            if (projectId == null) {
                println("⚠️ ВНИМАНИЕ: projectId = null, задачи будут созданы во Входящие!")
                onProgressMessage?.invoke("⚠️ Не удалось получить ID проекта")
                return buildJsonObject {
                    put("error", "Не удалось создать или найти проект. projectName: ${service.projectName}")
                }
            } else {
                println("✓ Project ID найден/создан: $projectId")
                onProgressMessage?.invoke("✓ Проект найден/создан в Todoist")
            }
            
            onProgressMessage?.invoke("Разбиваю задачу на подзадачи...")
            println("=== Шаг 2: Разбиение задачи на подзадачи ===")
            val breakdown = service.breakdownTask(taskDescription, ragContext)
            
            onProgressMessage?.invoke("✓ Задача разбита на ${breakdown.subtasks.size} подзадач")
            
            println("=== Результат разбиения задачи ===")
            println("Количество подзадач: ${breakdown.subtasks.size}")
            println("Project ID: $projectId")
            
            onProgressMessage?.invoke("Создаю задачи в Todoist...")
            
            val responseJson = buildJsonObject {
                put("success", true)
                put("project_id", projectId)
                put("subtasks_count", breakdown.subtasks.size)
                put("subtasks", JsonArray(breakdown.subtasks.sortedBy { it.order }.map { subtask ->
                    buildJsonObject {
                        put("title", subtask.title)
                        put("description", subtask.description ?: "")
                        put("priority", subtask.priority ?: 4)
                        put("order", subtask.order)
                        if (projectId != null) {
                            put("projectId", projectId)
                        }
                    }
                }))
                put("instruction", buildString {
                    appendLine("Задача успешно разбита на подзадачи. Проект создан/найден в Todoist.")
                    appendLine()
                    appendLine("═══════════════════════════════════════════════════════════")
                    appendLine("КРИТИЧЕСКИ ВАЖНО: Все подзадачи ДОЛЖНЫ быть созданы в проекте!")
                    appendLine("Project ID: $projectId")
                    appendLine("ОБЯЗАТЕЛЬНО передавай projectId=\"$projectId\" при КАЖДОМ вызове create_task!")
                    appendLine("НЕ создавай задачи во Входящие - всегда используй projectId!")
                    appendLine("═══════════════════════════════════════════════════════════")
                    appendLine()
                    appendLine("ВАЖНО: Главную задачу создавать НЕ нужно! Создавай ТОЛЬКО подзадачи!")
                    appendLine()
                    appendLine("Для КАЖДОЙ подзадачи из списка subtasks вызови create_task:")
                    appendLine("ТОЧНЫЙ формат JSON для КАЖДОЙ подзадачи (скопируй и используй):")
                    breakdown.subtasks.sortedBy { it.order }.forEachIndexed { index, subtask ->
                        val desc = subtask.description?.replace("\"", "\\\"")?.replace("\n", " ") ?: ""
                        appendLine("Подзадача ${index + 1}: {\"content\": \"${subtask.title.replace("\"", "\\\"")}\", \"description\": \"$desc\", \"priority\": ${subtask.priority ?: 4}, \"projectId\": \"$projectId\"}")
                    }
                    appendLine()
                    appendLine("═══════════════════════════════════════════════════════════")
                    appendLine("КРИТИЧЕСКИЕ ТРЕБОВАНИЯ:")
                    appendLine("1. НЕ создавай главную задачу - только подзадачи!")
                    appendLine("2. Вызови create_task для КАЖДОЙ подзадачи отдельно")
                    appendLine("3. Не пропускай ни одной подзадачи!")
                    appendLine("4. ВСЕ задачи ДОЛЖНЫ иметь projectId=\"$projectId\"")
                    appendLine("5. БЕЗ projectId задачи попадут во Входящие - это ОШИБКА!")
                    appendLine("6. Проверь, что projectId присутствует в КАЖДОМ вызове create_task!")
                    appendLine()
                    appendLine("═══════════════════════════════════════════════════════════")
                    appendLine("КРИТИЧЕСКИ ВАЖНО - АВТОМАТИЧЕСКОЕ ВЫПОЛНЕНИЕ ЗАДАЧ:")
                    appendLine("ПОСЛЕ создания ВСЕХ ${breakdown.subtasks.size} подзадач, ты ОБЯЗАН вызвать execute_tasks!")
                    appendLine()
                    appendLine("ШАГ 1: Создай ВСЕ ${breakdown.subtasks.size} подзадач через create_task")
                    appendLine("ШАГ 2: Сразу после создания ВСЕХ подзадач вызови execute_tasks:")
                    appendLine("execute_tasks({\"project_id\": \"$projectId\"})")
                    appendLine()
                    appendLine("Инструмент execute_tasks автоматически:")
                    appendLine("- Получит список всех задач из проекта")
                    appendLine("- Выполнит каждую задачу по очереди")
                    appendLine("- Использует RAG для получения контекста проекта")
                    appendLine("- Отметит задачи как выполненные в Todoist")
                    appendLine("- Покажет прогресс выполнения в чате")
                    appendLine()
                    appendLine("КРИТИЧЕСКИ ВАЖНО:")
                    appendLine("1. Вызови execute_tasks ТОЛЬКО после создания ВСЕХ ${breakdown.subtasks.size} подзадач")
                    appendLine("2. НЕ пропускай вызов execute_tasks - это обязательный шаг!")
                    appendLine("3. Вызови execute_tasks в том же цикле вызовов инструментов, что и create_task")
                    appendLine("4. Формат вызова: execute_tasks({\"project_id\": \"$projectId\"})")
                    appendLine("═══════════════════════════════════════════════════════════")
                })
            }
            
            println("=== Финальный JSON ответ breakdown_task ===")
            println("project_id в ответе: ${responseJson["project_id"]}")
            println("Количество подзадач: ${responseJson["subtasks_count"]}")
            
            return responseJson
        } catch (e: Exception) {
            println("Ошибка при разбиении задачи: ${e.message}")
            e.printStackTrace()
            buildJsonObject {
                put("error", "Ошибка при разбиении задачи: ${e.message}")
                put("details", e.stackTraceToString())
            }
        }
    }
    
    private fun createExecuteTasksTool(): DeepSeekTool {
        return DeepSeekTool(
            type = "function",
            function = DeepSeekFunction(
                name = "execute_tasks",
                description = """
Автоматически выполняет задачи из текущего проекта в Todoist. 
Используй этот инструмент ПОСЛЕ создания всех подзадач через breakdown_task и create_task.

Инструмент автоматически:
1. Получает список незавершенных задач из проекта
2. Берет первую задачу из списка
3. Использует RAG для получения контекста проекта (если доступен)
4. Выполняет задачу, генерируя решение/код/реализацию
5. Отмечает задачу как выполненную в Todoist
6. Повторяет процесс пока список задач не будет пуст

ВАЖНО: 
- Используй этот инструмент ТОЛЬКО после создания всех подзадач
- Инструмент работает автоматически - не нужно вызывать его для каждой задачи отдельно
- Все шаги выполнения отображаются в чате
                """.trimIndent(),
                parameters = buildJsonObject {
                    put("type", "object")
                    putJsonObject("properties") {
                        putJsonObject("project_id") {
                            put("type", "string")
                            put("description", "ID проекта в Todoist (обязательно)")
                        }
                    }
                    putJsonArray("required") {
                        add("project_id")
                    }
                }
            )
        )
    }
    
    private suspend fun executeTasks(arguments: JsonObject, ragContext: String?): JsonElement {
        val projectId = arguments["project_id"]?.jsonPrimitive?.content
            ?: return buildJsonObject {
                put("error", "project_id обязателен")
            }
        
        if (deepSeek == null || mcpManager == null) {
            return buildJsonObject {
                put("error", "DeepSeek или MCPServerManager не доступны")
            }
        }
        
        return try {
            var completedTasksCount = 0
            var iteration = 0
            val maxIterations = 50 // Защита от бесконечного цикла
            
            while (iteration < maxIterations) {
                iteration++
                
                // Получаем список задач
                onProgressMessage?.invoke("Получаю список задач из проекта...")
                val getTasksResult = mcpManager.callTool("get_tasks", buildJsonObject {
                    put("projectId", projectId)
                })
                
                // Парсим ответ
                val tasks = parseTasksFromResponse(getTasksResult)
                val activeTasks = tasks.filter { !it.isCompleted }
                
                if (activeTasks.isEmpty()) {
                    onProgressMessage?.invoke("✓ Все задачи выполнены!")
                    return buildJsonObject {
                        put("success", true)
                        put("completed_tasks", completedTasksCount)
                        put("message", "Все задачи из проекта выполнены")
                    }
                }
                
                // Берем первую задачу
                val currentTask = activeTasks.first()
                onProgressMessage?.invoke("Выполняю задачу: \"${currentTask.content}\"...")
                
                // Получаем контекст из RAG если доступен
                var taskContext = ""
                if (ragService != null && ragService.isAvailable()) {
                    try {
                        val query = "${currentTask.content} ${currentTask.description ?: ""}"
                        val chunks = ragService.searchRelevantChunks(query)
                        if (chunks.isNotEmpty()) {
                            val rankedChunks = chunks.map { 
                                com.qualiorstudio.aiadventultimate.ai.RankedChunk(
                                    chunk = it.chunk,
                                    similarity = it.similarity
                                )
                            }
                            taskContext = ragService.buildContext(rankedChunks)
                            println("RAG контекст для задачи: ${taskContext.take(200)}...")
                        }
                    } catch (e: Exception) {
                        println("Ошибка получения RAG контекста: ${e.message}")
                    }
                }
                
                // Получаем инструменты проекта для работы с файлами
                val availableTools = projectTools?.getTools() ?: emptyList()
                
                // Формируем промпт для выполнения задачи
                val taskPrompt = buildString {
                    appendLine("═══════════════════════════════════════════════════════════")
                    appendLine("ЗАДАЧА: ${currentTask.content}")
                    if (currentTask.description != null && currentTask.description.isNotBlank()) {
                        appendLine()
                        appendLine("ОПИСАНИЕ: ${currentTask.description}")
                    }
                    appendLine("═══════════════════════════════════════════════════════════")
                    appendLine()
                    
                    if (taskContext.isNotBlank()) {
                        appendLine("КОНТЕКСТ ПРОЕКТА (из базы знаний):")
                        appendLine(taskContext)
                        appendLine()
                        appendLine("ВАЖНО: Используй этот контекст для поиска нужных файлов!")
                        appendLine("НЕ используй list_files без необходимости - файлы указаны в контексте!")
                        appendLine()
                    }
                    
                    appendLine("═══════════════════════════════════════════════════════════")
                    appendLine("ОБЯЗАТЕЛЬНЫЕ ДЕЙСТВИЯ (выполни ВСЕ шаги):")
                    appendLine("═══════════════════════════════════════════════════════════")
                    appendLine()
                    appendLine("ШАГ 1: НАЙДИ нужный файл")
                    if (taskContext.isNotBlank()) {
                        appendLine("  - Посмотри в контексте выше - там уже есть пути к файлам!")
                        appendLine("  - Используй search_in_files ТОЛЬКО если файл не указан в контексте")
                    } else {
                        appendLine("  - Используй search_in_files для поиска по коду")
                    }
                    appendLine()
                    appendLine("ШАГ 2: ПРОЧИТАЙ файл с помощью read_file")
                    appendLine("  Пример: read_file({\"path\": \"src/main/kotlin/MyFile.kt\"})")
                    appendLine()
                    appendLine("ШАГ 3: ИЗМЕНИ код (если нужно изменить существующий файл)")
                    appendLine("  - Внеси изменения в прочитанный код")
                    appendLine("  - Сохрани ВЕСЬ файл целиком (не только измененную часть!)")
                    appendLine()
                    appendLine("ШАГ 4: ЗАПИШИ изменения с помощью write_file")
                    appendLine("  Пример: write_file({\"path\": \"src/main/kotlin/MyFile.kt\", \"content\": \"...весь код файла...\"})")
                    appendLine()
                    appendLine("ШАГ 5: ПОДТВЕРДИ изменения с помощью read_file")
                    appendLine("  - Прочитай файл еще раз, чтобы убедиться, что изменения сохранены")
                    appendLine()
                    appendLine("═══════════════════════════════════════════════════════════")
                    appendLine("КРИТИЧЕСКИ ВАЖНО:")
                    appendLine("═══════════════════════════════════════════════════════════")
                    appendLine("1. ТЫ ОБЯЗАН вызвать минимум 2 инструмента:")
                    appendLine("   - read_file (прочитать файл)")
                    appendLine("   - write_file (записать изменения)")
                    appendLine()
                    appendLine("2. НЕ ПИШИ текстовое описание изменений!")
                    appendLine("   НЕПРАВИЛЬНО: \"Я создал компонент CalculatorInput.kt\"")
                    appendLine("   ПРАВИЛЬНО: [вызов write_file с полным кодом компонента]")
                    appendLine()
                    appendLine("3. НЕ используй list_files без необходимости!")
                    appendLine("   - Файлы УЖЕ указаны в контексте проекта выше")
                    appendLine("   - Используй list_files ТОЛЬКО если нужно посмотреть структуру НОВОЙ директории")
                    appendLine()
                    appendLine("4. При создании НОВОГО файла:")
                    appendLine("   - НЕ нужен read_file (файл еще не существует)")
                    appendLine("   - Просто вызови write_file с полным содержимым")
                    appendLine()
                    appendLine("5. При изменении СУЩЕСТВУЮЩЕГО файла:")
                    appendLine("   - Сначала read_file (прочитать)")
                    appendLine("   - Затем write_file (записать ВЕСЬ файл с изменениями)")
                    appendLine()
                    appendLine("═══════════════════════════════════════════════════════════")
                    appendLine("НАЧИНАЙ ВЫПОЛНЕНИЕ ПРЯМО СЕЙЧАС!")
                    appendLine("Первый вызов инструмента должен быть read_file или write_file!")
                    appendLine("═══════════════════════════════════════════════════════════")
                }
                
                // Формируем системный промпт
                val systemPrompt = buildString {
                    appendLine("Ты - AI-разработчик, который РЕАЛЬНО изменяет код в проекте.")
                    appendLine()
                    appendLine("ТВОЯ ГЛАВНАЯ ЗАДАЧА:")
                    appendLine("- НЕ описывать изменения текстом")
                    appendLine("- РЕАЛЬНО изменять файлы через инструменты")
                    appendLine("- ОБЯЗАТЕЛЬНО вызывать read_file и write_file")
                    appendLine()
                    appendLine("ПРАВИЛА РАБОТЫ:")
                    appendLine("1. ВСЕГДА используй инструменты для изменения кода")
                    appendLine("2. НЕ отвечай текстом без вызова инструментов")
                    appendLine("3. Контекст проекта из RAG УЖЕ содержит пути к файлам - используй их!")
                    appendLine("4. НЕ используй list_files без крайней необходимости")
                    appendLine("5. При write_file записывай ВЕСЬ файл целиком, а не только изменения")
                }
                
                // Выполняем задачу через LLM с инструментами и обработкой tool calls
                var currentMessages = mutableListOf(
                    DeepSeekMessage(role = "system", content = systemPrompt),
                    DeepSeekMessage(role = "user", content = taskPrompt)
                )
                
                var iterationCount = 0
                val maxToolCallIterations = 10
                var finalResponse = ""
                
                while (iterationCount < maxToolCallIterations) {
                    val response = deepSeek.sendMessage(currentMessages, availableTools.ifEmpty { null }, temperature = 0.7, maxTokens = 8000)
                    val choice = response.choices.firstOrNull()
                    
                    if (choice == null) {
                        break
                    }
                    
                    val assistantMessage = choice.message
                    currentMessages.add(assistantMessage)
                    
                    // Если есть tool calls, обрабатываем их
                    val toolCalls = assistantMessage.toolCalls
                    if (toolCalls != null && toolCalls.isNotEmpty() && projectTools != null) {
                        println("Обработка ${toolCalls.size} вызовов инструментов для задачи")
                        for (toolCall in toolCalls) {
                            val functionName = toolCall.function.name
                            val argumentsStr = toolCall.function.arguments
                            
                            println("  Вызов инструмента: $functionName")
                            
                            try {
                                val argumentsJson = json.parseToJsonElement(argumentsStr).jsonObject
                                val toolResult = projectTools.executeTool(functionName, argumentsJson)
                                
                                val resultContent = when {
                                    toolResult is JsonObject && toolResult.containsKey("text") -> 
                                        toolResult["text"]?.jsonPrimitive?.content ?: toolResult.toString()
                                    toolResult is JsonObject && toolResult.containsKey("content") -> 
                                        toolResult["content"]?.jsonPrimitive?.content ?: toolResult.toString()
                                    else -> toolResult.toString()
                                }
                                
                                println("  Результат инструмента: ${resultContent.take(100)}...")
                                
                                currentMessages.add(
                                    DeepSeekMessage(
                                        role = "tool",
                                        content = resultContent,
                                        toolCallId = toolCall.id,
                                        type = "tool"
                                    )
                                )
                                
                                // Показываем прогресс в чате
                                when (functionName) {
                                    "read_file" -> {
                                        val path = argumentsJson["path"]?.jsonPrimitive?.content ?: ""
                                        onProgressMessage?.invoke("Читаю файл: $path")
                                    }
                                    "write_file" -> {
                                        val path = argumentsJson["path"]?.jsonPrimitive?.content ?: ""
                                        onProgressMessage?.invoke("Записываю изменения в файл: $path")
                                    }
                                    "list_files" -> {
                                        val path = argumentsJson["path"]?.jsonPrimitive?.content ?: ""
                                        onProgressMessage?.invoke("Просматриваю структуру: $path")
                                    }
                                }
                            } catch (e: Exception) {
                                println("  Ошибка выполнения инструмента: ${e.message}")
                                e.printStackTrace()
                                currentMessages.add(
                                    DeepSeekMessage(
                                        role = "tool",
                                        content = "Ошибка: ${e.message}",
                                        toolCallId = toolCall.id,
                                        type = "tool"
                                    )
                                )
                            }
                        }
                        iterationCount++
                        continue
                    }
                    
                    // Если нет tool calls, получаем финальный ответ
                    finalResponse = assistantMessage.content ?: "Задача выполнена"
                    break
                }
                
                println("Результат выполнения задачи: ${finalResponse.take(200)}...")
                
                // Добавляем результат выполнения в чат через промежуточное сообщение
                val resultPreview = if (finalResponse.length > 500) {
                    finalResponse.take(500) + "..."
                } else {
                    finalResponse
                }
                if (resultPreview.isNotBlank()) {
                    onProgressMessage?.invoke("Результат выполнения:\n$resultPreview")
                }
                
                // Отмечаем задачу как выполненную
                try {
                    mcpManager.callTool("close_task", buildJsonObject {
                        put("taskId", currentTask.id)
                    })
                    completedTasksCount++
                    onProgressMessage?.invoke("✓ Задача \"${currentTask.content}\" выполнена и отмечена как завершенная")
                } catch (e: Exception) {
                    println("Ошибка при закрытии задачи: ${e.message}")
                    onProgressMessage?.invoke("⚠️ Задача выполнена, но не удалось отметить как завершенную: ${e.message}")
                }
                
                // Небольшая задержка между задачами
                kotlinx.coroutines.delay(500)
            }
            
            buildJsonObject {
                put("success", true)
                put("completed_tasks", completedTasksCount)
                put("message", "Выполнено задач: $completedTasksCount")
            }
        } catch (e: Exception) {
            println("Ошибка при выполнении задач: ${e.message}")
            e.printStackTrace()
            buildJsonObject {
                put("error", "Ошибка при выполнении задач: ${e.message}")
            }
        }
    }
    
    private fun parseTasksFromResponse(getTasksResult: JsonElement): List<TodoistTask> {
        return when {
            getTasksResult is JsonArray -> {
                val firstElement = getTasksResult.jsonArray.firstOrNull()
                if (firstElement is JsonObject && firstElement.containsKey("text")) {
                    val textContent = firstElement["text"]?.jsonPrimitive?.content
                    if (textContent != null) {
                        try {
                            val parsedJson = json.parseToJsonElement(textContent)
                            if (parsedJson is JsonArray) {
                                parsedJson.jsonArray.mapNotNull { parseTask(it) }
                            } else {
                                emptyList()
                            }
                        } catch (e: Exception) {
                            println("Ошибка парсинга JSON-строки задач: ${e.message}")
                            emptyList()
                        }
                    } else {
                        emptyList()
                    }
                } else {
                    getTasksResult.jsonArray.mapNotNull { parseTask(it) }
                }
            }
            getTasksResult is JsonObject -> {
                val tasksArray = when {
                    getTasksResult.containsKey("tasks") -> {
                        val tasksValue = getTasksResult["tasks"]
                        if (tasksValue is JsonArray) tasksValue else JsonArray(emptyList())
                    }
                    getTasksResult.containsKey("content") -> {
                        val content = getTasksResult["content"]
                        if (content is JsonArray) content else JsonArray(emptyList())
                    }
                    else -> JsonArray(emptyList())
                }
                tasksArray.jsonArray.mapNotNull { parseTask(it) }
            }
            else -> emptyList()
        }
    }
    
    private fun parseTask(taskJson: JsonElement): TodoistTask? {
        if (taskJson !is JsonObject) return null
        return try {
            TodoistTask(
                id = taskJson["id"]?.jsonPrimitive?.content ?: return null,
                content = taskJson["content"]?.jsonPrimitive?.content ?: "",
                description = taskJson["description"]?.jsonPrimitive?.content,
                priority = taskJson["priority"]?.jsonPrimitive?.intOrNull,
                due = taskJson["due"]?.jsonPrimitive?.content,
                projectId = taskJson["projectId"]?.jsonPrimitive?.content,
                isCompleted = taskJson["isCompleted"]?.jsonPrimitive?.booleanOrNull ?: false
            )
        } catch (e: Exception) {
            println("Ошибка парсинга задачи: ${e.message}")
            null
        }
    }
    
    @Serializable
    data class TodoistTask(
        val id: String,
        val content: String,
        val description: String? = null,
        val priority: Int? = null,
        val due: String? = null,
        val projectId: String? = null,
        val isCompleted: Boolean = false
    )
}

