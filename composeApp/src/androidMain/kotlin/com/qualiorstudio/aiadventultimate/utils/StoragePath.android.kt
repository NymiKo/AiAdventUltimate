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

actual fun getSupportEmbeddingsIndexPath(): String {
    return File(getStorageDirectory(), "support_embeddings_index.json").absolutePath
}

