package com.qualiorstudio.aiadventultimate.ai

class TextChunker(
    private val chunkSize: Int = 500,
    private val chunkOverlap: Int = 50
) {
    fun chunkText(text: String): List<String> {
        if (text.length <= chunkSize) {
            return listOf(text)
        }

        val chunks = mutableListOf<String>()
        var startIndex = 0
        var iterations = 0
        val maxIterations = (text.length / (chunkSize - chunkOverlap)) + 100

        while (startIndex < text.length && iterations < maxIterations) {
            iterations++
            
            val endIndex = minOf(startIndex + chunkSize, text.length)
            
            if (endIndex <= startIndex) {
                break
            }
            
            var chunk = text.substring(startIndex, endIndex)
            var nextStartIndex = endIndex
            
            if (endIndex < text.length) {
                val lastSpaceIndex = chunk.lastIndexOf(' ')
                val lastNewlineIndex = chunk.lastIndexOf('\n')
                val breakIndex = maxOf(lastSpaceIndex, lastNewlineIndex)
                
                if (breakIndex > chunkSize / 2 && breakIndex > 0) {
                    chunk = chunk.substring(0, breakIndex)
                    nextStartIndex = startIndex + breakIndex + 1
                }
            }
            
            if (chunk.isNotBlank()) {
                chunks.add(chunk.trim())
            }
            
            if (chunkOverlap > 0 && nextStartIndex < text.length) {
                startIndex = maxOf(nextStartIndex - chunkOverlap, startIndex + 1)
            } else {
                startIndex = nextStartIndex
            }
            
            if (startIndex >= text.length) {
                break
            }
        }

        if (iterations >= maxIterations) {
            println("Warning: Reached max iterations in chunkText. Text length: ${text.length}, Chunks created: ${chunks.size}")
        }

        return chunks
    }

    fun chunkTexts(texts: List<String>): List<String> {
        return texts.flatMap { chunkText(it) }
    }
}

