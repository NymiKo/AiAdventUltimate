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
    onFileSelected: (String?) -> Unit,
    enabled: Boolean
) {
    Button(
        onClick = {
            val fileChooser = JFileChooser(System.getProperty("user.home"))
            fileChooser.fileFilter = FileNameExtensionFilter("HTML Files", "html", "htm")
            fileChooser.dialogTitle = "Выберите HTML файл"
            
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
        Text("Загрузить HTML файл")
    }
}

