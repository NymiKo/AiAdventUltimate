package com.qualiorstudio.aiadventultimate.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.time.ZoneId
import java.util.Locale

@Composable
actual fun JsonFilePickerButton(
    onFileSelected: (String?) -> Unit,
    enabled: Boolean
) {
    Button(
        onClick = {
            val fileChooser = JFileChooser(System.getProperty("user.home"))
            fileChooser.fileFilter = FileNameExtensionFilter(
                "JSON файлы (*.json)",
                "json"
            )
            fileChooser.dialogTitle = "Выберите JSON файл"
            fileChooser.isMultiSelectionEnabled = false
            
            val result = fileChooser.showOpenDialog(null)
            if (result == JFileChooser.APPROVE_OPTION) {
                val selectedFile = fileChooser.selectedFile
                onFileSelected(selectedFile.absolutePath)
            } else {
                onFileSelected(null)
            }
        },
        enabled = enabled,
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(
            imageVector = Icons.Default.UploadFile,
            contentDescription = null,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text("Загрузить JSON файл")
    }
}

actual fun formatDateString(dateString: String): String {
    return try {
        val cleanedDate = dateString.trim()
        val instant = try {
            Instant.parse(cleanedDate)
        } catch (e: Exception) {
            try {
                val formatter = DateTimeFormatter.ISO_DATE_TIME
                formatter.parse(cleanedDate, Instant::from)
            } catch (e2: Exception) {
                return dateString
            }
        }
        val formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm", Locale.getDefault())
            .withZone(ZoneId.systemDefault())
        formatter.format(instant)
    } catch (e: Exception) {
        dateString
    }
}
