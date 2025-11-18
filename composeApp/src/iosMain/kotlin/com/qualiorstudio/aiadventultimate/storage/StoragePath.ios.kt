package com.qualiorstudio.aiadventultimate.storage

import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask

actual fun getDataDirectory(): String {
    val fileManager = NSFileManager.defaultManager
    val urls = fileManager.URLsForDirectory(
        NSDocumentDirectory,
        NSUserDomainMask
    )
    val documentDirectory = urls?.firstOrNull()
    return documentDirectory?.path ?: ""
}

