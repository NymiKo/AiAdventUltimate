package com.qualiorstudio.aiadventultimate.utils

import java.io.File
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

actual class FilePicker {
    private var selectedFile: String? = null
    
    actual suspend fun pickFile(): String? {
        return suspendCancellableCoroutine { continuation ->
            val fileChooser = JFileChooser(System.getProperty("user.home"))
            fileChooser.fileFilter = FileNameExtensionFilter("HTML Files", "html", "htm")
            fileChooser.dialogTitle = "Выберите HTML файл"
            
            val result = fileChooser.showOpenDialog(null)
            if (result == JFileChooser.APPROVE_OPTION) {
                val selectedFile = fileChooser.selectedFile
                continuation.resume(selectedFile.absolutePath)
            } else {
                continuation.resume(null)
            }
        }
    }
}

