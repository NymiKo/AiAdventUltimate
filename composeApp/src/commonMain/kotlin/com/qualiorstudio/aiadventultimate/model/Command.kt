package com.qualiorstudio.aiadventultimate.model

data class Command(
    val name: String,
    val description: String,
    val example: String? = null
) {
    val fullCommand: String
        get() = "/$name"
}

object Commands {
    val HELP = Command(
        name = "help",
        description = "Получить помощь по проекту и ответы на вопросы",
        example = "/help как использовать агентов"
    )
    
    val ALL = listOf(HELP)
    
    fun findByName(name: String): Command? {
        return ALL.find { it.name == name }
    }
    
    fun filterByQuery(query: String): List<Command> {
        val lowerQuery = query.lowercase()
        return ALL.filter { 
            it.name.contains(lowerQuery) || 
            it.description.lowercase().contains(lowerQuery)
        }
    }
}

