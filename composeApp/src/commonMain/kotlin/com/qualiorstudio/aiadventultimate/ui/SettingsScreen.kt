package com.qualiorstudio.aiadventultimate.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.qualiorstudio.aiadventultimate.api.LMStudio
import com.qualiorstudio.aiadventultimate.viewmodel.SettingsViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = viewModel { SettingsViewModel() },
    onBack: () -> Unit
) {
    val settings by viewModel.settings.collectAsState()
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        // Заголовок
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Назад"
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Настройки",
                style = MaterialTheme.typography.headlineMedium
            )
        }
        
        // Секция внешнего вида
        SettingsSection(title = "Внешний вид") {
            SettingsSwitchItem(
                title = "Темная тема",
                description = "Использовать темную цветовую схему",
                icon = Icons.Default.Palette,
                checked = settings.darkTheme,
                onCheckedChange = { viewModel.setDarkTheme(it) }
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Секция локальной LLM
        SettingsSection(title = "Локальная LLM") {
            SettingsSwitchItem(
                title = "Использовать локальную LLM",
                description = "Использовать локальную модель из LM Studio вместо облачной",
                icon = Icons.Default.Storage,
                checked = settings.useLocalLLM,
                onCheckedChange = { viewModel.setUseLocalLLM(it) }
            )
            
            if (settings.useLocalLLM) {
                Spacer(modifier = Modifier.height(8.dp))
                LocalLLMModelSelector(
                    baseUrl = settings.lmStudioBaseUrl,
                    selectedModel = settings.localLLMModel,
                    onModelSelected = { viewModel.setLocalLLMModel(it) }
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Секция параметров модели
        SettingsSection(title = "Параметры модели") {
            SettingsSliderItem(
                title = "Temperature",
                description = "Креативность ответов (0.0-2.0)",
                icon = Icons.Default.Tune,
                value = settings.temperature,
                onValueChange = { viewModel.setTemperature(it) },
                valueRange = 0.0f..2.0f,
                steps = 19
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            SettingsNumberFieldItem(
                title = "Max Tokens",
                description = "Максимальное количество токенов в ответе",
                icon = Icons.Default.Tune,
                value = settings.maxTokens,
                onValueChange = { viewModel.setMaxTokens(it) }
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Секция AI
        SettingsSection(title = "Искусственный интеллект") {
            SettingsSwitchItem(
                title = "Использовать RAG",
                description = "Включить поиск по эмбеддингам для улучшения ответов",
                icon = Icons.Default.Storage,
                checked = settings.useRAG,
                onCheckedChange = { viewModel.setUseRAG(it) }
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Секция RAG параметров
        SettingsSection(title = "Параметры RAG") {
            SettingsNumberFieldItem(
                title = "Top K",
                description = "Количество релевантных чанков для поиска",
                icon = Icons.Default.Tune,
                value = settings.ragTopK,
                onValueChange = { viewModel.setRagTopK(it) }
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            SettingsSliderItem(
                title = "Rerank Min Score",
                description = "Минимальный порог для reranker (0.0-1.0)",
                icon = Icons.Default.Tune,
                value = settings.rerankMinScore,
                onValueChange = { viewModel.setRerankMinScore(it) },
                valueRange = 0.0f..1.0f,
                steps = 99
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            SettingsSliderItem(
                title = "Retention Ratio",
                description = "Доля сохраняемых результатов после rerank (0.0-1.0)",
                icon = Icons.Default.Tune,
                value = settings.rerankedRetentionRatio,
                onValueChange = { viewModel.setRerankedRetentionRatio(it) },
                valueRange = 0.0f..1.0f,
                steps = 99
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            SettingsTextFieldItem(
                title = "LM Studio URL",
                description = "Базовый URL для LM Studio API",
                icon = Icons.Default.Storage,
                value = settings.lmStudioBaseUrl,
                onValueChange = { viewModel.setLmStudioBaseUrl(it) },
                keyboardType = KeyboardType.Uri
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            SettingsNumberFieldItem(
                title = "Max Iterations",
                description = "Максимальное количество итераций tool calls",
                icon = Icons.Default.Tune,
                value = settings.maxIterations,
                onValueChange = { viewModel.setMaxIterations(it) }
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Секция голоса
        SettingsSection(title = "Голосовой ввод и вывод") {
            SettingsSwitchItem(
                title = "Голосовой ввод",
                description = "Разрешить запись голосовых сообщений",
                icon = Icons.Default.Mic,
                checked = settings.enableVoiceInput,
                onCheckedChange = { viewModel.setEnableVoiceInput(it) }
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            SettingsSwitchItem(
                title = "Голосовой вывод",
                description = "Озвучивать ответы AI",
                icon = Icons.Default.Settings,
                checked = settings.enableVoiceOutput,
                onCheckedChange = { viewModel.setEnableVoiceOutput(it) }
            )
        }
    }
}

@Composable
fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            content()
        }
    }
}

@Composable
fun SettingsSwitchItem(
    title: String,
    description: String? = null,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (description != null) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
        
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
fun SettingsTextFieldItem(
    title: String,
    description: String? = null,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: String,
    onValueChange: (String) -> Unit,
    keyboardType: KeyboardType = KeyboardType.Text,
    isPassword: Boolean = false
) {
    var passwordVisible by remember { mutableStateOf(false) }
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (description != null) {
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            visualTransformation = if (isPassword && !passwordVisible) PasswordVisualTransformation() else VisualTransformation.None,
            trailingIcon = if (isPassword) {
                {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = if (passwordVisible) "Скрыть пароль" else "Показать пароль",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else null,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline
            )
        )
    }
}

@Composable
fun SettingsSliderItem(
    title: String,
    description: String? = null,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: Double,
    onValueChange: (Double) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int = 0
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = String.format("%.2f", value),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                if (description != null) {
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.toDouble()) },
            valueRange = valueRange,
            steps = steps,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun SettingsNumberFieldItem(
    title: String,
    description: String? = null,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: Int,
    onValueChange: (Int) -> Unit
) {
    var textValue by remember(value) { mutableStateOf(value.toString()) }
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (description != null) {
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        OutlinedTextField(
            value = textValue,
            onValueChange = { newValue ->
                textValue = newValue
                newValue.toIntOrNull()?.let { onValueChange(it) }
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline
            )
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalLLMModelSelector(
    baseUrl: String,
    selectedModel: String?,
    onModelSelected: (String?) -> Unit
) {
    var availableModels by remember { mutableStateOf<List<String>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    
    LaunchedEffect(baseUrl) {
        if (baseUrl.isNotBlank()) {
            isLoading = true
            error = null
            try {
                val lmStudio = LMStudio(baseUrl = baseUrl)
                val models = lmStudio.getAvailableModels()
                availableModels = models
                if (models.isNotEmpty() && selectedModel == null) {
                    onModelSelected(models.first())
                }
                lmStudio.close()
            } catch (e: Exception) {
                error = "Не удалось загрузить модели: ${e.message}"
                availableModels = emptyList()
            } finally {
                isLoading = false
            }
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Storage,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "Модель LLM",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Выберите локальную модель из LM Studio",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        if (isLoading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Загрузка моделей...",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else if (error != null) {
            Text(
                text = error!!,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        } else if (availableModels.isEmpty()) {
            Text(
                text = "Модели не найдены. Убедитесь, что LM Studio запущен и модели загружены.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded }
            ) {
                OutlinedTextField(
                    value = selectedModel ?: "",
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(),
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                    },
                    label = { Text("Выберите модель") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    )
                )
                
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    availableModels.forEach { model ->
                        DropdownMenuItem(
                            text = { Text(model) },
                            onClick = {
                                onModelSelected(model)
                                expanded = false
                            }
                        )
                    }
                    Divider()
                    DropdownMenuItem(
                        text = { Text("Отменить выбор") },
                        onClick = {
                            onModelSelected(null)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

