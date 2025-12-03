package com.qualiorstudio.aiadventultimate.service

import com.qualiorstudio.aiadventultimate.ai.EmbeddingIndex
import com.qualiorstudio.aiadventultimate.ai.EmbeddingPipeline
import com.qualiorstudio.aiadventultimate.ai.FileStorage
import com.qualiorstudio.aiadventultimate.ai.RAGReranker
import com.qualiorstudio.aiadventultimate.api.LMStudio
import com.qualiorstudio.aiadventultimate.utils.getSupportEmbeddingsIndexPath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SupportRAGService(
    private val lmStudioBaseUrl: String = "http://localhost:1234",
    private val indexFilePath: String = getSupportEmbeddingsIndexPath(),
    private val topK: Int = 8,
    private val rerankMinScore: Double = 0.55,
    private val rerankedRetentionRatio: Double = 0.6
) {
    private val lmStudio: LMStudio? by lazy {
        try {
            LMStudio(baseUrl = lmStudioBaseUrl)
        } catch (e: Exception) {
            println("SupportRAG: LMStudio недоступен: ${e.message}")
            null
        }
    }
    
    private val index: EmbeddingIndex by lazy {
        EmbeddingIndex(indexFilePath)
    }
    
    private val pipeline: EmbeddingPipeline? by lazy {
        lmStudio?.let { EmbeddingPipeline(it, index) }
    }
    
    private val reranker: RAGReranker = RAGReranker()
    
    suspend fun searchRelevantDocs(query: String): List<com.qualiorstudio.aiadventultimate.ai.ScoredEmbeddingChunk> {
        return withContext(Dispatchers.Default) {
            try {
                val currentPipeline = pipeline
                if (currentPipeline == null) {
                    println("SupportRAG: Pipeline недоступен")
                    return@withContext emptyList()
                }
                
                val chunks = currentPipeline.search(query, topK = topK)
                println("SupportRAG: Найдено ${chunks.size} релевантных документов")
                chunks
            } catch (e: Exception) {
                println("SupportRAG: Ошибка при поиске: ${e.message}")
                e.printStackTrace()
                emptyList()
            }
        }
    }
    
    suspend fun buildSupportContext(
        userQuestion: String,
        relevantTickets: List<com.qualiorstudio.aiadventultimate.model.Ticket> = emptyList()
    ): String {
        val chunks = searchRelevantDocs(userQuestion)
        val contextBuilder = StringBuilder()
        
        if (chunks.isNotEmpty()) {
            contextBuilder.append("=== ДОКУМЕНТАЦИЯ И FAQ ===\n\n")
            chunks.forEachIndexed { index, scoredChunk ->
                val chunk = scoredChunk.chunk
                val metadataParts = mutableListOf<String>()
                chunk.metadata["title"]?.let { metadataParts.add("Заголовок: $it") }
                chunk.metadata["fileName"]?.let { metadataParts.add("Файл: $it") }
                chunk.metadata["url"]?.let { metadataParts.add("URL: $it") }
                chunk.metadata["source"]?.let { metadataParts.add("Источник: $it") }
                
                val metadataStr = if (metadataParts.isNotEmpty()) {
                    " [${metadataParts.joinToString(", ")}]"
                } else {
                    ""
                }
                
                contextBuilder.append("[Документ ${index + 1}]$metadataStr\n")
                contextBuilder.append("${chunk.text}\n\n")
            }
        }
        
        if (relevantTickets.isNotEmpty()) {
            contextBuilder.append("\n=== СВЯЗАННЫЕ ТИКЕТЫ ===\n\n")
            relevantTickets.forEachIndexed { index, ticket ->
                contextBuilder.append("[Тикет ${index + 1}] ID: ${ticket.id}\n")
                contextBuilder.append("Пользователь: ${ticket.userName ?: ticket.userId}\n")
                contextBuilder.append("Статус: ${ticket.status}\n")
                contextBuilder.append("Заголовок: ${ticket.title}\n")
                contextBuilder.append("Описание: ${ticket.description}\n")
                if (ticket.tags.isNotEmpty()) {
                    contextBuilder.append("Теги: ${ticket.tags.joinToString(", ")}\n")
                }
                contextBuilder.append("\n")
            }
        }
        
        return contextBuilder.toString().trim()
    }
    
    fun buildSupportPrompt(
        userQuestion: String,
        context: String,
        ticketContext: String? = null
    ): String {
        val promptBuilder = StringBuilder()
        
        promptBuilder.append("""
Ты - ассистент поддержки. Отвечай на вопросы пользователей четко и по делу.

КРИТИЧЕСКИ ВАЖНЫЕ ПРАВИЛА:
1. НЕ используй вводные фразы типа "На основе документации", "Согласно FAQ", "Привет", "Отличный вопрос!"
2. НЕ упоминай источники информации явно - просто используй информацию из них
3. Отвечай ПРЯМО на вопрос без лишних слов
4. Если нужны шаги настройки - опиши их четко и последовательно
5. Будь лаконичным, но информативным
6. Используй markdown для форматирования (списки, код, выделения), но без лишних комментариев

СТИЛЬ ОТВЕТА:
- Начинай сразу с ответа на вопрос
- Если вопрос требует настройки - опиши шаги
- Если информации недостаточно - скажи кратко: "Информации недостаточно. Обратитесь в поддержку."
- Не добавляй приветствия, благодарности или упоминания источников

""".trimIndent())
        
        if (context.isNotEmpty()) {
            promptBuilder.append("=== ДОКУМЕНТАЦИЯ И FAQ ===\n\n")
            promptBuilder.append(context)
            promptBuilder.append("\n\n")
        }
        
        if (ticketContext != null && ticketContext.isNotEmpty()) {
            promptBuilder.append("=== КОНТЕКСТ ТИКЕТОВ ===\n\n")
            promptBuilder.append(ticketContext)
            promptBuilder.append("\n\n")
        }
        
        promptBuilder.append("=== ВОПРОС ПОЛЬЗОВАТЕЛЯ ===\n\n")
        promptBuilder.append(userQuestion)
        promptBuilder.append("\n\n")
        
        promptBuilder.append("""
Ответь на вопрос пользователя. Используй информацию из документации для ответа, но НЕ упоминай источники. Отвечай прямо, четко и по делу. Если нужны шаги настройки - опиши их последовательно.
        """.trimIndent())
        
        return promptBuilder.toString()
    }
    
    fun isAvailable(): Boolean {
        return pipeline != null && index.loadIndex() != null
    }
    
    fun close() {
        lmStudio?.close()
    }
}

fun createSupportRAGService(): SupportRAGService = SupportRAGService()

