package com.qualiorstudio.aiadventultimate.utils

import java.awt.Desktop
import java.net.URI

actual fun openUrl(url: String) {
    try {
        if (Desktop.isDesktopSupported()) {
            Desktop.getDesktop().browse(URI(url))
        } else {
            val os = System.getProperty("os.name").lowercase()
            val processBuilder = when {
                os.contains("mac") -> ProcessBuilder("open", url)
                os.contains("nix") || os.contains("nux") -> ProcessBuilder("xdg-open", url)
                os.contains("win") -> ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", url)
                else -> null
            }
            processBuilder?.start()
        }
    } catch (e: Exception) {
        println("Не удалось открыть URL: ${e.message}")
        e.printStackTrace()
    }
}

