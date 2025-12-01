package com.qualiorstudio.aiadventultimate.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.io.File
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

@Composable
actual fun FilePickerButton(
    onFilesSelected: (List<String>) -> Unit,
    enabled: Boolean
) {
    Button(
        onClick = {
            val fileChooser = JFileChooser(System.getProperty("user.home"))
            fileChooser.fileFilter = FileNameExtensionFilter("HTML и PDF файлы", "html", "htm", "pdf")
            fileChooser.dialogTitle = "Выберите HTML/PDF файлы"
            fileChooser.isMultiSelectionEnabled = true
            
            val result = fileChooser.showOpenDialog(null)
            if (result == JFileChooser.APPROVE_OPTION) {
                val selectedFiles = fileChooser.selectedFiles
                val filePaths = selectedFiles.map { it.absolutePath }
                onFilesSelected(filePaths)
            } else {
                onFilesSelected(emptyList())
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
        Text("Загрузить HTML/PDF файлы")
    }
}

