package com.qualiorstudio.aiadventultimate.storage

import com.qualiorstudio.aiadventultimate.model.Project
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString

class ProjectStorage {
    private val json = Json { 
        prettyPrint = true
        ignoreUnknownKeys = true
    }
    
    private val projectFileName = "current_project.json"

    suspend fun saveProject(project: Project) {
        try {
            val projectJson = json.encodeToString(project)
            writeToFile(projectFileName, projectJson)
        } catch (e: Exception) {
            println("Error saving project: ${e.message}")
            e.printStackTrace()
        }
    }

    suspend fun loadProject(): Project? {
        return try {
            val projectJson = readFromFile(projectFileName)
            if (projectJson.isNotBlank()) {
                json.decodeFromString<Project>(projectJson)
            } else {
                null
            }
        } catch (e: Exception) {
            println("Error loading project: ${e.message}")
            null
        }
    }

    suspend fun clearProject() {
        try {
            deleteFile(projectFileName)
        } catch (e: Exception) {
            println("Error clearing project: ${e.message}")
        }
    }

    private suspend fun writeToFile(fileName: String, content: String) {
        val path = getStoragePath()
        val file = java.io.File(path, fileName)
        file.parentFile?.mkdirs()
        file.writeText(content)
    }

    private suspend fun readFromFile(fileName: String): String {
        val path = getStoragePath()
        val file = java.io.File(path, fileName)
        return if (file.exists()) {
            file.readText()
        } else {
            ""
        }
    }

    private suspend fun deleteFile(fileName: String) {
        val path = getStoragePath()
        val file = java.io.File(path, fileName)
        if (file.exists()) {
            file.delete()
        }
    }

    private fun getStoragePath(): String {
        return getDataDirectory()
    }
}

