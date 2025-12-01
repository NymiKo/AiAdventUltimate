package com.qualiorstudio.aiadventultimate.voice

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import javax.sound.sampled.*

actual fun createVoiceInputService(): VoiceInputService {
    return DesktopVoiceInputService()
}

class DesktopVoiceInputService : VoiceInputService {
    private var targetDataLine: TargetDataLine? = null
    private var recordingJob: Job? = null
    private val audioOutputStream = ByteArrayOutputStream()
    private val client = HttpClient()
    
    private val format = AudioFormat(
        16000f,
        16,
        1,
        true,
        false
    )
    
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
                    println("🔍 Проверяем: ${file.absolutePath}")
                    if (file.exists()) {
                        envFile = file
                        println("✓ Найден .env файл: ${file.absolutePath}")
                        break
                    }
                }
                
                if (envFile != null && envFile.exists()) {
                    val lines = envFile.readLines()
                    println("🔍 Прочитано строк: ${lines.size}")
                    
                    lines.forEach { line ->
                        if (line.isNotBlank() && !line.startsWith("#")) {
                            val parts = line.split("=", limit = 2)
                            if (parts.size == 2) {
                                val key = parts[0].trim()
                                val value = parts[1].trim()
                                envCache[key] = value
                                println("✓ Загружена переменная: $key")
                            }
                        }
                    }
                    println("✓ Всего загружено ${envCache.size} переменных из .env")
                    println("✓ YANDEX_API_KEY: ${if (envCache["YANDEX_API_KEY"]?.isNotEmpty() == true) "установлен" else "не найден"}")
                    println("✓ YANDEX_FOLDER_ID: ${if (envCache["YANDEX_FOLDER_ID"]?.isNotEmpty() == true) "установлен" else "не найден"}")
                } else {
                    println("⚠️ Файл .env не найден ни в одном из проверенных путей")
                    println("⚠️ Текущая рабочая директория: ${System.getProperty("user.dir")}")
                }
            } catch (e: Exception) {
                println("⚠️ Не удалось загрузить .env: ${e.message}")
                e.printStackTrace()
            }
        }
        
        fun getEnvVar(key: String): String {
            return System.getenv(key) ?: envCache[key] ?: ""
        }
    }
    
    override fun isSupported(): Boolean {
        return true
    }
    
    override suspend fun startRecording() = withContext(Dispatchers.IO) {
        try {
            audioOutputStream.reset()
            
            val info = DataLine.Info(TargetDataLine::class.java, format)
            
            if (!AudioSystem.isLineSupported(info)) {
                throw Exception("Микрофон не поддерживается")
            }
            
            targetDataLine = (AudioSystem.getLine(info) as TargetDataLine).apply {
                open(format)
                start()
            }
            
            recordingJob = CoroutineScope(Dispatchers.IO).launch {
                val buffer = ByteArray(4096)
                val line = targetDataLine ?: return@launch
                
                while (isActive && line.isOpen) {
                    val count = line.read(buffer, 0, buffer.size)
                    if (count > 0) {
                        audioOutputStream.write(buffer, 0, count)
                    }
                }
            }
        } catch (e: Exception) {
            stopRecordingInternal()
            throw e
        }
    }
    
    override suspend fun stopRecording(): Result<String> = withContext(Dispatchers.IO) {
        try {
            stopRecordingInternal()
            
            val pcmData = audioOutputStream.toByteArray()
            
            if (pcmData.isEmpty()) {
                return@withContext Result.failure(Exception("Аудио данные отсутствуют"))
            }
            
            println("🎤 Записано ${pcmData.size} байт PCM данных")
            
            val text = recognizeSpeech(pcmData)
            Result.success(text)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override fun isRecording(): Boolean {
        return targetDataLine?.isOpen == true && recordingJob?.isActive == true
    }
    
    private fun stopRecordingInternal() {
        recordingJob?.cancel()
        recordingJob = null
        
        targetDataLine?.apply {
            stop()
            close()
        }
        targetDataLine = null
    }
    
    private fun addWavHeader(pcmData: ByteArray): ByteArray {
        val sampleRate = 16000
        val channels = 1
        val bitsPerSample = 16
        
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val blockAlign = channels * bitsPerSample / 8
        val dataSize = pcmData.size
        val chunkSize = 36 + dataSize
        
        val header = ByteArray(44)
        
        header[0] = 'R'.code.toByte()
        header[1] = 'I'.code.toByte()
        header[2] = 'F'.code.toByte()
        header[3] = 'F'.code.toByte()
        
        header[4] = (chunkSize and 0xff).toByte()
        header[5] = ((chunkSize shr 8) and 0xff).toByte()
        header[6] = ((chunkSize shr 16) and 0xff).toByte()
        header[7] = ((chunkSize shr 24) and 0xff).toByte()
        
        header[8] = 'W'.code.toByte()
        header[9] = 'A'.code.toByte()
        header[10] = 'V'.code.toByte()
        header[11] = 'E'.code.toByte()
        
        header[12] = 'f'.code.toByte()
        header[13] = 'm'.code.toByte()
        header[14] = 't'.code.toByte()
        header[15] = ' '.code.toByte()
        
        header[16] = 16
        header[17] = 0
        header[18] = 0
        header[19] = 0
        
        header[20] = 1
        header[21] = 0
        
        header[22] = channels.toByte()
        header[23] = 0
        
        header[24] = (sampleRate and 0xff).toByte()
        header[25] = ((sampleRate shr 8) and 0xff).toByte()
        header[26] = ((sampleRate shr 16) and 0xff).toByte()
        header[27] = ((sampleRate shr 24) and 0xff).toByte()
        
        header[28] = (byteRate and 0xff).toByte()
        header[29] = ((byteRate shr 8) and 0xff).toByte()
        header[30] = ((byteRate shr 16) and 0xff).toByte()
        header[31] = ((byteRate shr 24) and 0xff).toByte()
        
        header[32] = blockAlign.toByte()
        header[33] = 0
        
        header[34] = bitsPerSample.toByte()
        header[35] = 0
        
        header[36] = 'd'.code.toByte()
        header[37] = 'a'.code.toByte()
        header[38] = 't'.code.toByte()
        header[39] = 'a'.code.toByte()
        
        header[40] = (dataSize and 0xff).toByte()
        header[41] = ((dataSize shr 8) and 0xff).toByte()
        header[42] = ((dataSize shr 16) and 0xff).toByte()
        header[43] = ((dataSize shr 24) and 0xff).toByte()
        
        return header + pcmData
    }
    
    private suspend fun recognizeSpeech(audioData: ByteArray): String {
        val apiKey = getEnvVar("YANDEX_API_KEY")
        val folderId = getEnvVar("YANDEX_FOLDER_ID")
        
        println("🔑 API Key: ${if (apiKey.isNotEmpty()) apiKey.take(10) + "..." else "пусто"}")
        println("📁 Folder ID: ${if (folderId.isNotEmpty()) folderId else "пусто"}")
        
        if (apiKey.isEmpty() || folderId.isEmpty()) {
            return "[Требуется настройка YANDEX_API_KEY и YANDEX_FOLDER_ID в файле .env]"
        }
        
        return try {
            println("📤 Отправляем ${audioData.size} байт аудио в Yandex SpeechKit")
            
            val response: HttpResponse = client.post("https://stt.api.cloud.yandex.net/speech/v1/stt:recognize") {
                header("Authorization", "Api-Key $apiKey")
                parameter("folderId", folderId)
                parameter("lang", "ru-RU")
                parameter("format", "lpcm")
                parameter("sampleRateHertz", "16000")
                contentType(ContentType("audio", "x-pcm;bit=16;rate=16000"))
                setBody(audioData)
            }
            
            println("📥 Получен ответ: ${response.status}")
            
            if (response.status.isSuccess()) {
                val responseBody = response.bodyAsText()
                println("✅ Ответ: $responseBody")
                val jsonResponse = Json.decodeFromString<YandexSTTResponse>(responseBody)
                jsonResponse.result ?: "Не удалось распознать речь"
            } else {
                val errorBody = response.bodyAsText()
                println("❌ Ошибка: $errorBody")
                "Ошибка распознавания: ${response.status}\n$errorBody"
            }
        } catch (e: Exception) {
            println("💥 Исключение: ${e.message}")
            e.printStackTrace()
            "Ошибка: ${e.message}"
        }
    }
}

@Serializable
data class YandexSTTResponse(
    val result: String? = null
)

