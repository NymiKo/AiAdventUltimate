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

actual fun getChatsFilePath(): String {
    val dataDir = getDataDirectory()
    return if (dataDir.isNotEmpty()) {
        "$dataDir/chats.json"
    } else {
        "chats.json"
    }
}

actual fun getAgentsFilePath(): String {
    val dataDir = getDataDirectory()
    return if (dataDir.isNotEmpty()) {
        "$dataDir/agents.json"
    } else {
        "agents.json"
    }
}

actual fun getAgentConnectionsFilePath(): String {
    val dataDir = getDataDirectory()
    return if (dataDir.isNotEmpty()) {
        "$dataDir/agent_connections.json"
    } else {
        "agent_connections.json"
    }
}

actual fun getMCPServersFilePath(): String {
    val dataDir = getDataDirectory()
    return if (dataDir.isNotEmpty()) {
        "$dataDir/mcp_servers.json"
    } else {
        "mcp_servers.json"
    }
}

actual fun getTodoistProjectsFilePath(): String {
    val dataDir = getDataDirectory()
    return if (dataDir.isNotEmpty()) {
        "$dataDir/todoist_projects.json"
    } else {
        "todoist_projects.json"
    }
}

