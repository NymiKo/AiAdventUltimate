package com.qualiorstudio.aiadventultimate.repository

import com.qualiorstudio.aiadventultimate.model.MCPServer
import com.qualiorstudio.aiadventultimate.storage.MCPServerStorage
import com.qualiorstudio.aiadventultimate.storage.getMCPServersFilePath
import com.qualiorstudio.aiadventultimate.utils.currentTimeMillis
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

const val DEFAULT_GITHUB_MCP_SERVER_ID = "default-github-mcp-server"

class MCPServerRepositoryImpl : MCPServerRepository {
    private val storage = MCPServerStorage(getMCPServersFilePath())
    private val _servers = MutableStateFlow<List<MCPServer>>(emptyList())
    private val _isLoading = MutableStateFlow(true)
    private var isInitialized = false
    
    val isLoading: MutableStateFlow<Boolean> = _isLoading
    
    private suspend fun ensureInitialized() {
        if (!isInitialized) {
            reloadServers()
            isInitialized = true
        }
    }
    
    override suspend fun getAllServers(): List<MCPServer> {
        ensureInitialized()
        return _servers.value
    }
    
    override suspend fun getServerById(id: String): MCPServer? {
        ensureInitialized()
        return _servers.value.find { it.id == id }
    }
    
    override suspend fun saveServer(server: MCPServer) {
        ensureInitialized()
        val currentServers = _servers.value.toMutableList()
        val existingIndex = currentServers.indexOfFirst { it.id == server.id }
        
        val serverToSave = if (existingIndex >= 0) {
            server.copy(updatedAt = currentTimeMillis())
        } else {
            server.copy(updatedAt = currentTimeMillis())
        }
        
        if (existingIndex >= 0) {
            currentServers[existingIndex] = serverToSave
        } else {
            currentServers.add(serverToSave)
        }
        
        currentServers.sortByDescending { it.updatedAt }
        _servers.value = currentServers
        storage.saveServers(currentServers)
    }
    
    override suspend fun deleteServer(id: String) {
        if (id == DEFAULT_GITHUB_MCP_SERVER_ID) {
            throw IllegalArgumentException("Нельзя удалить сервер GitHub MCP по умолчанию")
        }
        ensureInitialized()
        val currentServers = _servers.value.toMutableList()
        currentServers.removeAll { it.id == id }
        _servers.value = currentServers
        storage.saveServers(currentServers)
    }
    
    override suspend fun updateServer(server: MCPServer) {
        saveServer(server)
    }
    
    override suspend fun reloadServers() {
        _isLoading.value = true
        try {
            val loadedServers = storage.loadServers()
            val hasGitHubServer = loadedServers.any { it.id == DEFAULT_GITHUB_MCP_SERVER_ID }
            
            val serversToSave = if (!hasGitHubServer) {
                val githubServer = createDefaultGitHubServer()
                (loadedServers + githubServer).sortedByDescending { it.updatedAt }
            } else {
                loadedServers.sortedByDescending { it.updatedAt }
            }
            
            if (!hasGitHubServer) {
                storage.saveServers(serversToSave)
            }
            
            _servers.value = serversToSave
            isInitialized = true
        } finally {
            _isLoading.value = false
        }
    }
    
    private fun createDefaultGitHubServer(): MCPServer {
        return MCPServer(
            id = DEFAULT_GITHUB_MCP_SERVER_ID,
            name = "GitHub MCP",
            command = "docker",
            args = listOf("run", "-i", "--rm", "-e", "GITHUB_PERSONAL_ACCESS_TOKEN", "ghcr.io/github/github-mcp-server"),
            env = emptyMap(),
            enabled = true,
            createdAt = currentTimeMillis(),
            updatedAt = currentTimeMillis()
        )
    }
    
    override fun observeAllServers(): Flow<List<MCPServer>> {
        return _servers.asStateFlow()
    }
}

