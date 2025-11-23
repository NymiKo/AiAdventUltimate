package com.qualiorstudio.aiadventultimate.utils

import java.io.File

actual fun loadEnvFile(): Map<String, String> {
    val envMap = mutableMapOf<String, String>()
    
    val currentDir = System.getProperty("user.dir")
    val userHome = System.getProperty("user.home")
    
    val possiblePaths = mutableListOf<File>()
    
    possiblePaths.add(File(".env"))
    possiblePaths.add(File(currentDir, ".env"))
    possiblePaths.add(File(userHome, ".env"))
    
    try {
        val parentDir = File(currentDir).parentFile
        if (parentDir != null) {
            possiblePaths.add(File(parentDir, ".env"))
        }
    } catch (e: Exception) {
    }
    
    try {
        val projectRoot = File(currentDir).canonicalFile
        if (projectRoot.name == "AiAdventUltimate") {
            possiblePaths.add(File(projectRoot, ".env"))
        }
    } catch (e: Exception) {
    }
    
    val envFile = possiblePaths.firstOrNull { 
        try {
            it.exists() && it.isFile && it.canRead()
        } catch (e: Exception) {
            false
        }
    }
    
    if (envFile == null) {
        println("Warning: .env file not found. Searched in:")
        possiblePaths.forEach { path ->
            val exists = try { path.exists() } catch (e: Exception) { false }
            println("  - ${path.absolutePath} ${if (exists) "(exists)" else "(not found)"}")
        }
        println("Current working directory: $currentDir")
        return envMap
    }
    
    println("Loading .env file from: ${envFile.absolutePath}")
    
    try {
        var lineNumber = 0
        envFile.readLines().forEach { line ->
            lineNumber++
            val trimmed = line.trim()
            if (trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
                val parts = trimmed.split("=", limit = 2)
                if (parts.size == 2) {
                    val key = parts[0].trim()
                    var value = parts[1].trim()
                    
                    if (value.startsWith("\"") && value.endsWith("\"")) {
                        value = value.removeSurrounding("\"")
                    } else if (value.startsWith("'") && value.endsWith("'")) {
                        value = value.removeSurrounding("'")
                    }
                    
                    envMap[key] = value
                } else {
                    println("Warning: Invalid format in .env line $lineNumber: $trimmed")
                }
            }
        }
        println("Loaded ${envMap.size} environment variables from .env file")
        envMap.keys.forEach { key ->
            val valuePreview = envMap[key]?.take(10) ?: ""
            println("  - $key=${valuePreview}...")
        }
    } catch (e: Exception) {
        println("Failed to load .env file: ${e.message}")
        e.printStackTrace()
    }
    
    return envMap
}

