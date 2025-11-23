package com.qualiorstudio.aiadventultimate.voice

actual fun createVoiceInputService(): VoiceInputService {
    return EmptyVoiceInputService()
}

class EmptyVoiceInputService : VoiceInputService {
    override fun isSupported(): Boolean = false
    
    override suspend fun startRecording() {}
    
    override suspend fun stopRecording(): Result<String> {
        return Result.failure(Exception("Голосовой ввод пока не поддерживается на Android"))
    }
    
    override fun isRecording(): Boolean = false
}

