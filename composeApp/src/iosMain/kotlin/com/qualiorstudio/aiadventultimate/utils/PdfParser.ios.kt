package com.qualiorstudio.aiadventultimate.utils

import java.io.File

actual class PdfParser {
    actual fun extractText(filePath: String): String {
        throw Exception("PDF парсинг пока не поддерживается на iOS")
    }
    
    actual fun extractTitle(filePath: String): String? {
        return File(filePath).nameWithoutExtension
    }
}

