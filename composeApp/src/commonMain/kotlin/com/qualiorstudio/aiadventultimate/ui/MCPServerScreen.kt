package com.qualiorstudio.aiadventultimate.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.qualiorstudio.aiadventultimate.model.MCPServer
import com.qualiorstudio.aiadventultimate.model.MCPServerConnectionStatus
import com.qualiorstudio.aiadventultimate.repository.MCPServerRepository
import com.qualiorstudio.aiadventultimate.repository.DEFAULT_GITHUB_MCP_SERVER_ID
import com.qualiorstudio.aiadventultimate.service.MCPServerService
import com.qualiorstudio.aiadventultimate.service.MCPServerServiceImpl
import com.qualiorstudio.aiadventultimate.utils.currentTimeMillis
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import java.util.UUID

@Composable
fun MCPServerTabContent(
    repository: MCPServerRepository,
    mcpService: MCPServerService,
    onCreateServer: () -> Unit
) {
    val servers by repository.observeAllServers().collectAsState(initial = emptyList())
    val isLoading by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    var showEditServerDialog by remember { mutableStateOf(false) }
    var editingServer by remember { mutableStateOf<MCPServer?>(null) }
    
    LaunchedEffect(Unit) {
        coroutineScope.launch {
            repository.reloadServers()
        }
    }
    
    LaunchedEffect(servers) {
        servers.forEach { server ->
            val currentStatus = mcpService.getServerStatus(server.id).value
            val isRunning = currentStatus?.status == MCPServerConnectionStatus.CONNECTED || 
                           currentStatus?.status == MCPServerConnectionStatus.CONNECTING
            
            if (server.enabled && !isRunning) {
                mcpService.checkServerStatus(server)
            } else if (!server.enabled && isRunning) {
                mcpService.stopServer(server.id)
            }
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
                text = "MCP Серверы",
                style = MaterialTheme.typography.titleLarge
            )
            FloatingActionButton(
                onClick = onCreateServer,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Добавить MCP сервер",
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (servers.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Нет подключенных MCP серверов",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(onClick = onCreateServer) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Добавить сервер")
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(servers) { server ->
                    val serverStatus by mcpService.getServerStatus(server.id).collectAsState()
                    
                    MCPServerListItem(
                        server = server,
                        status = serverStatus,
                        onToggleEnabled = { enabled ->
                            coroutineScope.launch {
                                repository.updateServer(server.copy(enabled = enabled))
                                if (enabled) {
                                    mcpService.startServer(server.copy(enabled = enabled))
                                } else {
                                    mcpService.stopServer(server.id)
                                }
                            }
                        },
                        onDeleteClick = {
                            coroutineScope.launch {
                                mcpService.stopServer(server.id)
                                repository.deleteServer(server.id)
                            }
                        },
                        onRetry = {
                            coroutineScope.launch {
                                mcpService.startServer(server)
                            }
                        },
                        onEditClick = {
                            showEditServerDialog = true
                            editingServer = server
                        }
                    )
                }
            }
        }
    }
    
    if (showEditServerDialog && editingServer != null) {
        EditMCPServerDialog(
            server = editingServer!!,
            repository = repository,
            mcpService = mcpService,
            onDismiss = { 
                showEditServerDialog = false
                editingServer = null
            },
            onServerUpdated = { 
                showEditServerDialog = false
                editingServer = null
            }
        )
    }
}

@Composable
fun MCPServerListItem(
    server: MCPServer,
    status: com.qualiorstudio.aiadventultimate.model.MCPServerStatus?,
    onToggleEnabled: (Boolean) -> Unit,
    onDeleteClick: () -> Unit,
    onRetry: () -> Unit,
    onEditClick: () -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showErrorDialog by remember { mutableStateOf(false) }
    
    val connectionStatus = status?.status ?: MCPServerConnectionStatus.DISCONNECTED
    val errorMessage = status?.errorMessage
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (server.enabled)
                MaterialTheme.colorScheme.surfaceVariant
            else
                MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatusIndicator(status = connectionStatus)
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = server.name,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (!server.enabled) {
                                Text(
                                    text = "(отключен)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = server.command,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (server.args.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Args: ${server.args.joinToString(", ")}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Switch(
                        checked = server.enabled,
                        onCheckedChange = onToggleEnabled
                    )
                    IconButton(
                        onClick = onEditClick
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Редактировать",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    if (server.id != DEFAULT_GITHUB_MCP_SERVER_ID) {
                        IconButton(
                        onClick = { showDeleteDialog = true }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Удалить",
                            tint = MaterialTheme.colorScheme.error
                        )
                        }
                    }
                }
            }
            
            if (connectionStatus == MCPServerConnectionStatus.ERROR && errorMessage != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Error,
                        contentDescription = "Ошибка",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        onClick = onRetry,
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text("Повторить", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
    
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Удалить MCP сервер?") },
            text = { Text("Вы уверены, что хотите удалить сервер \"${server.name}\"?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteClick()
                        showDeleteDialog = false
                    }
                ) {
                    Text("Удалить", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Отмена")
                }
            }
        )
    }
}

