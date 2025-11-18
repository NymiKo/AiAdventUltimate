package com.qualiorstudio.aiadventultimate.storage

import android.content.Context
import androidx.compose.ui.platform.LocalContext
import java.io.File

actual fun getDataDirectory(): String {
    val context = getApplicationContext()
    return context.filesDir.absolutePath
}

private var applicationContext: Context? = null

fun setApplicationContext(context: Context) {
    applicationContext = context.applicationContext
}

fun getApplicationContext(): Context {
    return applicationContext ?: throw IllegalStateException("Application context not set")
}

