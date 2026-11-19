package com.example.viewmodel

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.MediaPlayer
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.AudioExportManager
import com.example.data.UserPreferencesRepository
import com.example.data.UserSettings
import com.example.data.VoiceCatalog
import com.example.model.AppThemeMode
import com.example.model.AudioFormat
import com.example.model.ExportUiState
import com.example.model.ExportedAudioItem
import com.example.model.LanguageOption
import com.example.model.TtsPlaybackStatus
import com.example.model.VocalProfile
import com.example.tts.TtsEngineManager
import com.example.tts.TtsPlaybackService
import com.example.tts.TtsRuntimeState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class AudioPlayerState(
    val activeItemId: String? = null,
    val isPlaying: Boolean = false,
    val currentPositionMs: Int = 0,
    val durationMs: Int = 0
)

class VaniVoxViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext = application.applicationContext
    private val prefsRepo = UserPreferencesRepository(appContext)
    private val ttsManager = TtsEngineManager(appContext)
    private val exportManager = AudioExportManager(appContext)

    val settings: StateFlow<UserSettings> = prefsRepo.settingsFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, UserSettings())

    val ttsState: StateFlow<TtsRuntimeState> = ttsManager.state

    val exports: StateFlow<List<ExportedAudioItem>> = exportManager.exports

    private val _inputText = MutableStateFlow(VoiceCatalog.languageForTag("hi-IN").sampleText)
    val inputText: StateFlow<String> = _inputText.asStateFlow()

    private val _exportState = MutableStateFlow<ExportUiState>(ExportUiState.Idle)
    val exportState: StateFlow<ExportUiState> = _exportState.asStateFlow()

    private val _playerState = MutableStateFlow(AudioPlayerState())
    val playerState: StateFlow<AudioPlayerState> = _playerState.asStateFlow()

    private val _statusBanner = MutableStateFlow<String?>(null)
    val statusBanner: StateFlow<String?> = _statusBanner.asStateFlow()

    val activeLanguage: StateFlow<LanguageOption> = settings
        .combine(_inputText) { s, _ -> VoiceCatalog.languageForTag(s.selectedLanguageTag) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, VoiceCatalog.languages.first())

    val activeProfile: StateFlow<VocalProfile> = settings
        .combine(_inputText) { s, _ -> VoiceCatalog.profileForId(s.selectedProfileId) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, VoiceCatalog.vocalProfiles.first())

    private var mediaPlayer: MediaPlayer? = null
    private var progressJob: Job? = null
    private var hasNotificationPermission: Boolean = true

    private val notificationReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.getStringExtra(TtsPlaybackService.EXTRA_CMD)) {
                TtsPlaybackService.CMD_TOGGLE -> toggleSpeakOrPause()
                TtsPlaybackService.CMD_STOP -> stopSpeaking()
            }
        }
    }

    init {
        val filter = IntentFilter(TtsPlaybackService.BROADCAST_CONTROL)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            appContext.registerReceiver(notificationReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            appContext.registerReceiver(notificationReceiver, filter)
        }

        ttsManager.onPlaybackStarted = { title ->
            if (hasNotificationPermission) {
                runCatching { TtsPlaybackService.updateForeground(appContext, title, isPaused = false) }
            }
        }
        ttsManager.onPlaybackPaused = {
            if (hasNotificationPermission) {
                val lang = VoiceCatalog.languageForTag(settings.value.selectedLanguageTag)
                val prof = VoiceCatalog.profileForId(settings.value.selectedProfileId)
                runCatching {
                    TtsPlaybackService.updateForeground(
                        appContext,
                        "${lang.displayName} • ${prof.displayName}",
                        isPaused = true
                    )
                }
            }
        }
        ttsManager.onPlaybackCompletedOrStopped = {
            TtsPlaybackService.dismiss(appContext)
        }

        viewModelScope.launch {
            settings.collect { s ->
                val profile = VoiceCatalog.profileForId(s.selectedProfileId)
                ttsManager.configureVoice(
                    languageTag = s.selectedLanguageTag,
                    profile = profile,
                    offlineOnly = s.offlineOnlyMode,
                    pitchOverride = s.pitchOverride,
                    speedOverride = s.speedOverride,
                    pauseCadenceMs = s.pauseCadenceMs,
                    preferredSystemVoiceName = s.selectedSystemVoiceName
                )
            }
        }
    }

    fun setNotificationPermissionGranted(granted: Boolean) {
        hasNotificationPermission = granted
    }

    fun updateInputText(newText: String) {
        _inputText.value = newText
    }

    fun loadSampleForActiveLanguage() {
        val lang = VoiceCatalog.languageForTag(settings.value.selectedLanguageTag)
        _inputText.value = lang.sampleText
    }

    fun selectLanguage(tag: String, replaceSampleIfDefault: Boolean = true) {
        val prevSample = VoiceCatalog.languageForTag(settings.value.selectedLanguageTag).sampleText
        val currentText = _inputText.value.trim()
        val shouldReplace = replaceSampleIfDefault &&
            (currentText.isEmpty() || currentText == prevSample || VoiceCatalog.languages.any { it.sampleText == currentText })

        viewModelScope.launch {
            prefsRepo.setLanguageTag(tag)
            if (shouldReplace) {
                _inputText.value = VoiceCatalog.languageForTag(tag).sampleText
            }
        }
    }

    fun selectVocalProfile(profile: VocalProfile) {
        viewModelScope.launch {
            prefsRepo.setVocalProfile(profile.id, profile.naturalPauseMs)
        }
    }

    fun previewVocalProfile(profile: VocalProfile) {
        stopExportPlayback()
        viewModelScope.launch {
            prefsRepo.setVocalProfile(profile.id, profile.naturalPauseMs)
            val lang = VoiceCatalog.languageForTag(settings.value.selectedLanguageTag)
            ttsManager.configureVoice(
                languageTag = settings.value.selectedLanguageTag,
                profile = profile,
                offlineOnly = settings.value.offlineOnlyMode,
                pitchOverride = settings.value.pitchOverride,
                speedOverride = settings.value.speedOverride,
                pauseCadenceMs = profile.naturalPauseMs,
                preferredSystemVoiceName = null
            )
            val previewText = if (lang.tag.startsWith("hi")) {
                "नमस्ते, मैं ${profile.displayName} हूँ। यह ${profile.styleType.label} आवाज़ का नमूना है।"
            } else {
                "Hello, I am ${profile.displayName}. This is my ${profile.styleType.label.lowercase()} studio voice."
            }
            ttsManager.speak(previewText, "${profile.displayName} Preview")
        }
    }

    fun selectSystemVoice(voiceName: String?) {
        viewModelScope.launch {
            prefsRepo.setSystemVoiceName(voiceName)
        }
    }

    fun cycleThemeMode() {
        val next = when (settings.value.themeMode) {
            AppThemeMode.SYSTEM -> AppThemeMode.DARK
            AppThemeMode.DARK -> AppThemeMode.LIGHT
            AppThemeMode.LIGHT -> AppThemeMode.SYSTEM
        }
        viewModelScope.launch { prefsRepo.setThemeMode(next) }
    }

    fun setThemeMode(mode: AppThemeMode) {
        viewModelScope.launch { prefsRepo.setThemeMode(mode) }
    }

    fun toggleOfflineOnlyMode() {
        viewModelScope.launch { prefsRepo.setOfflineOnlyMode(!settings.value.offlineOnlyMode) }
    }

    fun toggleBatterySaverMode() {
        viewModelScope.launch { prefsRepo.setBatterySaverMode(!settings.value.batterySaverMode) }
    }

    fun setPitchOverride(pitch: Float) {
        viewModelScope.launch { prefsRepo.setPitchOverride(pitch) }
    }

    fun setSpeedOverride(speed: Float) {
        viewModelScope.launch { prefsRepo.setSpeedOverride(speed) }
    }

    fun cycleQuickSpeed() {
        val presets = listOf(0.85f, 1.0f, 1.15f, 1.35f)
        val current = settings.value.speedOverride
        val next = presets.firstOrNull { it > current + 0.03f } ?: presets.first()
        setSpeedOverride(next)
    }

    fun setPauseCadenceMs(ms: Long) {
        viewModelScope.launch { prefsRepo.setPauseCadenceMs(ms) }
    }

    fun resetAcousticTuning() {
        viewModelScope.launch {
            prefsRepo.setPitchOverride(1.0f)
            prefsRepo.setSpeedOverride(1.0f)
            val prof = VoiceCatalog.profileForId(settings.value.selectedProfileId)
            prefsRepo.setPauseCadenceMs(prof.naturalPauseMs)
        }
    }

    fun toggleSpeakOrPause() {
        stopExportPlayback()
        val lang = VoiceCatalog.languageForTag(settings.value.selectedLanguageTag)
        val prof = VoiceCatalog.profileForId(settings.value.selectedProfileId)
        val titleHint = "${lang.displayName} • ${prof.displayName} (${prof.styleType.label})"

        when (ttsState.value.status) {
            TtsPlaybackStatus.SPEAKING, TtsPlaybackStatus.PREPARING -> ttsManager.pause()
            TtsPlaybackStatus.PAUSED -> ttsManager.resume(titleHint)
            TtsPlaybackStatus.IDLE, TtsPlaybackStatus.ERROR -> {
                if (_inputText.value.isBlank()) {
                    _statusBanner.value = "Enter or paste text first to speak."
                    return
                }
                ttsManager.speak(_inputText.value, titleHint)
            }
        }
    }

    fun stopSpeaking() {
        ttsManager.stop()
    }

    fun exportSpeech(title: String, format: AudioFormat) {
        val text = _inputText.value.trim()
        if (text.isEmpty()) {
            _exportState.value = ExportUiState.Error("Please enter text before exporting audio.")
            return
        }
        stopSpeaking()
        viewModelScope.launch {
            prefsRepo.setPreferredExportFormat(format)
            _exportState.value = ExportUiState.Exporting(format, "Synthesizing high-fidelity voice…")
            try {
                val wavSegments = ttsManager.synthesizeSegmentsToTempWavs(text)
                if (wavSegments.isEmpty()) {
                    _exportState.value = ExportUiState.Error(
                        "Audio synthesis failed. Check that an offline or system voice is installed for this language."
                    )
                    return@launch
                }
                val lang = VoiceCatalog.languageForTag(settings.value.selectedLanguageTag)
                val prof = VoiceCatalog.profileForId(settings.value.selectedProfileId)
                val item = exportManager.stitchAndExport(
                    wavSegments = wavSegments,
                    title = title.ifBlank { lang.displayName + " Voice" },
                    format = format,
                    languageName = lang.displayName,
                    vocalProfileName = "${prof.displayName} (${prof.gender.label} · ${prof.styleType.label})",
                    onProgress = { stage ->
                        _exportState.value = ExportUiState.Exporting(format, stage)
                    }
                )
                _exportState.value = ExportUiState.Success(item)
            } catch (e: Exception) {
                _exportState.value = ExportUiState.Error(e.localizedMessage ?: "Export failed.")
            }
        }
    }

    fun clearExportState() {
        _exportState.value = ExportUiState.Idle
    }

    fun clearStatusBanner() {
        _statusBanner.value = null
    }

    fun toggleExportPlayback(item: ExportedAudioItem) {
        val current = _playerState.value
        if (current.activeItemId == item.id && mediaPlayer != null) {
            if (current.isPlaying) {
                mediaPlayer?.pause()
                progressJob?.cancel()
                _playerState.value = current.copy(isPlaying = false)
            } else {
                ttsManager.stop()
                mediaPlayer?.start()
                _playerState.value = current.copy(isPlaying = true)
                startProgressUpdates()
            }
            return
        }

        stopExportPlayback()
        ttsManager.stop()
        runCatching {
            val mp = MediaPlayer().apply {
                setDataSource(item.filePath)
                prepare()
                setOnCompletionListener {
                    stopExportPlayback()
                }
                start()
            }
            mediaPlayer = mp
            _playerState.value = AudioPlayerState(
                activeItemId = item.id,
                isPlaying = true,
                currentPositionMs = 0,
                durationMs = mp.duration.coerceAtLeast(1)
            )
            startProgressUpdates()
        }.onFailure {
            _statusBanner.value = "Unable to play ${item.fileName}"
            stopExportPlayback()
        }
    }

    fun seekExportPlayback(positionMs: Int) {
        mediaPlayer?.let { mp ->
            runCatching {
                mp.seekTo(positionMs)
                _playerState.value = _playerState.value.copy(currentPositionMs = positionMs)
            }
        }
    }

    fun stopExportPlayback() {
        progressJob?.cancel()
        progressJob = null
        runCatching {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        }
        mediaPlayer = null
        _playerState.value = AudioPlayerState()
    }

    private fun startProgressUpdates() {
        progressJob?.cancel()
        progressJob = viewModelScope.launch {
            while (isActive && mediaPlayer != null) {
                val mp = mediaPlayer ?: break
                val playing = runCatching { mp.isPlaying }.getOrDefault(false)
                val pos = runCatching { mp.currentPosition }.getOrDefault(0)
                _playerState.value = _playerState.value.copy(
                    isPlaying = playing,
                    currentPositionMs = pos
                )
                if (!playing) break
                delay(200L)
            }
        }
    }

    fun saveItemToDownloads(item: ExportedAudioItem) {
        viewModelScope.launch {
            val uri = exportManager.saveItemToDownloads(item)
            _statusBanner.value = if (uri != null) {
                "Saved ${item.fileName} to Downloads/VaniVox"
            } else {
                "Could not save to Downloads."
            }
        }
    }

    fun deleteExportItem(item: ExportedAudioItem) {
        if (_playerState.value.activeItemId == item.id) {
            stopExportPlayback()
        }
        exportManager.deleteItem(item)
    }

    fun buildShareIntent(item: ExportedAudioItem): Intent? = exportManager.buildShareIntent(item)

    fun buildInstallVoiceDataIntent(): Intent = ttsManager.buildInstallVoiceDataIntent()

    override fun onCleared() {
        super.onCleared()
        runCatching { appContext.unregisterReceiver(notificationReceiver) }
        stopExportPlayback()
        TtsPlaybackService.dismiss(appContext)
        ttsManager.release()
    }
}