@Composable
fun StatusIndicator(status: MCPServerConnectionStatus) {
    val color = when (status) {
        MCPServerConnectionStatus.CONNECTED -> Color(0xFF4CAF50)
        MCPServerConnectionStatus.CONNECTING -> Color(0xFFFF9800)
        MCPServerConnectionStatus.ERROR -> Color(0xFFF44336)
        MCPServerConnectionStatus.DISCONNECTED -> Color(0xFF9E9E9E)
    }
    
    Box(
        modifier = Modifier
            .size(12.dp)
            .clip(CircleShape)
            .background(color)
    )
}

@Composable
fun CreateMCPServerDialog(
    repository: MCPServerRepository,
    onDismiss: () -> Unit,
    onServerCreated: () -> Unit
) {
    var serverName by remember { mutableStateOf("") }
    var command by remember { mutableStateOf("") }
    var argsText by remember { mutableStateOf("") }
    var envText by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Добавить MCP сервер") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (errorMessage != null) {
                    Text(
                        text = errorMessage!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                
                OutlinedTextField(
                    value = serverName,
                    onValueChange = { serverName = it },
                    label = { Text("Название сервера") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("Например: GitHub MCP") }
                )
                
                OutlinedTextField(
                    value = command,
                    onValueChange = { command = it },
                    label = { Text("Команда") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("Например: docker или npx") },
                    supportingText = { 
                        Text("Основная команда для запуска (docker, npx, node и т.д.). Убедитесь, что команда установлена и доступна в PATH.")
                    }
                )
                
                OutlinedTextField(
                    value = argsText,
                    onValueChange = { argsText = it },
                    label = { Text("Аргументы (через запятую)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = false,
                    minLines = 2,
                    maxLines = 4,
                    placeholder = { 
                        Text(
                            if (command.lowercase() == "docker") {
                                "run, -i, --rm, -e, GITHUB_PERSONAL_ACCESS_TOKEN, ghcr.io/github/github-mcp-server"
                            } else {
                                "-g, @modelcontextprotocol/server-github"
                            }
                        )
                    },
                    supportingText = { 
                        Text(
                            if (command.lowercase() == "docker") {
                                "Для Docker укажите все аргументы после 'docker': run, -i, --rm, -e, переменная, образ"
                            } else {
                                "Аргументы команды, разделенные запятыми"
                            }
                        )
                    }
                )
                
                OutlinedTextField(
                    value = envText,
                    onValueChange = { envText = it },
                    label = { Text("Переменные окружения (KEY=VALUE, по одной на строку)") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 5,
                    placeholder = { Text("GITHUB_TOKEN=your_token\nAPI_KEY=your_key") }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (serverName.isBlank()) {
                        errorMessage = "Введите название сервера"
                        return@Button
                    }
                    if (command.isBlank()) {
                        errorMessage = "Введите команду"
                        return@Button
                    }
                    
                    if (command.contains(" ") && argsText.isBlank()) {
                        val parts = command.split(" ", limit = 2)
                        if (parts.size == 2) {
                            errorMessage = "Похоже, вы ввели всю команду в поле 'Команда'. " +
                                    "В поле 'Команда' должна быть только основная команда (например: docker), " +
                                    "а все остальное (run, -i, --rm и т.д.) должно быть в поле 'Аргументы'."
                            return@Button
                        }
                    }
                    
                    errorMessage = null
                    coroutineScope.launch {
                        try {
                            val args = if (argsText.isBlank()) {
                                emptyList()
                            } else {
                                argsText.split(",")
                                    .map { it.trim() }
                                    .filter { it.isNotBlank() }
                            }
                            
                            val env = envText.split("\n")
                                .map { it.trim() }
                                .filter { it.isNotBlank() }
                                .associate { line ->
                                    val parts = line.split("=", limit = 2)
                                    if (parts.size == 2) {
                                        parts[0].trim() to parts[1].trim()
                                    } else {
                                        parts[0].trim() to ""
                                    }
                                }
                            
                            val server = MCPServer(
                                id = UUID.randomUUID().toString(),
                                name = serverName,
                                command = command,
                                args = args,
                                env = env,
                                enabled = true
                            )
                            repository.saveServer(server)
                            onServerCreated()
                        } catch (e: Exception) {
                            errorMessage = e.message ?: "Ошибка при создании сервера"
                        }
                    }
                },
                enabled = serverName.isNotBlank() && command.isNotBlank()
            ) {
                Text("Добавить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        }
    )
}

@Composable
fun EditMCPServerDialog(
    server: MCPServer,
    repository: MCPServerRepository,
    mcpService: MCPServerService,
    onDismiss: () -> Unit,
    onServerUpdated: () -> Unit
) {
    var serverName by remember { mutableStateOf(server.name) }
    var command by remember { mutableStateOf(server.command) }
    var argsText by remember { mutableStateOf(server.args.joinToString(", ")) }
    var envText by remember { mutableStateOf(server.env.entries.joinToString("\n") { "${it.key}=${it.value}" }) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Редактировать MCP сервер") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (errorMessage != null) {
                    Text(
                        text = errorMessage!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                
                OutlinedTextField(
                    value = serverName,
                    onValueChange = { serverName = it },
                    label = { Text("Название сервера") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("Например: GitHub MCP") }
                )
                
                OutlinedTextField(
                    value = command,
                    onValueChange = { command = it },
                    label = { Text("Команда") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("Например: docker или npx") },
                    supportingText = { 
                        Text("Основная команда для запуска (docker, npx, node и т.д.). Убедитесь, что команда установлена и доступна в PATH.")
                    }
                )
                
                OutlinedTextField(
                    value = argsText,
                    onValueChange = { argsText = it },
                    label = { Text("Аргументы (через запятую)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = false,
                    minLines = 2,
                    maxLines = 4,
                    placeholder = { 
                        Text(
                            if (command.lowercase() == "docker") {
                                "run, -i, --rm, -e, GITHUB_PERSONAL_ACCESS_TOKEN, ghcr.io/github/github-mcp-server"
                            } else {
                                "-g, @modelcontextprotocol/server-github"
                            }
                        )
                    },
                    supportingText = { 
                        Text(
                            if (command.lowercase() == "docker") {
                                "Для Docker укажите все аргументы после 'docker': run, -i, --rm, -e, переменная, образ"
                            } else {
                                "Аргументы команды, разделенные запятыми"
                            }
                        )
                    }
                )
                
                OutlinedTextField(
                    value = envText,
                    onValueChange = { envText = it },
                    label = { Text("Переменные окружения (KEY=VALUE, по одной на строку)") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 5,
                    placeholder = { Text("GITHUB_TOKEN=your_token\nAPI_KEY=your_key") }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (serverName.isBlank()) {
                        errorMessage = "Введите название сервера"
                        return@Button
                    }
                    if (command.isBlank()) {
                        errorMessage = "Введите команду"
                        return@Button
                    }
                    
                    if (command.contains(" ") && argsText.isBlank()) {
                        val parts = command.split(" ", limit = 2)
                        if (parts.size == 2) {
                            errorMessage = "Похоже, вы ввели всю команду в поле 'Команда'. " +
                                    "В поле 'Команда' должна быть только основная команда (например: docker), " +
                                    "а все остальное (run, -i, --rm и т.д.) должно быть в поле 'Аргументы'."
                            return@Button
                        }
                    }
                    
                    errorMessage = null
                    coroutineScope.launch {
                        try {
                            val args = if (argsText.isBlank()) {
                                emptyList()
                            } else {
                                argsText.split(",")
                                    .map { it.trim() }
                                    .filter { it.isNotBlank() }
                            }
                            
                            val env = envText.split("\n")
                                .map { it.trim() }
                                .filter { it.isNotBlank() }
                                .associate { line ->
                                    val parts = line.split("=", limit = 2)
                                    if (parts.size == 2) {
                                        parts[0].trim() to parts[1].trim()
                                    } else {
                                        parts[0].trim() to ""
                                    }
                                }
                            
                            val wasRunning = mcpService.getServerStatus(server.id).value?.status == 
                                MCPServerConnectionStatus.CONNECTED ||
                                mcpService.getServerStatus(server.id).value?.status == 
                                MCPServerConnectionStatus.CONNECTING
                            
                            if (wasRunning) {
                                mcpService.stopServer(server.id)
                            }
                            
                            val updatedServer = server.copy(
                                name = serverName,
                                command = command,
                                args = args,
                                env = env,
                                updatedAt = currentTimeMillis()
                            )
                            repository.updateServer(updatedServer)
                            
                            if (updatedServer.enabled && wasRunning) {
                                mcpService.startServer(updatedServer)
                            }
                            
                            onServerUpdated()
                        } catch (e: Exception) {
                            errorMessage = e.message ?: "Ошибка при обновлении сервера"
                        }
                    }
                },
                enabled = serverName.isNotBlank() && command.isNotBlank()
            ) {
                Text("Сохранить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        }
    )
}

