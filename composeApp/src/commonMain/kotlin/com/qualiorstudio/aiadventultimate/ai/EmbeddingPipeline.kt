package com.qualiorstudio.aiadventultimate.ai

import com.qualiorstudio.aiadventultimate.api.LMStudio
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

class EmbeddingPipeline(
    private val lmStudio: LMStudio,
    private val index: EmbeddingIndex,
    val chunker: TextChunker = TextChunker(),
    private val model: String? = null
) {
    suspend fun processText(
        text: String,
        metadata: Map<String, String> = emptyMap(),
        model: String? = null
    ): List<EmbeddingChunk> {
        return withContext(Dispatchers.Default) {
            println("Processing text: ${text.take(100)}...")
            
            val chunks = chunker.chunkText(text)
            println("Text split into ${chunks.size} chunks")
            
            val modelToUse = model ?: this@EmbeddingPipeline.model
            val embeddings = lmStudio.generateEmbeddings(chunks, modelToUse)
            println("Generated ${embeddings.size} embeddings")
            
            val embeddingChunks = chunks.zip(embeddings).map { (chunkText, embedding) ->
                EmbeddingChunk(
                    id = UUID.randomUUID().toString(),
                    text = chunkText,
                    embedding = embedding,
                    metadata = metadata
                )
            }
            
            index.addChunks(embeddingChunks)
            println("Added ${embeddingChunks.size} chunks to index")
            
            embeddingChunks
        }
    }

    suspend fun processTexts(
        texts: List<String>,
        metadata: Map<String, String> = emptyMap(),
        model: String? = null
    ): List<EmbeddingChunk> {
        return withContext(Dispatchers.Default) {
            println("Processing ${texts.size} texts")
            
            val allChunks = chunker.chunkTexts(texts)
            println("Texts split into ${allChunks.size} chunks")
            
            val modelToUse = model ?: this@EmbeddingPipeline.model
            val embeddings = lmStudio.generateEmbeddings(allChunks, modelToUse)
            println("Generated ${embeddings.size} embeddings")
            
            val embeddingChunks = allChunks.zip(embeddings).map { (chunkText, embedding) ->
                EmbeddingChunk(
                    id = UUID.randomUUID().toString(),
                    text = chunkText,
                    embedding = embedding,
                    metadata = metadata
                )
            }
            
            index.addChunks(embeddingChunks)
            println("Added ${embeddingChunks.size} chunks to index")
            
            embeddingChunks
        }
    }

    suspend fun search(
        query: String,
        topK: Int = 5,
        model: String? = null
    ): List<ScoredEmbeddingChunk> {
        return withContext(Dispatchers.Default) {
            val modelToUse = model ?: this@EmbeddingPipeline.model
            val queryEmbedding = lmStudio.generateEmbedding(query, modelToUse)
            index.searchSimilar(queryEmbedding, topK)
        }
    }

    suspend fun getAvailableModels(): List<String> {
        return lmStudio.getAvailableModels()
    }

    fun close() {
        lmStudio.close()
    }
}

