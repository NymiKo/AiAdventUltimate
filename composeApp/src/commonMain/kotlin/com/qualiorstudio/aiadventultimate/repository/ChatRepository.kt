package com.qualiorstudio.aiadventultimate.repository

import com.qualiorstudio.aiadventultimate.model.Chat
import kotlinx.coroutines.flow.Flow

interface ChatRepository {
    suspend fun getAllChats(): List<Chat>
    suspend fun getChatById(id: String): Chat?
    suspend fun saveChat(chat: Chat)
    suspend fun deleteChat(id: String)
    suspend fun updateChat(chat: Chat)
    suspend fun reloadChats()
    fun observeAllChats(): Flow<List<Chat>>
}

