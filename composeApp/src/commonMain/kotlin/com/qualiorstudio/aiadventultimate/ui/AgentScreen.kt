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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.qualiorstudio.aiadventultimate.model.Agent
import com.qualiorstudio.aiadventultimate.viewmodel.AgentViewModel
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope

@Composable
fun AgentScreen(
    agentViewModel: AgentViewModel,
    selectedAgents: List<Agent>,
    useCoordinator: Boolean,
    onBack: () -> Unit,
    onAgentsSelected: (List<Agent>) -> Unit,
    onUseCoordinatorChange: (Boolean) -> Unit,
    onShowConnections: () -> Unit
) {
    val agents by agentViewModel.agents.collectAsState()
    val isLoading by agentViewModel.isLoading.collectAsState()
    val isGeneratingPrompt by agentViewModel.isGeneratingPrompt.collectAsState()
    var showCreateDialog by remember { mutableStateOf(false) }
    val selectedAgentIds = remember { mutableStateSetOf<String>() }
    val coroutineScope = rememberCoroutineScope()
    
    LaunchedEffect(selectedAgents) {
        selectedAgentIds.clear()
        selectedAgents.forEach { selectedAgentIds.add(it.id) }
    }
    
    LaunchedEffect(Unit) {
        agentViewModel.observeAgents()
    }
    
    fun toggleAgentSelection(agent: Agent) {
        if (selectedAgentIds.contains(agent.id)) {
            selectedAgentIds.remove(agent.id)
        } else {
            selectedAgentIds.add(agent.id)
        }
        val updatedSelectedAgents = agents.filter { selectedAgentIds.contains(it.id) }
        onAgentsSelected(updatedSelectedAgents)
    }
    
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
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
                        text = "Агенты",
                        style = MaterialTheme.typography.headlineMedium
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(onClick = onShowConnections) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Связи между агентами"
                        )
                    }
                    FloatingActionButton(
                        onClick = { showCreateDialog = true },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Создать агента"
                        )
                    }
                }
            }
            
            if (selectedAgents.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "Координатор агентов",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = "Автоматически выбирает наиболее подходящего агента из выбранных",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = useCoordinator,
                        onCheckedChange = onUseCoordinatorChange
                    )
                }
            }
        }
        
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (agents.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Нет созданных агентов",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(onClick = { showCreateDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Создать агента")
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(agents) { agent ->
                    AgentListItem(
                        agent = agent,
                        isSelected = selectedAgentIds.contains(agent.id),
                        onAgentClick = { toggleAgentSelection(agent) },
                        onDeleteClick = {
                            coroutineScope.launch {
                                agentViewModel.deleteAgent(agent.id)
                                selectedAgentIds.remove(agent.id)
                                val updatedSelectedAgents = agents.filter { selectedAgentIds.contains(it.id) }
                                onAgentsSelected(updatedSelectedAgents)
                            }
                        }
                    )
                }
            }
        }
    }
    
    if (showCreateDialog) {
        CreateAgentDialog(
            agentViewModel = agentViewModel,
            isGeneratingPrompt = isGeneratingPrompt,
            onDismiss = { showCreateDialog = false },
            onAgentCreated = { agent ->
                showCreateDialog = false
                selectedAgentIds.add(agent.id)
                val updatedSelectedAgents = (agentViewModel.agents.value.filter { selectedAgentIds.contains(it.id) } + agent)
                onAgentsSelected(updatedSelectedAgents)
            }
        )
    }
}

@Composable
fun AgentListItem(
    agent: Agent,
    isSelected: Boolean,
    onAgentClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onAgentClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Выбран",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = agent.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = agent.role,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
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
            title = { Text("Удалить агента?") },
            text = { Text("Вы уверены, что хотите удалить агента \"${agent.name}\"?") },
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
fun CreateAgentDialog(
    agentViewModel: AgentViewModel,
    isGeneratingPrompt: Boolean,
    onDismiss: () -> Unit,
    onAgentCreated: (Agent) -> Unit
) {
    var agentName by remember { mutableStateOf("") }
    var agentRole by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()
    
    AlertDialog(
        onDismissRequest = { if (!isGeneratingPrompt) onDismiss() },
        title = { Text("Создать агента") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (isGeneratingPrompt) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        Text("Генерация системного промпта...")
                    }
                }
                
                if (errorMessage != null) {
                    Text(
                        text = errorMessage!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                
                OutlinedTextField(
                    value = agentName,
                    onValueChange = { agentName = it },
                    label = { Text("Имя агента") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isGeneratingPrompt,
                    singleLine = true
                )
                
                OutlinedTextField(
                    value = agentRole,
                    onValueChange = { agentRole = it },
                    label = { Text("Описание роли") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isGeneratingPrompt,
                    minLines = 3,
                    maxLines = 5,
                    placeholder = { Text("Опишите роль агента, его специализацию и стиль общения...") }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (agentName.isBlank()) {
                        errorMessage = "Введите имя агента"
                        return@Button
                    }
                    if (agentRole.isBlank()) {
                        errorMessage = "Опишите роль агента"
                        return@Button
                    }
                    
                    errorMessage = null
                    coroutineScope.launch {
                        try {
                            val agent = agentViewModel.createAgent(agentName, agentRole)
                            onAgentCreated(agent)
                        } catch (e: Exception) {
                            errorMessage = e.message ?: "Ошибка при создании агента"
                        }
                    }
                },
                enabled = !isGeneratingPrompt && agentName.isNotBlank() && agentRole.isNotBlank()
            ) {
                Text("Создать")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isGeneratingPrompt
            ) {
                Text("Отмена")
            }
        }
    )
}

