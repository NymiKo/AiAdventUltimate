package com.qualiorstudio.aiadventultimate.utils

import java.io.File

actual fun getStorageDirectory(): String {
    val homeDir = System.getProperty("user.home")
    val appDir = File(homeDir, ".aiadventultimate")
    if (!appDir.exists()) {
        appDir.mkdirs()
    }
    return appDir.absolutePath
}

actual fun getEmbeddingsIndexPath(): String {
    return File(getStorageDirectory(), "embeddings_index.json").absolutePath
}

