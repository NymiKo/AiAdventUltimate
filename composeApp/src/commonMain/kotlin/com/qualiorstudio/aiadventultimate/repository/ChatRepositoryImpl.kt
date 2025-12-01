package com.qualiorstudio.aiadventultimate.repository

import com.qualiorstudio.aiadventultimate.model.Chat
import com.qualiorstudio.aiadventultimate.storage.ChatStorage
import com.qualiorstudio.aiadventultimate.storage.getChatsFilePath
import com.qualiorstudio.aiadventultimate.utils.currentTimeMillis
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class ChatRepositoryImpl : ChatRepository {
    private val storage = ChatStorage(getChatsFilePath())
    private val _chats = MutableStateFlow<List<Chat>>(emptyList())
    private val _isLoading = MutableStateFlow(true)
    private var isInitialized = false
    
    val isLoading: MutableStateFlow<Boolean> = _isLoading
    
    private suspend fun ensureInitialized() {
        if (!isInitialized) {
            reloadChats()
            isInitialized = true
        }
    }
    
    override suspend fun getAllChats(): List<Chat> {
        ensureInitialized()
        return _chats.value
    }
    
    override suspend fun getChatById(id: String): Chat? {
        ensureInitialized()
        return _chats.value.find { it.id == id }
    }
    
    override suspend fun saveChat(chat: Chat) {
        ensureInitialized()
        val currentChats = _chats.value.toMutableList()
        val existingIndex = currentChats.indexOfFirst { it.id == chat.id }
        
        val chatToSave = if (existingIndex >= 0) {
            chat.copy(updatedAt = currentTimeMillis())
        } else {
            chat.copy(updatedAt = currentTimeMillis())
        }
        
        if (existingIndex >= 0) {
            currentChats[existingIndex] = chatToSave
        } else {
            currentChats.add(chatToSave)
        }
        
        currentChats.sortByDescending { it.updatedAt }
        _chats.value = currentChats
        storage.saveChats(currentChats)
    }
    
    override suspend fun deleteChat(id: String) {
        ensureInitialized()
        val currentChats = _chats.value.toMutableList()
        currentChats.removeAll { it.id == id }
        _chats.value = currentChats
        storage.saveChats(currentChats)
    }
    
    override suspend fun updateChat(chat: Chat) {
        saveChat(chat)
    }
    
    override suspend fun reloadChats() {
        _isLoading.value = true
        try {
            val loadedChats = storage.loadChats()
            _chats.value = loadedChats.sortedByDescending { it.updatedAt }
            isInitialized = true
        } finally {
            _isLoading.value = false
        }
    }
    
    override fun observeAllChats(): Flow<List<Chat>> {
        return _chats.asStateFlow()
    }
}

