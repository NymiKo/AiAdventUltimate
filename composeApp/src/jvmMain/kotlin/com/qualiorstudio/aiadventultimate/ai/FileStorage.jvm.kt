package com.qualiorstudio.aiadventultimate.ai

import java.io.File

actual class FileStorage actual constructor(filePath: String) {
    private val file = File(filePath)
    
    actual fun writeText(text: String) {
        file.writeText(text)
    }
    
    actual fun readText(): String {
        return file.readText()
    }
    
    actual fun exists(): Boolean {
        return file.exists()
    }
}
