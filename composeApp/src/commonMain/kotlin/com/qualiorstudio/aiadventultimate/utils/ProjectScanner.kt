package com.qualiorstudio.aiadventultimate.utils

import com.qualiorstudio.aiadventultimate.model.Project
import java.io.File

object ProjectScanner {
    
    fun findProjectFiles(project: Project, maxFiles: Int = 1000): List<String> {
        val projectFiles = mutableListOf<String>()
        val projectDir = File(project.path)
        
        if (!projectDir.exists() || !projectDir.isDirectory) {
            return emptyList()
        }
        
        fun scanDirectory(dir: File, depth: Int = 0) {
            if (depth > 15 || projectFiles.size >= maxFiles) {
                return
            }
            
            val files = dir.listFiles() ?: return
            
            for (file in files) {
                if (projectFiles.size >= maxFiles) {
                    break
                }
                
                when {
                    file.isDirectory && !shouldSkipDirectory(file.name) -> {
                        scanDirectory(file, depth + 1)
                    }
                    file.isFile && !shouldSkipFile(file.name) -> {
                        projectFiles.add(file.absolutePath)
                    }
                }
            }
        }
        
        scanDirectory(projectDir)
        return projectFiles
    }
    
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
            ".cache",
            ".mvn",
            "venv",
            "__pycache__",
            ".pytest_cache",
            ".tox",
            ".coverage",
            "coverage",
            ".nyc_output",
            ".next",
            ".nuxt",
            ".output",
            "iosApp",
            "androidApp"
        )
        return dirName in skipDirs || (dirName.startsWith(".") && dirName != ".github")
    }
    
    private fun shouldSkipFile(fileName: String): Boolean {
        val skipExtensions = setOf(
            "class",
            "jar",
            "war",
            "ear",
            "zip",
            "tar",
            "gz",
            "7z",
            "rar",
            "exe",
            "dll",
            "so",
            "dylib",
            "o",
            "a",
            "lib",
            "bin",
            "dat",
            "db",
            "sqlite",
            "sqlite3",
            "log",
            "tmp",
            "temp",
            "cache",
            "lock",
            "pid",
            "swp",
            "swo",
            "DS_Store",
            "ico",
            "png",
            "jpg",
            "jpeg",
            "gif",
            "svg",
            "webp",
            "bmp",
            "tiff",
            "ico",
            "woff",
            "woff2",
            "ttf",
            "eot",
            "otf",
            "mp3",
            "mp4",
            "avi",
            "mov",
            "wmv",
            "flv",
            "webm",
            "mkv"
        )
        
        val extension = fileName.substringAfterLast('.', "").lowercase()
        
        if (extension in skipExtensions) {
            return true
        }
        
        val skipNames = setOf(
            ".gitignore",
            ".gitattributes",
            ".gitkeep",
            ".DS_Store",
            "Thumbs.db",
            ".classpath",
            ".project",
            ".settings"
        )
        
        return fileName in skipNames || fileName.startsWith(".")
    }
}

