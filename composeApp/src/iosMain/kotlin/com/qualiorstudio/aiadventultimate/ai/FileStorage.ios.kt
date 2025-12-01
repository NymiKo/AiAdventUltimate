package com.qualiorstudio.aiadventultimate.ai

import platform.Foundation.NSString
import platform.Foundation.NSFileManager
import platform.Foundation.stringWithContentsOfFile
import platform.Foundation.writeToFile
import platform.Foundation.NSUTF8StringEncoding

actual class FileStorage(filePath: String) {
    private val fileManager = NSFileManager.defaultManager
    private val filePath = filePath
    
    actual fun writeText(text: String) {
        val nsString = NSString.create(string = text)
        nsString.writeToFile(
            path = this.filePath,
            atomically = true,
            encoding = NSUTF8StringEncoding,
            error = null
        )
    }
    
    actual fun readText(): String {
        val content = NSString.stringWithContentsOfFile(
            path = this.filePath,
            encoding = NSUTF8StringEncoding,
            error = null
        )
        return content?.toString() ?: ""
    }
    
    actual fun exists(): Boolean {
        return fileManager.fileExistsAtPath(this.filePath)
    }
}

