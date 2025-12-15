package com.qualiorstudio.aiadventultimate.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qualiorstudio.aiadventultimate.model.Personalization
import com.qualiorstudio.aiadventultimate.storage.PersonalizationStorage
import com.qualiorstudio.aiadventultimate.storage.getPersonalizationFilePath
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PersonalizationViewModel : ViewModel() {
    private val storage = PersonalizationStorage(getPersonalizationFilePath())
    
    private val _personalization = MutableStateFlow<Personalization>(Personalization())
    val personalization: StateFlow<Personalization> = _personalization.asStateFlow()
    
    init {
        loadPersonalization()
    }
    
    private fun loadPersonalization() {
        viewModelScope.launch {
            try {
                _personalization.value = storage.loadPersonalization()
            } catch (e: Exception) {
                println("Failed to load personalization: ${e.message}")
            }
        }
    }
    
    private fun savePersonalization() {
        viewModelScope.launch {
            try {
                storage.savePersonalization(_personalization.value)
            } catch (e: Exception) {
                println("Failed to save personalization: ${e.message}")
            }
        }
    }
    
    fun setName(name: String) {
        _personalization.value = _personalization.value.copy(name = name)
        savePersonalization()
    }
    
    fun setPreferences(preferences: String) {
        _personalization.value = _personalization.value.copy(preferences = preferences)
        savePersonalization()
    }
    
    fun setHabits(habits: String) {
        _personalization.value = _personalization.value.copy(habits = habits)
        savePersonalization()
    }
    
    fun setWorkStyle(workStyle: String) {
        _personalization.value = _personalization.value.copy(workStyle = workStyle)
        savePersonalization()
    }
    
    fun setCommunicationStyle(communicationStyle: String) {
        _personalization.value = _personalization.value.copy(communicationStyle = communicationStyle)
        savePersonalization()
    }
    
    fun setInterests(interests: String) {
        _personalization.value = _personalization.value.copy(interests = interests)
        savePersonalization()
    }
    
    fun setGoals(goals: String) {
        _personalization.value = _personalization.value.copy(goals = goals)
        savePersonalization()
    }
    
    fun setAdditionalInfo(additionalInfo: String) {
        _personalization.value = _personalization.value.copy(additionalInfo = additionalInfo)
        savePersonalization()
    }
    
    fun updatePersonalization(personalization: Personalization) {
        _personalization.value = personalization
        savePersonalization()
    }
}

