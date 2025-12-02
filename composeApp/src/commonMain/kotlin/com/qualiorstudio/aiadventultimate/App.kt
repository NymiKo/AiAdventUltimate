package com.qualiorstudio.aiadventultimate

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mikepenz.markdown.m3.Markdown
import com.qualiorstudio.aiadventultimate.model.ChatMessage
import com.qualiorstudio.aiadventultimate.model.ChatResponseVariant
import com.qualiorstudio.aiadventultimate.model.Command
import com.qualiorstudio.aiadventultimate.model.Commands
import com.qualiorstudio.aiadventultimate.repository.AgentConnectionRepository
import com.qualiorstudio.aiadventultimate.repository.AgentConnectionRepositoryImpl
import com.qualiorstudio.aiadventultimate.repository.ChatRepository
import com.qualiorstudio.aiadventultimate.repository.ChatRepositoryImpl
import com.qualiorstudio.aiadventultimate.repository.MCPServerRepository
import com.qualiorstudio.aiadventultimate.repository.MCPServerRepositoryImpl
import com.qualiorstudio.aiadventultimate.service.MCPServerService
import com.qualiorstudio.aiadventultimate.service.MCPServerServiceImpl
import com.qualiorstudio.aiadventultimate.ui.AgentConnectionScreen
import com.qualiorstudio.aiadventultimate.ui.AgentScreen
import com.qualiorstudio.aiadventultimate.ui.ChatListScreen
import com.qualiorstudio.aiadventultimate.ui.DesktopSidePanel
import com.qualiorstudio.aiadventultimate.ui.EmbeddingScreenContent
import com.qualiorstudio.aiadventultimate.ui.SettingsScreen
import com.qualiorstudio.aiadventultimate.utils.isDesktop
import com.qualiorstudio.aiadventultimate.theme.AiAdventUltimateTheme
import com.qualiorstudio.aiadventultimate.ui.ProjectBar
import com.qualiorstudio.aiadventultimate.ui.ProjectScreen
import com.qualiorstudio.aiadventultimate.viewmodel.AgentViewModel
import com.qualiorstudio.aiadventultimate.viewmodel.ChatViewModel
import com.qualiorstudio.aiadventultimate.viewmodel.EmbeddingViewModel
import com.qualiorstudio.aiadventultimate.viewmodel.SettingsViewModel
import com.qualiorstudio.aiadventultimate.voice.createVoiceInputService
import com.qualiorstudio.aiadventultimate.mcp.createMCPServerManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
@Preview
fun App() {
    val settingsViewModel: SettingsViewModel = viewModel { SettingsViewModel() }
    val settings by settingsViewModel.settings.collectAsState()
    val chatRepository = remember { ChatRepositoryImpl() }
    val coroutineScope = rememberCoroutineScope()
    
    LaunchedEffect(Unit) {
        coroutineScope.launch {
            chatRepository.reloadChats()
        }
    }
    
    AiAdventUltimateTheme(darkTheme = settings.darkTheme) {
        ChatScreen(
            settingsViewModel = settingsViewModel,
            chatRepository = chatRepository
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    settingsViewModel: SettingsViewModel = viewModel { SettingsViewModel() },
    chatRepository: ChatRepository = remember { ChatRepositoryImpl() },
    connectionRepository: AgentConnectionRepository = remember { AgentConnectionRepositoryImpl() },
    mcpServerRepository: MCPServerRepository = remember { MCPServerRepositoryImpl() },
    projectRepository: com.qualiorstudio.aiadventultimate.repository.ProjectRepository = remember { com.qualiorstudio.aiadventultimate.repository.ProjectRepositoryImpl() },
    mcpServerService: MCPServerService = remember { MCPServerServiceImpl() },
    embeddingViewModel: EmbeddingViewModel = viewModel { EmbeddingViewModel() },
    viewModel: ChatViewModel = viewModel { ChatViewModel(settingsViewModel, chatRepository, connectionRepository, mcpServerRepository, projectRepository, embeddingViewModel) },
    agentViewModel: AgentViewModel = viewModel { AgentViewModel(deepSeekApiKey = settingsViewModel.settings.value.deepSeekApiKey) }
) {
    var currentScreen by remember { mutableStateOf("chat") }
    var showChatList by remember { mutableStateOf(false) }
    var showSidePanel by remember { mutableStateOf(false) }
    var showProjectScreen by remember { mutableStateOf(false) }
    val isDesktopPlatform = isDesktop()
    val messages by viewModel.messages.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val settings by settingsViewModel.settings.collectAsState()
    val selectedAgents by viewModel.selectedAgents.collectAsState()
    val useCoordinator by viewModel.useCoordinator.collectAsState()
    val currentProject by viewModel.currentProject.collectAsState()
    val currentBranch = viewModel.currentBranch.collectAsState().value
    val githubBranchInfo by viewModel.githubBranchInfo.collectAsState()
    val useRAG = settings.useRAG
    var messageText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val voiceService = remember { createVoiceInputService() }
    var isRecording by remember { mutableStateOf(false) }
    var isProcessingVoice by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val mcpManager = remember { createMCPServerManager() }
    
    LaunchedEffect(Unit) {
        projectRepository.loadLastProject()
        coroutineScope.launch {
            try {
                mcpManager.initializeServers(mcpServerRepository)
                val tools = mcpManager.getAvailableTools()
                println("✓ MCP серверы автоматически подключены при запуске. Доступно инструментов: ${tools.size}")
                
                val servers = mcpServerRepository.getAllServers()
                val hasGitHubServer = servers.any { 
                    it.id == com.qualiorstudio.aiadventultimate.repository.DEFAULT_GITHUB_MCP_SERVER_ID && it.enabled 
                }
                
                if (hasGitHubServer && currentProject != null) {
                    viewModel.checkGitHubConnection()
                }
            } catch (e: Exception) {
                println("Ошибка автоматического подключения MCP серверов: ${e.message}")
                e.printStackTrace()
            }
        }
    }
    
    LaunchedEffect(currentProject) {
        if (currentProject != null) {
            coroutineScope.launch {
                try {
                    val servers = mcpServerRepository.getAllServers()
                    val hasGitHubServer = servers.any { 
                        it.id == com.qualiorstudio.aiadventultimate.repository.DEFAULT_GITHUB_MCP_SERVER_ID && it.enabled 
                    }
                    
                    if (hasGitHubServer) {
                        viewModel.checkGitHubConnection()
                    }
                } catch (e: Exception) {
                    println("Ошибка при проверке GitHub подключения: ${e.message}")
                }
            }
        }
    }
    
    LaunchedEffect(currentScreen) {
        if (currentScreen != "chat" && isDesktopPlatform) {
            showSidePanel = false
        }
    }
    
    LaunchedEffect(settings.deepSeekApiKey) {
        agentViewModel.setDeepSeekApiKey(settings.deepSeekApiKey)
    }
    
    // Синхронизируем настройки с ChatViewModel
    LaunchedEffect(useRAG) {
        viewModel.setUseRAG(useRAG)
    }
    
    LaunchedEffect(settings.enableVoiceOutput) {
        viewModel.setEnableVoiceOutput(settings.enableVoiceOutput)
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "AiAdvent Ultimate",
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (githubBranchInfo?.isGitHubRepo == true && currentBranch != null) {
                                var expanded by remember { mutableStateOf(false) }
                                Box {
                                    Surface(
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        shape = MaterialTheme.shapes.small,
                                        modifier = Modifier.clickable { expanded = true }
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Text(
                                                text = currentBranch,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                            Icon(
                                                imageVector = Icons.Default.ArrowDropDown,
                                                contentDescription = "Выбрать ветку",
                                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                    
                                    DropdownMenu(
                                        expanded = expanded,
                                        onDismissRequest = { expanded = false }
                                    ) {
                                        BranchDropdownMenuContent(
                                            onBranchSelected = { branch ->
                                                expanded = false
                                            },
                                            viewModel = viewModel
                                        )
                                    }
                                }
                            }
                        }
                        Text(
                            text = when {
                                showProjectScreen -> "Управление проектом"
                                showChatList -> "Список чатов"
                                currentScreen == "chat" -> when {
                                    selectedAgents.isEmpty() -> "AI чат"
                                    selectedAgents.size == 1 -> selectedAgents.first().name
                                    else -> "${selectedAgents.size} агента"
                                }
                                currentScreen == "embeddings" -> "Индекс эмбеддингов"
                                currentScreen == "settings" -> "Настройки"
                                currentScreen == "agents" -> "Агенты"
                                currentScreen == "connections" -> "Связи между агентами"
                                else -> "AI чат"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    when {
                        showProjectScreen -> {
                            IconButton(onClick = { showProjectScreen = false }) {
                                Icon(
                                    imageVector = Icons.Default.ArrowBack,
                                    contentDescription = "Назад"
                                )
                            }
                        }
                        currentScreen == "settings" || currentScreen == "agents" || currentScreen == "connections" -> {
                            IconButton(onClick = { 
                                currentScreen = when (currentScreen) {
                                    "connections" -> "agents"
                                    else -> "chat"
                                }
                            }) {
                                Icon(
                                    imageVector = Icons.Default.ArrowBack,
                                    contentDescription = "Назад"
                                )
                            }
                        }
                        showChatList -> {
                            IconButton(onClick = { showChatList = false }) {
                                Icon(
                                    imageVector = Icons.Default.ArrowBack,
                                    contentDescription = "Назад"
                                )
                            }
                        }
                        else -> {
                            Row {
                                IconButton(onClick = { showChatList = true }) {
                                    Icon(
                                        imageVector = Icons.Default.Chat,
                                        contentDescription = "Список чатов"
                                    )
                                }
                                if (isDesktopPlatform && currentScreen == "chat") {
                                    IconButton(onClick = { showSidePanel = !showSidePanel }) {
                                        Icon(
                                            imageVector = Icons.Default.Person,
                                            contentDescription = "Агенты",
                                            tint = if (showSidePanel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                } else {
                                    IconButton(onClick = { currentScreen = "agents" }) {
                                        Icon(
                                            imageVector = Icons.Default.Person,
                                            contentDescription = "Агенты"
                                        )
                                    }
                                }
                                IconButton(onClick = { currentScreen = "settings" }) {
                                    Icon(
                                        imageVector = Icons.Default.Settings,
                                        contentDescription = "Настройки"
                                    )
                                }
                            }
                        }
                    }
                },
                actions = {
                    when {
                        currentScreen == "chat" && !showChatList -> {
                            if (selectedAgents.isNotEmpty()) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = "Координатор",
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                        Switch(
                                            checked = useCoordinator,
                                            onCheckedChange = { viewModel.setUseCoordinator(it) }
                                        )
                                    }
                                    TextButton(
                                        onClick = { viewModel.clearSelectedAgents() }
                                    ) {
                                        Text("Очистить", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                            IconButton(onClick = { viewModel.clearChat() }) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Очистить чат"
                                )
                            }
                            IconButton(
                                onClick = { currentScreen = "embeddings" }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Storage,
                                    contentDescription = "Эмбеддинги"
                                )
                            }
                        }
                        currentScreen == "embeddings" -> {
                            IconButton(
                                onClick = { currentScreen = "chat" }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Tune,
                                    contentDescription = "Чат"
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Row(
                modifier = Modifier.fillMaxSize()
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(horizontal = 32.dp, vertical = 24.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .widthIn(max = 1100.dp)
                            .align(Alignment.Center)
                    ) {
                        when {
                            showProjectScreen -> {
                                ProjectScreen(
                                    currentProject = currentProject,
                                    onOpenProject = { path ->
                                        viewModel.openProject(path)
                                        showProjectScreen = false
                                    },
                                    onCloseProject = {
                                        viewModel.closeProject()
                                        showProjectScreen = false
                                    }
                                )
                            }
                            showChatList -> {
                                ChatListScreen(
                                    chatRepository = chatRepository,
                                    onChatSelected = { chatId ->
                                        viewModel.loadChat(chatId)
                                        showChatList = false
                                    },
                                    onCreateNewChat = {
                                        viewModel.createNewChat()
                                        showChatList = false
                                    }
                                )
                            }
                            currentScreen == "embeddings" -> {
                                EmbeddingScreenContent(
                                    viewModel = embeddingViewModel,
                                    onFilesSelected = { filePaths ->
                                        if (filePaths.isNotEmpty()) {
                                            coroutineScope.launch {
                                                try {
                                                    embeddingViewModel.processHtmlFiles(filePaths)
                                                } catch (e: Exception) {
                                                    println("Failed to process files: ${e.message}")
                                                }
                                            }
                                        }
                                    }
                                )
                            }
                            currentScreen == "settings" -> {
                                SettingsScreen(
                                    viewModel = settingsViewModel,
                                    onBack = { currentScreen = "chat" }
                                )
                            }
                            currentScreen == "agents" -> {
                                AgentScreen(
                                    agentViewModel = agentViewModel,
                                    selectedAgents = selectedAgents,
                                    useCoordinator = useCoordinator,
                                    onBack = { currentScreen = "chat" },
                                    onAgentsSelected = { agents ->
                                        viewModel.setSelectedAgents(agents)
                                    },
                                    onUseCoordinatorChange = { enabled ->
                                        viewModel.setUseCoordinator(enabled)
                                    },
                                    onShowConnections = { currentScreen = "connections" }
                                )
                            }
                            currentScreen == "connections" -> {
                                AgentConnectionScreen(
                                    agents = agentViewModel.agents.value,
                                    connectionRepository = connectionRepository,
                                    onBack = { currentScreen = "agents" }
                                )
                            }
                            else -> {
                                ChatContent(
                                    messages = messages,
                                    isLoading = isLoading,
                                    messageText = messageText,
                                    onMessageTextChange = { messageText = it },
                                    listState = listState,
                                    voiceService = voiceService,
                                    isRecording = isRecording,
                                    isProcessingVoice = isProcessingVoice,
                                    onIsRecordingChange = { isRecording = it },
                                    onIsProcessingVoiceChange = { isProcessingVoice = it },
                                    onSendMessage = {
                                        val currentText = messageText
                                        viewModel.sendMessage(currentText)
                                        messageText = ""
                                    },
                                    onSendMessageWithText = { text ->
                                        viewModel.sendMessage(text)
                                        messageText = ""
                                    },
                                    coroutineScope = coroutineScope,
                                    enableVoiceInput = settings.enableVoiceInput,
                                    currentProject = currentProject,
                                    currentBranch = currentBranch,
                                    onProjectClick = { showProjectScreen = true }
                                )
                            }
                        }
                    }
                }
                
                if (isDesktopPlatform && currentScreen == "chat" && !showChatList) {
                    DesktopSidePanel(
                        isOpen = showSidePanel,
                        onClose = { showSidePanel = false },
                        agentViewModel = agentViewModel,
                        selectedAgents = selectedAgents,
                        onAgentsSelected = { agents ->
                            viewModel.setSelectedAgents(agents)
                        },
                        connectionRepository = connectionRepository,
                        mcpServerRepository = mcpServerRepository,
                        mcpServerService = mcpServerService
                    )
                }
            }
        }
    }
}

@Composable
fun ChatContent(
    messages: List<ChatMessage>,
    isLoading: Boolean,
    messageText: String,
    onMessageTextChange: (String) -> Unit,
    listState: LazyListState,
    voiceService: com.qualiorstudio.aiadventultimate.voice.VoiceInputService,
    isRecording: Boolean,
    isProcessingVoice: Boolean,
    onIsRecordingChange: (Boolean) -> Unit,
    onIsProcessingVoiceChange: (Boolean) -> Unit,
    onSendMessage: () -> Unit,
    onSendMessageWithText: (String) -> Unit,
    coroutineScope: CoroutineScope,
    enableVoiceInput: Boolean = true,
    currentProject: com.qualiorstudio.aiadventultimate.model.Project? = null,
    currentBranch: String? = null,
    onProjectClick: () -> Unit = {}
) {
    val isCommandMode = messageText.startsWith("/")
    val commandQuery = if (isCommandMode) {
        messageText.substring(1).trim()
    } else {
        ""
    }
    
    val filteredCommands = if (isCommandMode && commandQuery.isNotEmpty()) {
        Commands.filterByQuery(commandQuery)
    } else if (isCommandMode) {
        Commands.ALL
    } else {
        emptyList()
    }
    
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
                ProjectBar(
                    project = currentProject,
                    currentBranch = currentBranch,
                    onClick = onProjectClick
                )
                
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    state = listState
                ) {
                    items(messages) { message ->
                        ChatMessageItem(message)
                    }

                    if (isLoading) {
                        item {
                            TypingIndicator()
                        }
                    }
                }

                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isCommandMode && filteredCommands.isNotEmpty()) {
                        CommandMenu(
                            commands = filteredCommands,
                            onCommandSelected = { command ->
                                val currentText = messageText
                                val afterSlash = if (currentText.startsWith("/")) {
                                    currentText.substring(1).trim()
                                } else {
                                    currentText
                                }
                                val remainingText = if (afterSlash.startsWith(command.name)) {
                                    afterSlash.substring(command.name.length).trim()
                                } else {
                                    afterSlash
                                }
                                onMessageTextChange("/${command.name}${if (remainingText.isNotEmpty()) " $remainingText" else ""}")
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextField(
                            value = messageText,
                            onValueChange = onMessageTextChange,
                            modifier = Modifier.weight(1f),
                            placeholder = { 
                                Text(
                                    when {
                                        isRecording -> "Идет запись..."
                                        isProcessingVoice -> "Обработка голоса..."
                                        else -> "Введите сообщение..."
                                    }
                                )
                            },
                            enabled = !isLoading && !isRecording && !isProcessingVoice,
                            shape = RoundedCornerShape(24.dp),
                            colors = TextFieldDefaults.colors(
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                disabledIndicatorColor = Color.Transparent
                            )
                        )

                    Spacer(modifier = Modifier.width(8.dp))

                    if (enableVoiceInput && voiceService.isSupported() && !isLoading) {
                        when {
                            isRecording -> {
                                FloatingActionButton(
                                    onClick = {
                                        coroutineScope.launch {
                                            onIsRecordingChange(false)
                                            onIsProcessingVoiceChange(true)
                                            val result = voiceService.stopRecording()
                                            result.onSuccess { text ->
                                                if (text.isNotBlank()) {
                                                    onSendMessageWithText(text)
                                                }
                                                onIsProcessingVoiceChange(false)
                                            }
                                            result.onFailure { error ->
                                                println("Voice input error: ${error.message}")
                                                onIsProcessingVoiceChange(false)
                                            }
                                        }
                                    },
                                    containerColor = MaterialTheme.colorScheme.error
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Mic,
                                        contentDescription = "Остановить запись"
                                    )
                                }
                            }
                            isProcessingVoice -> {
                                ProcessingVoiceButton()
                            }
                            else -> {
                                IconButton(
                                    onClick = {
                                        coroutineScope.launch {
                                            try {
                                                voiceService.startRecording()
                                                onIsRecordingChange(true)
                                            } catch (e: Exception) {
                                                println("Failed to start recording: ${e.message}")
                                            }
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Mic,
                                        contentDescription = "Голосовой ввод",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.width(8.dp))
                    }

                    if (!isLoading && messageText.isNotBlank()) {
                        FloatingActionButton(
                            onClick = {
                                onSendMessage()
                            }
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Отправить"
                            )
                        }
                    } else {
                        FloatingActionButton(
                            onClick = { }
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Отправить",
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                            )
                        }
                    }
                }
            }
        }
    }

@Composable
fun CommandMenu(
    commands: List<Command>,
    onCommandSelected: (Command) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .widthIn(max = 400.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(8.dp)
        ) {
            Text(
                text = "Команды",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
            commands.forEach { command ->
                CommandMenuItem(
                    command = command,
                    onClick = { onCommandSelected(command) }
                )
            }
        }
    }
}

@Composable
fun CommandMenuItem(
    command: Command,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = command.fullCommand,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = command.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            command.example?.let { example ->
                Text(
                    text = "Пример: $example",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}

@Composable
fun ChatMessageItem(message: ChatMessage) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp, horizontal = 8.dp)
    ) {
        val maxWidth = maxWidth * 0.75f
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = if (message.isUser) Arrangement.End else Arrangement.Start
        ) {
            Column(
                horizontalAlignment = if (message.isUser) Alignment.End else Alignment.Start,
                modifier = Modifier.widthIn(max = maxWidth)
            ) {
                if (!message.isUser && message.agentName != null) {
                    Text(
                        text = message.agentName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp, start = 4.dp)
                    )
                }
                Card(
                    modifier = Modifier.wrapContentWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (message.isUser)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.secondaryContainer
                    ),
                    shape = RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomStart = if (message.isUser) 16.dp else 4.dp,
                        bottomEnd = if (message.isUser) 4.dp else 16.dp
                    )
                ) {
                    CompositionLocalProvider(
                        LocalContentColor provides if (message.isUser)
                            MaterialTheme.colorScheme.onPrimary
                        else
                            MaterialTheme.colorScheme.onSecondaryContainer
                    ) {
                        Markdown(
                            content = message.text,
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                }

                if (!message.isUser && message.variants.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    message.variants.forEachIndexed { index, variant ->
                        ResponseVariantCard(variant = variant)
                        if (index != message.variants.lastIndex) {
                            Spacer(modifier = Modifier.height(6.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ResponseVariantCard(variant: ChatResponseVariant) {
    var expanded by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        colors = CardDefaults.cardColors(
            containerColor = if (variant.isPreferred)
                MaterialTheme.colorScheme.surfaceVariant
            else
                MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = variant.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    variant.metadata?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (expanded) {
                Spacer(modifier = Modifier.height(8.dp))
                Markdown(
                    content = variant.body,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
fun TypingIndicator() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.Start
    ) {
        Card(
            modifier = Modifier.widthIn(max = 100.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            ),
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = 4.dp,
                bottomEnd = 16.dp
            )
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                repeat(3) { index ->
                    TypingDot(delay = index * 150)
                }
            }
        }
    }
}

@Composable
fun ProcessingVoiceButton() {
    val infiniteTransition = rememberInfiniteTransition()
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        )
    )
    
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.7f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    FloatingActionButton(
        onClick = {},
        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = alpha),
        modifier = Modifier
    ) {
        Icon(
            imageVector = Icons.Default.Mic,
            contentDescription = "Обработка голоса",
            modifier = Modifier.size((24 * scale).dp),
            tint = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}

@Composable
fun TypingDot(delay: Int) {
    val infiniteTransition = rememberInfiniteTransition()
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, delayMillis = delay),
            repeatMode = RepeatMode.Reverse
        )
    )

    Box(
        modifier = Modifier
            .size(8.dp)
            .background(
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = scale),
                shape = RoundedCornerShape(50)
            )
    )
}

@Composable
fun BranchDropdownMenuContent(
    onBranchSelected: (String) -> Unit,
    viewModel: com.qualiorstudio.aiadventultimate.viewmodel.ChatViewModel
) {
    var branches by remember { mutableStateOf<com.qualiorstudio.aiadventultimate.service.BranchList?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    
    LaunchedEffect(Unit) {
        if (branches == null) {
            isLoading = true
            coroutineScope.launch {
                try {
                    branches = viewModel.getBranches()
                } catch (e: Exception) {
                    println("Ошибка при получении списка веток: ${e.message}")
                } finally {
                    isLoading = false
                }
            }
        }
    }
    
    if (isLoading) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp))
        }
    } else if (branches == null) {
        DropdownMenuItem(
            text = { Text("Не удалось загрузить список веток") },
            onClick = { }
        )
    } else {
        if (branches!!.localBranches.isNotEmpty()) {
            DropdownMenuItem(
                text = {
                    Text(
                        text = "Локальные ветки",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                },
                onClick = { },
                enabled = false
            )
            Divider()
            branches!!.localBranches.forEach { branch ->
                DropdownMenuItem(
                    text = { Text(branch) },
                    onClick = { onBranchSelected(branch) }
                )
            }
        }
        
        if (branches!!.remoteBranches.isNotEmpty()) {
            if (branches!!.localBranches.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
            }
            DropdownMenuItem(
                text = {
                    Text(
                        text = "Удаленные ветки",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                },
                onClick = { },
                enabled = false
            )
            Divider()
            branches!!.remoteBranches.forEach { branch ->
                DropdownMenuItem(
                    text = { Text(branch) },
                    onClick = { onBranchSelected(branch) }
                )
            }
        }
        
        if (branches!!.localBranches.isEmpty() && branches!!.remoteBranches.isEmpty()) {
            DropdownMenuItem(
                text = { Text("Ветки не найдены") },
                onClick = { }
            )
        }
    }
}