package com.qualiorstudio.aiadventultimate.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.content.Intent
import android.net.Uri
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun FilePickerButton(
    onFilesSelected: (List<String>) -> Unit,
    enabled: Boolean
) {
    val context = LocalContext.current
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uris = result.data?.clipData?.let { clipData ->
            (0 until clipData.itemCount).mapNotNull { index ->
                clipData.getItemAt(index).uri
            }
        } ?: result.data?.data?.let { listOf(it) } ?: emptyList()
        
        val filePaths = uris.mapNotNull { uri ->
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val mimeType = context.contentResolver.getType(uri) ?: ""
                val extension = when {
                    mimeType.contains("pdf") -> "pdf"
                    mimeType.contains("html") -> "html"
                    else -> {
                        val fileName = uri.toString().substringAfterLast("/")
                        when {
                            fileName.endsWith(".pdf", ignoreCase = true) -> "pdf"
                            fileName.endsWith(".html", ignoreCase = true) -> "html"
                            fileName.endsWith(".htm", ignoreCase = true) -> "html"
                            else -> "html"
                        }
                    }
                }
                val tempFile = File(context.cacheDir, "temp_${System.currentTimeMillis()}_${uris.indexOf(uri)}.$extension")
                val outputStream = FileOutputStream(tempFile)
                
                inputStream?.use { input ->
                    outputStream.use { output ->
                        input.copyTo(output)
                    }
                }
                
                tempFile.absolutePath
            } catch (e: Exception) {
                null
            }
        }
        
        onFilesSelected(filePaths)
    }
    
    Button(
        onClick = {
            val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "*/*"
                putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("text/html", "application/pdf"))
                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
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
        Text("Загрузить HTML/PDF файлы")
    }
}

