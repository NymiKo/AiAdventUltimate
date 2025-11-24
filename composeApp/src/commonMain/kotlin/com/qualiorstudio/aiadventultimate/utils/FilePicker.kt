package com.qualiorstudio.aiadventultimate.utils

expect class FilePicker {
    suspend fun pickFile(): String?
}

