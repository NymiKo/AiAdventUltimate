package com.qualiorstudio.aiadventultimate.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.qualiorstudio.aiadventultimate.model.Agent
import com.qualiorstudio.aiadventultimate.repository.AgentConnectionRepository
import com.qualiorstudio.aiadventultimate.repository.MCPServerRepository
import com.qualiorstudio.aiadventultimate.service.MCPServerService
import com.qualiorstudio.aiadventultimate.viewmodel.AgentViewModel
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope

enum class SidePanelTab {
    AGENTS,
    MCP_SERVERS
}

@Composable
fun DesktopSidePanel(
    isOpen: Boolean,
    onClose: () -> Unit,
    agentViewModel: AgentViewModel,
    selectedAgents: List<Agent>,
    onAgentsSelected: (List<Agent>) -> Unit,
    connectionRepository: AgentConnectionRepository,
    mcpServerRepository: MCPServerRepository,
    mcpServerService: MCPServerService,
    modifier: Modifier = Modifier
) {
    val agents by agentViewModel.agents.collectAsState()
    val isLoading by agentViewModel.isLoading.collectAsState()
    val isGeneratingPrompt by agentViewModel.isGeneratingPrompt.collectAsState()
    var selectedTab by remember { mutableStateOf<SidePanelTab?>(SidePanelTab.AGENTS) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var showCreateConnectionDialog by remember { mutableStateOf(false) }
    var showCreateMCPServerDialog by remember { mutableStateOf(false) }
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
    
    val panelWidth = 400.dp
    val tabBarWidth = 64.dp
    
    AnimatedVisibility(
        visible = isOpen,
        modifier = modifier,
        enter = slideInHorizontally(
            initialOffsetX = { it },
            animationSpec = tween(300, easing = FastOutSlowInEasing)
        ) + fadeIn(animationSpec = tween(300)),
        exit = slideOutHorizontally(
            targetOffsetX = { it },
            animationSpec = tween(300, easing = FastOutSlowInEasing)
        ) + fadeOut(animationSpec = tween(300))
    ) {
        Row(
            modifier = Modifier
                .fillMaxHeight()
                .width(panelWidth + tabBarWidth)
        ) {
            Column(
                modifier = Modifier
                    .width(tabBarWidth)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                TabIconButton(
                    icon = Icons.Default.Person,
                    isSelected = selectedTab == SidePanelTab.AGENTS,
                    onClick = { 
                        selectedTab = if (selectedTab == SidePanelTab.AGENTS) null else SidePanelTab.AGENTS
                    },
                    contentDescription = "Агенты"
                )
                TabIconButton(
                    icon = Icons.Default.Settings,
                    isSelected = selectedTab == SidePanelTab.MCP_SERVERS,
                    onClick = { 
                        selectedTab = if (selectedTab == SidePanelTab.MCP_SERVERS) null else SidePanelTab.MCP_SERVERS
                    },
                    contentDescription = "MCP Серверы"
                )
            }
            
            Column(
                modifier = Modifier
                    .width(panelWidth)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                when (selectedTab) {
                    SidePanelTab.AGENTS -> {
                        AgentsTabContent(
                            agents = agents,
                            isLoading = isLoading,
                            isGeneratingPrompt = isGeneratingPrompt,
                            selectedAgentIds = selectedAgentIds,
                            onToggleAgent = { toggleAgentSelection(it) },
                            onDeleteAgent = { agent ->
                                coroutineScope.launch {
                                    agentViewModel.deleteAgent(agent.id)
                                    selectedAgentIds.remove(agent.id)
                                    val updatedSelectedAgents = agents.filter { selectedAgentIds.contains(it.id) }
                                    onAgentsSelected(updatedSelectedAgents)
                                }
                            },
                            onCreateAgent = { showCreateDialog = true },
                            onCreateConnection = { showCreateConnectionDialog = true }
                        )
                    }
                    SidePanelTab.MCP_SERVERS -> {
                        MCPServerTabContent(
                            repository = mcpServerRepository,
                            mcpService = mcpServerService,
                            onCreateServer = { showCreateMCPServerDialog = true }
                        )
                    }
                    null -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Выберите вкладку",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
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
    
    if (showCreateConnectionDialog) {
        CreateAgentConnectionDialog(
            agents = agents,
            connectionRepository = connectionRepository,
            onDismiss = { showCreateConnectionDialog = false },
            onConnectionCreated = { showCreateConnectionDialog = false }
        )
    }
    
    if (showCreateMCPServerDialog) {
        CreateMCPServerDialog(
            repository = mcpServerRepository,
            onDismiss = { showCreateMCPServerDialog = false },
            onServerCreated = { showCreateMCPServerDialog = false }
        )
    }
}

@Composable
fun TabIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    contentDescription: String?,
    modifier: Modifier = Modifier
) {
    IconButton(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (isSelected)
                MaterialTheme.colorScheme.primary
            else
                MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
fun AgentsTabContent(
    agents: List<Agent>,
    isLoading: Boolean,
    isGeneratingPrompt: Boolean,
    selectedAgentIds: MutableSet<String>,
    onToggleAgent: (Agent) -> Unit,
    onDeleteAgent: (Agent) -> Unit,
    onCreateAgent: () -> Unit,
    onCreateConnection: () -> Unit
) {
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
                text = "Агенты",
                style = MaterialTheme.typography.titleLarge
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(
                    onClick = onCreateConnection,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Создать связь между агентами",
                        modifier = Modifier.size(20.dp)
                    )
                }
                FloatingActionButton(
                    onClick = onCreateAgent,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Создать агента",
                        modifier = Modifier.size(20.dp)
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
                    Button(onClick = onCreateAgent) {
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
                        onAgentClick = { onToggleAgent(agent) },
                        onDeleteClick = { onDeleteAgent(agent) }
                    )
                }
            }
        }
    }
}

