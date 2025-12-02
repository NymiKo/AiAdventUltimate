package com.qualiorstudio.aiadventultimate.ai

import com.qualiorstudio.aiadventultimate.api.LMStudio
import com.qualiorstudio.aiadventultimate.utils.getEmbeddingsIndexPath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

data class RankedChunk(
    val chunk: EmbeddingChunk,
    val similarity: Double,
    val rerankScore: Double? = null,
    val combinedScore: Double? = null
)

data class RAGVariantContext(
    val id: String,
    val title: String,
    val prompt: String,
    val context: String,
    val chunks: List<RankedChunk>,
    val similarityThreshold: Double?,
    val totalCandidates: Int,
    val averageSimilarity: Double?,
    val averageCombinedScore: Double?
)

data class RAGComparisonResult(
    val baseline: RAGVariantContext,
    val reranked: RAGVariantContext
)

class RAGService(
    private val lmStudioBaseUrl: String = "http://localhost:1234",
    private val indexFilePath: String = getEmbeddingsIndexPath(),
    private val topK: Int = 12,
    private val rerankMinScore: Double = 0.58,
    private val rerankedRetentionRatio: Double = 0.5,
    private val reranker: RAGReranker = RAGReranker()
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
    
    suspend fun searchRelevantChunks(query: String): List<ScoredEmbeddingChunk> {
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
    
    fun buildContext(chunks: List<RankedChunk>): String {
        if (chunks.isEmpty()) {
            return ""
        }
        
        val contextBuilder = StringBuilder()
        contextBuilder.append("Релевантная информация из базы знаний:\n\n")
        
        chunks.forEachIndexed { index, chunk ->
            val metrics = buildString {
                append("sim=${formatScore(chunk.similarity)}")
                chunk.rerankScore?.let { append(", lex=${formatScore(it)}") }
                chunk.combinedScore?.let { append(", score=${formatScore(it)}") }
            }
            
            // Собираем метаданные для отображения
            val metadataParts = mutableListOf<String>()
            chunk.chunk.metadata["title"]?.let { metadataParts.add("Заголовок: $it") }
            chunk.chunk.metadata["fileName"]?.let { metadataParts.add("Файл: $it") }
            chunk.chunk.metadata["url"]?.let { metadataParts.add("URL: $it") }
            chunk.chunk.metadata["source"]?.let { metadataParts.add("Источник: $it") }
            
            val metadataStr = if (metadataParts.isNotEmpty()) {
                " [${metadataParts.joinToString(", ")}]"
            } else {
                ""
            }
            
            contextBuilder.append("[Источник ${index + 1}]$metadataStr\n($metrics)\n${chunk.chunk.text}\n\n")
        }
        
        return contextBuilder.toString().trim()
    }
    
    fun buildRAGPrompt(userQuestion: String, context: String): String {
        if (context.isEmpty()) {
            return userQuestion
        }
        
        return """
ИНФОРМАЦИЯ ИЗ БАЗЫ ЗНАНИЙ (RAG) - ПРИОРИТЕТНЫЙ ИСТОЧНИК:

$context

Вопрос пользователя: $userQuestion

ИНСТРУКЦИИ ПО ИСПОЛЬЗОВАНИЮ ИНФОРМАЦИИ:
1. ПРИОРИТЕТ: Используй информацию из базы знаний (RAG) как основной источник
2. ДОПОЛНЕНИЕ: Если информации из RAG недостаточно для полного ответа:
   - Используй доступные MCP инструменты для получения дополнительной информации
   - Если MCP инструменты недоступны или не дают нужной информации, можешь использовать свои знания
3. ЦИТИРОВАНИЕ: 
   - ВСЕГДА указывай источники из RAG: после каждого факта добавь ссылку [Источник X]
   - Используй прямые цитаты в кавычках при цитировании из RAG
   - Если используешь MCP или свои знания, четко укажи это в ответе
4. В конце ответа добавь раздел "Источники:" со списком всех использованных RAG источников с их метаданными

Пример формата ответа:
[Текст ответа с цитатами из RAG [Источник 1], [Источник 2], и при необходимости дополненный информацией из MCP или общих знаний]

Источники:
[Источник 1]: [Заголовок: ...] [Файл: ...] [URL: ...] (если есть)
[Источник 2]: [Заголовок: ...] [Файл: ...]
        """.trimIndent()
    }

    suspend fun buildComparison(userQuestion: String): RAGComparisonResult? {
        val scoredChunks = searchRelevantChunks(userQuestion)
        if (scoredChunks.isEmpty()) {
            println("RAG: Контекст не найден, будут использованы исходные вопросы для сравнения")
        }
        val baselineRanked = scoredChunks.map {
            RankedChunk(
                chunk = it.chunk,
                similarity = it.similarity,
                combinedScore = it.similarity
            )
        }
        val reranked = reranker.rerank(userQuestion, scoredChunks)
        val desiredSize = (scoredChunks.size * rerankedRetentionRatio)
            .coerceAtLeast(1.0)
            .toInt()
        val filteredReranked = reranked
            .filter { (it.combinedScore ?: 0.0) >= rerankMinScore }
            .ifEmpty { reranked }
            .take(desiredSize.coerceAtMost(reranked.size))
        
        val baseline = buildVariantContext(
            id = "baseline",
            title = "Без reranker",
            userQuestion = userQuestion,
            chunks = baselineRanked,
            threshold = null,
            totalCandidates = scoredChunks.size
        )
        
        val rerankedVariant = buildVariantContext(
            id = "reranked",
            title = "С reranker",
            userQuestion = userQuestion,
            chunks = if (filteredReranked.isNotEmpty()) filteredReranked else reranked,
            threshold = rerankMinScore,
            totalCandidates = scoredChunks.size
        )

        return RAGComparisonResult(baseline, rerankedVariant)
    }

    private fun buildVariantContext(
        id: String,
        title: String,
        userQuestion: String,
        chunks: List<RankedChunk>,
        threshold: Double?,
        totalCandidates: Int
    ): RAGVariantContext {
        val context = buildContext(chunks)
        val prompt = buildRAGPrompt(userQuestion, context)
        val average = if (chunks.isNotEmpty()) chunks.map { it.similarity }.average() else null
        val avgCombined = if (chunks.isNotEmpty()) chunks.mapNotNull { it.combinedScore }.takeIf { it.isNotEmpty() }?.average() else null
        return RAGVariantContext(
            id = id,
            title = title,
            prompt = prompt,
            context = context,
            chunks = chunks,
            similarityThreshold = threshold,
            totalCandidates = totalCandidates,
            averageSimilarity = average,
            averageCombinedScore = avgCombined
        )
    }

    private fun formatScore(value: Double?): String {
        if (value == null) return "-"
        val rounded = (value * 100).roundToInt() / 100.0
        return rounded.toString()
    }
    
    fun isAvailable(): Boolean {
        return pipeline != null && index.loadIndex() != null
    }
    
    fun close() {
        lmStudio?.close()
    }
}

