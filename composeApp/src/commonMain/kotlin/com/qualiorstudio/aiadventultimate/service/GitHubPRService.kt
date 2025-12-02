package com.qualiorstudio.aiadventultimate.service

import com.qualiorstudio.aiadventultimate.mcp.MCPServerManager
import kotlinx.serialization.json.*

interface GitHubPRService {
    suspend fun getOpenPullRequests(owner: String, repo: String, mcpManager: MCPServerManager?): List<PullRequest>
    suspend fun getPullRequestDiff(owner: String, repo: String, prNumber: Int, title: String, headBranch: String?, baseBranch: String?, mcpManager: MCPServerManager?): String?
    suspend fun addReviewComment(owner: String, repo: String, pullNumber: Int, comment: String, mcpManager: MCPServerManager?): Boolean
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
        title: String,
        headBranch: String?,
        baseBranch: String?,
        mcpManager: MCPServerManager?
    ): String? {
        if (mcpManager == null) {
            return null
        }
        
        return try {
            val availableTools = mcpManager.getAvailableTools().map { it.function.name }
            println("=== Доступные инструменты для получения diff ===")
            availableTools.forEach { println("  - $it") }
            
            val diffTool = availableTools.find { 
                it.equals("pull_request_read", ignoreCase = true) ||
                it.contains("pull_request_read", ignoreCase = true)
            } ?: availableTools.find {
                it.contains("pull_request", ignoreCase = true) && 
                (it.contains("read", ignoreCase = true) || it.contains("get", ignoreCase = true)) &&
                !it.contains("create", ignoreCase = true) &&
                !it.contains("list", ignoreCase = true)
            } ?: availableTools.find {
                it.contains("github_get_pull_request", ignoreCase = true)
            } ?: availableTools.find {
                it.contains("get_pull_request", ignoreCase = true) && 
                it.contains("github", ignoreCase = true) &&
                !it.contains("create", ignoreCase = true)
            }
            
            if (diffTool == null) {
                println("GitHub diff tool not found. Available tools: ${availableTools.joinToString()}")
                return null
            }
            
            println("=== Используется инструмент для diff: $diffTool ===")
            
            val toolInfo = mcpManager.getAvailableTools().find { it.function.name == diffTool }
            if (toolInfo != null) {
                println("Описание инструмента: ${toolInfo.function.description}")
                println("Параметры инструмента: ${toolInfo.function.parameters}")
            }
            
            val arguments = buildJsonObject {
                put("owner", owner)
                put("repo", repo)
                put("pullNumber", prNumber)
                put("method", "get_diff")
            }
            
            val alternativeArguments = buildJsonObject {
                put("owner", owner)
                put("repo", repo)
                put("pull_number", prNumber)
                put("method", "get_diff")
            }
            
            val alternativeArguments2 = buildJsonObject {
                put("owner", owner)
                put("repo", repo)
                put("number", prNumber)
                put("method", "get_diff")
            }
            
            val argumentsToUse = arguments
            
            var result = try {
                println("Вызов инструмента с параметрами: owner=$owner, repo=$repo, pullNumber=$prNumber, method=get_diff")
                mcpManager.callTool(diffTool, arguments)
            } catch (e: Exception) {
                println("Ошибка при вызове с pullNumber, пробую с pull_number: ${e.message}")
                try {
                    mcpManager.callTool(diffTool, alternativeArguments)
                } catch (e2: Exception) {
                    println("Ошибка при вызове с pull_number, пробую с number: ${e2.message}")
                    try {
                        mcpManager.callTool(diffTool, alternativeArguments2)
                    } catch (e3: Exception) {
                        println("Все варианты вызова не удались: ${e3.message}")
                        throw e
                    }
                }
            }
            
            println("=== Результат diff от MCP сервера ===")
            println("Тип результата: ${result::class.simpleName}")
            println("Результат (первые 500 символов): ${result.toString().take(500)}")
            
            when (result) {
                is JsonObject -> {
                    println("Парсинг JsonObject, ключи: ${result.keys.joinToString()}")
                    
                    val content = result["content"]
                    if (content != null) {
                        when (content) {
                            is JsonPrimitive -> {
                                val contentStr = content.jsonPrimitive.content
                                val type = result["type"]?.jsonPrimitive?.content
                                if (type == "text") {
                                    println("Найден content как text")
                                    return contentStr
                                }
                                try {
                                    val parsed = Json.parseToJsonElement(contentStr)
                                    val diffFromParsed = extractDiffFromJson(parsed)
                                    if (diffFromParsed != null) return diffFromParsed
                                } catch (e: Exception) {
                                    println("Не удалось распарсить content как JSON: ${e.message}")
                                }
                            }
                            is JsonObject -> {
                                val diffFromContent = extractDiffFromJson(content)
                                if (diffFromContent != null) return diffFromContent
                            }
                            is JsonArray -> {
                                content.forEach { element ->
                                    if (element is JsonObject) {
                                        val diffFromElement = extractDiffFromJson(element)
                                        if (diffFromElement != null) return diffFromElement
                                    }
                                }
                            }
                        }
                    }
                    
                    val text = result["text"]?.jsonPrimitive?.content
                    if (text != null) {
                        println("Найден text")
                        try {
                            val parsed = Json.parseToJsonElement(text)
                            val diffFromParsed = extractDiffFromJson(parsed)
                            if (diffFromParsed != null) return diffFromParsed
                        } catch (e: Exception) {
                            return text
                        }
                    }
                    
                    val diff = result["diff"]?.jsonPrimitive?.content
                    if (diff != null) {
                        println("Найден diff")
                        return diff
                    }
                    
                    val patch = result["patch"]?.jsonPrimitive?.content
                    if (patch != null) {
                        println("Найден patch")
                        return patch
                    }
                    
                    val diffUrl = result["diff_url"]?.jsonPrimitive?.content
                    if (diffUrl != null) {
                        println("Найден diff_url: $diffUrl")
                    }
                    
                    val patchUrl = result["patch_url"]?.jsonPrimitive?.content
                    if (patchUrl != null) {
                        println("Найден patch_url: $patchUrl")
                    }
                    
                    val diffFromObject = extractDiffFromJson(result)
                    if (diffFromObject != null) return diffFromObject
                }
                is JsonPrimitive -> {
                    val content = result.jsonPrimitive.content
                    println("Результат - JsonPrimitive, длина: ${content.length}")
                    try {
                        val parsed = Json.parseToJsonElement(content)
                        val diffFromParsed = extractDiffFromJson(parsed)
                        if (diffFromParsed != null) return diffFromParsed
                    } catch (e: Exception) {
                        return content
                    }
                }
                is JsonArray -> {
                    println("Результат - JsonArray, элементов: ${result.size}")
                    result.forEachIndexed { index, element ->
                        if (element is JsonObject) {
                            val type = element["type"]?.jsonPrimitive?.content
                            val text = element["text"]?.jsonPrimitive?.content
                            if (type == "text" && text != null) {
                                println("Найден text в элементе $index")
                                try {
                                    val parsed = Json.parseToJsonElement(text)
                                    val diffFromParsed = extractDiffFromJson(parsed)
                                    if (diffFromParsed != null) return diffFromParsed
                                } catch (e: Exception) {
                                    return text
                                }
                            } else {
                                val diffFromElement = extractDiffFromJson(element)
                                if (diffFromElement != null) return diffFromElement
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
    
    private fun extractDiffFromJson(element: JsonElement): String? {
        when (element) {
            is JsonObject -> {
                val diff = element["diff"]?.jsonPrimitive?.content
                if (diff != null) return diff
                
                val patch = element["patch"]?.jsonPrimitive?.content
                if (patch != null) return patch
                
                val files = element["files"] as? JsonArray
                if (files != null) {
                    val diffParts = mutableListOf<String>()
                    files.forEach { fileElement ->
                        if (fileElement is JsonObject) {
                            val filename = fileElement["filename"]?.jsonPrimitive?.content ?: "unknown"
                            val patch = fileElement["patch"]?.jsonPrimitive?.content
                            if (patch != null) {
                                diffParts.add("--- $filename\n+++ $filename\n$patch")
                            }
                        }
                    }
                    if (diffParts.isNotEmpty()) {
                        return diffParts.joinToString("\n\n")
                    }
                }
                
                val content = element["content"]?.jsonPrimitive?.content
                if (content != null && (content.startsWith("diff") || content.startsWith("---") || content.contains("@@@"))) {
                    return content
                }
            }
            is JsonArray -> {
                element.forEach { item ->
                    val diff = extractDiffFromJson(item)
                    if (diff != null) return diff
                }
            }
            is JsonPrimitive -> {
                val content = element.jsonPrimitive.content
                if (content.startsWith("diff") || content.startsWith("---") || content.contains("@@@")) {
                    return content
                }
            }
            else -> {}
        }
        return null
    }
    
    override suspend fun addReviewComment(
        owner: String,
        repo: String,
        pullNumber: Int,
        comment: String,
        mcpManager: MCPServerManager?
    ): Boolean {
        if (mcpManager == null) {
            return false
        }
        
        return try {
            val availableTools = mcpManager.getAvailableTools().map { it.function.name }
            println("=== Доступные инструменты для добавления комментария ===")
            availableTools.forEach { println("  - $it") }
            
            val issueCommentTool = availableTools.find {
                it.equals("add_issue_comment", ignoreCase = true) ||
                it.contains("add_issue_comment", ignoreCase = true)
            }
            
            val createReviewTool = availableTools.find { 
                it.equals("pull_request_review_write", ignoreCase = true) ||
                it.contains("pull_request_review_write", ignoreCase = true)
            }
            
            if (issueCommentTool != null) {
                println("=== Используется add_issue_comment для добавления комментария ===")
                val issueCommentArguments = buildJsonObject {
                    put("owner", owner)
                    put("repo", repo)
                    put("issue_number", pullNumber)
                    put("body", comment)
                }
                
                val result = try {
                    mcpManager.callTool(issueCommentTool, issueCommentArguments)
                } catch (e: Exception) {
                    println("Ошибка при добавлении комментария через issue_comment: ${e.message}")
                    e.printStackTrace()
                    null
                }
                
                if (result != null) {
                    println("=== Результат добавления комментария через issue_comment ===")
                    println("Тип результата: ${result::class.simpleName}")
                    println("Результат: $result")
                    
                    var success = false
                    when (result) {
                        is JsonObject -> {
                            val htmlUrl = result["html_url"]?.jsonPrimitive?.content
                            val url = result["url"]?.jsonPrimitive?.content
                            if (htmlUrl != null || url != null) {
                                println("Комментарий успешно добавлен: ${htmlUrl ?: url}")
                                success = true
                            } else {
                                val id = result["id"]?.jsonPrimitive?.content
                                if (id != null) {
                                    println("Комментарий успешно добавлен, ID: $id")
                                    success = true
                                }
                            }
                        }
                        is JsonArray -> {
                            result.forEach { element ->
                                if (element is JsonObject) {
                                    val type = element["type"]?.jsonPrimitive?.content
                                    val text = element["text"]?.jsonPrimitive?.content
                                    if (type == "text" && text != null) {
                                        if (text.contains("error", ignoreCase = true) || text.contains("failed", ignoreCase = true)) {
                                            println("Ошибка при добавлении комментария: $text")
                                        } else {
                                            println("Комментарий добавлен: $text")
                                            success = true
                                        }
                                    }
                                }
                            }
                        }
                        else -> {
                            println("Комментарий добавлен (неожиданный формат ответа)")
                            success = true
                        }
                    }
                    
                    if (success) {
                        return true
                    }
                }
            }
            
            if (createReviewTool != null) {
                println("=== Пробую создать review с комментарием через pull_request_review_write ===")
                val reviewArguments = buildJsonObject {
                    put("owner", owner)
                    put("repo", repo)
                    put("pullNumber", pullNumber)
                    put("method", "create")
                    put("body", comment)
                    put("event", "COMMENT")
                }
                
                val result = try {
                    mcpManager.callTool(createReviewTool, reviewArguments)
                } catch (e: Exception) {
                    println("Ошибка при создании review с комментарием: ${e.message}")
                    e.printStackTrace()
                    null
                }
                
                if (result != null) {
                    println("=== Результат создания review ===")
                    println("Тип результата: ${result::class.simpleName}")
                    println("Результат: $result")
                    
                    var success = false
                    when (result) {
                        is JsonObject -> {
                            val htmlUrl = result["html_url"]?.jsonPrimitive?.content
                            val url = result["url"]?.jsonPrimitive?.content
                            if (htmlUrl != null || url != null) {
                                println("Review с комментарием успешно создан: ${htmlUrl ?: url}")
                                success = true
                            } else {
                                val id = result["id"]?.jsonPrimitive?.content
                                if (id != null) {
                                    println("Review с комментарием успешно создан, ID: $id")
                                    success = true
                                }
                            }
                        }
                        is JsonArray -> {
                            result.forEach { element ->
                                if (element is JsonObject) {
                                    val type = element["type"]?.jsonPrimitive?.content
                                    val text = element["text"]?.jsonPrimitive?.content
                                    if (type == "text" && text != null) {
                                        if (text.contains("error", ignoreCase = true) || text.contains("failed", ignoreCase = true)) {
                                            println("Ошибка при создании review: $text")
                                        } else {
                                            println("Review создан: $text")
                                            success = true
                                        }
                                    }
                                }
                            }
                        }
                        else -> {
                            println("Review создан (неожиданный формат ответа)")
                            success = true
                        }
                    }
                    
                    if (success) {
                        return true
                    }
                }
            }
            
            println("Не удалось добавить комментарий ни одним из способов")
            false
        } catch (e: Exception) {
            println("Ошибка при добавлении комментария через MCP: ${e.message}")
            e.printStackTrace()
            false
        }
    }
}

fun createGitHubPRService(): GitHubPRService = GitHubPRServiceImpl()

