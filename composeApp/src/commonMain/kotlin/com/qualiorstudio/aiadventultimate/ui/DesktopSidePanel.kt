package com.qualiorstudio.aiadventultimate.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Assessment
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
import com.qualiorstudio.aiadventultimate.service.GitHubPRService
import com.qualiorstudio.aiadventultimate.service.createGitHubPRService
import com.qualiorstudio.aiadventultimate.service.PullRequest
import com.qualiorstudio.aiadventultimate.utils.openUrl
import com.qualiorstudio.aiadventultimate.viewmodel.AgentViewModel
import com.qualiorstudio.aiadventultimate.viewmodel.SettingsViewModel
import com.qualiorstudio.aiadventultimate.viewmodel.SupportViewModel
import com.qualiorstudio.aiadventultimate.viewmodel.SupportEmbeddingViewModel
import com.mikepenz.markdown.m3.Markdown
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.serialization.json.*
import kotlinx.serialization.Serializable
import com.qualiorstudio.aiadventultimate.api.LMStudio
import com.qualiorstudio.aiadventultimate.api.DeepSeekMessage
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.UploadFile
import java.io.File

enum class SidePanelTab {
    AGENTS,
    MCP_SERVERS,
    PULL_REQUESTS,
    SUPPORT,
    TODOIST,
    SYSTEM_PROMPT,
    ANALYTICS
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
    currentProject: com.qualiorstudio.aiadventultimate.model.Project? = null,
    githubBranchInfo: com.qualiorstudio.aiadventultimate.service.GitHubBranchInfo? = null,
    mcpManager: com.qualiorstudio.aiadventultimate.mcp.MCPServerManager? = null,
    settingsViewModel: SettingsViewModel? = null,
    chatViewModel: com.qualiorstudio.aiadventultimate.viewmodel.ChatViewModel? = null,
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
                TabIconButton(
                    icon = Icons.Default.Code,
                    isSelected = selectedTab == SidePanelTab.PULL_REQUESTS,
                    onClick = { 
                        selectedTab = if (selectedTab == SidePanelTab.PULL_REQUESTS) null else SidePanelTab.PULL_REQUESTS
                    },
                    contentDescription = "Pull Requests"
                )
                TabIconButton(
                    icon = Icons.Default.Help,
                    isSelected = selectedTab == SidePanelTab.SUPPORT,
                    onClick = { 
                        selectedTab = if (selectedTab == SidePanelTab.SUPPORT) null else SidePanelTab.SUPPORT
                    },
                    contentDescription = "Поддержка"
                )
                TabIconButton(
                    icon = Icons.Default.CheckCircle,
                    isSelected = selectedTab == SidePanelTab.TODOIST,
                    onClick = { 
                        selectedTab = if (selectedTab == SidePanelTab.TODOIST) null else SidePanelTab.TODOIST
                    },
                    contentDescription = "Todoist"
                )
                TabIconButton(
                    icon = Icons.Default.Edit,
                    isSelected = selectedTab == SidePanelTab.SYSTEM_PROMPT,
                    onClick = { 
                        selectedTab = if (selectedTab == SidePanelTab.SYSTEM_PROMPT) null else SidePanelTab.SYSTEM_PROMPT
                    },
                    contentDescription = "Системный промпт"
                )
                TabIconButton(
                    icon = Icons.Default.Assessment,
                    isSelected = selectedTab == SidePanelTab.ANALYTICS,
                    onClick = { 
                        selectedTab = if (selectedTab == SidePanelTab.ANALYTICS) null else SidePanelTab.ANALYTICS
                    },
                    contentDescription = "Аналитика"
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
                    SidePanelTab.PULL_REQUESTS -> {
                        PullRequestsTabContent(
                            currentProject = currentProject,
                            githubBranchInfo = githubBranchInfo,
                            mcpManager = mcpManager,
                            settingsViewModel = settingsViewModel,
                            onReviewRequest = { pr ->
                                // Review будет обрабатываться внутри компонента
                            }
                        )
                    }
                    SidePanelTab.SUPPORT -> {
                        SupportTabContent(
                            settingsViewModel = settingsViewModel
                        )
                    }
                    SidePanelTab.TODOIST -> {
                        com.qualiorstudio.aiadventultimate.ui.TodoistTabContent(
                            currentProject = currentProject,
                            mcpManager = mcpManager,
                            chatViewModel = chatViewModel
                        )
                    }
                    SidePanelTab.SYSTEM_PROMPT -> {
                        SystemPromptTabContent(
                            chatViewModel = chatViewModel
                        )
                    }
                    SidePanelTab.ANALYTICS -> {
                        AnalyticsTabContent()
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

@Composable
fun PullRequestsTabContent(
    currentProject: com.qualiorstudio.aiadventultimate.model.Project?,
    githubBranchInfo: com.qualiorstudio.aiadventultimate.service.GitHubBranchInfo?,
    mcpManager: com.qualiorstudio.aiadventultimate.mcp.MCPServerManager?,
    settingsViewModel: SettingsViewModel? = null,
    onReviewRequest: ((PullRequest) -> Unit)? = null
) {
    var pullRequests by remember { mutableStateOf<List<PullRequest>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val prService = remember { createGitHubPRService() }
    
    fun loadPullRequests() {
        if (githubBranchInfo?.isGitHubRepo != true || 
            githubBranchInfo.owner == null || 
            githubBranchInfo.repo == null ||
            mcpManager == null) {
            errorMessage = "Проект не подключен к GitHub репозиторию"
            return
        }
        
        isLoading = true
        errorMessage = null
        coroutineScope.launch {
            try {
                val prs = prService.getOpenPullRequests(
                    owner = githubBranchInfo.owner!!,
                    repo = githubBranchInfo.repo!!,
                    mcpManager = mcpManager
                )
                pullRequests = prs
            } catch (e: Exception) {
                errorMessage = "Ошибка при загрузке PR: ${e.message}"
                println("Ошибка при загрузке PR: ${e.message}")
                e.printStackTrace()
            } finally {
                isLoading = false
            }
        }
    }
    
    LaunchedEffect(currentProject, githubBranchInfo, mcpManager) {
        if (currentProject != null && 
            githubBranchInfo?.isGitHubRepo == true && 
            githubBranchInfo.owner != null && 
            githubBranchInfo.repo != null &&
            mcpManager != null) {
            loadPullRequests()
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
                text = "Pull Requests",
                style = MaterialTheme.typography.titleLarge
            )
            IconButton(
                onClick = { loadPullRequests() },
                enabled = !isLoading && 
                    githubBranchInfo?.isGitHubRepo == true && 
                    githubBranchInfo.owner != null && 
                    githubBranchInfo.repo != null &&
                    mcpManager != null
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Обновить список PR",
                    tint = if (isLoading) 
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    else
                        MaterialTheme.colorScheme.primary
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
                    Button(onClick = { loadPullRequests() }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Повторить")
                    }
                }
            }
        } else if (githubBranchInfo?.isGitHubRepo != true || 
                   githubBranchInfo.owner == null || 
                   githubBranchInfo.repo == null ||
                   mcpManager == null) {
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
                        text = "Проект не подключен к GitHub",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Откройте проект, подключенный к GitHub репозиторию",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else if (pullRequests.isEmpty()) {
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
                        text = "Нет открытых Pull Requests",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Репозиторий: ${githubBranchInfo.owner}/${githubBranchInfo.repo}",
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
                items(pullRequests) { pr ->
                    PullRequestListItem(
                        pr = pr,
                        githubBranchInfo = githubBranchInfo,
                        mcpManager = mcpManager,
                        settingsViewModel = settingsViewModel
                    )
                }
            }
        }
    }
}

@Composable
fun PullRequestListItem(
    pr: PullRequest,
    githubBranchInfo: com.qualiorstudio.aiadventultimate.service.GitHubBranchInfo?,
    mcpManager: com.qualiorstudio.aiadventultimate.mcp.MCPServerManager?,
    settingsViewModel: SettingsViewModel? = null
) {
    var isReviewing by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val prService = remember { createGitHubPRService() }
    
    fun startReview() {
        if (githubBranchInfo?.owner == null || githubBranchInfo.repo == null || mcpManager == null) {
            return
        }
        
        isReviewing = true
        
        coroutineScope.launch {
            try {
                val diff = prService.getPullRequestDiff(
                    owner = githubBranchInfo.owner!!,
                    repo = githubBranchInfo.repo!!,
                    prNumber = pr.number,
                    title = pr.title,
                    headBranch = pr.headBranch,
                    baseBranch = pr.baseBranch,
                    mcpManager = mcpManager
                )
                
                if (diff == null || diff.isBlank()) {
                    println("Не удалось получить diff для PR #${pr.number}")
                    isReviewing = false
                    return@launch
                }
                
                val reviewPrompt = """
Проведи code review для следующего Pull Request:

Название PR: ${pr.title}
Автор: ${pr.author ?: "Неизвестно"}
Описание: ${pr.body ?: "Нет описания"}

Diff изменений:
```diff
$diff
```

Проанализируй код и предоставь:
1. Общую оценку изменений
2. Найденные баги или потенциальные проблемы
3. Предложения по улучшению кода
4. Рекомендации по стилю и best practices
5. Комментарии по безопасности (если применимо)

Будь конструктивным и конкретным в своих замечаниях.
                """.trimIndent()
                
                val ollamaChat = com.qualiorstudio.aiadventultimate.api.OllamaChat()
                
                val messages = listOf(
                    com.qualiorstudio.aiadventultimate.api.DeepSeekMessage(
                        role = "system",
                        content = "Ты опытный code reviewer. Проводишь тщательный анализ кода, находишь баги, предлагаешь улучшения и следуешь best practices."
                    ),
                    com.qualiorstudio.aiadventultimate.api.DeepSeekMessage(
                        role = "user",
                        content = reviewPrompt
                    )
                )
                
                val response = ollamaChat.sendMessage(messages, null, temperature = 0.3, maxTokens = 4000)
                val reviewText = response.choices.firstOrNull()?.message?.content ?: "Не удалось получить ответ от AI"
                
                println("Review сгенерирован, длина: ${reviewText.length}")
                
                val commentAdded = prService.addReviewComment(
                    owner = githubBranchInfo.owner!!,
                    repo = githubBranchInfo.repo!!,
                    pullNumber = pr.number,
                    comment = reviewText,
                    mcpManager = mcpManager
                )
                
                if (commentAdded) {
                    println("Review успешно добавлен как комментарий в PR #${pr.number}")
                } else {
                    println("Не удалось добавить review как комментарий в PR #${pr.number}")
                }
            } catch (e: Exception) {
                println("Ошибка при review PR: ${e.message}")
                e.printStackTrace()
            } finally {
                isReviewing = false
            }
        }
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                pr.url?.let { url ->
                    openUrl(url)
                }
            },
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
                    text = "#${pr.number}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                if (pr.author != null) {
                    Text(
                        text = "@${pr.author}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            Text(
                text = pr.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            
            if (pr.body != null && pr.body.isNotBlank()) {
                Text(
                    text = pr.body.take(150) + if (pr.body.length > 150) "..." else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (pr.headBranch != null && pr.baseBranch != null) {
                    Text(
                        text = "${pr.headBranch} → ${pr.baseBranch}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (pr.updatedAt != null) {
                    Text(
                        text = "Обновлено: ${formatDateString(pr.updatedAt)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Button(
                    onClick = { startReview() },
                    enabled = !isReviewing && githubBranchInfo?.owner != null && githubBranchInfo.repo != null && mcpManager != null
                ) {
                    if (isReviewing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Анализ...")
                    } else {
                        Icon(
                            imageVector = Icons.Default.Visibility,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Review")
                    }
                }
            }
        }
    }
}

expect fun formatDateString(dateString: String): String

@Composable
fun SupportTabContent(
    settingsViewModel: SettingsViewModel? = null
) {
    var showDocsScreen by remember { mutableStateOf(false) }
    val defaultSettingsViewModel: SettingsViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val supportViewModel = remember(settingsViewModel) {
        SupportViewModel(
            settingsViewModel = settingsViewModel ?: defaultSettingsViewModel
        )
    }
    val supportEmbeddingViewModel = remember { SupportEmbeddingViewModel() }
    val coroutineScope = rememberCoroutineScope()
    
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = !showDocsScreen,
                onClick = { showDocsScreen = false },
                label = { Text("Чат поддержки") }
            )
            FilterChip(
                selected = showDocsScreen,
                onClick = { showDocsScreen = true },
                label = { Text("Документация") }
            )
        }
        
        Box(modifier = Modifier.weight(1f)) {
            if (showDocsScreen) {
                SupportDocsScreenContent(
                    viewModel = supportEmbeddingViewModel,
                    onFilesSelected = { filePaths ->
                        if (filePaths.isNotEmpty()) {
                            coroutineScope.launch {
                                supportEmbeddingViewModel.processSupportDocs(filePaths)
                            }
                        }
                    }
                )
            } else {
                SupportScreen(
                    viewModel = supportViewModel,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@Composable
fun SystemPromptTabContent(
    chatViewModel: com.qualiorstudio.aiadventultimate.viewmodel.ChatViewModel?
) {
    val systemPrompt by (chatViewModel?.systemPrompt ?: kotlinx.coroutines.flow.MutableStateFlow<String?>(null)).collectAsState()
    var promptText by remember(systemPrompt) { mutableStateOf(systemPrompt ?: "") }
    val coroutineScope = rememberCoroutineScope()
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Системный промпт",
            style = MaterialTheme.typography.titleLarge
        )
        
        Text(
            text = "Настройте системный промпт для текущего чата. Этот промпт определяет поведение AI ассистента.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        OutlinedTextField(
            value = promptText,
            onValueChange = { promptText = it },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            label = { Text("Системный промпт") },
            placeholder = { Text("Введите системный промпт...") },
            minLines = 10,
            maxLines = 20,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline
            )
        )
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    coroutineScope.launch {
                        chatViewModel?.updateSystemPrompt(if (promptText.isBlank()) null else promptText)
                    }
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("Сохранить")
            }
            
            OutlinedButton(
                onClick = {
                    promptText = ""
                    coroutineScope.launch {
                        chatViewModel?.updateSystemPrompt(null)
                    }
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("Очистить")
            }
        }
        
        if (systemPrompt != null && systemPrompt!!.isNotBlank()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(12.dp)
                ) {
                    Text(
                        text = "Текущий промпт сохранен",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
    }
}

@Composable
expect fun JsonFilePickerButton(
    onFileSelected: (String?) -> Unit,
    enabled: Boolean
)

@Composable
fun AnalyticsTabContent() {
    var jsonData by remember { mutableStateOf<String?>(null) }
    var question by remember { mutableStateOf("") }
    var answer by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val lmStudio = remember { LMStudio() }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Аналитика данных",
            style = MaterialTheme.typography.titleLarge
        )
        
        Text(
            text = "Загрузите JSON файл с аналитическими данными или логами проекта и задайте вопросы по ним.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        JsonFilePickerButton(
            onFileSelected = { filePath ->
                if (filePath != null) {
                    try {
                        val file = File(filePath)
                        val fileSize = file.length()
                        val maxSize = 10 * 1024 * 1024
                        
                        if (fileSize > maxSize) {
                            errorMessage = "Файл слишком большой (${fileSize / 1024 / 1024} МБ). Максимальный размер: ${maxSize / 1024 / 1024} МБ"
                            jsonData = null
                            return@JsonFilePickerButton
                        }
                        
                        val content = file.readText()
                        jsonData = content
                        errorMessage = null
                        answer = null
                    } catch (e: Exception) {
                        errorMessage = "Ошибка при чтении файла: ${e.message}"
                        jsonData = null
                    }
                }
            },
            enabled = !isLoading
        )
        
        if (jsonData != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = "Файл загружен",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Размер данных: ${jsonData!!.length} символов",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = {
                            jsonData = null
                            answer = null
                            question = ""
                            errorMessage = null
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Очистить данные"
                        )
                    }
                }
            }
        }
        
        if (errorMessage != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Text(
                    text = errorMessage ?: "",
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
        
        OutlinedTextField(
            value = question,
            onValueChange = { question = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Ваш вопрос") },
            placeholder = { Text("Например: Какая ошибка происходит чаще всего?") },
            enabled = !isLoading && jsonData != null,
            minLines = 2,
            maxLines = 4,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            trailingIcon = {
                IconButton(
                    onClick = {
                        if (question.isNotBlank() && jsonData != null && !isLoading) {
                            isLoading = true
                            errorMessage = null
                            answer = null
                            
                            coroutineScope.launch {
                                try {
                                    val dataPreview = if (jsonData!!.length > 50000) {
                                        jsonData!!.take(50000) + "\n\n... (данные обрезаны, показаны первые 50000 символов)"
                                    } else {
                                        jsonData!!
                                    }
                                    
                                    val prompt = """
Проанализируй следующие JSON данные и ответь на вопрос пользователя, используя ТОЛЬКО факты и числа из предоставленных данных.

JSON данные:
$dataPreview

Вопрос пользователя: $question

ВАЖНО:
- Используй ТОЛЬКО конкретные числа, значения и факты из JSON данных выше
- Выполняй точные расчеты на основе данных (суммы, проценты, средние значения)
- Указывай точные значения из данных, не используй обобщения типа "много", "мало", "некоторые"
- Если данных недостаточно для ответа, так и скажи
- Приводи конкретные примеры из данных (названия, ID, значения)
- Не делай предположений или выводов, которых нет в данных
- Форматируй числа с точностью до 2 знаков после запятой, если это проценты или десятичные дроби
                                    """.trimIndent()
                                    
                                    val messages = listOf(
                                        DeepSeekMessage(
                                            role = "system",
                                            content = """Ты точный аналитик данных. Твоя задача - анализировать JSON данные и предоставлять ТОЛЬКО конкретные факты, числа и расчеты из этих данных.

ПРАВИЛА ОТВЕТА:
1. Используй ТОЛЬКО данные из предоставленного JSON - никаких обобщений или предположений
2. Всегда приводи точные числа и значения из данных
3. Выполняй математические расчеты на основе данных (суммы, проценты, средние, максимумы, минимумы)
4. Указывай конкретные названия, ID, типы из данных
5. Если вопрос требует сравнения - сравнивай конкретные значения из данных
6. Если данных недостаточно - так и скажи, не выдумывай
7. Форматируй ответ структурированно: сначала краткий ответ с числами, затем детали с расчетами
8. НЕ используй фразы типа "много", "мало", "некоторые", "часто" - всегда указывай точные числа или проценты

Пример хорошего ответа:
"NetworkError происходит чаще всего: 456 раз (36.8% от всех ошибок). Следующая по частоте - AuthenticationError: 234 раза (18.9%)."

Пример плохого ответа:
"Часто происходят сетевые ошибки и ошибки аутентификации." - это плохо, нет конкретных чисел."""
                                        ),
                                        DeepSeekMessage(
                                            role = "user",
                                            content = prompt
                                        )
                                    )
                                    
                                    val response = lmStudio.sendMessage(
                                        messages = messages,
                                        temperature = 0.3,
                                        maxTokens = 4000
                                    )
                                    
                                    answer = response.choices.firstOrNull()?.message?.content
                                        ?: "Не удалось получить ответ от модели"
                                } catch (e: Exception) {
                                    errorMessage = "Ошибка при запросе к LM Studio: ${e.message}"
                                    e.printStackTrace()
                                } finally {
                                    isLoading = false
                                }
                            }
                        }
                    },
                    enabled = !isLoading && jsonData != null && question.isNotBlank()
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Отправить вопрос"
                        )
                    }
                }
            }
        )
        
        if (answer != null) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = "Ответ:",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Markdown(
                        content = answer ?: "",
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

