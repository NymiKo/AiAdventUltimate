package com.qualiorstudio.aiadventultimate.service

import com.qualiorstudio.aiadventultimate.mcp.MCPServerManager
import kotlinx.serialization.json.*

interface GitHubPRService {
    suspend fun getOpenPullRequests(owner: String, repo: String, mcpManager: MCPServerManager?): List<PullRequest>
    suspend fun getPullRequestDiff(owner: String, repo: String, prNumber: Int, mcpManager: MCPServerManager?): String?
}

data class PullRequest(
    val number: Int,
    val title: String,
    val body: String?,
    val state: String,
    val author: String?,
    val createdAt: String?,
    val updatedAt: String?,
    val url: String?,
    val headBranch: String?,
    val baseBranch: String?
)

class GitHubPRServiceImpl : GitHubPRService {
    
    override suspend fun getOpenPullRequests(
        owner: String,
        repo: String,
        mcpManager: MCPServerManager?
    ): List<PullRequest> {
        if (mcpManager == null) {
            return emptyList()
        }
        
        return try {
            val availableTools = mcpManager.getAvailableTools().map { it.function.name }
            println("=== Доступные инструменты GitHub MCP ===")
            availableTools.forEach { println("  - $it") }
            
            val githubPRTool = availableTools.find { 
                it.contains("pull", ignoreCase = true) && 
                it.contains("request", ignoreCase = true) &&
                (it.contains("list", ignoreCase = true) || it.contains("search", ignoreCase = true))
            } ?: availableTools.find { 
                it.contains("github", ignoreCase = true) && 
                it.contains("pull", ignoreCase = true)
            } ?: availableTools.find {
                it.contains("list_pull_requests", ignoreCase = true) || 
                it.contains("listPullRequests", ignoreCase = true) ||
                it.contains("github_list_pull_requests", ignoreCase = true)
            }
            
            if (githubPRTool == null) {
                println("GitHub PR tool not found. Available tools: ${availableTools.joinToString()}")
                return emptyList()
            }
            
            println("=== Используется инструмент: $githubPRTool ===")
            
            val arguments = buildJsonObject {
                put("owner", owner)
                put("repo", repo)
                put("state", "open")
            }
            
            println("=== Аргументы для вызова: $arguments ===")
            
            val result = mcpManager.callTool(githubPRTool, arguments)
            
            println("=== Результат от MCP сервера ===")
            println("Тип результата: ${result::class.simpleName}")
            println("Результат: $result")
            
            parsePullRequests(result)
        } catch (e: Exception) {
            println("Ошибка при получении PR через MCP: ${e.message}")
            e.printStackTrace()
            emptyList()
        }
    }
    
