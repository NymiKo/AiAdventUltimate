package com.qualiorstudio.aiadventultimate.repository

import com.qualiorstudio.aiadventultimate.model.AgentConnection
import kotlinx.coroutines.flow.Flow

interface AgentConnectionRepository {
    suspend fun getAllConnections(): List<AgentConnection>
    suspend fun getConnectionById(id: String): AgentConnection?
    suspend fun getConnectionsByAgent(agentId: String): List<AgentConnection>
    suspend fun getConnectionsForAgent(agentId: String): List<AgentConnection>
    suspend fun saveConnection(connection: AgentConnection)
    suspend fun deleteConnection(id: String)
    suspend fun updateConnection(connection: AgentConnection)
    suspend fun reloadConnections()
    fun observeAllConnections(): Flow<List<AgentConnection>>
}

