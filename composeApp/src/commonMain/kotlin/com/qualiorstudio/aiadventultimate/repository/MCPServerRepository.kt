package com.qualiorstudio.aiadventultimate.repository

import com.qualiorstudio.aiadventultimate.model.MCPServer
import kotlinx.coroutines.flow.Flow

interface MCPServerRepository {
    suspend fun getAllServers(): List<MCPServer>
    suspend fun getServerById(id: String): MCPServer?
    suspend fun saveServer(server: MCPServer)
    suspend fun deleteServer(id: String)
    suspend fun updateServer(server: MCPServer)
    suspend fun reloadServers()
    fun observeAllServers(): Flow<List<MCPServer>>
}



