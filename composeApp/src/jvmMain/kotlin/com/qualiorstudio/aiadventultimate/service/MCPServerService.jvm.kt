package com.qualiorstudio.aiadventultimate.service

import com.qualiorstudio.aiadventultimate.model.MCPServer
import com.qualiorstudio.aiadventultimate.model.MCPServerConnectionStatus
import com.qualiorstudio.aiadventultimate.model.MCPServerStatus
import com.qualiorstudio.aiadventultimate.utils.currentTimeMillis
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.ConcurrentHashMap

actual class MCPServerServiceImpl : MCPServerService {
    private val statuses = ConcurrentHashMap<String, MutableStateFlow<MCPServerStatus?>>()
    private val processes = ConcurrentHashMap<String, Process>()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    override fun getServerStatus(serverId: String): StateFlow<MCPServerStatus?> {
        return statuses.getOrPut(serverId) {
            MutableStateFlow(MCPServerStatus(serverId, MCPServerConnectionStatus.DISCONNECTED))
        }.asStateFlow()
    }
    
    override fun startServer(server: MCPServer) {
        if (!server.enabled) {
            updateStatus(server.id, MCPServerConnectionStatus.DISCONNECTED, "Сервер отключен")
            return
        }
        
        stopServer(server.id)
        updateStatus(server.id, MCPServerConnectionStatus.CONNECTING)
        
        scope.launch {
            try {
                val commandToCheck = server.command.split(" ").first()
                if (!isCommandAvailable(commandToCheck)) {
                    val errorMsg = if (server.command.contains(" ")) {
                        "Команда '${server.command}' содержит пробелы. " +
                        "В поле 'Команда' должна быть только основная команда (например: docker), " +
                        "а все аргументы (run, -i, --rm и т.д.) должны быть в поле 'Аргументы'.\n\n" +
                        "Правильный формат:\n" +
                        "Команда: docker\n" +
                        "Аргументы: run, -i, --rm, -e, GITHUB_PERSONAL_ACCESS_TOKEN, ghcr.io/github/github-mcp-server"
                    } else {
                        "Команда '${server.command}' не найдена. Убедитесь, что она установлена и доступна в PATH.\n\n" +
                        "Для проверки выполните в терминале: ${server.command} --version"
                    }
                    updateStatus(
                        server.id,
                        MCPServerConnectionStatus.ERROR,
                        errorMsg
                    )
                    return@launch
                }
                
                val processBuilder = ProcessBuilder().apply {
                    command(server.command, *server.args.toTypedArray())
                    environment().putAll(server.env)
                    redirectErrorStream(true)
                }
                
                val process = processBuilder.start()
                processes[server.id] = process
                
                scope.launch {
                    val reader = BufferedReader(InputStreamReader(process.inputStream))
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        println("[MCP ${server.name}] $line")
                    }
                }
                
                scope.launch {
                    val exitCode = process.waitFor()
                    processes.remove(server.id)
                    if (exitCode != 0) {
                        updateStatus(
                            server.id,
                            MCPServerConnectionStatus.ERROR,
                            "Процесс завершился с кодом $exitCode"
                        )
                    } else {
                        updateStatus(server.id, MCPServerConnectionStatus.DISCONNECTED, "Процесс завершен")
                    }
                }
                
                delay(2000)
                if (process.isAlive) {
                    updateStatus(server.id, MCPServerConnectionStatus.CONNECTED)
                } else {
                    updateStatus(
                        server.id,
                        MCPServerConnectionStatus.ERROR,
                        "Процесс завершился слишком быстро"
                    )
                }
            } catch (e: java.io.IOException) {
                val errorMessage = when {
                    e.message?.contains("error=2") == true || e.message?.contains("No such file or directory") == true -> {
                        "Команда '${server.command}' не найдена. Убедитесь, что она установлена и доступна в PATH.\n\n" +
                        "Для Docker: убедитесь, что Docker установлен и запущен.\n" +
                        "Для проверки выполните в терминале: ${server.command} --version"
                    }
                    e.message?.contains("error=13") == true -> {
                        "Нет прав на выполнение команды '${server.command}'"
                    }
                    else -> {
                        "Ошибка запуска процесса: ${e.message ?: "Неизвестная ошибка"}"
                    }
                }
                updateStatus(server.id, MCPServerConnectionStatus.ERROR, errorMessage)
                e.printStackTrace()
            } catch (e: Exception) {
                val errorMessage = when {
                    e.message?.contains("Cannot run program") == true -> {
                        "Не удалось запустить команду '${server.command}'. Проверьте правильность команды и аргументов."
                    }
                    else -> {
                        "Ошибка: ${e.message ?: "Неизвестная ошибка"}"
                    }
                }
                updateStatus(server.id, MCPServerConnectionStatus.ERROR, errorMessage)
                e.printStackTrace()
            }
        }
    }
    
    private fun isCommandAvailable(command: String): Boolean {
        return try {
            val os = System.getProperty("os.name").lowercase()
            val checkCommand = when {
                os.contains("win") -> listOf("cmd", "/c", "where", command)
                else -> listOf("which", command)
            }
            val process = ProcessBuilder(checkCommand)
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .start()
            val exitCode = process.waitFor()
            exitCode == 0
        } catch (e: Exception) {
            false
        }
    }
    
    override fun stopServer(serverId: String) {
        processes[serverId]?.let { process ->
            if (process.isAlive) {
                process.destroy()
                try {
                    if (!process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)) {
                        process.destroyForcibly()
                    }
                } catch (e: Exception) {
                    process.destroyForcibly()
                }
            }
            processes.remove(serverId)
        }
        updateStatus(serverId, MCPServerConnectionStatus.DISCONNECTED)
    }
    
    override fun checkServerStatus(server: MCPServer) {
        val process = processes[server.id]
        if (process != null && process.isAlive) {
            updateStatus(server.id, MCPServerConnectionStatus.CONNECTED)
        } else {
            if (server.enabled) {
                startServer(server)
            } else {
                updateStatus(server.id, MCPServerConnectionStatus.DISCONNECTED, "Сервер отключен")
            }
        }
    }
    
    override fun getAllStatuses(): Map<String, StateFlow<MCPServerStatus?>> {
        return statuses.mapValues { it.value.asStateFlow() }
    }
    
    private fun updateStatus(serverId: String, status: MCPServerConnectionStatus, errorMessage: String? = null) {
        statuses.getOrPut(serverId) {
            MutableStateFlow(MCPServerStatus(serverId, MCPServerConnectionStatus.DISCONNECTED))
        }.value = MCPServerStatus(serverId, status, errorMessage, currentTimeMillis())
    }
    
    fun shutdown() {
        processes.values.forEach { process ->
            if (process.isAlive) {
                process.destroy()
            }
        }
        scope.cancel()
    }
}

