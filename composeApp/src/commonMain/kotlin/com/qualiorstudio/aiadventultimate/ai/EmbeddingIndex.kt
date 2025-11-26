package com.qualiorstudio.aiadventultimate.ai

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString

@Serializable
data class EmbeddingChunk(
    val id: String,
    val text: String,
    val embedding: List<Double>,
    val metadata: Map<String, String> = emptyMap()
)

@Serializable
data class ScoredEmbeddingChunk(
    val chunk: EmbeddingChunk,
    val similarity: Double
)

@Serializable
data class EmbeddingIndexData(
    val chunks: List<EmbeddingChunk>,
    val createdAt: Long = System.currentTimeMillis(),
    val model: String = "local-model"
)

class EmbeddingIndex(private val filePath: String) {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }
    
    private val fileStorage = FileStorage(filePath)

    fun saveIndex(indexData: EmbeddingIndexData) {
        try {
            val jsonString = json.encodeToString(EmbeddingIndexData.serializer(), indexData)
            fileStorage.writeText(jsonString)
            println("Index saved to $filePath (${indexData.chunks.size} chunks)")
        } catch (e: Exception) {
            println("Failed to save index: ${e.message}")
            e.printStackTrace()
            throw Exception("Failed to save index: ${e.message}")
        }
    }

    fun loadIndex(): EmbeddingIndexData? {
        return try {
            if (!fileStorage.exists()) {
                println("Index file not found: $filePath")
                return null
            }
            val jsonString = fileStorage.readText()
            json.decodeFromString<EmbeddingIndexData>(jsonString)
        } catch (e: Exception) {
            println("Failed to load index: ${e.message}")
            e.printStackTrace()
            null
        }
    }

    fun addChunks(newChunks: List<EmbeddingChunk>) {
        val existingIndex = loadIndex() ?: EmbeddingIndexData(chunks = emptyList())
        val updatedChunks = existingIndex.chunks + newChunks
        val updatedIndex = existingIndex.copy(chunks = updatedChunks)
        saveIndex(updatedIndex)
    }

    fun searchSimilar(queryEmbedding: List<Double>, topK: Int = 5): List<ScoredEmbeddingChunk> {
        val index = loadIndex() ?: return emptyList()
        
        val similarities = index.chunks.map { chunk ->
            val similarity = cosineSimilarity(queryEmbedding, chunk.embedding)
            Pair(chunk, similarity)
        }.sortedByDescending { it.second }
        
        return similarities.take(topK).map { ScoredEmbeddingChunk(it.first, it.second) }
    }

    private fun cosineSimilarity(vec1: List<Double>, vec2: List<Double>): Double {
        if (vec1.size != vec2.size) {
            return 0.0
        }
        
        val dotProduct = vec1.zip(vec2).sumOf { it.first * it.second }
        val magnitude1 = kotlin.math.sqrt(vec1.sumOf { it * it })
        val magnitude2 = kotlin.math.sqrt(vec2.sumOf { it * it })
        
        if (magnitude1 == 0.0 || magnitude2 == 0.0) {
            return 0.0
        }
        
        return dotProduct / (magnitude1 * magnitude2)
    }
}

