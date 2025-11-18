package com.qualiorstudio.aiadventultimate.ai

import com.qualiorstudio.aiadventultimate.api.DeepSeek
import com.qualiorstudio.aiadventultimate.api.DeepSeekMessage
import com.qualiorstudio.aiadventultimate.mcp.McpToolCall
import com.qualiorstudio.aiadventultimate.mcp.TodoistClient
import kotlinx.serialization.json.Json

class AIAgent(
    private val deepSeek: DeepSeek,
    private val todoistClient: TodoistClient
) {
    private val json = Json { ignoreUnknownKeys = true }
    
    private val systemPrompt = """
You are a helpful AI assistant with access to Todoist task management.

Available tools:
1. get_tasks - Get all tasks or filter by project
   Parameters: projectId (optional), filter (optional, e.g., "today", "priority 1")

2. create_task - Create a new task
   Parameters: content (required), description (optional), projectId (optional), priority (optional, 1-4), due (optional, e.g., "today", "tomorrow")

3. update_task - Update an existing task
   Parameters: taskId (required), content (optional), description (optional), priority (optional), due (optional)

4. close_task - Mark a task as completed
   Parameters: taskId (required)

5. delete_task - Delete a task
   Parameters: taskId (required)

6. get_projects - Get all projects

When a user asks to manage tasks, respond with a tool call in this format:
TOOL_CALL: {
  "name": "tool_name",
  "arguments": {
    "param1": "value1",
    "param2": "value2"
  }
}

If the user's request doesn't require tools, respond normally.
Always be helpful and friendly.
    """.trimIndent()

    suspend fun processMessage(
        userMessage: String,
        conversationHistory: List<DeepSeekMessage>
    ): String {
        val messages = mutableListOf(
            DeepSeekMessage(role = "system", content = systemPrompt)
        )
        messages.addAll(conversationHistory)
        messages.add(DeepSeekMessage(role = "user", content = userMessage))

        val response = deepSeek.sendMessage(messages)

        return if (response.contains("TOOL_CALL:")) {
            val toolCallResult = executeToolCall(response)
            
            val updatedMessages = messages.toMutableList()
            updatedMessages.add(DeepSeekMessage(role = "assistant", content = response))
            updatedMessages.add(DeepSeekMessage(role = "user", content = "Tool result: $toolCallResult"))
            
            deepSeek.sendMessage(updatedMessages)
        } else {
            response
        }
    }

    private suspend fun executeToolCall(response: String): String {
        return try {
            val toolCallText = response.substringAfter("TOOL_CALL:").trim()
            val toolCallJson = if (toolCallText.startsWith("```json")) {
                toolCallText.substringAfter("```json").substringBefore("```").trim()
            } else if (toolCallText.startsWith("```")) {
                toolCallText.substringAfter("```").substringBefore("```").trim()
            } else {
                toolCallText
            }
            
            val toolCall = json.decodeFromString<McpToolCall>(toolCallJson)
            val result = todoistClient.executeToolCall(toolCall)
            
            if (result.isError) {
                "Error: ${result.content}"
            } else {
                result.content
            }
        } catch (e: Exception) {
            "Error parsing or executing tool call: ${e.message}"
        }
    }

    suspend fun getAvailableTools(): String {
        return """
Available Todoist tools:
- get_tasks: Get all tasks
- create_task: Create a new task
- update_task: Update a task
- close_task: Complete a task
- delete_task: Delete a task
- get_projects: Get all projects

Ask me to manage your tasks!
        """.trimIndent()
    }
}

