package com.qualiorstudio.aiadventultimate.api

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

class YandexGPT(
    var apiKey: String,
    private val folderId: String,
) {
    private val client: HttpClient by lazy { createHttpClient() }
    private val baseUrl = "https://llm.api.cloud.yandex.net/foundationModels/v1/completion"

    private fun createHttpClient(): HttpClient {
        return HttpClient {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    prettyPrint = true
                    isLenient = true
                })
            }
        }
    }

    suspend fun sendMessage(
        messages: List<MessageInfo>,
        modelUri: String = "gpt://$folderId/yandexgpt/rc",
    ): StructuredResponse {
        return try {
            val systemInstruction = """
                Системный промт: AI-диетолог

Ты — эксперт по персонализированному питанию. Помогаешь составить индивидуальное меню, задавая уточняющие вопросы по одному в логичной последовательности.

Принципы работы:
Постепенность — задаешь один вопрос за раз, ждешь ответа.

Логичный порядок — начинаешь с базовых данных (рост/вес), затем переходишь к целям, ограничениям и образу жизни.

Автоматические расчеты — после получения роста и веса сразу вычисляешь норму калорий и озвучиваешь ее пользователю.

Как вести диалог:
Сначала запрашиваешь рост, вес и желаемые изменения (цель + срок).

Затем уточняешь ограничения (аллергии, диеты и т.д.).

Далее спрашиваешь про режим (время на готовку, питание вне дома).

В конце — вкусовые предпочтения (любимые продукты).

После сбора данных выдаешь недельное меню, включая:

Калорийность и цели

Блюда с пометками времени приготовления

Список продуктов (оптом и на каждый день)

Важно:

Вопросы должны быть естественными, а не шаблонными.

Не перегружай пользователя — спрашивай только то, что нужно для составления меню.

Держи структуру, но адаптируй формулировки под контекст.
                """.trimMargin()

            val modifiedMessages = messages.toMutableList()
            if (modifiedMessages.isNotEmpty() && modifiedMessages.last().role == "user") {
                val lastIndex = modifiedMessages.lastIndex
                modifiedMessages[lastIndex] = MessageInfo(
                    role = modifiedMessages[lastIndex].role,
                    text = systemInstruction + modifiedMessages[lastIndex].text
                )
            }

            val request = ChatRequest(
                modelUri = modelUri,
                messages = modifiedMessages,
            )

            val response = client.post(baseUrl) {
                header("Authorization", "Bearer $apiKey")
                contentType(ContentType.Application.Json)
                setBody(request)
            }

            if (response.status == HttpStatusCode.UnprocessableEntity) {
                val errorBody = response.bodyAsText()
                return StructuredResponse(
                    title = "Ошибка",
                    answer = "API Validation Error: $errorBody",
                    question = "",
                    tokens = 0
                )
            }

            if (!response.status.isSuccess()) {
                val errorBody = response.bodyAsText()
                return StructuredResponse(
                    title = "Ошибка",
                    answer = "API Error (${response.status.value}): $errorBody",
                    question = "",
                    tokens = 0
                )
            }

            val responseBody = response.body<ChatResponse>()
            val rawText = responseBody.result.alternatives.first().message.text
            val actualTokens = responseBody.result.usage.totalTokens.toIntOrNull() ?: 0
            
            parseStructuredResponse(rawText, actualTokens)
        } catch (e: Exception) {
            StructuredResponse(
                title = "Ошибка",
                answer = "Request failed: ${e.message ?: "Unknown error"}",
                question = "",
                tokens = 0
            )
        }
    }

    private fun parseStructuredResponse(text: String, actualTokens: Int): StructuredResponse {
        return try {
            val cleanText = text
                .removeSurrounding("```json", "```")
                .removeSurrounding("```", "```")
                .trim()

            val parsed = Json { ignoreUnknownKeys = true }.decodeFromString<StructuredResponse>(cleanText)
            parsed.copy(tokens = actualTokens)
        } catch (e: Exception) {
            StructuredResponse(
                title = "Ответ",
                answer = text,
                question = "",
                tokens = actualTokens
            )
        }
    }
}

