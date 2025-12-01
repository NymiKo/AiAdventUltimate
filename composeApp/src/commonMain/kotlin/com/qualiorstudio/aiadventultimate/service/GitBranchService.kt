package com.qualiorstudio.aiadventultimate.service

import com.qualiorstudio.aiadventultimate.mcp.MCPServerManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import java.io.File

interface GitBranchService {
    suspend fun getCurrentBranch(projectPath: String): String?
    suspend fun getGitHubBranchInfo(projectPath: String, mcpManager: MCPServerManager?): GitHubBranchInfo?
}

data class GitHubBranchInfo(
    val branch: String,
    val isGitHubRepo: Boolean = false,
    val owner: String? = null,
    val repo: String? = null
)

class GitBranchServiceImpl : GitBranchService {
    
    override suspend fun getCurrentBranch(projectPath: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                val gitDir = File(projectPath, ".git")
                if (!gitDir.exists() || !gitDir.isDirectory) {
                    return@withContext null
                }
                
                val headFile = File(gitDir, "HEAD")
                if (!headFile.exists()) {
                    return@withContext null
                }
                
                val headContent = headFile.readText().trim()
                if (headContent.startsWith("ref: refs/heads/")) {
                    return@withContext headContent.removePrefix("ref: refs/heads/")
                } else {
                    return@withContext headContent.take(7)
                }
            } catch (e: Exception) {
                println("Ошибка при получении текущей ветки: ${e.message}")
                null
            }
        }
    }
    
    override suspend fun getGitHubBranchInfo(
        projectPath: String,
        mcpManager: MCPServerManager?
    ): GitHubBranchInfo? {
        val branch = getCurrentBranch(projectPath) ?: return null
        
        if (mcpManager == null) {
            return GitHubBranchInfo(branch = branch)
        }
        
        val remoteUrl = getRemoteUrl(projectPath) ?: return GitHubBranchInfo(branch = branch)
        val (owner, repo) = parseGitHubUrl(remoteUrl) ?: return GitHubBranchInfo(branch = branch)
        
        val availableTools = try {
            mcpManager.getAvailableTools().map { it.function.name }
        } catch (e: Exception) {
            println("Ошибка при получении списка инструментов MCP: ${e.message}")
            return GitHubBranchInfo(branch = branch, isGitHubRepo = true, owner = owner, repo = repo)
        }
        
        val hasGitHubTools = availableTools.any { it.contains("github", ignoreCase = true) }
        
        if (!hasGitHubTools) {
            return GitHubBranchInfo(branch = branch, isGitHubRepo = true, owner = owner, repo = repo)
        }
        
        return GitHubBranchInfo(
            branch = branch,
            isGitHubRepo = true,
            owner = owner,
            repo = repo
        )
    }
    
    private suspend fun getRemoteUrl(projectPath: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                val gitConfig = File(projectPath, ".git/config")
                if (!gitConfig.exists()) {
                    return@withContext null
                }
                
                gitConfig.readLines().forEach { line ->
                    if (line.trim().startsWith("url = ")) {
                        return@withContext line.trim().removePrefix("url = ")
                    }
                }
                null
            } catch (e: Exception) {
                null
            }
        }
    }
    
    private fun parseGitHubUrl(url: String): Pair<String, String>? {
        val patterns = listOf(
            Regex("github\\.com[:/]([^/]+)/([^/\\.]+)(?:\\.git)?$"),
            Regex("git@github\\.com:([^/]+)/([^/\\.]+)(?:\\.git)?$")
        )
        
        patterns.forEach { pattern ->
            val match = pattern.find(url)
            if (match != null) {
                val owner = match.groupValues[1]
                val repo = match.groupValues[2]
                return Pair(owner, repo)
            }
        }
        
        return null
    }
}

fun createGitBranchService(): GitBranchService = GitBranchServiceImpl()