    private fun parsePullRequests(result: JsonElement): List<PullRequest> {
        val prs = mutableListOf<PullRequest>()
        
        println("=== Парсинг результата ===")
        
        when (result) {
            is JsonArray -> {
                println("Результат - JsonArray, элементов: ${result.size}")
                result.forEachIndexed { index, element ->
                    println("  Элемент $index: ${element::class.simpleName}")
                    if (element is JsonObject) {
                        val type = element["type"]?.jsonPrimitive?.content
                        val text = element["text"]?.jsonPrimitive?.content
                        
                        if (type == "text" && text != null) {
                            println("  Найден text элемент, парсим JSON из строки")
                            try {
                                val parsedJson = Json.parseToJsonElement(text)
                                println("  Распарсенный JSON: ${parsedJson::class.simpleName}")
                                when (parsedJson) {
                                    is JsonArray -> {
                                        parsedJson.forEach { prElement ->
                                            if (prElement is JsonObject) {
                                                prs.add(parsePullRequest(prElement))
                                            }
                                        }
                                    }
                                    is JsonObject -> {
                                        prs.add(parsePullRequest(parsedJson))
                                    }
                                    else -> {
                                        println("  Неожиданный тип распарсенного JSON: ${parsedJson::class.simpleName}")
                                    }
                                }
                            } catch (e: Exception) {
                                println("  Ошибка парсинга text как JSON: ${e.message}")
                                e.printStackTrace()
                            }
                        } else {
                            println("  Парсинг PR из элемента $index (не text элемент)")
                            prs.add(parsePullRequest(element))
                        }
                    }
                }
            }
            is JsonObject -> {
                println("Результат - JsonObject")
                println("Ключи: ${result.keys.joinToString()}")
                
                val content = result["content"]
                if (content != null) {
                    println("Найден ключ 'content': ${content::class.simpleName}")
                    when (content) {
                        is JsonArray -> {
                            content.forEach { element ->
                                if (element is JsonObject) {
                                    prs.add(parsePullRequest(element))
                                }
                            }
                        }
                        is JsonObject -> {
                            prs.add(parsePullRequest(content))
                        }
                        is JsonPrimitive -> {
                            val contentStr = content.jsonPrimitive.content
                            println("Content - строка, пытаемся распарсить JSON")
                            try {
                                val parsed = Json.parseToJsonElement(contentStr)
                                parsePullRequests(parsed).forEach { prs.add(it) }
                            } catch (e: Exception) {
                                println("Ошибка парсинга content как JSON: ${e.message}")
                            }
                        }
                        else -> {}
                    }
                }
                
                val items = result["items"] as? JsonArray
                if (items != null) {
                    println("Найден ключ 'items', элементов: ${items.size}")
                    items.forEach { element ->
                        if (element is JsonObject) {
                            prs.add(parsePullRequest(element))
                        }
                    }
                }
                
                val data = result["data"] as? JsonArray
                if (data != null) {
                    println("Найден ключ 'data', элементов: ${data.size}")
                    data.forEach { element ->
                        if (element is JsonObject) {
                            prs.add(parsePullRequest(element))
                        }
                    }
                }
                
                val pullRequests = result["pull_requests"] as? JsonArray
                if (pullRequests != null) {
                    println("Найден ключ 'pull_requests', элементов: ${pullRequests.size}")
                    pullRequests.forEach { element ->
                        if (element is JsonObject) {
                            prs.add(parsePullRequest(element))
                        }
                    }
                }
                
                if (prs.isEmpty() && result.containsKey("number")) {
                    println("Результат похож на один PR, парсим как объект PR")
                    prs.add(parsePullRequest(result))
                }
            }
            is JsonPrimitive -> {
                println("Результат - JsonPrimitive: ${result.jsonPrimitive.content}")
                try {
                    val parsed = Json.parseToJsonElement(result.jsonPrimitive.content)
                    parsePullRequests(parsed).forEach { prs.add(it) }
                } catch (e: Exception) {
                    println("Ошибка парсинга primitive как JSON: ${e.message}")
                }
            }
            is JsonNull -> {
                println("Результат - JsonNull")
            }
        }
        
        println("=== Найдено PR: ${prs.size} ===")
        prs.forEachIndexed { index, pr ->
            println("  PR $index: #${pr.number} - ${pr.title}")
        }
        
        return prs
    }
    
