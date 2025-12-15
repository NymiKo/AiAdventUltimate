package com.qualiorstudio.aiadventultimate.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.qualiorstudio.aiadventultimate.viewmodel.PersonalizationViewModel

@Composable
fun PersonalizationTabContent(
    viewModel: PersonalizationViewModel = viewModel { PersonalizationViewModel() }
) {
    val personalization by viewModel.personalization.collectAsState()
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Персонализация",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        Text(
            text = "Настройте информацию о себе, чтобы агент мог лучше понимать ваши предпочтения и привычки",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        PersonalizationSection(
            title = "Основная информация",
            icon = Icons.Default.Person
        ) {
            PersonalizationTextField(
                label = "Имя",
                value = personalization.name,
                onValueChange = { viewModel.setName(it) },
                placeholder = "Введите ваше имя",
                modifier = Modifier.fillMaxWidth()
            )
        }
        
        PersonalizationSection(
            title = "Предпочтения",
            icon = Icons.Default.Favorite
        ) {
            PersonalizationTextField(
                label = "Предпочтения",
                value = personalization.preferences,
                onValueChange = { viewModel.setPreferences(it) },
                placeholder = "Опишите ваши предпочтения (например, любимые инструменты, стиль работы и т.д.)",
                modifier = Modifier.fillMaxWidth(),
                maxLines = 5
            )
        }
        
        PersonalizationSection(
            title = "Привычки",
            icon = Icons.Default.Schedule
        ) {
            PersonalizationTextField(
                label = "Привычки",
                value = personalization.habits,
                onValueChange = { viewModel.setHabits(it) },
                placeholder = "Опишите ваши рабочие привычки (например, время работы, предпочитаемые методы организации и т.д.)",
                modifier = Modifier.fillMaxWidth(),
                maxLines = 5
            )
        }
        
        PersonalizationSection(
            title = "Стиль работы",
            icon = Icons.Default.Work
        ) {
            PersonalizationTextField(
                label = "Стиль работы",
                value = personalization.workStyle,
                onValueChange = { viewModel.setWorkStyle(it) },
                placeholder = "Опишите ваш стиль работы (например, предпочитаете ли вы детальные инструкции или краткие задачи)",
                modifier = Modifier.fillMaxWidth(),
                maxLines = 5
            )
        }
        
        PersonalizationSection(
            title = "Стиль общения",
            icon = Icons.Default.Chat
        ) {
            PersonalizationTextField(
                label = "Стиль общения",
                value = personalization.communicationStyle,
                onValueChange = { viewModel.setCommunicationStyle(it) },
                placeholder = "Опишите предпочитаемый стиль общения (формальный, неформальный, краткий, подробный и т.д.)",
                modifier = Modifier.fillMaxWidth(),
                maxLines = 5
            )
        }
        
        PersonalizationSection(
            title = "Интересы",
            icon = Icons.Default.Star
        ) {
            PersonalizationTextField(
                label = "Интересы",
                value = personalization.interests,
                onValueChange = { viewModel.setInterests(it) },
                placeholder = "Опишите ваши интересы и увлечения",
                modifier = Modifier.fillMaxWidth(),
                maxLines = 5
            )
        }
        
        PersonalizationSection(
            title = "Цели",
            icon = Icons.Default.Flag
        ) {
            PersonalizationTextField(
                label = "Цели",
                value = personalization.goals,
                onValueChange = { viewModel.setGoals(it) },
                placeholder = "Опишите ваши цели и задачи",
                modifier = Modifier.fillMaxWidth(),
                maxLines = 5
            )
        }
        
        PersonalizationSection(
            title = "Дополнительная информация",
            icon = Icons.Default.Info
        ) {
            PersonalizationTextField(
                label = "Дополнительная информация",
                value = personalization.additionalInfo,
                onValueChange = { viewModel.setAdditionalInfo(it) },
                placeholder = "Любая другая информация, которая может помочь агенту лучше вас понять",
                modifier = Modifier.fillMaxWidth(),
                maxLines = 5
            )
        }
    }
}

@Composable
fun PersonalizationSection(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
fun PersonalizationTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    maxLines: Int = 1
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = {
                Text(
                    text = placeholder,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            },
            modifier = Modifier.fillMaxWidth(),
            maxLines = maxLines,
            shape = RoundedCornerShape(8.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline
            )
        )
    }
}

