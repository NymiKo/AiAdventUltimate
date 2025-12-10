package com.qualiorstudio.aiadventultimate.service

import com.qualiorstudio.aiadventultimate.api.DeepSeekMessage
import com.qualiorstudio.aiadventultimate.api.LLMProvider
import com.qualiorstudio.aiadventultimate.mcp.MCPServerManager
import com.qualiorstudio.aiadventultimate.storage.TodoistProjectStorage
import com.qualiorstudio.aiadventultimate.storage.getTodoistProjectsFilePath
import kotlinx.serialization.json.*
import kotlinx.serialization.Serializable

@Serializable
data class Subtask(
    val title: String,
    val description: String? = null,
    val priority: Int? = null,
    val order: Int
)

@Serializable
data class TaskBreakdown(
    val mainTask: String,
    val subtasks: List<Subtask>,
    val projectId: String? = null
)

class TaskBreakdownService(
    private val llmProvider: LLMProvider?,
    private val mcpManager: MCPServerManager,
    val projectName: String? = null
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val projectStorage = TodoistProjectStorage(getTodoistProjectsFilePath())
    
    suspend fun breakdownTask(userMessage: String, projectContext: String? = null): TaskBreakdown {
        val systemPrompt = """
Ты эксперт по планированию и разбиению задач на подзадачи. Твоя задача - анализировать запросы пользователя и разбивать их на конкретные, выполнимые подзадачи.

ПРИНЦИПЫ РАЗБИЕНИЯ:
1. Каждая подзадача должна быть конкретной и выполнимой за 1-4 часа
2. Подзадачи должны покрывать все аспекты: UI, логика, данные, тестирование
3. Подзадачи должны быть упорядочены логически (от базовых к более сложным)
4. Учитывай зависимости между подзадачами
5. Для разработки фич продумывай: дизайн UI, модели данных, API, бизнес-логику, тесты

СТРУКТУРА ПОДЗАДАЧ:
- UI компоненты: создание экранов, компонентов, стилизация
- Модели данных: структуры данных, схемы БД
- Бизнес-логика: обработка данных, валидация, алгоритмы
- API интеграция: запросы, обработка ответов
- Тестирование: unit тесты, интеграционные тесты
- Документация: описание, примеры использования

КРИТИЧЕСКИ ВАЖНО - ФОРМАТ ОТВЕТА:
Ты ДОЛЖЕН вернуть ТОЛЬКО валидный JSON без каких-либо дополнительных пояснений, комментариев или текста до/после JSON.

СТРОГИЙ ФОРМАТ JSON (обязательно соблюдай):
{
  "mainTask": "Краткое название основной задачи",
  "subtasks": [
    {
      "title": "Название подзадачи",
      "description": "Подробное описание того, что нужно сделать",
      "priority": 1,
      "order": 1
    },
    {
      "title": "Название второй подзадачи",
      "description": "Описание второй подзадачи",
      "priority": 2,
      "order": 2
    }
  ],
  "projectId": null
}

ОБЯЗАТЕЛЬНЫЕ ТРЕБОВАНИЯ:
1. Поле "subtasks" ДОЛЖНО быть массивом с минимум 2 элементами
2. Каждая подзадача ДОЛЖНА иметь поля: "title" (string), "description" (string или null), "priority" (число 1-4), "order" (число, начиная с 1)
3. Поле "order" должно быть уникальным для каждой подзадачи и идти последовательно (1, 2, 3, ...)
4. НЕ добавляй никакого текста до или после JSON
5. НЕ используй markdown код блоки (```json)
6. Верни ТОЛЬКО чистый JSON объект

ПРИМЕРЫ:

Запрос: "Добавить фичу с отображением деталей объявления"
Ответ:
{
  "mainTask": "Фича отображения деталей объявления",
  "subtasks": [
    {
      "title": "Создать UI компонент экрана деталей",
      "description": "Разработать Compose экран с отображением всех полей объявления: заголовок, описание, цена, фото, контакты. Добавить стилизацию и анимации.",
      "priority": 1,
      "order": 1
    },
    {
      "title": "Создать модель данных для деталей объявления",
      "description": "Определить data class для детальной информации об объявлении со всеми необходимыми полями.",
      "priority": 1,
      "order": 2
    },
    {
      "title": "Реализовать ViewModel для экрана деталей",
      "description": "Создать ViewModel с логикой загрузки данных объявления по ID, обработки состояний загрузки и ошибок.",
      "priority": 1,
      "order": 3
    },
    {
      "title": "Интегрировать API для получения деталей",
      "description": "Добавить метод в API сервис для запроса детальной информации об объявлении по ID.",
      "priority": 2,
      "order": 4
    },
    {
      "title": "Добавить навигацию к экрану деталей",
      "description": "Реализовать переход с экрана списка объявлений к экрану деталей при клике на элемент.",
      "priority": 2,
      "order": 5
    },
    {
      "title": "Добавить обработку ошибок и состояний загрузки",
      "description": "Реализовать отображение индикатора загрузки, обработку ошибок сети и пустых состояний.",
      "priority": 2,
      "order": 6
    },
    {
      "title": "Написать unit тесты для ViewModel",
      "description": "Создать тесты для проверки логики загрузки данных, обработки ошибок и состояний.",
      "priority": 3,
      "order": 7
    }
  ]
}

${if (projectContext != null && projectContext.isNotBlank()) {
    """
    
КОНТЕКСТ ПРОЕКТА ИЗ БАЗЫ ЗНАНИЙ (RAG):
$projectContext

ВАЖНО: Используй информацию из контекста проекта для более точного разбиения задач.
Учитывай существующую архитектуру, используемые технологии, структуру кода и паттерны проекта.
    """.trimIndent()
} else ""}
        """.trimIndent()
        
        val messages = listOf(
            DeepSeekMessage(role = "system", content = systemPrompt),
            DeepSeekMessage(role = "user", content = """
Проанализируй следующий запрос и разбей его на подзадачи:

$userMessage

Верни только валидный JSON без дополнительных пояснений.
            """.trimIndent())
        )
        
        val provider = llmProvider ?: throw Exception("LLMProvider не доступен")
        val response = provider.sendMessage(messages, null, temperature = 0.3, maxTokens = 4000)
        val responseText = response.choices.firstOrNull()?.message?.content?.trim() ?: 
            throw Exception("Не удалось получить ответ от LLM")
        
        println("=== Ответ от LLM для разбиения задачи ===")
        println("Полный ответ: $responseText")
        
        val jsonText = extractJsonFromResponse(responseText)
        println("Извлеченный JSON: $jsonText")
        
        try {
            println("=== Попытка парсинга JSON ===")
            println("JSON длина: ${jsonText.length}")
            println("JSON начинается с: ${jsonText.take(100)}")
            println("JSON заканчивается на: ${jsonText.takeLast(100)}")
            
            val breakdown = json.decodeFromString<TaskBreakdown>(jsonText)
            println("=== Успешно распарсен TaskBreakdown ===")
            println("Основная задача: ${breakdown.mainTask}")
            println("Количество подзадач: ${breakdown.subtasks.size}")
            
            if (breakdown.subtasks.isEmpty()) {
                println("⚠️ ВНИМАНИЕ: Массив подзадач пуст!")
            } else {
                breakdown.subtasks.forEachIndexed { index, subtask ->
                    println("  Подзадача ${index + 1}:")
                    println("    - title: ${subtask.title}")
                    println("    - description: ${subtask.description?.take(50) ?: "null"}...")
                    println("    - order: ${subtask.order}")
                    println("    - priority: ${subtask.priority}")
                }
            }
            
            return breakdown
        } catch (e: Exception) {
            println("=== ОШИБКА ПАРСИНГА JSON ===")
            println("Ошибка: ${e.message}")
            println("JSON текст: $jsonText")
            e.printStackTrace()
            throw Exception("Ошибка парсинга JSON ответа от LLM: ${e.message}. JSON: $jsonText")
        }
    }
    
    suspend fun getOrCreateProjectId(): String? {
        println("=== getOrCreateProjectId вызван ===")
        println("projectName: $projectName")
        if (projectName == null) {
            println("⚠️ projectName = null, возвращаем null")
            return null
        }
        
        val savedProjectId = projectStorage.getProjectId(projectName)
        
        return try {
            println("=== Поиск проекта '$projectName' в Todoist ===")
            
            val getProjectsResult = mcpManager.callTool("get_projects", buildJsonObject {})
            println("Результат get_projects: $getProjectsResult")
            println("Тип результата: ${getProjectsResult::class.simpleName}")
            
            val projects = when {
                getProjectsResult is JsonArray -> {
                    // Проверяем, является ли первый элемент объектом с полем "text" (JSON-строка)
                    val firstElement = getProjectsResult.jsonArray.firstOrNull()
                    if (firstElement is JsonObject && firstElement.containsKey("text")) {
                        val textContent = firstElement["text"]?.jsonPrimitive?.content
                        if (textContent != null) {
                            println("Найдена JSON-строка в ответе: $textContent")
                            try {
                                // Парсим JSON-строку
                                val parsedJson = json.parseToJsonElement(textContent)
                                if (parsedJson is JsonArray) {
                                    // Это массив проектов
                                    println("Распарсено проектов: ${parsedJson.jsonArray.size}")
                                    parsedJson
                                } else {
                                    println("Распарсенный JSON не является массивом")
                                    JsonArray(emptyList())
                                }
                            } catch (e: Exception) {
                                println("Ошибка парсинга JSON-строки: ${e.message}")
                                e.printStackTrace()
                                JsonArray(emptyList())
                            }
                        } else {
                            JsonArray(emptyList())
                        }
                    } else {
                        // Обычный массив проектов
                        getProjectsResult
                    }
                }
                getProjectsResult is JsonObject -> {
                    when {
                        getProjectsResult.containsKey("projects") -> {
                            val projectsValue = getProjectsResult["projects"]
                            if (projectsValue is JsonArray) projectsValue else JsonArray(emptyList())
                        }
                        getProjectsResult.containsKey("content") -> {
                            val content = getProjectsResult["content"]
                            if (content is JsonArray) content else JsonArray(emptyList())
                        }
                        else -> {
                            println("Не найден массив проектов в ответе")
                            JsonArray(emptyList())
                        }
                    }
                }
                else -> {
                    println("Неожиданный формат ответа от get_projects: ${getProjectsResult::class.simpleName}")
                    JsonArray(emptyList())
                }
            }
            
            println("Найдено проектов: ${projects.jsonArray.size}")
            projects.jsonArray.forEachIndexed { index, project ->
                if (project is JsonObject) {
                    val name = project["name"]?.jsonPrimitive?.content
                    val id = project["id"]?.jsonPrimitive?.content
                    println("  Проект $index: name='$name', id='$id'")
                }
            }
            
            val existingProject = projects.jsonArray.firstOrNull { project ->
                if (project is JsonObject) {
                    val name = project["name"]?.jsonPrimitive?.content
                    val matches = name?.equals(projectName, ignoreCase = true) == true
                    if (matches) {
                        println("Найден совпадающий проект: $name")
                    }
                    matches
                } else {
                    false
                }
            }
            
            if (existingProject != null) {
                val projectId = (existingProject as JsonObject)["id"]?.jsonPrimitive?.content
                if (projectId != null) {
                    println("✓ Найден существующий проект '$projectName' с ID: $projectId")
                    if (savedProjectId != projectId) {
                        println("Обновляем сохраненный projectId: $savedProjectId -> $projectId")
                        projectStorage.saveProjectMapping(projectName, projectId)
                    } else {
                        println("Сохраненный projectId совпадает с найденным")
                    }
                    return projectId
                }
            }
            
            if (savedProjectId != null) {
                val savedProjectExists = projects.jsonArray.any { project ->
                    if (project is JsonObject) {
                        project["id"]?.jsonPrimitive?.content == savedProjectId
                    } else {
                        false
                    }
                }
                if (savedProjectExists) {
                    println("✓ Сохраненный projectId '$savedProjectId' все еще валиден, используем его")
                    return savedProjectId
                } else {
                    println("⚠️ Сохраненный projectId '$savedProjectId' не найден в Todoist, удаляем из хранилища")
                    projectStorage.removeProjectMapping(projectName)
                }
            }
            
            println("Проект '$projectName' не найден, создаем новый...")
            val createProjectResult = mcpManager.callTool("create_project", buildJsonObject {
                put("name", projectName)
            })
            println("Результат create_project: $createProjectResult")
            println("Тип результата create_project: ${createProjectResult::class.simpleName}")
            
            val newProjectId = when {
                createProjectResult is JsonObject -> {
                    val id = createProjectResult["id"]?.jsonPrimitive?.content
                    if (id == null) {
                        println("⚠️ Поле 'id' не найдено в ответе create_project")
                        println("Доступные поля: ${createProjectResult.keys.joinToString()}")
                        createProjectResult["text"]?.jsonPrimitive?.content?.let { text ->
                            println("Найден текст в ответе: $text")
                            extractProjectIdFromText(text)
                        }
                    } else {
                        id
                    }
                }
                createProjectResult is JsonArray -> {
                    println("Ответ create_project - JsonArray, обрабатываем...")
                    val firstElement = createProjectResult.jsonArray.firstOrNull()
                    when {
                        firstElement is JsonObject -> {
                            // Проверяем, есть ли поле "text" с JSON-строкой
                            val textContent = firstElement["text"]?.jsonPrimitive?.content
                            if (textContent != null) {
                                println("Найден текст в первом элементе массива: $textContent")
                                try {
                                    // Парсим JSON-строку
                                    val parsedJson = json.parseToJsonElement(textContent)
                                    if (parsedJson is JsonObject) {
                                        val id = parsedJson["id"]?.jsonPrimitive?.content
                                        if (id != null) {
                                            println("✓ Извлечен ID из JSON-строки: $id")
                                            id
                                        } else {
                                            println("⚠️ Поле 'id' не найдено в распарсенном JSON")
                                            extractProjectIdFromText(textContent)
                                        }
                                    } else {
                                        println("⚠️ Распарсенный JSON не является объектом")
                                        extractProjectIdFromText(textContent)
                                    }
                                } catch (e: Exception) {
                                    println("Ошибка парсинга JSON-строки: ${e.message}")
                                    extractProjectIdFromText(textContent)
                                }
                            } else {
                                // Прямо в объекте может быть поле "id"
                                val id = firstElement["id"]?.jsonPrimitive?.content
                                if (id != null) {
                                    println("✓ Найден ID в первом элементе массива: $id")
                                    id
                                } else {
                                    println("⚠️ Поле 'id' не найдено в первом элементе массива")
                                    null
                                }
                            }
                        }
                        firstElement is JsonPrimitive && firstElement.isString -> {
                            extractProjectIdFromText(firstElement.content)
                        }
                        else -> {
                            println("⚠️ Первый элемент массива имеет неожиданный тип: ${firstElement?.let { it::class.simpleName }}")
                            null
                        }
                    }
                }
                createProjectResult is JsonPrimitive && createProjectResult.isString -> {
                    extractProjectIdFromText(createProjectResult.content)
                }
                else -> {
                    println("Неожиданный формат ответа от create_project: ${createProjectResult::class.simpleName}")
                    null
                }
            }
            
            if (newProjectId != null) {
                println("✓ Создан новый проект '$projectName' с ID: $newProjectId")
                projectStorage.saveProjectMapping(projectName, newProjectId)
            } else {
                println("✗ Ошибка создания проекта '$projectName' - не удалось извлечь ID")
                println("Полный ответ: $createProjectResult")
            }
            
            newProjectId
        } catch (e: Exception) {
            println("Ошибка при получении/создании проекта: ${e.message}")
            e.printStackTrace()
            null
        }
    }
    
    suspend fun createTasksInTodoist(breakdown: TaskBreakdown): List<String> {
        val createdTaskIds = mutableListOf<String>()
        
        val projectId = breakdown.projectId ?: getOrCreateProjectId()
        println("=== Создание задач в Todoist ===")
        println("Используется projectId: $projectId")
        println("Основная задача: ${breakdown.mainTask}")
        println("Количество подзадач для создания: ${breakdown.subtasks.size}")
        
        val mainTaskId = createMainTask(breakdown.mainTask, projectId)
        if (mainTaskId != null) {
            createdTaskIds.add(mainTaskId)
            println("✓ Основная задача создана с ID: $mainTaskId")
            
            for (subtask in breakdown.subtasks.sortedBy { it.order }) {
                println("Создание подзадачи: ${subtask.title} (order=${subtask.order})")
                val subtaskId = createSubtask(
                    mainTaskId = mainTaskId,
                    subtask = subtask,
                    projectId = projectId
                )
                if (subtaskId != null) {
                    createdTaskIds.add(subtaskId)
                    println("✓ Подзадача создана с ID: $subtaskId")
                } else {
                    println("✗ Ошибка создания подзадачи: ${subtask.title}")
                }
            }
        } else {
            println("✗ Ошибка создания основной задачи")
        }
        
        println("=== Итого создано задач: ${createdTaskIds.size} ===")
        return createdTaskIds
    }
    
    private suspend fun createMainTask(title: String, projectId: String?): String? {
        return try {
            val arguments = buildJsonObject {
                put("content", title)
                projectId?.let { put("projectId", it) }
            }
            
            val result = mcpManager.callTool("create_task", arguments)
            println("=== Результат создания основной задачи ===")
            println("Тип результата: ${result::class.simpleName}")
            println("Результат: $result")
            
            val taskId = when {
                result is JsonObject -> {
                    result["id"]?.jsonPrimitive?.content ?: 
                    result["text"]?.jsonPrimitive?.content?.let { extractIdFromText(it) }
                }
                result is JsonPrimitive && result.isString -> {
                    extractIdFromText(result.content)
                }
                else -> {
                    println("Неожиданный формат ответа от create_task: ${result::class.simpleName}")
                    null
                }
            }
            taskId
        } catch (e: Exception) {
            println("Ошибка создания основной задачи: ${e.message}")
            e.printStackTrace()
            null
        }
    }
    
    private suspend fun createSubtask(
        mainTaskId: String,
        subtask: Subtask,
        projectId: String?
    ): String? {
        return try {
            val description = subtask.description?.let { 
                "Подзадача для задачи #$mainTaskId\n\n$it"
            } ?: "Подзадача для задачи #$mainTaskId"
            
            val arguments = buildJsonObject {
                put("content", subtask.title)
                put("description", description)
                subtask.priority?.let { put("priority", it) }
                projectId?.let { put("projectId", it) }
            }
            
            val result = mcpManager.callTool("create_task", arguments)
            println("=== Результат создания подзадачи '${subtask.title}' ===")
            println("Тип результата: ${result::class.simpleName}")
            println("Результат: $result")
            
            val taskId = when {
                result is JsonObject -> {
                    result["id"]?.jsonPrimitive?.content ?: 
                    result["text"]?.jsonPrimitive?.content?.let { extractIdFromText(it) }
                }
                result is JsonPrimitive && result.isString -> {
                    extractIdFromText(result.content)
                }
                else -> {
                    println("Неожиданный формат ответа от create_task: ${result::class.simpleName}")
                    null
                }
            }
            taskId
        } catch (e: Exception) {
            println("Ошибка создания подзадачи '${subtask.title}': ${e.message}")
            e.printStackTrace()
            null
        }
    }
    
    private fun extractJsonFromResponse(response: String): String {
        val trimmed = response.trim()
        
        if (trimmed.startsWith("```json")) {
            val jsonStart = trimmed.indexOf('{')
            val jsonEnd = trimmed.lastIndexOf('}')
            if (jsonStart != -1 && jsonEnd != -1 && jsonEnd > jsonStart) {
                return trimmed.substring(jsonStart, jsonEnd + 1)
            }
        }
        
        if (trimmed.startsWith("```")) {
            val codeBlockStart = trimmed.indexOf('\n')
            val codeBlockEnd = trimmed.lastIndexOf("```")
            if (codeBlockStart != -1 && codeBlockEnd != -1 && codeBlockEnd > codeBlockStart) {
                val jsonContent = trimmed.substring(codeBlockStart + 1, codeBlockEnd).trim()
                val jsonStart = jsonContent.indexOf('{')
                val jsonEnd = jsonContent.lastIndexOf('}')
                if (jsonStart != -1 && jsonEnd != -1 && jsonEnd > jsonStart) {
                    return jsonContent.substring(jsonStart, jsonEnd + 1)
                }
            }
        }
        
        val jsonStart = trimmed.indexOfFirst { it == '{' }
        val jsonEnd = trimmed.indexOfLast { it == '}' }
        
        if (jsonStart != -1 && jsonEnd != -1 && jsonEnd > jsonStart) {
            val jsonCandidate = trimmed.substring(jsonStart, jsonEnd + 1)
            if (isValidJson(jsonCandidate)) {
                return jsonCandidate
            }
        }
        
        println("⚠️ Не удалось извлечь валидный JSON, возвращаем весь ответ")
        return trimmed
    }
    
    private fun isValidJson(text: String): Boolean {
        return try {
            json.parseToJsonElement(text)
            true
        } catch (e: Exception) {
            false
        }
    }
    
    private fun extractIdFromText(text: String): String? {
        val jsonStart = text.indexOfFirst { it == '{' }
        if (jsonStart != -1) {
            val jsonEnd = text.indexOfLast { it == '}' }
            if (jsonEnd != -1 && jsonEnd > jsonStart) {
                try {
                    val jsonText = text.substring(jsonStart, jsonEnd + 1)
                    val jsonObject = json.parseToJsonElement(jsonText).jsonObject
                    return jsonObject["id"]?.jsonPrimitive?.content
                } catch (e: Exception) {
                    println("Ошибка парсинга JSON из текста: ${e.message}")
                }
            }
        }
        
        val idMatch = Regex("\"id\"\\s*:\\s*\"([^\"]+)\"").find(text)
        return idMatch?.groupValues?.get(1)
    }
    
    private fun extractProjectIdFromText(text: String): String? {
        val jsonStart = text.indexOfFirst { it == '{' }
        if (jsonStart != -1) {
            val jsonEnd = text.indexOfLast { it == '}' }
            if (jsonEnd != -1 && jsonEnd > jsonStart) {
                try {
                    val jsonText = text.substring(jsonStart, jsonEnd + 1)
                    val jsonObject = json.parseToJsonElement(jsonText).jsonObject
                    return jsonObject["id"]?.jsonPrimitive?.content
                } catch (e: Exception) {
                    println("Ошибка парсинга JSON из текста create_project: ${e.message}")
                }
            }
        }
        
        val idMatch = Regex("\"id\"\\s*:\\s*\"([^\"]+)\"").find(text)
        return idMatch?.groupValues?.get(1)
    }
}

