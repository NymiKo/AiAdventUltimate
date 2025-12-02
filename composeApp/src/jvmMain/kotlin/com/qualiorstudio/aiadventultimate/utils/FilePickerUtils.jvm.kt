package com.qualiorstudio.aiadventultimate.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.swing.JFileChooser
import javax.swing.SwingUtilities
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

actual suspend fun pickDirectory(): String? = withContext(Dispatchers.IO) {
    suspendCoroutine { continuation ->
        SwingUtilities.invokeLater {
            val fileChooser = JFileChooser().apply {
                fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
                dialogTitle = "Выберите папку проекта"
            }
            
            val result = fileChooser.showOpenDialog(null)
            
            if (result == JFileChooser.APPROVE_OPTION) {
                continuation.resume(fileChooser.selectedFile.absolutePath)
            } else {
                continuation.resume(null)
            }
        }
    }
}

