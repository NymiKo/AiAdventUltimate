package com.qualiorstudio.aiadventultimate.mcp

import com.qualiorstudio.aiadventultimate.api.DeepSeekTool
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger

actual class MCPClientImpl actual constructor(
    private val command: String,
    private val args: List<String>,
    private val env: Map<String, String>
) : MCPClient {
    private var process: Process? = null
    private var reader: BufferedReader? = null
    private var writer: BufferedWriter? = null
    private val json = Json { 
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
        explicitNulls = false
    }
    private val requestIdCounter = AtomicInteger(1)
    private val pendingRequests = ConcurrentHashMap<Int, CompletableDeferred<MCPResponse>>()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var isInitialized = false
    
    override suspend fun connect() {
        if (process?.isAlive == true) {
            return
        }
        
        try {
            val processBuilder = ProcessBuilder().apply {
                command(this@MCPClientImpl.command, *args.toTypedArray())
                environment().putAll(env)
                redirectErrorStream(false)
            }
            
            process = processBuilder.start()
            reader = BufferedReader(InputStreamReader(process!!.inputStream))
            writer = BufferedWriter(OutputStreamWriter(process!!.outputStream))
            
            println("[MCP] Процесс запущен, PID: ${process!!.pid()}")
            println("[MCP] Команда: $command ${args.joinToString(" ")}")
            
            scope.launch(Dispatchers.IO) {
                readResponses()
            }
            
            scope.launch(Dispatchers.IO) {
                val errorReader = BufferedReader(InputStreamReader(process!!.errorStream))
                var errorLine: String?
                while (errorReader.readLine().also { errorLine = it } != null) {
                    println("[MCP STDERR] $errorLine")
                }
            }
            
            delay(3000)
            
            val initRequestId = requestIdCounter.getAndIncrement()
            val initParams = buildJsonObject {
                put("protocolVersion", "2024-11-05")
                put("capabilities", buildJsonObject {
                    put("experimental", buildJsonObject {})
                    put("sampling", buildJsonObject {})
                })
                put("clientInfo", buildJsonObject {
                    put("name", "AiAdventUltimate")
                    put("version", "1.0.0")
                })
            }
            
            val initRequest = MCPRequest(
                id = initRequestId,
                method = "initialize",
                params = initParams
            )
            
            println("[MCP] Параметры initialize: ${json.encodeToString(initParams)}")
            
            val initResponse = sendRequest(initRequest)
            if (initResponse.error == null) {
                isInitialized = true
                println("MCP сервер инициализирован: ${initResponse.result}")
            } else {
                throw Exception("Ошибка инициализации MCP: ${initResponse.error?.message}")
            }
        } catch (e: Exception) {
            println("Ошибка подключения к MCP серверу: ${e.message}")
            e.printStackTrace()
            disconnect()
            throw e
        }
    }
    
    override suspend fun disconnect() {
        try {
            writer?.close()
            reader?.close()
            process?.destroy()
            process?.waitFor(2, java.util.concurrent.TimeUnit.SECONDS)
            if (process?.isAlive == true) {
                process?.destroyForcibly()
            }
        } catch (e: Exception) {
            println("Ошибка при отключении: ${e.message}")
        } finally {
            process = null
            reader = null
            writer = null
            isInitialized = false
        }
    }
    
    override suspend fun listTools(): List<MCPTool> {
        if (!isInitialized) {
            connect()
        }
        
        println("[MCP] Запрос списка инструментов...")
        val request = MCPRequest(
            id = requestIdCounter.getAndIncrement(),
            method = "tools/list",
            params = buildJsonObject {}
        )
        
        val response = sendRequest(request)
        if (response.error != null) {
            println("[MCP] Ошибка получения инструментов: ${response.error?.message}")
            throw Exception("Ошибка получения списка инструментов: ${response.error?.message}")
        }
        
        println("[MCP] Ответ получен: ${response.result}")
        val toolsArray = response.result?.jsonObject?.get("tools")?.jsonArray
        val tools = toolsArray?.mapNotNull { toolElement ->
            try {
                val toolObj = toolElement.jsonObject
                val tool = MCPTool(
                    name = toolObj["name"]?.jsonPrimitive?.content ?: "",
                    description = toolObj["description"]?.jsonPrimitive?.content ?: "",
                    inputSchema = toolObj["inputSchema"]?.jsonObject ?: buildJsonObject {}
                )
                println("[MCP] Найден инструмент: ${tool.name}")
                tool
            } catch (e: Exception) {
                println("Ошибка парсинга инструмента: ${e.message}")
                e.printStackTrace()
                null
            }
        } ?: emptyList()
        
        println("[MCP] Всего загружено инструментов: ${tools.size}")
        return tools
    }
    
    override suspend fun callTool(name: String, arguments: JsonObject): JsonElement {
        if (!isInitialized) {
            connect()
        }
        
        val request = MCPRequest(
            id = requestIdCounter.getAndIncrement(),
            method = "tools/call",
            params = buildJsonObject {
                put("name", name)
                put("arguments", arguments)
            }
        )
        
        val response = sendRequest(request)
        if (response.error != null) {
            throw Exception("Ошибка вызова инструмента $name: ${response.error?.message}")
        }
        
        return response.result?.jsonObject?.get("content") ?: buildJsonObject {}
    }
    
    override fun isConnected(): Boolean {
        return process?.isAlive == true && isInitialized
    }
    
    private suspend fun sendRequest(request: MCPRequest): MCPResponse {
        val deferred = CompletableDeferred<MCPResponse>()
        val id = request.id ?: requestIdCounter.getAndIncrement()
        pendingRequests[id] = deferred
        
        try {
            val requestWithId = request.copy(id = id, jsonrpc = "2.0")
            val requestJson = json.encodeToString(MCPRequest.serializer(), requestWithId)
            println("[MCP] Отправка запроса ID=$id, метод=${request.method}")
            println("[MCP] Полный запрос JSON: $requestJson")
            
            if (!requestJson.contains("\"jsonrpc\"")) {
                println("[MCP] ⚠️ ВНИМАНИЕ: поле jsonrpc отсутствует в запросе!")
                val fixedJson = requestJson.replaceFirst("{", "{\"jsonrpc\":\"2.0\",")
                println("[MCP] Исправленный запрос: $fixedJson")
                writer?.write(fixedJson)
            } else {
                writer?.write(requestJson)
            }
            writer?.write("\n")
            writer?.flush()
            println("[MCP] Запрос отправлен, ожидание ответа...")
            
            val response = withTimeout(30000) {
                deferred.await()
            }
            println("[MCP] Получен ответ на запрос ID=$id")
            return response
        } catch (e: TimeoutCancellationException) {
            pendingRequests.remove(id)
            println("[MCP] Таймаут ожидания ответа для запроса ID=$id")
            throw Exception("Таймаут ожидания ответа от MCP сервера")
        } catch (e: Exception) {
            pendingRequests.remove(id)
            println("[MCP] Ошибка отправки запроса: ${e.message}")
            e.printStackTrace()
            throw Exception("Ошибка отправки запроса: ${e.message}")
        }
    }
    
    private fun readResponses() {
        scope.launch(Dispatchers.IO) {
            try {
                var line: String?
                while (reader?.readLine().also { line = it } != null && process?.isAlive == true) {
                    line?.let { responseLine ->
                        val trimmedLine = responseLine.trim()
                        if (trimmedLine.isEmpty()) {
                            return@let
                        }
                        try {
                            println("[MCP] Получена строка: $trimmedLine")
                            val response = json.decodeFromString<MCPResponse>(trimmedLine)
                            response.id?.let { id ->
                                println("[MCP] Ответ для запроса ID=$id")
                                if (response.error != null) {
                                    println("[MCP] Ошибка в ответе: код=${response.error.code}, сообщение=${response.error.message}")
                                } else {
                                    println("[MCP] Успешный ответ")
                                }
                                pendingRequests[id]?.complete(response)
                                pendingRequests.remove(id)
                            } ?: run {
                                println("[MCP] Ответ без ID, игнорируем: $trimmedLine")
                            }
                        } catch (e: Exception) {
                            println("[MCP] Ошибка парсинга ответа: ${e.message}")
                            println("[MCP] Строка: $trimmedLine")
                            e.printStackTrace()
                        }
                    }
                }
                println("[MCP] Чтение ответов завершено (процесс завершен)")
            } catch (e: Exception) {
                println("[MCP] Ошибка чтения ответов от MCP сервера: ${e.message}")
                e.printStackTrace()
                pendingRequests.values.forEach { it.completeExceptionally(e) }
                pendingRequests.clear()
            }
        }
    }
}

