package com.qualiorstudio.aiadventultimate.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.qualiorstudio.aiadventultimate.model.Chat
import com.qualiorstudio.aiadventultimate.repository.ChatRepository
import com.qualiorstudio.aiadventultimate.repository.ChatRepositoryImpl
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ChatListScreen(
    chatRepository: ChatRepository = ChatRepositoryImpl(),
    onChatSelected: (String) -> Unit,
    onCreateNewChat: () -> Unit
) {
    val chats by chatRepository.observeAllChats().collectAsState(initial = emptyList())
    val coroutineScope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(true) }
    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedChatIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    
    LaunchedEffect(Unit) {
        coroutineScope.launch {
            chatRepository.reloadChats()
            isLoading = false
        }
    }
    
    LaunchedEffect(isSelectionMode) {
        if (!isSelectionMode) {
            selectedChatIds = emptySet()
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
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = if (isSelectionMode && selectedChatIds.isNotEmpty()) {
                        "Выбрано: ${selectedChatIds.size}"
                    } else {
                        "Чаты"
                    },
                    style = MaterialTheme.typography.headlineMedium
                )
                if (isSelectionMode && selectedChatIds.isNotEmpty()) {
                    IconButton(
                        onClick = { showDeleteDialog = true }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Удалить выбранные",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isSelectionMode) {
                    TextButton(
                        onClick = { isSelectionMode = false }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Отмена")
                    }
                } else {
                    if (!isLoading && chats.isNotEmpty()) {
                        FloatingActionButton(
                            onClick = onCreateNewChat,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Новый чат"
                            )
                        }
                    }
                    if (chats.isNotEmpty()) {
                        IconButton(
                            onClick = { isSelectionMode = true }
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckBoxOutlineBlank,
                                contentDescription = "Выбрать чаты"
                            )
                        }
                    }
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
        } else if (chats.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Нет сохраненных чатов",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(onClick = onCreateNewChat) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Создать новый чат")
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(chats) { chat ->
                    ChatListItem(
                        chat = chat,
                        isSelected = selectedChatIds.contains(chat.id),
                        isSelectionMode = isSelectionMode,
                        onChatClick = {
                            if (isSelectionMode) {
                                selectedChatIds = if (selectedChatIds.contains(chat.id)) {
                                    selectedChatIds - chat.id
                                } else {
                                    selectedChatIds + chat.id
                                }
                            } else {
                                onChatSelected(chat.id)
                            }
                        },
                        onDeleteClick = {
                            coroutineScope.launch {
                                chatRepository.deleteChat(chat.id)
                            }
                        }
                    )
                }
            }
        }
    }
    
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { 
                Text(
                    if (selectedChatIds.size == 1) "Удалить чат?" else "Удалить чаты?"
                ) 
            },
            text = { 
                Text(
                    if (selectedChatIds.size == 1) {
                        "Вы уверены, что хотите удалить этот чат?"
                    } else {
                        "Вы уверены, что хотите удалить ${selectedChatIds.size} чатов?"
                    }
                ) 
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        coroutineScope.launch {
                            selectedChatIds.forEach { chatId ->
                                chatRepository.deleteChat(chatId)
                            }
                            selectedChatIds = emptySet()
                            isSelectionMode = false
                        }
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
fun ChatListItem(
    chat: Chat,
    isSelected: Boolean = false,
    isSelectionMode: Boolean = false,
    onChatClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onChatClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isSelectionMode) {
                Icon(
                    imageVector = if (isSelected) {
                        Icons.Default.CheckBox
                    } else {
                        Icons.Default.CheckBoxOutlineBlank
                    },
                    contentDescription = if (isSelected) "Выбрано" else "Не выбрано",
                    tint = if (isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
            }
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = chat.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                val previewText = chat.messages.lastOrNull()?.text ?: ""
                Text(
                    text = previewText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = formatDate(chat.updatedAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
            if (!isSelectionMode) {
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
    
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Удалить чат?") },
            text = { Text("Вы уверены, что хотите удалить этот чат?") },
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

private fun formatDate(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    
    return when {
        diff < 60_000 -> "Только что"
        diff < 3_600_000 -> "${diff / 60_000} мин назад"
        diff < 86_400_000 -> "${diff / 3_600_000} ч назад"
        diff < 604_800_000 -> "${diff / 86_400_000} дн назад"
        else -> {
            val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
            dateFormat.format(Date(timestamp))
        }
    }
}

