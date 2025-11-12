package com.qualiorstudio.aiadventultimate

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.MenuOpen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.qualiorstudio.aiadventultimate.model.ChatMessage
import com.qualiorstudio.aiadventultimate.viewmodel.ChatViewModel
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
@Preview
fun App() {
    MaterialTheme {
        ChatScreen()
    }
}

@Composable
fun ChatScreen(viewModel: ChatViewModel = viewModel { ChatViewModel() }) {
    val messages by viewModel.messages.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    var messageText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    var selectedMode by remember { mutableStateOf(WorkspaceMode.Chat) }
    var isAgentPanelVisible by remember { mutableStateOf(false) }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    LaunchedEffect(selectedMode) {
        isAgentPanelVisible = false
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF111216))
    ) {
        Sidebar(
            selectedMode = selectedMode,
            onModeSelect = { selectedMode = it },
            onClearChat = { viewModel.clearChat() }
        )
        VerticalDivider(color = Color(0xFF1E2026))
        MainWorkspace(
            modifier = Modifier.weight(1f),
            selectedMode = selectedMode,
            messages = messages,
            isLoading = isLoading,
            messageText = messageText,
            listState = listState,
            onMessageChange = { messageText = it },
            onSendMessage = {
                viewModel.sendMessage(messageText)
                messageText = ""
            },
            onToggleAgentPanel = { if (selectedMode == WorkspaceMode.Agent) isAgentPanelVisible = !isAgentPanelVisible },
            isAgentPanelVisible = isAgentPanelVisible
        )
        if (selectedMode == WorkspaceMode.Agent && isAgentPanelVisible) {
            VerticalDivider(color = Color(0xFF1E2026))
            AgentSelectionPanel(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(320.dp)
            )
        }
    }
}

enum class WorkspaceMode {
    Chat,
    Agent
}

private data class SidebarConversation(
    val title: String,
    val time: String,
    val description: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Sidebar(
    selectedMode: WorkspaceMode,
    onModeSelect: (WorkspaceMode) -> Unit,
    onClearChat: () -> Unit
) {
    val conversations = remember {
        listOf(
            SidebarConversation("Анализ данных", "10:30", "Сегодня"),
            SidebarConversation("Общий вопрос", "09:15", "Новая задача"),
            SidebarConversation("Разработка системы", "Вчера", "Продуктивная сессия"),
            SidebarConversation("Консультация", "Вчера", "Обсуждение стратегии")
        )
    }

    Column(
        modifier = Modifier
            .width(280.dp)
            .fillMaxHeight()
            .background(Color(0xFF14171C))
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = "AI System",
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White
            )
            FilledTonalButton(
                onClick = onClearChat,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("Новый чат")
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = "Режим",
                style = MaterialTheme.typography.titleMedium,
                color = Color(0xFF8D95A6)
            )
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier.fillMaxWidth()
            ) {
                SegmentedButton(
                    shape = SegmentedButtonDefaults.itemShape(0, 2),
                    onClick = { onModeSelect(WorkspaceMode.Chat) },
                    selected = selectedMode == WorkspaceMode.Chat
                ) {
                    Text("Чат")
                }
                SegmentedButton(
                    shape = SegmentedButtonDefaults.itemShape(1, 2),
                    onClick = { onModeSelect(WorkspaceMode.Agent) },
                    selected = selectedMode == WorkspaceMode.Agent
                ) {
                    Text("Агент")
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = "Недавние беседы",
                style = MaterialTheme.typography.titleMedium,
                color = Color(0xFF8D95A6)
            )
            conversations.forEach { item ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color(0xFF191D25),
                    shape = RoundedCornerShape(18.dp),
                    tonalElevation = 2.dp
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White
                        )
                        Text(
                            text = item.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFF9AA2B4)
                        )
                        Text(
                            text = item.time,
                            style = MaterialTheme.typography.labelMedium,
                            color = Color(0xFF5F6677)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Text(
            text = "Настройки",
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFF5F6677)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainWorkspace(
    modifier: Modifier = Modifier,
    selectedMode: WorkspaceMode,
    messages: List<ChatMessage>,
    isLoading: Boolean,
    messageText: String,
    listState: LazyListState,
    onMessageChange: (String) -> Unit,
    onSendMessage: () -> Unit,
    onToggleAgentPanel: () -> Unit,
    isAgentPanelVisible: Boolean
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .background(Color(0xFF0F131A))
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = if (selectedMode == WorkspaceMode.Chat) "AI Чат" else "Мультимодальная система",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White
                )
                Text(
                    text = if (selectedMode == WorkspaceMode.Chat) "Общайтесь с ассистентом и получайте ответы в реальном времени" else "Подключайте агентов и распределяйте задачи между ними",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF8D95A6)
                )
            }
            if (selectedMode == WorkspaceMode.Agent) {
                FilledTonalButton(
                    onClick = onToggleAgentPanel,
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.MenuOpen,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (isAgentPanelVisible) "Скрыть панель" else "Показать панель")
                }
            }
        }

        Surface(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            color = Color(0xFF141820),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
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

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    TextField(
                        value = messageText,
                        onValueChange = onMessageChange,
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Введите сообщение...") },
                        enabled = !isLoading,
                        shape = RoundedCornerShape(24.dp),
                        colors = TextFieldDefaults.colors(
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            disabledIndicatorColor = Color.Transparent
                        )
                    )

                    FilledIconButton(
                        onClick = onSendMessage,
                        enabled = messageText.isNotBlank() && !isLoading,
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = null
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AgentSelectionPanel(modifier: Modifier = Modifier) {
    val agents = remember {
        listOf(
            AgentOption("Исследователь", "Поиск и анализ информации", true),
            AgentOption("Аналитик", "Обработка данных", true),
            AgentOption("Писатель", "Создание контента", false),
            AgentOption("Разработчик", "Написание кода", false)
        )
    }

    Column(
        modifier = modifier
            .background(Color(0xFF14171C))
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Агентная система",
                style = MaterialTheme.typography.titleLarge,
                color = Color.White
            )
            Text(
                text = "Выберите агентов для совместной работы",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF8D95A6)
            )
        }

        agents.forEach { agent ->
            Surface(
                shape = RoundedCornerShape(20.dp),
                tonalElevation = if (agent.isActive) 6.dp else 0.dp,
                color = if (agent.isActive) Color(0xFF1E2230) else Color(0xFF191D25),
                border = if (agent.isActive) BorderStroke(1.dp, Color(0xFF4D8DFF)) else null,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = agent.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White
                    )
                    Text(
                        text = agent.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF9AA2B4)
                    )
                    Text(
                        text = if (agent.isActive) "Активен" else "Не активен",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (agent.isActive) Color(0xFF4D8DFF) else Color(0xFF5F6677)
                    )
                }
            }
        }
    }
}

private data class AgentOption(
    val name: String,
    val description: String,
    val isActive: Boolean
)

@Composable
fun ChatMessageItem(message: ChatMessage) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = if (message.isUser) Arrangement.End else Arrangement.Start
    ) {
        Card(
            modifier = Modifier.widthIn(max = 300.dp),
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
            Text(
                text = message.text,
                modifier = Modifier.padding(12.dp),
                color = if (message.isUser)
                    MaterialTheme.colorScheme.onPrimary
                else
                    MaterialTheme.colorScheme.onSecondaryContainer
            )
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