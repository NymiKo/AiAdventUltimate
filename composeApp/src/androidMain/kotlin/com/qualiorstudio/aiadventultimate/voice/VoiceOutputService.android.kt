package com.qualiorstudio.aiadventultimate.voice

actual fun createVoiceOutputService(): VoiceOutputService {
    return AndroidVoiceOutputService()
}

class AndroidVoiceOutputService : VoiceOutputService {
    override fun isSupported(): Boolean = false
    
    override suspend fun speak(text: String): Result<Unit> {
        return Result.failure(Exception("TTS not supported on Android yet"))
    }
    
    override fun stopSpeaking() {
    }
    
    override fun isSpeaking(): Boolean = false
}

