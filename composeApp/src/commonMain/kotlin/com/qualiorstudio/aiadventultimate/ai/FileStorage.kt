package com.qualiorstudio.aiadventultimate.ai

expect class FileStorage(filePath: String) {
    fun writeText(text: String)
    fun readText(): String
    fun exists(): Boolean
}

