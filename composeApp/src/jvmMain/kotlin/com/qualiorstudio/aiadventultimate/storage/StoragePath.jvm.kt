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

actual fun getPersonalizationFilePath(): String {
    return File(getDataDirectory(), "personalization.json").absolutePath
}

