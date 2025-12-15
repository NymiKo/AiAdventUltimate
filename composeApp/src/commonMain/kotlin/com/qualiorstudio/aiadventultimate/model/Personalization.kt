package com.qualiorstudio.aiadventultimate.model

import kotlinx.serialization.Serializable

@Serializable
data class Personalization(
    val name: String = "",
    val preferences: String = "",
    val habits: String = "",
    val workStyle: String = "",
    val communicationStyle: String = "",
    val interests: String = "",
    val goals: String = "",
    val additionalInfo: String = ""
) {
    fun isEmpty(): Boolean {
        return name.isBlank() && 
               preferences.isBlank() && 
               habits.isBlank() && 
               workStyle.isBlank() && 
               communicationStyle.isBlank() && 
               interests.isBlank() && 
               goals.isBlank() && 
               additionalInfo.isBlank()
    }
    
    fun toSystemPrompt(): String {
        if (isEmpty()) return ""
        
        val parts = mutableListOf<String>()
        parts.add("PERSONALIZATION CONTEXT:")
        parts.add("You are interacting with a specific user. Use the following information to personalize your responses:")
        
        if (name.isNotBlank()) {
            parts.add("- Name: $name")
        }
        
        if (preferences.isNotBlank()) {
            parts.add("- Preferences: $preferences")
        }
        
        if (habits.isNotBlank()) {
            parts.add("- Habits: $habits")
        }
        
        if (workStyle.isNotBlank()) {
            parts.add("- Work Style: $workStyle")
        }
        
        if (communicationStyle.isNotBlank()) {
            parts.add("- Communication Style: $communicationStyle")
        }
        
        if (interests.isNotBlank()) {
            parts.add("- Interests: $interests")
        }
        
        if (goals.isNotBlank()) {
            parts.add("- Goals: $goals")
        }
        
        if (additionalInfo.isNotBlank()) {
            parts.add("- Additional Information: $additionalInfo")
        }
        
        parts.add("")
        parts.add("IMPORTANT: Use this information to:")
        parts.add("1. Address the user by name when appropriate")
        parts.add("2. Adapt your communication style to match their preferences")
        parts.add("3. Consider their habits and work style when making suggestions")
        parts.add("4. Reference their interests and goals when relevant")
        parts.add("5. Provide personalized recommendations based on their profile")
        
        return parts.joinToString("\n")
    }
}

