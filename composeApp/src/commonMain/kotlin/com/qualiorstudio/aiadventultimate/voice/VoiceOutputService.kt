package com.qualiorstudio.aiadventultimate.voice

interface VoiceOutputService {
    fun isSupported(): Boolean
    
    suspend fun speak(text: String): Result<Unit>
    
    fun stopSpeaking()
    
    fun isSpeaking(): Boolean
}

expect fun createVoiceOutputService(): VoiceOutputService

