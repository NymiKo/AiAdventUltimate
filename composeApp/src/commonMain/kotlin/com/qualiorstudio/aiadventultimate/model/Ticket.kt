package com.qualiorstudio.aiadventultimate.model

import com.qualiorstudio.aiadventultimate.utils.currentTimeMillis
import kotlinx.serialization.Serializable

@Serializable
data class Ticket(
    val id: String,
    val userId: String,
    val userName: String? = null,
    val title: String,
    val description: String,
    val status: TicketStatus,
    val createdAt: Long = currentTimeMillis(),
    val updatedAt: Long = currentTimeMillis(),
    val tags: List<String> = emptyList()
)

@Serializable
enum class TicketStatus {
    OPEN,
    IN_PROGRESS,
    RESOLVED,
    CLOSED
}

@Serializable
data class User(
    val id: String,
    val name: String,
    val email: String? = null,
    val createdAt: Long = currentTimeMillis()
)

