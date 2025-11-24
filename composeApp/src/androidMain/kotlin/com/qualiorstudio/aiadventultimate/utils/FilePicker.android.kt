package com.qualiorstudio.aiadventultimate.utils

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

actual class FilePicker {
    private var selectedFileUri: Uri? = null
    
    actual suspend fun pickFile(): String? {
        return selectedFileUri?.let { uri ->
            val context = LocalContext.current
            val inputStream = context.contentResolver.openInputStream(uri)
            val tempFile = File(context.cacheDir, "temp_${System.currentTimeMillis()}.html")
            val outputStream = FileOutputStream(tempFile)
            
            inputStream?.use { input ->
                outputStream.use { output ->
                    input.copyTo(output)
                }
            }
            
            tempFile.absolutePath
        }
    }
    
    fun setSelectedUri(uri: Uri?) {
        selectedFileUri = uri
    }
}

@Composable
fun rememberFilePickerLauncher(
    onFileSelected: (String?) -> Unit
): androidx.activity.result.ActivityResultLauncher<Intent> {
    val context = LocalContext.current
    val filePicker = FilePicker()
    
    return rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uri = result.data?.data
        filePicker.setSelectedUri(uri)
        onFileSelected(filePicker.pickFile())
    }
}

