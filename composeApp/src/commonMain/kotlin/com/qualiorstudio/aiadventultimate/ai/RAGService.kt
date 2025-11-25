package com.qualiorstudio.aiadventultimate.ai

import com.qualiorstudio.aiadventultimate.api.LMStudio
import com.qualiorstudio.aiadventultimate.utils.getEmbeddingsIndexPath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class RAGService(
    private val lmStudioBaseUrl: String = "http://localhost:1234",
    private val indexFilePath: String = getEmbeddingsIndexPath(),
    private val topK: Int = 5
) {
    private val lmStudio: LMStudio? by lazy {
        try {
            LMStudio(baseUrl = lmStudioBaseUrl)
        } catch (e: Exception) {
            println("LMStudio недоступен: ${e.message}")
            null
        }
    }
    
    private val index: EmbeddingIndex by lazy {
        EmbeddingIndex(indexFilePath)
    }
    
    private val pipeline: EmbeddingPipeline? by lazy {
        lmStudio?.let { EmbeddingPipeline(it, index) }
    }
    
    suspend fun searchRelevantChunks(query: String): List<EmbeddingChunk> {
        return withContext(Dispatchers.Default) {
            try {
                val currentPipeline = pipeline
                if (currentPipeline == null) {
                    println("RAG: Pipeline недоступен, возвращаю пустой список")
                    return@withContext emptyList()
                }
                
                val chunks = currentPipeline.search(query, topK = topK)
                println("RAG: Найдено ${chunks.size} релевантных чанков для запроса: ${query.take(50)}...")
                chunks
            } catch (e: Exception) {
                println("RAG: Ошибка при поиске чанков: ${e.message}")
                e.printStackTrace()
                emptyList()
            }
        }
    }
    
    fun buildContext(chunks: List<EmbeddingChunk>): String {
        if (chunks.isEmpty()) {
            return ""
        }
        
        val contextBuilder = StringBuilder()
        contextBuilder.append("Релевантная информация из базы знаний:\n\n")
        
        chunks.forEachIndexed { index, chunk ->
            contextBuilder.append("[${index + 1}] ${chunk.text}\n\n")
        }
        
        return contextBuilder.toString().trim()
    }
    
    fun buildRAGPrompt(userQuestion: String, context: String): String {
        if (context.isEmpty()) {
            return userQuestion
        }
        
        return """
Используй следующую информацию из базы знаний для ответа на вопрос пользователя.
Если информация не релевантна вопросу, отвечай на основе своих знаний.

$context

Вопрос пользователя: $userQuestion

Ответь на вопрос, используя предоставленную информацию, если она релевантна.
        """.trimIndent()
    }
    
    suspend fun processWithRAG(userQuestion: String): String {
        val chunks = searchRelevantChunks(userQuestion)
        val context = buildContext(chunks)
        
        if (context.isEmpty()) {
            println("RAG: Контекст не найден, возвращаю исходный вопрос")
            return userQuestion
        }
        
        val ragPrompt = buildRAGPrompt(userQuestion, context)
        println("RAG: Сформирован промпт с контекстом (${chunks.size} чанков)")
        return ragPrompt
    }
    
    fun isAvailable(): Boolean {
        return pipeline != null && index.loadIndex() != null
    }
    
    fun close() {
        lmStudio?.close()
    }
}

