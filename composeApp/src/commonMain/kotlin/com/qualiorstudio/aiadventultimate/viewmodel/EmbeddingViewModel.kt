package com.qualiorstudio.aiadventultimate.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qualiorstudio.aiadventultimate.ai.EmbeddingPipeline
import com.qualiorstudio.aiadventultimate.ai.EmbeddingIndex
import com.qualiorstudio.aiadventultimate.ai.EmbeddingIndexData
import com.qualiorstudio.aiadventultimate.api.LMStudio
import com.qualiorstudio.aiadventultimate.utils.HtmlParser
import com.qualiorstudio.aiadventultimate.utils.getEmbeddingsIndexPath
import com.qualiorstudio.aiadventultimate.utils.openFileInSystem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class EmbeddingProgress(
    val currentStep: Int = 0,
    val totalSteps: Int = 0,
    val currentChunk: Int = 0,
    val totalChunks: Int = 0,
    val status: String = "",
    val isProcessing: Boolean = false
)

class EmbeddingViewModel(
    private val lmStudioBaseUrl: String = "http://localhost:1234",
    private val indexFilePath: String = getEmbeddingsIndexPath()
) : ViewModel() {
    private val lmStudio = LMStudio(baseUrl = lmStudioBaseUrl)
    private val index = EmbeddingIndex(indexFilePath)
    private val pipeline = EmbeddingPipeline(lmStudio, index)
    private val htmlParser = HtmlParser()
    
    private val _progress = MutableStateFlow(EmbeddingProgress())
    val progress: StateFlow<EmbeddingProgress> = _progress.asStateFlow()
    
    private val _availableModels = MutableStateFlow<List<String>>(emptyList())
    val availableModels: StateFlow<List<String>> = _availableModels.asStateFlow()
    
    private val _selectedModel = MutableStateFlow<String?>(null)
    val selectedModel: StateFlow<String?> = _selectedModel.asStateFlow()
    
    private val _indexData = MutableStateFlow<EmbeddingIndexData?>(null)
    val indexData: StateFlow<EmbeddingIndexData?> = _indexData.asStateFlow()
    
    val indexFilePathValue: String = indexFilePath
    
    init {
        viewModelScope.launch {
            loadAvailableModels()
            loadIndexData()
        }
    }
    
    private fun loadIndexData() {
        val data = index.loadIndex()
        _indexData.value = data
    }
    
    private suspend fun loadAvailableModels() {
        try {
            val models = pipeline.getAvailableModels()
            _availableModels.value = models
            if (models.isNotEmpty() && _selectedModel.value == null) {
                _selectedModel.value = models.first()
            }
        } catch (e: Exception) {
            println("Failed to load models: ${e.message}")
        }
    }
    
    suspend fun processHtmlFile(
        htmlContent: String,
        fileName: String,
        model: String? = null
    ): Result<Int> {
        return try {
            _progress.value = EmbeddingProgress(
                isProcessing = true,
                status = "Извлечение текста из HTML...",
                currentStep = 1,
                totalSteps = 3
            )
            
            val text = htmlParser.extractText(htmlContent)
            val title = htmlParser.extractTitle(htmlContent) ?: fileName
            
            if (text.isBlank()) {
                return Result.failure(Exception("Не удалось извлечь текст из HTML"))
            }
            
            val maxTextLength = 10_000_000
            val processedText = if (text.length > maxTextLength) {
                println("Warning: Text too long (${text.length} chars), truncating to $maxTextLength chars")
                text.substring(0, maxTextLength)
            } else {
                text
            }
            
            _progress.value = _progress.value.copy(
                status = "Разбивка текста на чанки...",
                currentStep = 2
            )
            
            val chunks = pipeline.chunker.chunkText(processedText)
            
            if (chunks.size > 10000) {
                return Result.failure(Exception("Слишком много чанков (${chunks.size}). Файл слишком большой для обработки."))
            }
            
            _progress.value = _progress.value.copy(
                status = "Генерация эмбеддингов...",
                currentStep = 3,
                currentChunk = 0,
                totalChunks = chunks.size
            )
            
            val modelToUse = model ?: _selectedModel.value
            val embeddings = mutableListOf<List<Double>>()
            
            chunks.forEachIndexed { index, chunk ->
                val embedding = lmStudio.generateEmbedding(chunk, modelToUse)
                embeddings.add(embedding)
                
                _progress.value = _progress.value.copy(
                    currentChunk = index + 1,
                    status = "Генерация эмбеддингов... (${index + 1}/${chunks.size})"
                )
            }
            
            val embeddingChunks = chunks.zip(embeddings).map { (chunkText, embedding) ->
                com.qualiorstudio.aiadventultimate.ai.EmbeddingChunk(
                    id = java.util.UUID.randomUUID().toString(),
                    text = chunkText,
                    embedding = embedding,
                    metadata = mapOf(
                        "source" to "html",
                        "fileName" to fileName,
                        "title" to title
                    )
                )
            }
            
            index.addChunks(embeddingChunks)
            loadIndexData()
            
            try {
                openFileInSystem(indexFilePath)
            } catch (e: Exception) {
                println("Failed to open file automatically: ${e.message}")
            }
            
            _progress.value = EmbeddingProgress(
                isProcessing = false,
                status = "Готово! Обработано ${embeddingChunks.size} чанков. Файл открыт в системе."
            )
            
            Result.success(embeddingChunks.size)
        } catch (e: Exception) {
            _progress.value = EmbeddingProgress(
                isProcessing = false,
                status = "Ошибка: ${e.message}"
            )
            Result.failure(e)
        }
    }
    
    fun setSelectedModel(model: String) {
        _selectedModel.value = model
    }
    
    fun resetProgress() {
        _progress.value = EmbeddingProgress()
    }
    
    fun openIndexFile() {
        openFileInSystem(indexFilePath)
    }
    
    override fun onCleared() {
        super.onCleared()
        pipeline.close()
    }
}

