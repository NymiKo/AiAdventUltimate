package com.qualiorstudio.aiadventultimate.repository

import com.qualiorstudio.aiadventultimate.model.Agent
import com.qualiorstudio.aiadventultimate.storage.AgentStorage
import com.qualiorstudio.aiadventultimate.storage.getAgentsFilePath
import com.qualiorstudio.aiadventultimate.utils.currentTimeMillis
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class AgentRepositoryImpl : AgentRepository {
    private val storage = AgentStorage(getAgentsFilePath())
    private val _agents = MutableStateFlow<List<Agent>>(emptyList())
    private val _isLoading = MutableStateFlow(true)
    private var isInitialized = false
    
    val isLoading: MutableStateFlow<Boolean> = _isLoading
    
    private suspend fun ensureInitialized() {
        if (!isInitialized) {
            reloadAgents()
            isInitialized = true
        }
    }
    
    override suspend fun getAllAgents(): List<Agent> {
        ensureInitialized()
        return _agents.value
    }
    
    override suspend fun getAgentById(id: String): Agent? {
        ensureInitialized()
        return _agents.value.find { it.id == id }
    }
    
    override suspend fun saveAgent(agent: Agent) {
        ensureInitialized()
        val currentAgents = _agents.value.toMutableList()
        val existingIndex = currentAgents.indexOfFirst { it.id == agent.id }
        
        val agentToSave = if (existingIndex >= 0) {
            agent.copy(updatedAt = currentTimeMillis())
        } else {
            agent.copy(updatedAt = currentTimeMillis())
        }
        
        if (existingIndex >= 0) {
            currentAgents[existingIndex] = agentToSave
        } else {
            currentAgents.add(agentToSave)
        }
        
        currentAgents.sortByDescending { it.updatedAt }
        _agents.value = currentAgents
        storage.saveAgents(currentAgents)
    }
    
    override suspend fun deleteAgent(id: String) {
        ensureInitialized()
        val currentAgents = _agents.value.toMutableList()
        currentAgents.removeAll { it.id == id }
        _agents.value = currentAgents
        storage.saveAgents(currentAgents)
    }
    
    override suspend fun updateAgent(agent: Agent) {
        saveAgent(agent)
    }
    
    override suspend fun reloadAgents() {
        _isLoading.value = true
        try {
            val loadedAgents = storage.loadAgents()
            _agents.value = loadedAgents.sortedByDescending { it.updatedAt }
            isInitialized = true
        } finally {
            _isLoading.value = false
        }
    }
    
    override fun observeAllAgents(): Flow<List<Agent>> {
        return _agents.asStateFlow()
    }
}

