package com.qualiorstudio.aiadventultimate.utils

expect class PdfParser() {
    fun extractText(filePath: String): String
    fun extractTitle(filePath: String): String?
}

