package com.qualiorstudio.aiadventultimate.storage

import com.qualiorstudio.aiadventultimate.ai.FileStorage
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString

@Serializable
data class TodoistProjectMapping(
    val projectName: String,
    val projectId: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Serializable
data class TodoistProjectsData(
    val mappings: Map<String, String> = emptyMap()
)

class TodoistProjectStorage(private val filePath: String) {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }
    
    private val fileStorage = FileStorage(filePath)

    fun saveProjectMapping(projectName: String, projectId: String) {
        try {
            val currentData = loadProjectsData()
            val updatedMappings = currentData.mappings.toMutableMap()
            updatedMappings[projectName] = projectId
            
            val projectsData = TodoistProjectsData(mappings = updatedMappings)
            val jsonString = json.encodeToString(TodoistProjectsData.serializer(), projectsData)
            fileStorage.writeText(jsonString)
            println("✓ Сохранено соответствие: проект '$projectName' -> ID '$projectId'")
        } catch (e: Exception) {
            println("Ошибка сохранения соответствия проекта: ${e.message}")
            e.printStackTrace()
        }
    }

    fun getProjectId(projectName: String): String? {
        val data = loadProjectsData()
        return data.mappings[projectName]
    }

    fun getAllMappings(): Map<String, String> {
        return loadProjectsData().mappings
    }

    fun removeProjectMapping(projectName: String) {
        try {
            val currentData = loadProjectsData()
            val updatedMappings = currentData.mappings.toMutableMap()
            updatedMappings.remove(projectName)
            
            val projectsData = TodoistProjectsData(mappings = updatedMappings)
            val jsonString = json.encodeToString(TodoistProjectsData.serializer(), projectsData)
            fileStorage.writeText(jsonString)
            println("✓ Удалено соответствие для проекта: '$projectName'")
        } catch (e: Exception) {
            println("Ошибка удаления соответствия проекта: ${e.message}")
            e.printStackTrace()
        }
    }

    private fun loadProjectsData(): TodoistProjectsData {
        return try {
            if (!fileStorage.exists()) {
                return TodoistProjectsData()
            }
            val jsonString = fileStorage.readText()
            if (jsonString.isBlank()) {
                return TodoistProjectsData()
            }
            json.decodeFromString<TodoistProjectsData>(jsonString)
        } catch (e: Exception) {
            println("Ошибка загрузки соответствий проектов: ${e.message}")
            e.printStackTrace()
            TodoistProjectsData()
        }
    }
}

