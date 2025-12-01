package com.qualiorstudio.aiadventultimate.storage

import com.qualiorstudio.aiadventultimate.ai.FileStorage
import com.qualiorstudio.aiadventultimate.model.Agent
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString

@Serializable
data class AgentsData(
    val agents: List<Agent> = emptyList()
)

class AgentStorage(private val filePath: String) {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }
    
    private val fileStorage = FileStorage(filePath)

    fun saveAgents(agents: List<Agent>) {
        try {
            val agentsData = AgentsData(agents = agents)
            val jsonString = json.encodeToString(AgentsData.serializer(), agentsData)
            fileStorage.writeText(jsonString)
        } catch (e: Exception) {
            println("Failed to save agents: ${e.message}")
            e.printStackTrace()
            throw Exception("Failed to save agents: ${e.message}")
        }
    }

    fun loadAgents(): List<Agent> {
        return try {
            if (!fileStorage.exists()) {
                return emptyList()
            }
            val jsonString = fileStorage.readText()
            if (jsonString.isBlank()) {
                return emptyList()
            }
            val agentsData = json.decodeFromString<AgentsData>(jsonString)
            agentsData.agents
        } catch (e: Exception) {
            println("Failed to load agents: ${e.message}")
            e.printStackTrace()
            emptyList()
        }
    }
}

