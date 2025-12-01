package com.qualiorstudio.aiadventultimate.utils

import java.io.File

actual fun getStorageDirectory(): String {
    val appDir = File(System.getProperty("user.home") ?: ".", ".aiadventultimate")
    if (!appDir.exists()) {
        appDir.mkdirs()
    }
    return appDir.absolutePath
}

actual fun getEmbeddingsIndexPath(): String {
    return File(getStorageDirectory(), "embeddings_index.json").absolutePath
}

