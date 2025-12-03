package com.qualiorstudio.aiadventultimate.service

import com.qualiorstudio.aiadventultimate.ai.FileStorage
import com.qualiorstudio.aiadventultimate.model.Ticket
import com.qualiorstudio.aiadventultimate.model.TicketStatus
import com.qualiorstudio.aiadventultimate.model.User
import com.qualiorstudio.aiadventultimate.utils.getStorageDirectory
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString

@Serializable
data class CRMData(
    val users: List<User> = emptyList(),
    val tickets: List<Ticket> = emptyList()
)

interface CRMService {
    suspend fun getAllUsers(): List<User>
    suspend fun getUserById(userId: String): User?
    suspend fun getAllTickets(): List<Ticket>
    suspend fun getTicketById(ticketId: String): Ticket?
    suspend fun getTicketsByUserId(userId: String): List<Ticket>
    suspend fun searchTickets(query: String): List<Ticket>
    suspend fun createTicket(ticket: Ticket)
    suspend fun updateTicket(ticket: Ticket)
    suspend fun createUser(user: User)
}

class CRMServiceImpl(
    private val storagePath: String = "${getStorageDirectory()}/crm_data.json"
) : CRMService {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }
    
    private val fileStorage = FileStorage(storagePath)
    
    private fun loadData(): CRMData {
        return try {
            if (!fileStorage.exists()) {
                return CRMData()
            }
            val jsonString = fileStorage.readText()
            if (jsonString.isBlank()) {
                return CRMData()
            }
            json.decodeFromString<CRMData>(jsonString)
        } catch (e: Exception) {
            println("Failed to load CRM data: ${e.message}")
            e.printStackTrace()
            CRMData()
        }
    }
    
    private fun saveData(data: CRMData) {
        try {
            val jsonString = json.encodeToString(CRMData.serializer(), data)
            fileStorage.writeText(jsonString)
        } catch (e: Exception) {
            println("Failed to save CRM data: ${e.message}")
            e.printStackTrace()
            throw Exception("Failed to save CRM data: ${e.message}")
        }
    }
    
    override suspend fun getAllUsers(): List<User> {
        return loadData().users
    }
    
    override suspend fun getUserById(userId: String): User? {
        return loadData().users.find { it.id == userId }
    }
    
    override suspend fun getAllTickets(): List<Ticket> {
        return loadData().tickets
    }
    
    override suspend fun getTicketById(ticketId: String): Ticket? {
        return loadData().tickets.find { it.id == ticketId }
    }
    
    override suspend fun getTicketsByUserId(userId: String): List<Ticket> {
        return loadData().tickets.filter { it.userId == userId }
    }
    
    override suspend fun searchTickets(query: String): List<Ticket> {
        val lowerQuery = query.lowercase()
        return loadData().tickets.filter { ticket ->
            ticket.title.lowercase().contains(lowerQuery) ||
            ticket.description.lowercase().contains(lowerQuery) ||
            ticket.tags.any { it.lowercase().contains(lowerQuery) }
        }
    }
    
    override suspend fun createTicket(ticket: Ticket) {
        val data = loadData()
        val updatedTickets = data.tickets + ticket
        saveData(data.copy(tickets = updatedTickets))
    }
    
    override suspend fun updateTicket(ticket: Ticket) {
        val data = loadData()
        val updatedTickets = data.tickets.map { if (it.id == ticket.id) ticket else it }
        saveData(data.copy(tickets = updatedTickets))
    }
    
    override suspend fun createUser(user: User) {
        val data = loadData()
        val updatedUsers = data.users + user
        saveData(data.copy(users = updatedUsers))
    }
}

fun createCRMService(): CRMService = CRMServiceImpl()

