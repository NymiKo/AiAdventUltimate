package com.qualiorstudio.aiadventultimate.voice

actual fun createVoiceOutputService(): VoiceOutputService {
    return IosVoiceOutputService()
}

class IosVoiceOutputService : VoiceOutputService {
    override fun isSupported(): Boolean = false
    
    override suspend fun speak(text: String): Result<Unit> {
        return Result.failure(Exception("TTS not supported on iOS yet"))
    }
    
    override fun stopSpeaking() {
    }
    
    override fun isSpeaking(): Boolean = false
}

