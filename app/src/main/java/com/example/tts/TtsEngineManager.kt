package com.example.tts

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import com.example.model.SystemVoiceInfo
import com.example.model.TtsPlaybackStatus
import com.example.model.VocalProfile
import com.example.model.VoiceGender
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.Locale
import java.util.UUID

data class TextSegment(
    val text: String,
    val startIndex: Int,
    val endIndex: Int,
    val pauseAfterMs: Long
)

data class TtsRuntimeState(
    val isReady: Boolean = false,
    val status: TtsPlaybackStatus = TtsPlaybackStatus.IDLE,
    val activeSegmentIndex: Int = 0,
    val totalSegments: Int = 0,
    val spokenRange: IntRange? = null,
    val activeVoiceDescription: String = "Initializing engine…",
    val isLanguageOfflineAvailable: Boolean = true,
    val availableVoicesForLocale: List<SystemVoiceInfo> = emptyList(),
    val errorMessage: String? = null
)

class TtsEngineManager(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var tts: TextToSpeech? = null
    private val initDeferred = CompletableDeferred<Boolean>()

    private val _state = MutableStateFlow(TtsRuntimeState())
    val state: StateFlow<TtsRuntimeState> = _state.asStateFlow()

    private var activeSegments: List<TextSegment> = emptyList()
    private var currentSegmentIndex: Int = 0
    private var currentLanguageTag: String = "hi-IN"
    private var currentProfile: VocalProfile? = null
    private var currentOfflineOnly: Boolean = true
    private var currentPitchOverride: Float = 1.0f
    private var currentSpeedOverride: Float = 1.0f
    private var currentPauseCadenceMs: Long = 240L
    private var currentSystemVoiceName: String? = null
    private var activePlaybackSessionId: String = ""

    private val synthesisWaiters = HashMap<String, CompletableDeferred<Boolean>>()

    var onPlaybackStarted: ((String) -> Unit)? = null
    var onPlaybackPaused: (() -> Unit)? = null
    var onPlaybackCompletedOrStopped: (() -> Unit)? = null

    init {
        initializeEngine()
    }

    private fun initializeEngine() {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                attachListener()
                _state.value = _state.value.copy(isReady = true, errorMessage = null)
                refreshLocaleVoices(currentLanguageTag, currentOfflineOnly, currentProfile, currentSystemVoiceName)
                if (!initDeferred.isCompleted) initDeferred.complete(true)
            } else {
                _state.value = _state.value.copy(
                    isReady = false,
                    status = TtsPlaybackStatus.ERROR,
                    errorMessage = "Text-to-Speech engine unavailable on this device."
                )
                if (!initDeferred.isCompleted) initDeferred.complete(false)
            }
        }
    }

    private fun attachListener() {
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                val id = utteranceId ?: return
                if (id.startsWith("synth_")) return
                val parts = id.split(":")
                if (parts.size < 3 || parts[0] != activePlaybackSessionId) return
                val segIdx = parts[1].toIntOrNull() ?: return
                scope.launch {
                    currentSegmentIndex = segIdx
                    val seg = activeSegments.getOrNull(segIdx)
                    _state.value = _state.value.copy(
                        status = TtsPlaybackStatus.SPEAKING,
                        activeSegmentIndex = segIdx,
                        spokenRange = seg?.let { it.startIndex..it.endIndex }
                    )
                }
            }

            override fun onRangeStart(utteranceId: String?, start: Int, end: Int, frame: Int) {
                val id = utteranceId ?: return
                if (id.startsWith("synth_")) return
                val parts = id.split(":")
                if (parts.size < 3 || parts[0] != activePlaybackSessionId) return
                val segIdx = parts[1].toIntOrNull() ?: return
                val seg = activeSegments.getOrNull(segIdx) ?: return
                scope.launch {
                    val globalStart = (seg.startIndex + start).coerceAtMost(seg.endIndex)
                    val globalEnd = (seg.startIndex + end).coerceAtMost(seg.endIndex)
                    _state.value = _state.value.copy(spokenRange = globalStart until globalEnd)
                }
            }

            override fun onDone(utteranceId: String?) {
                val id = utteranceId ?: return
                if (id.startsWith("synth_")) {
                    synthesisWaiters.remove(id)?.complete(true)
                    return
                }
                val parts = id.split(":")
                if (parts.size < 3 || parts[0] != activePlaybackSessionId) return
                val segIdx = parts[1].toIntOrNull() ?: return
                val kind = parts[2]
                scope.launch {
                    if (_state.value.status == TtsPlaybackStatus.PAUSED) return@launch
                    val isLastSegment = segIdx >= activeSegments.lastIndex
                    if (isLastSegment && (kind == "utt" || kind == "pause")) {
                        _state.value = _state.value.copy(
                            status = TtsPlaybackStatus.IDLE,
                            activeSegmentIndex = 0,
                            spokenRange = null
                        )
                        onPlaybackCompletedOrStopped?.invoke()
                    }
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                handleError(utteranceId)
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                handleError(utteranceId)
            }

            private fun handleError(utteranceId: String?) {
                val id = utteranceId ?: return
                if (id.startsWith("synth_")) {
                    synthesisWaiters.remove(id)?.complete(false)
                    return
                }
                scope.launch {
                    _state.value = _state.value.copy(
                        status = TtsPlaybackStatus.ERROR,
                        spokenRange = null,
                        errorMessage = "Voice playback encountered an error. Check offline language data."
                    )
                    onPlaybackCompletedOrStopped?.invoke()
                }
            }
        })
    }

    fun configureVoice(
        languageTag: String,
        profile: VocalProfile,
        offlineOnly: Boolean,
        pitchOverride: Float,
        speedOverride: Float,
        pauseCadenceMs: Long,
        preferredSystemVoiceName: String?
    ) {
        currentLanguageTag = languageTag
        currentProfile = profile
        currentOfflineOnly = offlineOnly
        currentPitchOverride = pitchOverride
        currentSpeedOverride = speedOverride
        currentPauseCadenceMs = pauseCadenceMs
        currentSystemVoiceName = preferredSystemVoiceName
        refreshLocaleVoices(languageTag, offlineOnly, profile, preferredSystemVoiceName)
    }

    private fun refreshLocaleVoices(
        languageTag: String,
        offlineOnly: Boolean,
        profile: VocalProfile?,
        preferredSystemVoiceName: String?
    ) {
        val engine = tts ?: return
        val locale = Locale.forLanguageTag(languageTag)
        val langResult = engine.setLanguage(locale)
        val langSupported = langResult != TextToSpeech.LANG_MISSING_DATA &&
            langResult != TextToSpeech.LANG_NOT_SUPPORTED

        val allVoices = runCatching { engine.voices?.toList().orEmpty() }.getOrDefault(emptyList())
        val matchingVoices = allVoices.filter { v ->
            val vLoc = v.locale
            vLoc.language.equals(locale.language, ignoreCase = true) &&
                (locale.country.isBlank() || vLoc.country.isBlank() ||
                    vLoc.country.equals(locale.country, ignoreCase = true))
        }.ifEmpty {
            allVoices.filter { it.locale.language.equals(locale.language, ignoreCase = true) }
        }

        val voiceInfos = matchingVoices
            .sortedWith(
                compareByDescending<Voice> { !it.isNetworkConnectionRequired }
                    .thenByDescending { it.quality }
                    .thenBy { it.name }
            )
            .map { v ->
                SystemVoiceInfo(
                    name = v.name,
                    displayName = formatVoiceLabel(v),
                    localeTag = v.locale.toLanguageTag(),
                    isOfflineReady = !v.isNetworkConnectionRequired,
                    quality = v.quality,
                    estimatedGender = inferGender(v)
                )
            }

        val pool = if (offlineOnly) {
            val offlinePool = matchingVoices.filter { !it.isNetworkConnectionRequired }
            offlinePool.ifEmpty { matchingVoices }
        } else {
            matchingVoices
        }

        val selectedVoice: Voice? = when {
            !preferredSystemVoiceName.isNullOrBlank() ->
                pool.firstOrNull { it.name == preferredSystemVoiceName }
                    ?: matchingVoices.firstOrNull { it.name == preferredSystemVoiceName }
            profile != null -> pickBestVoiceForProfile(pool, profile)
            else -> pool.maxByOrNull { it.quality }
        }

        if (selectedVoice != null) {
            runCatching { engine.voice = selectedVoice }
        }

        val finalPitch = ((profile?.pitchMultiplier ?: 1.0f) * currentPitchOverride).coerceIn(0.55f, 1.65f)
        val finalRate = ((profile?.rateMultiplier ?: 1.0f) * currentSpeedOverride).coerceIn(0.5f, 2.2f)
        engine.setPitch(finalPitch)
        engine.setSpeechRate(finalRate)

        val offlineAvailable = if (matchingVoices.isNotEmpty()) {
            matchingVoices.any { !it.isNetworkConnectionRequired }
        } else {
            langSupported
        }

        val voiceDesc = buildString {
            if (selectedVoice != null) {
                append(formatVoiceLabel(selectedVoice))
                append(if (!selectedVoice.isNetworkConnectionRequired) " • Offline" else " • Cloud")
            } else if (langSupported) {
                append("${locale.displayLanguage} Default")
            } else {
                append("${locale.displayLanguage} (Needs voice pack)")
            }
        }

        _state.value = _state.value.copy(
            availableVoicesForLocale = voiceInfos,
            isLanguageOfflineAvailable = offlineAvailable,
            activeVoiceDescription = voiceDesc,
            errorMessage = if (!langSupported && matchingVoices.isEmpty()) {
                "Offline voice data for ${locale.displayName} is not installed yet."
            } else {
                null
            }
        )
    }

    private fun pickBestVoiceForProfile(pool: List<Voice>, profile: VocalProfile): Voice? {
        if (pool.isEmpty()) return null
        val genderMatched = pool.filter { inferGender(it) == profile.gender }
        val candidates = genderMatched.ifEmpty { pool }
        return candidates.maxWithOrNull(
            compareBy<Voice> { !it.isNetworkConnectionRequired }
                .thenBy { it.quality }
                .thenBy { it.latency }
        )
    }

    private fun inferGender(voice: Voice): VoiceGender? {
        val n = voice.name.lowercase(Locale.ROOT)
        val features = runCatching { voice.features?.joinToString(" ")?.lowercase(Locale.ROOT) }.getOrNull().orEmpty()
        val combined = "$n $features"
        return when {
            combined.contains("female") || combined.contains("-sfg") || combined.contains("-iob") ||
                combined.contains("-hie") || combined.contains("-hid") || combined.contains("-tpf") ||
                combined.contains("-sfk") || combined.contains("wavenet-a") || combined.contains("wavenet-c") ||
                combined.contains("wavenet-e") || combined.contains("wavenet-f") ||
                combined.contains("-f-") -> VoiceGender.FEMALE
            combined.contains("male") || combined.contains("-iom") || combined.contains("-iol") ||
                combined.contains("-tpb") || combined.contains("-tpd") || combined.contains("wavenet-b") ||
                combined.contains("wavenet-d") || combined.contains("-m-") -> VoiceGender.MALE
            else -> null
        }
    }

    private fun formatVoiceLabel(voice: Voice): String {
        val genderTag = when (inferGender(voice)) {
            VoiceGender.FEMALE -> "Female"
            VoiceGender.MALE -> "Male"
            null -> "Studio"
        }
        val qualityTag = when {
            voice.quality >= Voice.QUALITY_VERY_HIGH -> "HD+"
            voice.quality >= Voice.QUALITY_HIGH -> "HD"
            else -> "Std"
        }
        val shortId = voice.name.substringAfterLast("-x-").substringAfterLast("#").take(6).uppercase(Locale.ROOT)
        return "$genderTag $qualityTag ($shortId)"
    }

    fun speak(text: String, titleHint: String) {
        val cleaned = text.trim()
        if (cleaned.isEmpty()) return

        scope.launch {
            if (!initDeferred.await()) return@launch
            val engine = tts ?: return@launch
            refreshLocaleVoices(
                currentLanguageTag,
                currentOfflineOnly,
                currentProfile,
                currentSystemVoiceName
            )
            activeSegments = segmentTextNaturally(cleaned, currentPauseCadenceMs)
            currentSegmentIndex = 0
            activePlaybackSessionId = UUID.randomUUID().toString().take(8)
            _state.value = _state.value.copy(
                status = TtsPlaybackStatus.PREPARING,
                activeSegmentIndex = 0,
                totalSegments = activeSegments.size,
                spokenRange = null,
                errorMessage = null
            )
            onPlaybackStarted?.invoke(titleHint)
            enqueueSegmentsFrom(engine, 0)
        }
    }

    fun pause() {
        val engine = tts ?: return
        if (_state.value.status == TtsPlaybackStatus.SPEAKING || _state.value.status == TtsPlaybackStatus.PREPARING) {
            engine.stop()
            _state.value = _state.value.copy(status = TtsPlaybackStatus.PAUSED)
            onPlaybackPaused?.invoke()
        }
    }

    fun resume(titleHint: String) {
        val engine = tts ?: return
        if (_state.value.status == TtsPlaybackStatus.PAUSED && activeSegments.isNotEmpty()) {
            _state.value = _state.value.copy(status = TtsPlaybackStatus.SPEAKING)
            onPlaybackStarted?.invoke(titleHint)
            enqueueSegmentsFrom(engine, currentSegmentIndex.coerceIn(0, activeSegments.lastIndex))
        }
    }

    fun stop() {
        tts?.stop()
        activePlaybackSessionId = ""
        activeSegments = emptyList()
        currentSegmentIndex = 0
        _state.value = _state.value.copy(
            status = TtsPlaybackStatus.IDLE,
            activeSegmentIndex = 0,
            totalSegments = 0,
            spokenRange = null
        )
        onPlaybackCompletedOrStopped?.invoke()
    }

    private fun enqueueSegmentsFrom(engine: TextToSpeech, startIdx: Int) {
        engine.stop()
        val params = Bundle().apply {
            if (currentOfflineOnly) {
                putString(TextToSpeech.Engine.KEY_FEATURE_NETWORK_SYNTHESIS, "false")
            }
        }
        for (i in startIdx until activeSegments.size) {
            val seg = activeSegments[i]
            val mode = if (i == startIdx) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
            val uttId = "$activePlaybackSessionId:$i:utt"
            engine.speak(seg.text, mode, params, uttId)
            if (seg.pauseAfterMs > 0L && i < activeSegments.lastIndex) {
                engine.playSilentUtterance(
                    seg.pauseAfterMs,
                    TextToSpeech.QUEUE_ADD,
                    "$activePlaybackSessionId:$i:pause"
                )
            }
        }
    }

    suspend fun synthesizeSegmentsToTempWavs(text: String): List<Pair<File, Long>> {
        val cleaned = text.trim()
        if (cleaned.isEmpty()) return emptyList()
        if (!initDeferred.await()) return emptyList()
        val engine = tts ?: return emptyList()

        refreshLocaleVoices(
            currentLanguageTag,
            currentOfflineOnly,
            currentProfile,
            currentSystemVoiceName
        )

        val segments = segmentTextNaturally(cleaned, currentPauseCadenceMs)
        val outList = ArrayList<Pair<File, Long>>(segments.size)
        val params = Bundle().apply {
            if (currentOfflineOnly) {
                putString(TextToSpeech.Engine.KEY_FEATURE_NETWORK_SYNTHESIS, "false")
            }
        }

        for ((index, seg) in segments.withIndex()) {
            val tempFile = File(context.cacheDir, "tts_seg_${System.currentTimeMillis()}_$index.wav")
            val uttId = "synth_${UUID.randomUUID().toString().take(8)}_$index"
            val deferred = CompletableDeferred<Boolean>()
            synthesisWaiters[uttId] = deferred

            val result = engine.synthesizeToFile(seg.text, params, tempFile, uttId)
            if (result != TextToSpeech.SUCCESS) {
                synthesisWaiters.remove(uttId)
                runCatching { tempFile.delete() }
                continue
            }

            val ok = withTimeoutOrNull(45_000L) { deferred.await() } ?: false
            if (ok && tempFile.exists() && tempFile.length() > 44L) {
                outList.add(tempFile to seg.pauseAfterMs)
            } else {
                runCatching { tempFile.delete() }
            }
        }
        return outList
    }

    fun buildInstallVoiceDataIntent(): Intent {
        return Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA)
    }

    fun release() {
        runCatching {
            tts?.stop()
            tts?.shutdown()
        }
        tts = null
    }

    companion object {
        fun segmentTextNaturally(text: String, basePauseMs: Long): List<TextSegment> {
            if (text.isBlank()) return emptyList()
            val segments = ArrayList<TextSegment>()
            val sentenceBreakChars = setOf('.', '!', '?', '।', '॥', '\n', '。', '؟', '!')
            val clauseBreakChars = setOf(',', ';', ':', '—', '–', '،')

            var start = 0
            var i = 0
            while (i < text.length) {
                val ch = text[i]
                val isSentenceEnd = ch in sentenceBreakChars
                val isClauseEnd = ch in clauseBreakChars && (i - start) >= 48
                val isLongChunk = (i - start) >= 220 && ch.isWhitespace()

                if (isSentenceEnd || isClauseEnd || isLongChunk) {
                    val end = (i + 1).coerceAtMost(text.length)
                    val slice = text.substring(start, end).trim()
                    if (slice.isNotEmpty()) {
                        val pause = when {
                            ch == '\n' -> (basePauseMs * 1.45f).toLong()
                            isSentenceEnd -> basePauseMs
                            isClauseEnd -> (basePauseMs * 0.5f).toLong()
                            else -> 40L
                        }
                        segments.add(TextSegment(slice, start, end, pause))
                    }
                    start = end
                }
                i++
            }

            if (start < text.length) {
                val slice = text.substring(start).trim()
                if (slice.isNotEmpty()) {
                    segments.add(TextSegment(slice, start, text.length, 0L))
                }
            }
            return segments
        }
    }
}
