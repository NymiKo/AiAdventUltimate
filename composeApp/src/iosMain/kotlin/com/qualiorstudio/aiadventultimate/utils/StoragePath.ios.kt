package com.qualiorstudio.aiadventultimate.utils

import platform.Foundation.NSFileManager
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSUserDomainMask
import platform.Foundation.NSString
import platform.Foundation.stringByAppendingPathComponent

actual fun getStorageDirectory(): String {
    val fileManager = NSFileManager.defaultManager
    val urls = fileManager.URLsForDirectory(
        NSDocumentDirectory,
        NSUserDomainMask
    )
    val documentsURL = urls.firstOrNull()
    return documentsURL?.path ?: ""
}

actual fun getEmbeddingsIndexPath(): String {
    val documentsPath = getStorageDirectory()
    val nsString = NSString.create(string = documentsPath)
    return nsString.stringByAppendingPathComponent("embeddings_index.json")
}

