package com.qualiorstudio.aiadventultimate.utils

import org.apache.pdfbox.Loader
import org.apache.pdfbox.pdmodel.PDDocument
import java.io.File

actual class PdfParser {
    actual fun extractText(filePath: String): String {
        val file = File(filePath)
        if (!file.exists()) {
            throw Exception("PDF файл не найден: $filePath")
        }
        
        return try {
            Loader.loadPDF(file).use { document ->
                val text = StringBuilder()
                val stripper = org.apache.pdfbox.text.PDFTextStripper()
                
                for (pageNum in 1..document.numberOfPages) {
                    stripper.startPage = pageNum
                    stripper.endPage = pageNum
                    val pageText = stripper.getText(document)
                    text.append(pageText)
                    if (pageNum < document.numberOfPages) {
                        text.append("\n\n")
                    }
                }
                
                text.toString().trim()
            }
        } catch (e: Exception) {
            throw Exception("Ошибка при чтении PDF: ${e.message}", e)
        }
    }
    
    actual fun extractTitle(filePath: String): String? {
        return try {
            val file = File(filePath)
            Loader.loadPDF(file).use { document ->
                val documentInformation = document.documentInformation
                documentInformation?.title?.takeIf { it.isNotBlank() }
                    ?: File(filePath).nameWithoutExtension
            }
        } catch (e: Exception) {
            File(filePath).nameWithoutExtension
        }
    }
}

