package com.qualiorstudio.aiadventultimate

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Delete
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(viewModel: ChatViewModel = viewModel { ChatViewModel() }) {
    val messages by viewModel.messages.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    var messageText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI Чат-бот") },
                actions = {
                    IconButton(onClick = { viewModel.clearChat() }) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Очистить чат"
                        )
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
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
                    onValueChange = { messageText = it },
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

                Spacer(modifier = Modifier.width(8.dp))

                if (!isLoading && messageText.isNotBlank()) {
                    FloatingActionButton(
                        onClick = {
                            viewModel.sendMessage(messageText)
                            messageText = ""
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
            Column(
                modifier = Modifier.padding(12.dp)
            ) {
                if (!message.isUser && message.temperature != null) {
                    Text(
                        text = "Температура: ${message.temperature}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f),
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
                Text(
                    text = message.text,
                    color = if (message.isUser)
                        MaterialTheme.colorScheme.onPrimary
                    else
                        MaterialTheme.colorScheme.onSecondaryContainer
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