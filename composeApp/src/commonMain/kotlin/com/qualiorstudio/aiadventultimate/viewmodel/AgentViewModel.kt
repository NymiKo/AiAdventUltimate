package com.qualiorstudio.aiadventultimate.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qualiorstudio.aiadventultimate.api.DeepSeek
import com.qualiorstudio.aiadventultimate.api.DeepSeekMessage
import com.qualiorstudio.aiadventultimate.model.Agent
import com.qualiorstudio.aiadventultimate.repository.AgentRepository
import com.qualiorstudio.aiadventultimate.repository.AgentRepositoryImpl
import com.qualiorstudio.aiadventultimate.utils.currentTimeMillis
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

class AgentViewModel(
    private val agentRepository: AgentRepository? = null,
    private val deepSeekApiKey: String? = null
) : ViewModel() {
    private val repository = agentRepository ?: AgentRepositoryImpl()
    private var deepSeek: DeepSeek? = null
    
    private val _agents = MutableStateFlow<List<Agent>>(emptyList())
    val agents: StateFlow<List<Agent>> = _agents.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val _isGeneratingPrompt = MutableStateFlow(false)
    val isGeneratingPrompt: StateFlow<Boolean> = _isGeneratingPrompt.asStateFlow()
    
    init {
        loadAgents()
        if (deepSeekApiKey != null) {
            deepSeek = DeepSeek(apiKey = deepSeekApiKey)
        }
    }
    
    fun setDeepSeekApiKey(apiKey: String) {
        deepSeek = DeepSeek(apiKey = apiKey)
    }
    
    private fun loadAgents() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                repository.reloadAgents()
                _agents.value = repository.getAllAgents()
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    fun observeAgents() {
        viewModelScope.launch {
            repository.observeAllAgents().collect { agents ->
                _agents.value = agents
            }
        }
    }
    
    suspend fun generateSystemPrompt(role: String): String {
        if (deepSeek == null) {
            throw Exception("DeepSeek API ключ не установлен. Пожалуйста, установите API ключ в настройках.")
        }
        
        _isGeneratingPrompt.value = true
        return try {
            val prompt = """
                Создай детальный системный промпт для AI агента на основе следующего описания роли.
                
                Описание роли:
                $role
                
                Требования к системному промпту:
                - Детально опиши характер и стиль общения агента
                - Укажи специализацию и область знаний агента
                - Определи тон и манеру общения (формальный/неформальный, дружелюбный/строгий и т.д.)
                - Опиши, как агент должен отвечать на вопросы и решать задачи
                - Включи инструкции по использованию информации из базы знаний (если применимо)
                - Промпт должен быть на русском языке
                - Промпт должен быть структурированным и понятным
                
                Верни только системный промпт без дополнительных пояснений и кавычек.
            """.trimIndent()
            
            val messages = listOf(
                DeepSeekMessage(
                    role = "system",
                    content = "Ты эксперт по созданию системных промптов для AI агентов. Создавай детальные и эффективные промпты."
                ),
                DeepSeekMessage(role = "user", content = prompt)
            )
            
            val response = deepSeek!!.sendMessage(messages, null, temperature = 0.7, maxTokens = 2000)
            response.choices.firstOrNull()?.message?.content?.trim()
                ?: throw Exception("Не удалось сгенерировать системный промпт")
        } catch (e: Exception) {
            throw Exception("Ошибка при генерации системного промпта: ${e.message}")
        } finally {
            _isGeneratingPrompt.value = false
        }
    }
    
    suspend fun createAgent(name: String, role: String): Agent {
        val systemPrompt = generateSystemPrompt(role)
        val agent = Agent(
            id = UUID.randomUUID().toString(),
            name = name,
            role = role,
            systemPrompt = systemPrompt,
            createdAt = currentTimeMillis(),
            updatedAt = currentTimeMillis()
        )
        repository.saveAgent(agent)
        loadAgents()
        return agent
    }
    
    suspend fun updateAgent(agent: Agent) {
        repository.updateAgent(agent)
        loadAgents()
    }
    
    suspend fun deleteAgent(id: String) {
        repository.deleteAgent(id)
        loadAgents()
    }
    
    suspend fun getAgentById(id: String): Agent? {
        return repository.getAgentById(id)
    }
}

