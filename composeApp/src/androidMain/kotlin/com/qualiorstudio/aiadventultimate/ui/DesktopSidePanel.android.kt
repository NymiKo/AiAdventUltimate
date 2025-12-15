package com.qualiorstudio.aiadventultimate.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.content.Intent
import android.net.Uri
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.time.ZoneId
import java.util.Locale

@Composable
actual fun JsonFilePickerButton(
    onFileSelected: (String?) -> Unit,
    enabled: Boolean
) {
    val context = LocalContext.current
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uri = result.data?.data
        if (uri != null) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val tempFile = File(context.cacheDir, "temp_json_${System.currentTimeMillis()}.json")
                val outputStream = FileOutputStream(tempFile)
                
                inputStream?.use { input ->
                    outputStream.use { output ->
                        input.copyTo(output)
                    }
                }
                
                onFileSelected(tempFile.absolutePath)
            } catch (e: Exception) {
                onFileSelected(null)
            }
        } else {
            onFileSelected(null)
        }
    }
    
    Button(
        onClick = {
            val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "application/json"
                addCategory(Intent.CATEGORY_OPENABLE)
            }
            filePickerLauncher.launch(intent)
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
