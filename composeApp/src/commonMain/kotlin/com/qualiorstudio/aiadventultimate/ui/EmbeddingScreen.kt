package com.qualiorstudio.aiadventultimate.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.qualiorstudio.aiadventultimate.viewmodel.EmbeddingViewModel

@Composable
expect fun FilePickerButton(
    onFileSelected: (String?) -> Unit,
    enabled: Boolean
)

@Composable
fun EmbeddingScreenContent(
    viewModel: EmbeddingViewModel = viewModel { EmbeddingViewModel() },
    onFileSelected: (String?) -> Unit
) {
    val progress by viewModel.progress.collectAsState()
    val availableModels by viewModel.availableModels.collectAsState()
    val selectedModel by viewModel.selectedModel.collectAsState()
    val indexData by viewModel.indexData.collectAsState()
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Генерация эмбеддингов",
            style = MaterialTheme.typography.headlineMedium
        )
        
        if (indexData != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Индекс эмбеддингов",
                            style = MaterialTheme.typography.titleMedium
                        )
                        IconButton(
                            onClick = { viewModel.openIndexFile() }
                        ) {
                            Icon(
                                imageVector = Icons.Default.FolderOpen,
                                contentDescription = "Открыть файл"
                            )
                        }
                    }
                    Text(
                        text = "Всего чанков: ${indexData!!.chunks.size}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "Модель: ${indexData!!.model}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                    Text(
                        text = "Путь: ${viewModel.indexFilePathValue}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        }
        
        if (availableModels.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Выберите модель:",
                        style = MaterialTheme.typography.labelLarge
                    )
                    availableModels.forEach { model ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedModel == model,
                                onClick = { viewModel.setSelectedModel(model) }
                            )
                            Text(
                                text = model,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    }
                }
            }
        }
        
        FilePickerButton(
            onFileSelected = { filePath ->
                if (filePath != null) {
                    onFileSelected(filePath)
                }
            },
            enabled = !progress.isProcessing && selectedModel != null
        )
        
        if (progress.isProcessing) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = progress.status,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    
                    if (progress.totalChunks > 0) {
                        LinearProgressIndicator(
                            progress = {
                                if (progress.totalChunks > 0) {
                                    progress.currentChunk.toFloat() / progress.totalChunks.toFloat()
                                } else {
                                    0f
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        
                        Text(
                            text = "${progress.currentChunk} / ${progress.totalChunks} чанков",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        } else if (progress.status.isNotEmpty() && !progress.isProcessing) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Text(
                    text = progress.status,
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

