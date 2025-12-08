package com.qualiorstudio.aiadventultimate.storage

import com.qualiorstudio.aiadventultimate.ai.FileStorage
import com.qualiorstudio.aiadventultimate.model.MCPServer
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString

@Serializable
data class MCPServersData(
    val servers: List<MCPServer> = emptyList()
)

class MCPServerStorage(private val filePath: String) {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }
    
    private val fileStorage = FileStorage(filePath)

    fun saveServers(servers: List<MCPServer>) {
        try {
            val serversData = MCPServersData(servers = servers)
            val jsonString = json.encodeToString(MCPServersData.serializer(), serversData)
            fileStorage.writeText(jsonString)
        } catch (e: Exception) {
            println("Failed to save MCP servers: ${e.message}")
            e.printStackTrace()
            throw Exception("Failed to save MCP servers: ${e.message}")
        }
    }

    fun loadServers(): List<MCPServer> {
        return try {
            if (!fileStorage.exists()) {
                return emptyList()
            }
            val jsonString = fileStorage.readText()
            if (jsonString.isBlank()) {
                return emptyList()
            }
            val serversData = json.decodeFromString<MCPServersData>(jsonString)
            serversData.servers
        } catch (e: Exception) {
            println("Failed to load MCP servers: ${e.message}")
            e.printStackTrace()
            emptyList()
        }
    }
}


