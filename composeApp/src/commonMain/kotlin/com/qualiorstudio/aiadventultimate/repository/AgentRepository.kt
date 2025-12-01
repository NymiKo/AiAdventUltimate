package com.qualiorstudio.aiadventultimate.repository

import com.qualiorstudio.aiadventultimate.model.Agent
import kotlinx.coroutines.flow.Flow

interface AgentRepository {
    suspend fun getAllAgents(): List<Agent>
    suspend fun getAgentById(id: String): Agent?
    suspend fun saveAgent(agent: Agent)
    suspend fun deleteAgent(id: String)
    suspend fun updateAgent(agent: Agent)
    suspend fun reloadAgents()
    fun observeAllAgents(): Flow<List<Agent>>
}

