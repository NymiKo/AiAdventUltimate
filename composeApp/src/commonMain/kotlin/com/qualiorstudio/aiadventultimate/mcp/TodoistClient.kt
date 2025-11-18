package com.qualiorstudio.aiadventultimate.mcp

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.*

class TodoistClient(
    private val apiToken: String
) {
    private val client: HttpClient by lazy { createHttpClient() }
    private val baseUrl = "https://api.todoist.com/rest/v2"

    private fun createHttpClient(): HttpClient {
        return HttpClient {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    prettyPrint = true
                    isLenient = true
                })
            }
        }
    }

    suspend fun executeToolCall(toolCall: McpToolCall): McpToolResult {
        return try {
            when (toolCall.name) {
                "get_tasks" -> getTasks(toolCall.arguments)
                "create_task" -> createTask(toolCall.arguments)
                "update_task" -> updateTask(toolCall.arguments)
                "close_task" -> closeTask(toolCall.arguments)
                "delete_task" -> deleteTask(toolCall.arguments)
                "get_projects" -> getProjects()
                else -> McpToolResult(
                    content = "Unknown tool: ${toolCall.name}",
                    isError = true
                )
            }
        } catch (e: Exception) {
            McpToolResult(
                content = "Error executing ${toolCall.name}: ${e.message}",
                isError = true
            )
        }
    }

    private suspend fun getTasks(args: Map<String, String>): McpToolResult {
        val response = client.get("$baseUrl/tasks") {
            header("Authorization", "Bearer $apiToken")
            args["projectId"]?.let { parameter("project_id", it) }
            args["filter"]?.let { parameter("filter", it) }
        }

        return if (response.status == HttpStatusCode.OK) {
            val tasks = response.body<JsonArray>()
            McpToolResult(content = tasks.toString())
        } else {
            McpToolResult(
                content = "Failed to get tasks: ${response.status}",
                isError = true
            )
        }
    }

    private suspend fun createTask(args: Map<String, String>): McpToolResult {
        val content = args["content"] ?: return McpToolResult(
            content = "Task content is required",
            isError = true
        )

        val requestBody = buildJsonObject {
            put("content", content)
            args["description"]?.let { put("description", it) }
            args["projectId"]?.let { put("project_id", it) }
            args["priority"]?.let { put("priority", it.toIntOrNull() ?: 1) }
            args["due"]?.let { put("due_string", it) }
        }

        val response = client.post("$baseUrl/tasks") {
            header("Authorization", "Bearer $apiToken")
            contentType(ContentType.Application.Json)
            setBody(requestBody)
        }

        return if (response.status == HttpStatusCode.OK || response.status == HttpStatusCode.Created) {
            val task = response.body<JsonObject>()
            McpToolResult(content = "Task created: ${task["content"]?.toString() ?: "success"}")
        } else {
            McpToolResult(
                content = "Failed to create task: ${response.status} - ${response.bodyAsText()}",
                isError = true
            )
        }
    }

    private suspend fun updateTask(args: Map<String, String>): McpToolResult {
        val taskId = args["taskId"] ?: return McpToolResult(
            content = "Task ID is required",
            isError = true
        )

        val requestBody = buildJsonObject {
            args["content"]?.let { put("content", it) }
            args["description"]?.let { put("description", it) }
            args["priority"]?.let { put("priority", it.toIntOrNull() ?: 1) }
            args["due"]?.let { put("due_string", it) }
        }

        val response = client.post("$baseUrl/tasks/$taskId") {
            header("Authorization", "Bearer $apiToken")
            contentType(ContentType.Application.Json)
            setBody(requestBody)
        }

        return if (response.status == HttpStatusCode.OK || response.status == HttpStatusCode.NoContent) {
            McpToolResult(content = "Task updated successfully")
        } else {
            McpToolResult(
                content = "Failed to update task: ${response.status}",
                isError = true
            )
        }
    }

    private suspend fun closeTask(args: Map<String, String>): McpToolResult {
        val taskId = args["taskId"] ?: return McpToolResult(
            content = "Task ID is required",
            isError = true
        )

        val response = client.post("$baseUrl/tasks/$taskId/close") {
            header("Authorization", "Bearer $apiToken")
        }

        return if (response.status == HttpStatusCode.OK || response.status == HttpStatusCode.NoContent) {
            McpToolResult(content = "Task closed successfully")
        } else {
            McpToolResult(
                content = "Failed to close task: ${response.status}",
                isError = true
            )
        }
    }

    private suspend fun deleteTask(args: Map<String, String>): McpToolResult {
        val taskId = args["taskId"] ?: return McpToolResult(
            content = "Task ID is required",
            isError = true
        )

        val response = client.delete("$baseUrl/tasks/$taskId") {
            header("Authorization", "Bearer $apiToken")
        }

        return if (response.status == HttpStatusCode.OK || response.status == HttpStatusCode.NoContent) {
            McpToolResult(content = "Task deleted successfully")
        } else {
            McpToolResult(
                content = "Failed to delete task: ${response.status}",
                isError = true
            )
        }
    }

    private suspend fun getProjects(): McpToolResult {
        val response = client.get("$baseUrl/projects") {
            header("Authorization", "Bearer $apiToken")
        }

        return if (response.status == HttpStatusCode.OK) {
            val projects = response.body<JsonArray>()
            McpToolResult(content = projects.toString())
        } else {
            McpToolResult(
                content = "Failed to get projects: ${response.status}",
                isError = true
            )
        }
    }
}

