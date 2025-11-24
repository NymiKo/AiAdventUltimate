package com.qualiorstudio.aiadventultimate.utils

import java.awt.Desktop
import java.io.File

actual fun openFileInSystem(filePath: String) {
    try {
        val file = File(filePath)
        val parentDir = file.parentFile ?: File(System.getProperty("user.home"))
        
        if (!parentDir.exists()) {
            parentDir.mkdirs()
        }
        
        if (Desktop.isDesktopSupported()) {
            val desktop = Desktop.getDesktop()
            if (file.exists()) {
                desktop.open(file)
            } else {
                desktop.open(parentDir)
            }
        } else {
            val os = System.getProperty("os.name").lowercase()
            val processBuilder = when {
                os.contains("mac") -> {
                    if (file.exists()) {
                        ProcessBuilder("open", "-R", file.absolutePath)
                    } else {
                        ProcessBuilder("open", parentDir.absolutePath)
                    }
                }
                os.contains("nix") || os.contains("nux") -> ProcessBuilder("xdg-open", parentDir.absolutePath)
                os.contains("win") -> ProcessBuilder("explorer", "/select,", file.absolutePath)
                else -> null
            }
            processBuilder?.start()
        }
    } catch (e: Exception) {
        println("Failed to open file: ${e.message}")
        e.printStackTrace()
    }
}

