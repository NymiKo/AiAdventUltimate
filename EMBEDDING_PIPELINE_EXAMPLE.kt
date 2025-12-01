package com.qualiorstudio.aiadventultimate

import com.qualiorstudio.aiadventultimate.ai.EmbeddingPipeline
import com.qualiorstudio.aiadventultimate.ai.EmbeddingIndex
import com.qualiorstudio.aiadventultimate.api.LMStudio
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    val lmStudio = LMStudio(
        baseUrl = "http://localhost:1234"
    )
    
    println("Получение списка доступных моделей...")
    val availableModels = lmStudio.getAvailableModels()
    if (availableModels.isNotEmpty()) {
        println("Доступные модели:")
        availableModels.forEach { println("  - $it") }
    } else {
        println("Не удалось получить список моделей или список пуст")
    }
    
    val index = EmbeddingIndex("embeddings_index.json")
    
    val modelToUse = availableModels.firstOrNull() ?: "local-model"
    println("\nИспользуется модель: $modelToUse")
    
    val pipeline = EmbeddingPipeline(
        lmStudio = lmStudio,
        index = index,
        model = modelToUse
    )
    
    val text = """
        Это пример длинного текста, который нужно разбить на чанки и создать эмбеддинги.
        Текст может быть любой длины, и пайплайн автоматически разобьет его на части
        оптимального размера для обработки. Каждый чанк будет преобразован в векторное
        представление (эмбеддинг) с помощью LM Studio, и все это будет сохранено в JSON индекс.
    """.trimIndent()
    
    println("\nОбработка текста...")
    val chunks = pipeline.processText(
        text = text,
        metadata = mapOf("source" = "example", "type" = "documentation"),
        model = modelToUse
    )
    
    println("Обработано ${chunks.size} чанков")
    
    println("\nПоиск похожих чанков...")
    val query = "пример текста"
    val similarChunks = pipeline.search(query, topK = 3, model = modelToUse)
    
    println("Найдено ${similarChunks.size} похожих чанков:")
    similarChunks.forEachIndexed { index, chunk ->
        println("${index + 1}. ${chunk.text.take(100)}...")
    }
    
    pipeline.close()
}

