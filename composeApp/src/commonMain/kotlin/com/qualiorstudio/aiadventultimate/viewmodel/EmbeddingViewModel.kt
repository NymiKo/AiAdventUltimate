package com.qualiorstudio.aiadventultimate.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qualiorstudio.aiadventultimate.ai.EmbeddingPipeline
import com.qualiorstudio.aiadventultimate.ai.EmbeddingIndex
import com.qualiorstudio.aiadventultimate.ai.EmbeddingIndexData
import com.qualiorstudio.aiadventultimate.api.LMStudio
import com.qualiorstudio.aiadventultimate.utils.HtmlParser
import com.qualiorstudio.aiadventultimate.utils.PdfParser
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
    val currentFile: Int = 0,
    val totalFiles: Int = 0,
    val currentFileName: String = "",
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
    private val pdfParser = PdfParser()
    
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
    
    suspend fun processHtmlFiles(
        filePaths: List<String>,
        model: String? = null
    ): Result<Int> {
        return try {
            val modelToUse = model ?: _selectedModel.value
            var totalChunksProcessed = 0
            var successfulFiles = 0
            
            _progress.value = EmbeddingProgress(
                isProcessing = true,
                status = "Обработка ${filePaths.size} файлов...",
                currentFile = 0,
                totalFiles = filePaths.size,
                totalSteps = 3
            )
            
            filePaths.forEachIndexed { fileIndex, filePath ->
                val file = java.io.File(filePath)
                val fileName = file.name
                val fileExtension = fileName.substringAfterLast('.', "").lowercase()
                
                _progress.value = _progress.value.copy(
                    currentFile = fileIndex + 1,
                    currentFileName = fileName,
                    status = "Обработка файла ${fileIndex + 1}/${filePaths.size}: $fileName"
                )
                
                try {
                    val result = when (fileExtension) {
                        "pdf" -> {
                            processPdfFile(
                                filePath = filePath,
                                fileName = fileName,
                                model = modelToUse,
                                updateProgress = { status, step, totalSteps ->
                                    _progress.value = _progress.value.copy(
                                        status = "$status ($fileName)",
                                        currentStep = step,
                                        totalSteps = totalSteps
                                    )
                                }
                            )
                        }
                        "html", "htm" -> {
                            val htmlContent = file.readText()
                            processHtmlFile(
                                htmlContent = htmlContent,
                                fileName = fileName,
                                model = modelToUse,
                                updateProgress = { status, step, totalSteps ->
                                    _progress.value = _progress.value.copy(
                                        status = "$status ($fileName)",
                                        currentStep = step,
                                        totalSteps = totalSteps
                                    )
                                }
                            )
                        }
                        "md", "markdown" -> {
                            val markdownContent = file.readText()
                            processMarkdownFile(
                                markdownContent = markdownContent,
                                fileName = fileName,
                                model = modelToUse,
                                updateProgress = { status, step, totalSteps ->
                                    _progress.value = _progress.value.copy(
                                        status = "$status ($fileName)",
                                        currentStep = step,
                                        totalSteps = totalSteps
                                    )
                                }
                            )
                        }
                        else -> {
                            try {
                                val fileContent = file.readText()
                                if (fileContent.isNotBlank() && fileContent.length < 10_000_000) {
                                    processTextFile(
                                        textContent = fileContent,
                                        fileName = fileName,
                                        fileExtension = fileExtension,
                                        model = modelToUse,
                                        updateProgress = { status, step, totalSteps ->
                                            _progress.value = _progress.value.copy(
                                                status = "$status ($fileName)",
                                                currentStep = step,
                                                totalSteps = totalSteps
                                            )
                                        }
                                    )
                                } else {
                                    Result.success(0)
                                }
                            } catch (e: Exception) {
                                println("Не удалось прочитать файл $fileName: ${e.message}")
                                Result.success(0)
                            }
                        }
                    }
                    
                    result.onSuccess { chunksCount ->
                        totalChunksProcessed += chunksCount
                        successfulFiles++
                    }.onFailure { error ->
                        println("Failed to process file $fileName: ${error.message}")
                    }
                } catch (e: Exception) {
                    println("Failed to read file $fileName: ${e.message}")
                }
            }
            
            loadIndexData()
            
            try {
                openFileInSystem(indexFilePath)
            } catch (e: Exception) {
                println("Failed to open file automatically: ${e.message}")
            }
            
            _progress.value = EmbeddingProgress(
                isProcessing = false,
                status = "Готово! Обработано $successfulFiles из ${filePaths.size} файлов. Всего чанков: $totalChunksProcessed. Файл открыт в системе."
            )
            
            Result.success(totalChunksProcessed)
        } catch (e: Exception) {
            _progress.value = EmbeddingProgress(
                isProcessing = false,
                status = "Ошибка: ${e.message}"
            )
            Result.failure(e)
        }
    }
    
    private suspend fun processHtmlFile(
        htmlContent: String,
        fileName: String,
        model: String? = null,
        updateProgress: (String, Int, Int) -> Unit = { _, _, _ -> }
    ): Result<Int> {
        return try {
            updateProgress("Извлечение текста из HTML...", 1, 3)
            
            val text = htmlParser.extractText(htmlContent)
            val title = htmlParser.extractTitle(htmlContent) ?: fileName
            
            if (text.isBlank()) {
                return Result.failure(Exception("Не удалось извлечь текст из HTML"))
            }
            
            return processTextContent(text, title, fileName, "html", model, updateProgress)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    private suspend fun processPdfFile(
        filePath: String,
        fileName: String,
        model: String? = null,
        updateProgress: (String, Int, Int) -> Unit = { _, _, _ -> }
    ): Result<Int> {
        return try {
            updateProgress("Извлечение текста из PDF...", 1, 3)
            
            val text = pdfParser.extractText(filePath)
            val title = pdfParser.extractTitle(filePath) ?: fileName
            
            if (text.isBlank()) {
                return Result.failure(Exception("Не удалось извлечь текст из PDF"))
            }
            
            return processTextContent(text, title, fileName, "pdf", model, updateProgress)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    private suspend fun processMarkdownFile(
        markdownContent: String,
        fileName: String,
        model: String? = null,
        updateProgress: (String, Int, Int) -> Unit = { _, _, _ -> }
    ): Result<Int> {
        return try {
            updateProgress("Обработка Markdown файла...", 1, 3)
            
            val title = fileName.substringBeforeLast('.')
            
            if (markdownContent.isBlank()) {
                return Result.failure(Exception("Файл пустой"))
            }
            
            return processTextContent(markdownContent, title, fileName, "markdown", model, updateProgress)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    private suspend fun processTextFile(
        textContent: String,
        fileName: String,
        fileExtension: String,
        model: String? = null,
        updateProgress: (String, Int, Int) -> Unit = { _, _, _ -> }
    ): Result<Int> {
        return try {
            updateProgress("Обработка текстового файла...", 1, 3)
            
            val title = fileName.substringBeforeLast('.')
            
            if (textContent.isBlank()) {
                return Result.success(0)
            }
            
            return processTextContent(textContent, title, fileName, fileExtension, model, updateProgress)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    private suspend fun processTextContent(
        text: String,
        title: String,
        fileName: String,
        sourceType: String,
        model: String?,
        updateProgress: (String, Int, Int) -> Unit
    ): Result<Int> {
        return try {
            val maxTextLength = 10_000_000
            val processedText = if (text.length > maxTextLength) {
                println("Warning: Text too long (${text.length} chars), truncating to $maxTextLength chars")
                text.substring(0, maxTextLength)
            } else {
                text
            }
            
            updateProgress("Разбивка текста на чанки...", 2, 3)
            
            val chunks = pipeline.chunker.chunkText(processedText)
            
            if (chunks.size > 10000) {
                return Result.failure(Exception("Слишком много чанков (${chunks.size}). Файл слишком большой для обработки."))
            }
            
            updateProgress("Генерация эмбеддингов...", 3, 3)
            
            val modelToUse = model ?: _selectedModel.value
            val embeddings = mutableListOf<List<Double>>()
            
            chunks.forEachIndexed { index, chunk ->
                val embedding = lmStudio.generateEmbedding(chunk, modelToUse)
                embeddings.add(embedding)
                
                _progress.value = _progress.value.copy(
                    currentChunk = index + 1,
                    totalChunks = chunks.size,
                    status = "Генерация эмбеддингов для $fileName... (${index + 1}/${chunks.size})"
                )
            }
            
            val embeddingChunks = chunks.zip(embeddings).map { (chunkText, embedding) ->
                com.qualiorstudio.aiadventultimate.ai.EmbeddingChunk(
                    id = java.util.UUID.randomUUID().toString(),
                    text = chunkText,
                    embedding = embedding,
                    metadata = mapOf(
                        "source" to sourceType,
                        "fileName" to fileName,
                        "title" to title
                    )
                )
            }
            
            index.addChunks(embeddingChunks)
            
            Result.success(embeddingChunks.size)
        } catch (e: Exception) {
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

