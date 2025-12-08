package com.qualiorstudio.aiadventultimate.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qualiorstudio.aiadventultimate.ai.FileStorage
import com.qualiorstudio.aiadventultimate.model.AppSettings
import com.qualiorstudio.aiadventultimate.storage.getDataDirectory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class SettingsViewModel : ViewModel() {
    private val settingsFilePath = "${getDataDirectory()}/settings.json"
    private val fileStorage = FileStorage(settingsFilePath)
    private val json = Json { 
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    
    private val _settings = MutableStateFlow<AppSettings>(AppSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()
    
    init {
        loadSettings()
    }
    
    private fun loadSettings() {
        viewModelScope.launch {
            try {
                if (fileStorage.exists()) {
                    val jsonString = fileStorage.readText()
                    if (jsonString.isNotBlank()) {
                        _settings.value = json.decodeFromString<AppSettings>(jsonString)
                    }
                }
            } catch (e: Exception) {
                println("Failed to load settings: ${e.message}")
                // Используем настройки по умолчанию
            }
        }
    }
    
    private fun saveSettings() {
        viewModelScope.launch {
            try {
                val jsonString = json.encodeToString(_settings.value)
                fileStorage.writeText(jsonString)
            } catch (e: Exception) {
                println("Failed to save settings: ${e.message}")
            }
        }
    }
    
    fun setDarkTheme(enabled: Boolean) {
        _settings.value = _settings.value.copy(darkTheme = enabled)
        saveSettings()
    }
    
    fun setUseRAG(enabled: Boolean) {
        _settings.value = _settings.value.copy(useRAG = enabled)
        saveSettings()
    }
    
    fun setEnableVoiceInput(enabled: Boolean) {
        _settings.value = _settings.value.copy(enableVoiceInput = enabled)
        saveSettings()
    }
    
    fun setEnableVoiceOutput(enabled: Boolean) {
        _settings.value = _settings.value.copy(enableVoiceOutput = enabled)
        saveSettings()
    }
    
    fun setDeepSeekApiKey(key: String) {
        _settings.value = _settings.value.copy(deepSeekApiKey = key)
        saveSettings()
    }
    
    fun setTemperature(value: Double) {
        _settings.value = _settings.value.copy(temperature = value.coerceIn(0.0, 2.0))
        saveSettings()
    }
    
    fun setMaxTokens(value: Int) {
        _settings.value = _settings.value.copy(maxTokens = value.coerceAtLeast(1))
        saveSettings()
    }
    
    fun setRagTopK(value: Int) {
        _settings.value = _settings.value.copy(ragTopK = value.coerceAtLeast(1))
        saveSettings()
    }
    
    fun setRerankMinScore(value: Double) {
        _settings.value = _settings.value.copy(rerankMinScore = value.coerceIn(0.0, 1.0))
        saveSettings()
    }
    
    fun setRerankedRetentionRatio(value: Double) {
        _settings.value = _settings.value.copy(rerankedRetentionRatio = value.coerceIn(0.0, 1.0))
        saveSettings()
    }
    
    fun setLmStudioBaseUrl(url: String) {
        _settings.value = _settings.value.copy(lmStudioBaseUrl = url)
        saveSettings()
    }
    
    fun setMaxIterations(value: Int) {
        _settings.value = _settings.value.copy(maxIterations = value.coerceAtLeast(1))
        saveSettings()
    }
    
    fun setUseLocalLLM(enabled: Boolean) {
        _settings.value = _settings.value.copy(useLocalLLM = enabled)
        saveSettings()
    }
    
    fun setLocalLLMModel(model: String?) {
        _settings.value = _settings.value.copy(localLLMModel = model)
        saveSettings()
    }
}

