package com.qualiorstudio.aiadventultimate.storage

import com.qualiorstudio.aiadventultimate.ai.FileStorage
import com.qualiorstudio.aiadventultimate.model.AgentConnection
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString

@Serializable
data class AgentConnectionsData(
    val connections: List<AgentConnection> = emptyList()
)

class AgentConnectionStorage(private val filePath: String) {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }
    
    private val fileStorage = FileStorage(filePath)

    fun saveConnections(connections: List<AgentConnection>) {
        try {
            val connectionsData = AgentConnectionsData(connections = connections)
            val jsonString = json.encodeToString(AgentConnectionsData.serializer(), connectionsData)
            fileStorage.writeText(jsonString)
        } catch (e: Exception) {
            println("Failed to save agent connections: ${e.message}")
            e.printStackTrace()
            throw Exception("Failed to save agent connections: ${e.message}")
        }
    }

    fun loadConnections(): List<AgentConnection> {
        return try {
            if (!fileStorage.exists()) {
                return emptyList()
            }
            val jsonString = fileStorage.readText()
            if (jsonString.isBlank()) {
                return emptyList()
            }
            val connectionsData = json.decodeFromString<AgentConnectionsData>(jsonString)
            connectionsData.connections
        } catch (e: Exception) {
            println("Failed to load agent connections: ${e.message}")
            e.printStackTrace()
            emptyList()
        }
    }
}

