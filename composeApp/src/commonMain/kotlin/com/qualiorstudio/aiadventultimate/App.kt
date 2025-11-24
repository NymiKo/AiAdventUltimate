package com.qualiorstudio.aiadventultimate

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.qualiorstudio.aiadventultimate.model.ChatMessage
import com.qualiorstudio.aiadventultimate.ui.EmbeddingScreenContent
import com.qualiorstudio.aiadventultimate.viewmodel.ChatViewModel
import com.qualiorstudio.aiadventultimate.viewmodel.EmbeddingViewModel
import com.qualiorstudio.aiadventultimate.voice.createVoiceInputService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.compose.ui.tooling.preview.Preview
import java.io.File

@Composable
@Preview
fun App() {
    MaterialTheme {
        ChatScreen()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel = viewModel { ChatViewModel() },
    embeddingViewModel: EmbeddingViewModel = viewModel { EmbeddingViewModel() }
) {
    var currentScreen by remember { mutableStateOf("chat") }
    val messages by viewModel.messages.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    var messageText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val voiceService = remember { createVoiceInputService() }
    var isRecording by remember { mutableStateOf(false) }
    var isProcessingVoice by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (currentScreen == "chat") "AI Чат-бот" else "Эмбеддинги") },
                actions = {
                    IconButton(
                        onClick = { currentScreen = if (currentScreen == "chat") "embeddings" else "chat" }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Storage,
                            contentDescription = if (currentScreen == "chat") "Эмбеддинги" else "Чат"
                        )
                    }
                    if (currentScreen == "chat") {
                        IconButton(onClick = { viewModel.clearChat() }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Очистить чат"
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (currentScreen) {
                "embeddings" -> {
                    EmbeddingScreenContent(
                        viewModel = embeddingViewModel,
                        onFileSelected = { filePath ->
                            if (filePath != null && filePath.isNotEmpty()) {
                                coroutineScope.launch {
                                    try {
                                        val file = File(filePath)
                                        val htmlContent = file.readText()
                                        val fileName = file.name
                                        embeddingViewModel.processHtmlFile(htmlContent, fileName)
                                    } catch (e: Exception) {
                                        println("Failed to load file: ${e.message}")
                                    }
                                }
                            }
                        }
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
                        coroutineScope = coroutineScope
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
    coroutineScope: CoroutineScope
) {
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
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

                    if (voiceService.isSupported() && !isLoading) {
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