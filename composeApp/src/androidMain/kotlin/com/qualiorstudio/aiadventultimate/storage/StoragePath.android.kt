package com.qualiorstudio.aiadventultimate.storage

import android.content.Context
import androidx.compose.ui.platform.LocalContext
import java.io.File

actual fun getDataDirectory(): String {
    val context = getApplicationContext()
    return context.filesDir.absolutePath
}

actual fun getChatsFilePath(): String {
    return File(getDataDirectory(), "chats.json").absolutePath
}

actual fun getAgentsFilePath(): String {
    return File(getDataDirectory(), "agents.json").absolutePath
}

actual fun getAgentConnectionsFilePath(): String {
    return File(getDataDirectory(), "agent_connections.json").absolutePath
}

actual fun getMCPServersFilePath(): String {
    return File(getDataDirectory(), "mcp_servers.json").absolutePath
}

actual fun getTodoistProjectsFilePath(): String {
    return File(getDataDirectory(), "todoist_projects.json").absolutePath
}

private var applicationContext: Context? = null

fun setApplicationContext(context: Context) {
    applicationContext = context.applicationContext
}

fun getApplicationContext(): Context {
    return applicationContext ?: throw IllegalStateException("Application context not set")
}

