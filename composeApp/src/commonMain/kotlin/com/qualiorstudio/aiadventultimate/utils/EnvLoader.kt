package com.qualiorstudio.aiadventultimate.utils

expect fun loadEnvFile(): Map<String, String>

fun getEnv(key: String, defaultValue: String = ""): String {
    val envValue = System.getenv(key)
    if (envValue != null) {
        return envValue
    }
    
    val envMap = loadEnvFile()
    return envMap[key] ?: defaultValue
}