    private fun parsePullRequest(obj: JsonObject): PullRequest {
        println("=== Парсинг PR объекта ===")
        println("Ключи объекта: ${obj.keys.joinToString()}")
        
        val number = obj["number"]?.let {
            when (it) {
                is JsonPrimitive -> it.content.toIntOrNull() ?: 0
                else -> 0
            }
        } ?: 0
        
        val title = obj["title"]?.let {
            when (it) {
                is JsonPrimitive -> it.content
                else -> ""
            }
        } ?: ""
        
        val body = obj["body"]?.let {
            when (it) {
                is JsonPrimitive -> it.content
                else -> null
            }
        }
        
        val state = obj["state"]?.let {
            when (it) {
                is JsonPrimitive -> it.content
                else -> "open"
            }
        } ?: "open"
        
        val user = obj["user"] as? JsonObject
        val author = user?.get("login")?.let {
            when (it) {
                is JsonPrimitive -> it.content
                else -> null
            }
        }
        
        val createdAt = obj["created_at"]?.let {
            when (it) {
                is JsonPrimitive -> it.content
                else -> null
            }
        }
        
        val updatedAt = obj["updated_at"]?.let {
            when (it) {
                is JsonPrimitive -> it.content
                else -> null
            }
        }
        
        val htmlUrl = obj["html_url"]?.let {
            when (it) {
                is JsonPrimitive -> it.content
                else -> null
            }
        }
        
        val url = htmlUrl ?: obj["url"]?.let {
            when (it) {
                is JsonPrimitive -> it.content
                else -> null
            }
        }
        
        val head = obj["head"] as? JsonObject
        val headBranch = head?.get("ref")?.let {
            when (it) {
                is JsonPrimitive -> it.content
                else -> null
            }
        }
        
        val base = obj["base"] as? JsonObject
        val baseBranch = base?.get("ref")?.let {
            when (it) {
                is JsonPrimitive -> it.content
                else -> null
            }
        }
        
        println("Распарсено: #$number - $title (автор: $author)")
        
        return PullRequest(
            number = number,
            title = title,
            body = body,
            state = state,
            author = author,
            createdAt = createdAt,
            updatedAt = updatedAt,
            url = url,
            headBranch = headBranch,
            baseBranch = baseBranch
        )
    }
    
    override suspend fun getPullRequestDiff(
        owner: String,
        repo: String,
        prNumber: Int,
        mcpManager: MCPServerManager?
    ): String? {
        if (mcpManager == null) {
            return null
        }
        
        return try {
            val availableTools = mcpManager.getAvailableTools().map { it.function.name }
            
            val diffTool = availableTools.find { 
                it.contains("pull", ignoreCase = true) && 
                (it.contains("diff", ignoreCase = true) || it.contains("patch", ignoreCase = true))
            } ?: availableTools.find {
                it.contains("github", ignoreCase = true) && 
                (it.contains("diff", ignoreCase = true) || it.contains("patch", ignoreCase = true))
            } ?: availableTools.find {
                it.contains("get_pull_request", ignoreCase = true) || 
                it.contains("getPullRequest", ignoreCase = true)
            }
            
            if (diffTool == null) {
                println("GitHub diff tool not found. Available tools: ${availableTools.joinToString()}")
                return null
            }
            
            println("=== Используется инструмент для diff: $diffTool ===")
            
            val arguments = buildJsonObject {
                put("owner", owner)
                put("repo", repo)
                put("pull_number", prNumber)
            }
            
            val result = mcpManager.callTool(diffTool, arguments)
            
            println("=== Результат diff от MCP сервера ===")
            println("Тип результата: ${result::class.simpleName}")
            
            when (result) {
                is JsonObject -> {
                    val content = result["content"]
                    if (content != null && content is JsonPrimitive) {
                        val contentStr = content.jsonPrimitive.content
                        val type = result["type"]?.jsonPrimitive?.content
                        if (type == "text") {
                            return contentStr
                        }
                    }
                    val text = result["text"]?.jsonPrimitive?.content
                    if (text != null) {
                        return text
                    }
                    val diff = result["diff"]?.jsonPrimitive?.content
                    if (diff != null) {
                        return diff
                    }
                    val patch = result["patch"]?.jsonPrimitive?.content
                    if (patch != null) {
                        return patch
                    }
                }
                is JsonPrimitive -> {
                    return result.jsonPrimitive.content
                }
                is JsonArray -> {
                    result.forEach { element ->
                        if (element is JsonObject) {
                            val type = element["type"]?.jsonPrimitive?.content
                            val text = element["text"]?.jsonPrimitive?.content
                            if (type == "text" && text != null) {
                                return text
                            }
                        }
                    }
                }
                else -> {}
            }
            
            null
        } catch (e: Exception) {
            println("Ошибка при получении diff через MCP: ${e.message}")
            e.printStackTrace()
            null
        }
    }
}

fun createGitHubPRService(): GitHubPRService = GitHubPRServiceImpl()

