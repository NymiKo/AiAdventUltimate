package com.qualiorstudio.aiadventultimate.storage

import java.io.File

actual fun getDataDirectory(): String {
    val userHome = System.getProperty("user.home")
    val dataDir = File(userHome, ".aiadventultimate")
    if (!dataDir.exists()) {
        dataDir.mkdirs()
    }
    return dataDir.absolutePath
}

