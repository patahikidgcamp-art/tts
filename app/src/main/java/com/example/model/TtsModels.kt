package com.example.model

enum class AppThemeMode(val label: String) {
    SYSTEM("Auto"),
    LIGHT("Light"),
    DARK("Dark")
}

enum class AudioFormat(
    val displayName: String,
    val extension: String,
    val mimeType: String,
    val specLabel: String
) {
    MP3(
        displayName = "MP3",
        extension = "mp3",
        mimeType = "audio/mpeg",
        specLabel = "128 kbps • Universal"
    ),
    WAV(
        displayName = "WAV",
        extension = "wav",
        mimeType = "audio/wav",
        specLabel = "16-bit PCM • Lossless"
    ),
    AAC(
        displayName = "AAC",
        extension = "aac",
        mimeType = "audio/aac",
        specLabel = "128 kbps • Compact Hi-Fi"
    )
}

enum class VoiceGender(val label: String) {
    FEMALE("Female"),
    MALE("Male")
}

enum class VoiceStyleType(val label: String) {
    SOFT("Soft"),
    SWEET("Sweet"),
    HARD("Bold / Hard"),
    WARM("Warm Story"),
    CRISP("Crisp Studio")
}

data class VocalProfile(
    val id: String,
    val displayName: String,
    val gender: VoiceGender,
    val styleType: VoiceStyleType,
    val tagline: String,
    val pitchMultiplier: Float,
    val rateMultiplier: Float,
    val naturalPauseMs: Long,
    val preferHighQuality: Boolean = true
)

data class LanguageOption(
    val tag: String,
    val shortCode: String,
    val displayName: String,
    val nativeName: String,
    val sampleText: String,
    val isQuickPin: Boolean = false
)

data class SystemVoiceInfo(
    val name: String,
    val displayName: String,
    val localeTag: String,
    val isOfflineReady: Boolean,
    val quality: Int,
    val estimatedGender: VoiceGender?
)

data class ExportedAudioItem(
    val id: String,
    val title: String,
    val fileName: String,
    val filePath: String,
    val format: AudioFormat,
    val languageName: String,
    val vocalProfileName: String,
    val durationMs: Long,
    val fileSizeBytes: Long,
    val createdAtMillis: Long,
    val downloadsUriString: String? = null
)

enum class TtsPlaybackStatus {
    IDLE,
    PREPARING,
    SPEAKING,
    PAUSED,
    ERROR
}

sealed interface ExportUiState {
    data object Idle : ExportUiState
    data class Exporting(val format: AudioFormat, val stage: String) : ExportUiState
    data class Success(val item: ExportedAudioItem) : ExportUiState
    data class Error(val message: String) : ExportUiState
}
