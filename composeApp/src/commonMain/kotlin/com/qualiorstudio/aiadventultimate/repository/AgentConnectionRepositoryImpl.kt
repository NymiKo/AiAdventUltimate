package com.qualiorstudio.aiadventultimate.repository

import com.qualiorstudio.aiadventultimate.model.AgentConnection
import com.qualiorstudio.aiadventultimate.storage.AgentConnectionStorage
import com.qualiorstudio.aiadventultimate.storage.getAgentConnectionsFilePath
import com.qualiorstudio.aiadventultimate.utils.currentTimeMillis
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class AgentConnectionRepositoryImpl : AgentConnectionRepository {
    private val storage = AgentConnectionStorage(getAgentConnectionsFilePath())
    private val _connections = MutableStateFlow<List<AgentConnection>>(emptyList())
    private val _isLoading = MutableStateFlow(true)
    private var isInitialized = false
    
    val isLoading: MutableStateFlow<Boolean> = _isLoading
    
    private suspend fun ensureInitialized() {
        if (!isInitialized) {
            reloadConnections()
            isInitialized = true
        }
    }
    
    override suspend fun getAllConnections(): List<AgentConnection> {
        ensureInitialized()
        return _connections.value
    }
    
    override suspend fun getConnectionById(id: String): AgentConnection? {
        ensureInitialized()
        return _connections.value.find { it.id == id }
    }
    
    override suspend fun getConnectionsByAgent(agentId: String): List<AgentConnection> {
        ensureInitialized()
        return _connections.value.filter { it.sourceAgentId == agentId }
    }
    
    override suspend fun getConnectionsForAgent(agentId: String): List<AgentConnection> {
        ensureInitialized()
        return _connections.value.filter { it.targetAgentId == agentId }
    }
    
    override suspend fun saveConnection(connection: AgentConnection) {
        ensureInitialized()
        val currentConnections = _connections.value.toMutableList()
        val existingIndex = currentConnections.indexOfFirst { it.id == connection.id }
        
        val connectionToSave = if (existingIndex >= 0) {
            connection.copy(updatedAt = currentTimeMillis())
        } else {
            connection.copy(updatedAt = currentTimeMillis())
        }
        
        if (existingIndex >= 0) {
            currentConnections[existingIndex] = connectionToSave
        } else {
            currentConnections.add(connectionToSave)
        }
        
        _connections.value = currentConnections
        storage.saveConnections(currentConnections)
    }
    
    override suspend fun deleteConnection(id: String) {
        ensureInitialized()
        val currentConnections = _connections.value.toMutableList()
        currentConnections.removeAll { it.id == id }
        _connections.value = currentConnections
        storage.saveConnections(currentConnections)
    }
    
    override suspend fun updateConnection(connection: AgentConnection) {
        saveConnection(connection)
    }
    
    override suspend fun reloadConnections() {
        _isLoading.value = true
        try {
            val loadedConnections = storage.loadConnections()
            _connections.value = loadedConnections
            isInitialized = true
        } finally {
            _isLoading.value = false
        }
    }
    
    override fun observeAllConnections(): Flow<List<AgentConnection>> {
        return _connections.asStateFlow()
    }
}

