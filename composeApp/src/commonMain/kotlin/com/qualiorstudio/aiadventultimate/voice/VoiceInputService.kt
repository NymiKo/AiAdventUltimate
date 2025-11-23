package com.qualiorstudio.aiadventultimate.voice

interface VoiceInputService {
    fun isSupported(): Boolean
    
    suspend fun startRecording()
    
    suspend fun stopRecording(): Result<String>
    
    fun isRecording(): Boolean
}

expect fun createVoiceInputService(): VoiceInputService

