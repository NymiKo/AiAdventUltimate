package com.qualiorstudio.aiadventultimate.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.qualiorstudio.aiadventultimate.api.OllamaChat
import com.qualiorstudio.aiadventultimate.api.OllamaLLMProvider
import com.qualiorstudio.aiadventultimate.mcp.MCPServerManager
import com.qualiorstudio.aiadventultimate.model.Project
import com.qualiorstudio.aiadventultimate.service.TaskBreakdownService
import com.qualiorstudio.aiadventultimate.storage.TodoistProjectStorage
import com.qualiorstudio.aiadventultimate.storage.getTodoistProjectsFilePath
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import kotlinx.serialization.json.Json

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

@Composable
fun TodoistTabContent(
    currentProject: Project?,
    mcpManager: MCPServerManager?,
    chatViewModel: com.qualiorstudio.aiadventultimate.viewmodel.ChatViewModel? = null
) {
    var tasks by remember { mutableStateOf<List<TodoistTask>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var projectId by remember { mutableStateOf<String?>(null) }
    var isCreatingProject by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val projectStorage = remember { TodoistProjectStorage(getTodoistProjectsFilePath()) }
    val json = Json { ignoreUnknownKeys = true }
    
    fun loadProjectId() {
        if (currentProject == null) {
            projectId = null
            return
        }
        
        val savedId = projectStorage.getProjectId(currentProject.name)
        projectId = savedId
    }
    
    suspend fun loadTasks() {
        if (projectId == null || mcpManager == null) {
            tasks = emptyList()
            return
        }
        
        isLoading = true
        errorMessage = null
        
        try {
            val getTasksResult = mcpManager.callTool("get_tasks", buildJsonObject {
                put("projectId", projectId)
            })
            
            println("Результат get_tasks: $getTasksResult")
            
            val tasksList = when {
                getTasksResult is JsonArray -> {
                    // Проверяем, является ли первый элемент объектом с полем "text" (JSON-строка)
                    val firstElement = getTasksResult.jsonArray.firstOrNull()
                    if (firstElement is JsonObject && firstElement.containsKey("text")) {
                        val textContent = firstElement["text"]?.jsonPrimitive?.content
                        if (textContent != null) {
                            println("Найдена JSON-строка в ответе: $textContent")
                            try {
                                // Парсим JSON-строку
                                val parsedJson = json.parseToJsonElement(textContent)
                                if (parsedJson is JsonArray) {
                                    // Это массив задач
                                    parsedJson.jsonArray.mapNotNull { taskJson ->
                                        if (taskJson is JsonObject) {
                                            try {
                                                TodoistTask(
                                                    id = taskJson["id"]?.jsonPrimitive?.content ?: "",
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
                                        } else {
                                            null
                                        }
                                    }
                                } else {
                                    println("Распарсенный JSON не является массивом")
                                    emptyList()
                                }
                            } catch (e: Exception) {
                                println("Ошибка парсинга JSON-строки: ${e.message}")
                                e.printStackTrace()
                                emptyList()
                            }
                        } else {
                            emptyList()
                        }
                    } else {
                        // Обычный массив задач
                        getTasksResult.jsonArray.mapNotNull { taskJson ->
                            if (taskJson is JsonObject) {
                                try {
                                    TodoistTask(
                                        id = taskJson["id"]?.jsonPrimitive?.content ?: "",
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
                            } else {
                                null
                            }
                        }
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
                    tasksArray.jsonArray.mapNotNull { taskJson ->
                        if (taskJson is JsonObject) {
                            try {
                                TodoistTask(
                                    id = taskJson["id"]?.jsonPrimitive?.content ?: "",
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
                        } else {
                            null
                        }
                    }
                }
                else -> {
                    println("Неожиданный формат ответа от get_tasks: ${getTasksResult::class.simpleName}")
                    emptyList()
                }
            }
            
            tasks = tasksList.filter { !it.isCompleted }
        } catch (e: Exception) {
            errorMessage = "Ошибка при загрузке задач: ${e.message}"
            println("Ошибка при загрузке задач: ${e.message}")
            e.printStackTrace()
        } finally {
            isLoading = false
        }
    }
    
    suspend fun createProjectInTodoist(): Boolean {
        if (currentProject == null || mcpManager == null) {
            return false
        }
        
        isCreatingProject = true
        errorMessage = null
        
        return try {
            val ollamaChat = OllamaChat()
            val llmProvider = OllamaLLMProvider(ollamaChat)
            val taskBreakdownService = TaskBreakdownService(
                llmProvider = llmProvider,
                mcpManager = mcpManager,
                projectName = currentProject.name
            )
            
            val newProjectId = taskBreakdownService.getOrCreateProjectId()
            if (newProjectId != null) {
                projectId = newProjectId
                loadTasks()
                true
            } else {
                errorMessage = "Не удалось создать проект в Todoist"
                false
            }
        } catch (e: Exception) {
            errorMessage = "Ошибка при создании проекта: ${e.message}"
            println("Ошибка при создании проекта: ${e.message}")
            e.printStackTrace()
            false
        } finally {
            isCreatingProject = false
        }
    }
    
    LaunchedEffect(currentProject) {
        loadProjectId()
    }
    
    LaunchedEffect(projectId) {
        if (projectId != null) {
            loadTasks()
        }
    }
    
    // Подписываемся на обновления задач из ChatViewModel
    val todoistUpdateTrigger by chatViewModel?.todoistTasksUpdateTrigger?.collectAsState() ?: remember { mutableStateOf(0L) }
    LaunchedEffect(todoistUpdateTrigger) {
        if (todoistUpdateTrigger > 0 && projectId != null && !isLoading) {
            println("Обновление списка задач Todoist после создания новой задачи")
            loadTasks()
        }
    }
    
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Todoist",
                style = MaterialTheme.typography.titleLarge
            )
            if (projectId != null) {
                IconButton(
                    onClick = { coroutineScope.launch { loadTasks() } },
                    enabled = !isLoading && mcpManager != null
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Обновить задачи",
                        tint = if (isLoading) 
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        else
                            MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
        
        if (currentProject == null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Проект не открыт",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Откройте проект для просмотра задач в Todoist",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else if (projectId == null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Проект \"${currentProject.name}\" не найден в Todoist",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Создайте проект в Todoist для синхронизации задач",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = { coroutineScope.launch { createProjectInTodoist() } },
                        enabled = !isCreatingProject && mcpManager != null
                    ) {
                        if (isCreatingProject) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Создание...")
                        } else {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Создать проект в Todoist")
                        }
                    }
                    if (mcpManager == null) {
                        Text(
                            text = "MCP сервер не подключен",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        } else if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (errorMessage != null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = errorMessage ?: "Ошибка",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                    Button(onClick = { coroutineScope.launch { loadTasks() } }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Повторить")
                    }
                }
            }
        } else if (tasks.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Список задач пуст",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "В проекте \"${currentProject?.name ?: ""}\" пока нет задач",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(tasks) { task ->
                    TodoistTaskListItem(
                        task = task
                    )
                }
            }
        }
    }
}

@Composable
fun TodoistTaskListItem(
    task: TodoistTask
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = task.content,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                if (task.priority != null && task.priority <= 2) {
                    val priorityColor = when (task.priority) {
                        1 -> MaterialTheme.colorScheme.error
                        2 -> MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Высокий приоритет",
                        tint = priorityColor,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            
            if (task.description != null && task.description.isNotBlank()) {
                Text(
                    text = task.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
            
            if (task.due != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Срок: ${task.due}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

