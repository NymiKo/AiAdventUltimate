package com.qualiorstudio.aiadventultimate.utils

import com.qualiorstudio.aiadventultimate.model.Project
import java.io.File

object ProjectScanner {
    
    fun findMarkdownFiles(project: Project, maxFiles: Int = 100): List<String> {
        val markdownFiles = mutableListOf<String>()
        val projectDir = File(project.path)
        
        if (!projectDir.exists() || !projectDir.isDirectory) {
            return emptyList()
        }
        
        fun scanDirectory(dir: File, depth: Int = 0) {
            if (depth > 10 || markdownFiles.size >= maxFiles) {
                return
            }
            
            val files = dir.listFiles() ?: return
            
            for (file in files) {
                if (markdownFiles.size >= maxFiles) {
                    break
                }
                
                when {
                    file.isDirectory && !shouldSkipDirectory(file.name) -> {
                        scanDirectory(file, depth + 1)
                    }
                    file.isFile && isMarkdownFile(file.name) -> {
                        markdownFiles.add(file.absolutePath)
                    }
                }
            }
        }
        
        scanDirectory(projectDir)
        return markdownFiles
    }
    
    private fun isMarkdownFile(fileName: String): Boolean {
        val extension = fileName.substringAfterLast('.', "").lowercase()
        return extension in listOf("md", "markdown")
    }
    
    private fun shouldSkipDirectory(dirName: String): Boolean {
        val skipDirs = setOf(
            ".git",
            ".idea",
            ".gradle",
            "build",
            "node_modules",
            ".vscode",
            "target",
            "out",
            "bin",
            ".settings",
            "dist",
            ".cache"
        )
        return dirName in skipDirs || dirName.startsWith(".")
    }
}

