package com.qualiorstudio.aiadventultimate.storage

import com.qualiorstudio.aiadventultimate.ai.FileStorage
import com.qualiorstudio.aiadventultimate.model.Chat
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString

@Serializable
data class ChatsData(
    val chats: List<Chat> = emptyList()
)

class ChatStorage(private val filePath: String) {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }
    
    private val fileStorage = FileStorage(filePath)

    fun saveChats(chats: List<Chat>) {
        try {
            val chatsData = ChatsData(chats = chats)
            val jsonString = json.encodeToString(ChatsData.serializer(), chatsData)
            fileStorage.writeText(jsonString)
        } catch (e: Exception) {
            println("Failed to save chats: ${e.message}")
            e.printStackTrace()
            throw Exception("Failed to save chats: ${e.message}")
        }
    }

    fun loadChats(): List<Chat> {
        return try {
            if (!fileStorage.exists()) {
                return emptyList()
            }
            val jsonString = fileStorage.readText()
            if (jsonString.isBlank()) {
                return emptyList()
            }
            val chatsData = json.decodeFromString<ChatsData>(jsonString)
            chatsData.chats
        } catch (e: Exception) {
            println("Failed to load chats: ${e.message}")
            e.printStackTrace()
            emptyList()
        }
    }
}



