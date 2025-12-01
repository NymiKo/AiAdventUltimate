package com.qualiorstudio.aiadventultimate.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.qualiorstudio.aiadventultimate.model.Agent
import com.qualiorstudio.aiadventultimate.model.AgentConnection
import com.qualiorstudio.aiadventultimate.model.ConnectionType
import com.qualiorstudio.aiadventultimate.repository.AgentConnectionRepository
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import java.util.UUID

@Composable
fun AgentConnectionScreen(
    agents: List<Agent>,
    connectionRepository: AgentConnectionRepository,
    onBack: () -> Unit
) {
    val connections by connectionRepository.observeAllConnections().collectAsState(initial = emptyList())
    var showCreateDialog by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    
    LaunchedEffect(Unit) {
        coroutineScope.launch {
            connectionRepository.reloadConnections()
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
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Назад"
                    )
                }
                Text(
                    text = "Связи между агентами",
                    style = MaterialTheme.typography.headlineMedium
                )
            }
            FloatingActionButton(
                onClick = { showCreateDialog = true },
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Создать связь"
                )
            }
        }
        
        if (connections.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Нет созданных связей",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(onClick = { showCreateDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Создать связь")
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(connections) { connection ->
                    val sourceAgent = agents.find { it.id == connection.sourceAgentId }
                    val targetAgent = agents.find { it.id == connection.targetAgentId }
                    
                    AgentConnectionListItem(
                        connection = connection,
                        sourceAgent = sourceAgent,
                        targetAgent = targetAgent,
                        onDeleteClick = {
                            coroutineScope.launch {
                                connectionRepository.deleteConnection(connection.id)
                            }
                        }
                    )
                }
            }
        }
    }
    
    if (showCreateDialog) {
        CreateAgentConnectionDialog(
            agents = agents,
            connectionRepository = connectionRepository,
            onDismiss = { showCreateDialog = false },
            onConnectionCreated = { showCreateDialog = false }
        )
    }
}

@Composable
fun AgentConnectionListItem(
    connection: AgentConnection,
    sourceAgent: Agent?,
    targetAgent: Agent?,
    onDeleteClick: () -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = sourceAgent?.name ?: "Неизвестный агент",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "→",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = targetAgent?.name ?: "Неизвестный агент",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = connectionTypeDisplayName(connection.connectionType),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = connection.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
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
    
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Удалить связь?") },
            text = { Text("Вы уверены, что хотите удалить эту связь между агентами?") },
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateAgentConnectionDialog(
    agents: List<Agent>,
    connectionRepository: AgentConnectionRepository,
    onDismiss: () -> Unit,
    onConnectionCreated: () -> Unit
) {
    var sourceAgentId by remember { mutableStateOf<String?>(null) }
    var targetAgentId by remember { mutableStateOf<String?>(null) }
    var connectionType by remember { mutableStateOf(ConnectionType.REVIEW) }
    var description by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Создать связь между агентами") },
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
                
                Text(
                    text = "Агент-источник:",
                    style = MaterialTheme.typography.labelMedium
                )
                var expandedSource by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = expandedSource,
                    onExpandedChange = { expandedSource = !expandedSource }
                ) {
                    OutlinedTextField(
                        value = agents.find { it.id == sourceAgentId }?.name ?: "",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Выберите агента-источник") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedSource) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = expandedSource,
                        onDismissRequest = { expandedSource = false }
                    ) {
                        agents.forEach { agent ->
                            DropdownMenuItem(
                                text = { Text(agent.name) },
                                onClick = {
                                    sourceAgentId = agent.id
                                    expandedSource = false
                                }
                            )
                        }
                    }
                }
                
                Text(
                    text = "Тип связи:",
                    style = MaterialTheme.typography.labelMedium
                )
                var expandedType by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = expandedType,
                    onExpandedChange = { expandedType = !expandedType }
                ) {
                    OutlinedTextField(
                        value = connectionTypeDisplayName(connectionType),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Тип связи") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedType) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = expandedType,
                        onDismissRequest = { expandedType = false }
                    ) {
                        ConnectionType.values().forEach { type ->
                            DropdownMenuItem(
                                text = { Text(connectionTypeDisplayName(type)) },
                                onClick = {
                                    connectionType = type
                                    expandedType = false
                                }
                            )
                        }
                    }
                }
                
                Text(
                    text = "Агент-получатель:",
                    style = MaterialTheme.typography.labelMedium
                )
                var expandedTarget by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = expandedTarget,
                    onExpandedChange = { expandedTarget = !expandedTarget }
                ) {
                    OutlinedTextField(
                        value = agents.find { it.id == targetAgentId }?.name ?: "",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Выберите агента-получатель") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedTarget) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = expandedTarget,
                        onDismissRequest = { expandedTarget = false }
                    ) {
                        agents.filter { it.id != sourceAgentId }.forEach { agent ->
                            DropdownMenuItem(
                                text = { Text(agent.name) },
                                onClick = {
                                    targetAgentId = agent.id
                                    expandedTarget = false
                                }
                            )
                        }
                    }
                }
                
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Описание связи") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 5,
                    placeholder = { Text("Опишите, как второй агент должен взаимодействовать с ответом первого...") }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (sourceAgentId == null) {
                        errorMessage = "Выберите агента-источник"
                        return@Button
                    }
                    if (targetAgentId == null) {
                        errorMessage = "Выберите агента-получатель"
                        return@Button
                    }
                    if (description.isBlank()) {
                        errorMessage = "Опишите связь"
                        return@Button
                    }
                    if (sourceAgentId == targetAgentId) {
                        errorMessage = "Агент не может быть связан сам с собой"
                        return@Button
                    }
                    
                    errorMessage = null
                    coroutineScope.launch {
                        try {
                            val connection = AgentConnection(
                                id = UUID.randomUUID().toString(),
                                sourceAgentId = sourceAgentId!!,
                                targetAgentId = targetAgentId!!,
                                description = description,
                                connectionType = connectionType
                            )
                            connectionRepository.saveConnection(connection)
                            onConnectionCreated()
                        } catch (e: Exception) {
                            errorMessage = e.message ?: "Ошибка при создании связи"
                        }
                    }
                },
                enabled = sourceAgentId != null && targetAgentId != null && description.isNotBlank() && sourceAgentId != targetAgentId
            ) {
                Text("Создать")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        }
    )
}

private fun connectionTypeDisplayName(type: ConnectionType): String {
    return when (type) {
        ConnectionType.REVIEW -> "Проверка"
        ConnectionType.VALIDATE -> "Валидация"
        ConnectionType.ENHANCE -> "Улучшение"
        ConnectionType.COLLABORATE -> "Сотрудничество"
    }
}

