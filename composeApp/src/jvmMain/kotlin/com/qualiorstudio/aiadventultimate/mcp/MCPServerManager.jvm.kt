package com.qualiorstudio.aiadventultimate.mcp

import com.qualiorstudio.aiadventultimate.api.DeepSeekTool
import com.qualiorstudio.aiadventultimate.model.MCPServer
import com.qualiorstudio.aiadventultimate.model.MCPServerStatus
import com.qualiorstudio.aiadventultimate.repository.MCPServerRepository
import com.qualiorstudio.aiadventultimate.service.MCPServerService
import com.qualiorstudio.aiadventultimate.service.MCPServerServiceImpl
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.*
import java.util.concurrent.ConcurrentHashMap

actual class MCPServerManagerImpl : MCPServerManager {
    private val clients = ConcurrentHashMap<String, MCPClient>()
    private val serverTools = ConcurrentHashMap<String, List<MCPTool>>()
    private val mcpService = MCPServerServiceImpl()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var isInitialized = false
    
    override suspend fun initializeServers(repository: MCPServerRepository) {
        if (isInitialized) {
            return
        }
        
        val servers = repository.getAllServers().filter { it.enabled }
        
        if (servers.isEmpty()) {
            println("Нет включенных MCP серверов для инициализации")
            isInitialized = true
            return
        }
        
        println("Инициализация ${servers.size} MCP серверов...")
        
        servers.forEach { server ->
            try {
                println("Подключение к MCP серверу '${server.name}'...")
                val client = MCPClientImpl(server.command, server.args, server.env)
                client.connect()
                clients[server.id] = client
                
                val tools = client.listTools()
                serverTools[server.id] = tools
                println("✓ MCP сервер '${server.name}' подключен, доступно инструментов: ${tools.size}")
                tools.forEach { tool ->
                    println("  - ${tool.name}: ${tool.description}")
                }
            } catch (e: Exception) {
                println("✗ Ошибка подключения к MCP серверу '${server.name}': ${e.message}")
                e.printStackTrace()
            }
        }
        
        isInitialized = true
        val totalTools = serverTools.values.sumOf { it.size }
        println("Инициализация завершена. Всего доступно инструментов: $totalTools")
    }
    
    override suspend fun getAvailableTools(): List<DeepSeekTool> {
        return serverTools.values.flatten().map { it.toDeepSeekTool() }
    }
    
    override suspend fun callTool(toolName: String, arguments: JsonObject): JsonElement {
        for ((serverId, tools) in serverTools) {
            val tool = tools.find { it.name == toolName }
            if (tool != null) {
                val client = clients[serverId]
                if (client != null && client.isConnected()) {
                    return client.callTool(toolName, arguments)
                } else {
                    throw Exception("MCP сервер отключен")
                }
            }
        }
        throw Exception("Инструмент '$toolName' не найден")
    }
    
    override fun hasTools(toolName: String): Boolean {
        return serverTools.values.flatten().any { it.name == toolName }
    }
    
    override fun getServerStatus(serverId: String): StateFlow<MCPServerStatus?> {
        return mcpService.getServerStatus(serverId)
    }
    
    override suspend fun shutdown() {
        clients.values.forEach { client ->
            try {
                client.disconnect()
            } catch (e: Exception) {
                println("Ошибка при отключении MCP клиента: ${e.message}")
            }
        }
        clients.clear()
        serverTools.clear()
        scope.cancel()
    }
}

actual fun createMCPServerManager(): MCPServerManager {
    return MCPServerManagerImpl()
}

