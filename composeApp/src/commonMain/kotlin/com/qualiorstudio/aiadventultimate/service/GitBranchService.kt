package com.qualiorstudio.aiadventultimate.service

import com.qualiorstudio.aiadventultimate.mcp.MCPServerManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import java.io.File

interface GitBranchService {
    suspend fun getCurrentBranch(projectPath: String): String?
    suspend fun getGitHubBranchInfo(projectPath: String, mcpManager: MCPServerManager?): GitHubBranchInfo?
    suspend fun getHeadFileLastModified(projectPath: String): Long?
    suspend fun getBranches(projectPath: String, mcpManager: MCPServerManager?): BranchList?
}

data class GitHubBranchInfo(
    val branch: String,
    val isGitHubRepo: Boolean = false,
    val owner: String? = null,
    val repo: String? = null
)

data class BranchList(
    val localBranches: List<String>,
    val remoteBranches: List<String>
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
    
    override suspend fun getHeadFileLastModified(projectPath: String): Long? {
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
                
                headFile.lastModified()
            } catch (e: Exception) {
                null
            }
        }
    }
    
    override suspend fun getBranches(projectPath: String, mcpManager: MCPServerManager?): BranchList? {
        return withContext(Dispatchers.IO) {
            try {
                val gitDir = File(projectPath, ".git")
                if (!gitDir.exists() || !gitDir.isDirectory) {
                    return@withContext null
                }
                
                val localBranches = mutableListOf<String>()
                val remoteBranches = mutableListOf<String>()
                
                val refsHeads = File(gitDir, "refs/heads")
                if (refsHeads.exists() && refsHeads.isDirectory) {
                    refsHeads.listFiles()?.forEach { branchFile ->
                        if (branchFile.isFile) {
                            localBranches.add(branchFile.name)
                        }
                    }
                }
                
                val refsRemotes = File(gitDir, "refs/remotes")
                if (refsRemotes.exists() && refsRemotes.isDirectory) {
                    refsRemotes.listFiles()?.forEach { remoteDir ->
                        if (remoteDir.isDirectory) {
                            remoteDir.listFiles()?.forEach { branchFile ->
                                if (branchFile.isFile) {
                                    remoteBranches.add("${remoteDir.name}/${branchFile.name}")
                                }
                            }
                        }
                    }
                }
                
                if (mcpManager != null) {
                    val branchInfo = getGitHubBranchInfo(projectPath, mcpManager)
                    if (branchInfo?.isGitHubRepo == true && branchInfo.owner != null && branchInfo.repo != null) {
                        try {
                            val availableTools = mcpManager.getAvailableTools().map { it.function.name }
                            val githubListBranchesTool = availableTools.find { 
                                it.contains("list", ignoreCase = true) && 
                                it.contains("branch", ignoreCase = true) &&
                                it.contains("github", ignoreCase = true)
                            } ?: availableTools.find { 
                                it.contains("github", ignoreCase = true) && 
                                it.contains("branch", ignoreCase = true)
                            }
                            
                            if (githubListBranchesTool != null) {
                                val arguments = buildJsonObject {
                                    put("owner", branchInfo.owner)
                                    put("repo", branchInfo.repo)
                                }
                                
                                val result = mcpManager.callTool(githubListBranchesTool, arguments)
                                
                                if (result is JsonObject) {
                                    val branchesArray = result["branches"] as? JsonArray
                                    branchesArray?.forEach { branchElement ->
                                        if (branchElement is JsonObject) {
                                            val name = branchElement["name"]?.jsonPrimitive?.content
                                            val remote = branchElement["remote"]?.jsonPrimitive?.content?.toBoolean() ?: false
                                            if (name != null) {
                                                if (remote) {
                                                    if (!remoteBranches.contains(name)) {
                                                        remoteBranches.add(name)
                                                    }
                                                } else {
                                                    if (!localBranches.contains(name)) {
                                                        localBranches.add(name)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            println("Ошибка при получении веток через MCP: ${e.message}")
                        }
                    }
                }
                
                BranchList(
                    localBranches = localBranches.sorted(),
                    remoteBranches = remoteBranches.sorted()
                )
            } catch (e: Exception) {
                println("Ошибка при получении списка веток: ${e.message}")
                null
            }
        }
    }
}

fun createGitBranchService(): GitBranchService = GitBranchServiceImpl()

