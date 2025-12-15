package com.qualiorstudio.aiadventultimate.storage

import com.qualiorstudio.aiadventultimate.ai.FileStorage
import com.qualiorstudio.aiadventultimate.model.Personalization
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString

@Serializable
data class PersonalizationData(
    val personalization: Personalization = Personalization()
)

class PersonalizationStorage(private val filePath: String) {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    
    private val fileStorage = FileStorage(filePath)

    fun savePersonalization(personalization: Personalization) {
        try {
            val data = PersonalizationData(personalization = personalization)
            val jsonString = json.encodeToString(PersonalizationData.serializer(), data)
            fileStorage.writeText(jsonString)
        } catch (e: Exception) {
            println("Failed to save personalization: ${e.message}")
            e.printStackTrace()
            throw Exception("Failed to save personalization: ${e.message}")
        }
    }

    fun loadPersonalization(): Personalization {
        return try {
            if (!fileStorage.exists()) {
                return Personalization()
            }
            val jsonString = fileStorage.readText()
            if (jsonString.isBlank()) {
                return Personalization()
            }
            val data = json.decodeFromString<PersonalizationData>(jsonString)
            data.personalization
        } catch (e: Exception) {
            println("Failed to load personalization: ${e.message}")
            e.printStackTrace()
            Personalization()
        }
    }
}

