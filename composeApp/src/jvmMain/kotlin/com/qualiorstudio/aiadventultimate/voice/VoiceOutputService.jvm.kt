package com.qualiorstudio.aiadventultimate.voice

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.*
import java.io.ByteArrayInputStream
import javax.sound.sampled.*

actual fun createVoiceOutputService(): VoiceOutputService {
    return DesktopVoiceOutputService()
}

class DesktopVoiceOutputService : VoiceOutputService {
    private val client = HttpClient()
    private var playbackJob: Job? = null
    private var sourceDataLine: SourceDataLine? = null
    private var isSpeakingFlag = false
    
    companion object {
        private val envCache = mutableMapOf<String, String>()
        
        init {
            loadEnvFile()
        }
        
        private fun loadEnvFile() {
            try {
                val possiblePaths = listOf(
                    ".env",
                    "../.env",
                    "../../.env",
                    "../../../.env",
                    System.getProperty("user.dir") + "/.env"
                )
                
                var envFile: java.io.File? = null
                for (path in possiblePaths) {
                    val file = java.io.File(path)
                    if (file.exists()) {
                        envFile = file
                        println("✓ Найден .env файл: ${file.absolutePath}")
                        break
                    }
                }
                
                if (envFile != null && envFile.exists()) {
                    val lines = envFile.readLines()
                    
                    lines.forEach { line ->
                        if (line.isNotBlank() && !line.startsWith("#")) {
                            val parts = line.split("=", limit = 2)
                            if (parts.size == 2) {
                                val key = parts[0].trim()
                                val value = parts[1].trim()
                                envCache[key] = value
                            }
                        }
                    }
                    println("✓ Загружено ${envCache.size} переменных из .env для TTS")
                }
            } catch (e: Exception) {
                println("⚠️ Не удалось загрузить .env для TTS: ${e.message}")
            }
        }
        
        fun getEnvVar(key: String): String {
            return System.getenv(key) ?: envCache[key] ?: ""
        }
    }
    
    override fun isSupported(): Boolean = true
    
    override suspend fun speak(text: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            stopSpeaking()
            
            val apiKey = getEnvVar("YANDEX_API_KEY")
            val folderId = getEnvVar("YANDEX_FOLDER_ID")
            
            if (apiKey.isEmpty() || folderId.isEmpty()) {
                return@withContext Result.failure(
                    Exception("Требуется настройка YANDEX_API_KEY и YANDEX_FOLDER_ID в файле .env")
                )
            }
            
            println("🔊 Синтезируем речь: ${text.take(50)}...")
            
            val response: HttpResponse = client.post("https://tts.api.cloud.yandex.net/speech/v1/tts:synthesize") {
                header("Authorization", "Api-Key $apiKey")
                contentType(ContentType.Application.FormUrlEncoded)
                setBody(
                    listOf(
                        "text" to text,
                        "lang" to "ru-RU",
                        "voice" to "filipp",
                        "emotion" to "neutral",
                        "speed" to "1.0",
                        "format" to "lpcm",
                        "sampleRateHertz" to "48000",
                        "folderId" to folderId
                    ).formUrlEncode()
                )
            }
            
            if (response.status.isSuccess()) {
                val audioData = response.readRawBytes()
                println("✅ Получено ${audioData.size} байт аудио данных")
                
                playAudio(audioData)
                Result.success(Unit)
            } else {
                val errorBody = response.bodyAsText()
                println("❌ Ошибка синтеза речи: ${response.status}\n$errorBody")
                Result.failure(Exception("Ошибка синтеза речи: ${response.status}"))
            }
        } catch (e: Exception) {
            println("💥 Исключение при синтезе речи: ${e.message}")
            e.printStackTrace()
            Result.failure(e)
        }
    }
    
    override fun stopSpeaking() {
        playbackJob?.cancel()
        playbackJob = null
        sourceDataLine?.apply {
            stop()
            close()
        }
        sourceDataLine = null
        isSpeakingFlag = false
    }
    
    override fun isSpeaking(): Boolean = isSpeakingFlag
    
    private suspend fun playAudio(audioData: ByteArray) = withContext(Dispatchers.IO) {
        try {
            isSpeakingFlag = true
            
            val format = AudioFormat(
                48000f,
                16,
                1,
                true,
                false
            )
            
            val info = DataLine.Info(SourceDataLine::class.java, format)
            
            if (!AudioSystem.isLineSupported(info)) {
                println("⚠️ Формат аудио не поддерживается")
                return@withContext
            }
            
            sourceDataLine = (AudioSystem.getLine(info) as SourceDataLine).apply {
                open(format)
                start()
            }
            
            playbackJob = CoroutineScope(Dispatchers.IO).launch {
                val line = sourceDataLine ?: return@launch
                val stream = ByteArrayInputStream(audioData)
                val buffer = ByteArray(4096)
                
                while (isActive) {
                    val bytesRead = stream.read(buffer)
                    if (bytesRead == -1) break
                    line.write(buffer, 0, bytesRead)
                }
                
                line.drain()
                line.stop()
                line.close()
                isSpeakingFlag = false
            }
            
            playbackJob?.join()
        } catch (e: Exception) {
            println("💥 Ошибка воспроизведения: ${e.message}")
            e.printStackTrace()
            isSpeakingFlag = false
        }
    }
}

