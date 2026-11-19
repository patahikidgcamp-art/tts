package com.example.audio

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.example.model.AudioFormat
import com.example.model.ExportedAudioItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.util.UUID

class AudioExportManager(private val context: Context) {

    private val exportsDir: File by lazy {
        File(context.filesDir, "exports").apply { mkdirs() }
    }

    private val indexFile: File by lazy {
        File(context.filesDir, "exports_index.json")
    }

    private val _exports = MutableStateFlow<List<ExportedAudioItem>>(emptyList())
    val exports: StateFlow<List<ExportedAudioItem>> = _exports.asStateFlow()

    init {
        loadIndex()
    }

    private fun loadIndex() {
        if (!indexFile.exists()) {
            _exports.value = emptyList()
            return
        }
        val items = runCatching {
            val raw = indexFile.readText()
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val filePath = obj.getString("filePath")
                    if (File(filePath).exists()) {
                        add(
                            ExportedAudioItem(
                                id = obj.getString("id"),
                                title = obj.getString("title"),
                                fileName = obj.getString("fileName"),
                                filePath = filePath,
                                format = AudioFormat.valueOf(obj.getString("format")),
                                languageName = obj.getString("languageName"),
                                vocalProfileName = obj.getString("vocalProfileName"),
                                durationMs = obj.getLong("durationMs"),
                                fileSizeBytes = obj.getLong("fileSizeBytes"),
                                createdAtMillis = obj.getLong("createdAtMillis"),
                                downloadsUriString = obj.optString("downloadsUriString").takeIf { it.isNotBlank() }
                            )
                        )
                    }
                }
            }.sortedByDescending { it.createdAtMillis }
        }.getOrDefault(emptyList())
        _exports.value = items
    }

    private fun persistIndex(items: List<ExportedAudioItem>) {
        val arr = JSONArray()
        items.forEach { item ->
            arr.put(
                JSONObject().apply {
                    put("id", item.id)
                    put("title", item.title)
                    put("fileName", item.fileName)
                    put("filePath", item.filePath)
                    put("format", item.format.name)
                    put("languageName", item.languageName)
                    put("vocalProfileName", item.vocalProfileName)
                    put("durationMs", item.durationMs)
                    put("fileSizeBytes", item.fileSizeBytes)
                    put("createdAtMillis", item.createdAtMillis)
                    put("downloadsUriString", item.downloadsUriString ?: "")
                }
            )
        }
        indexFile.writeText(arr.toString())
    }

    suspend fun stitchAndExport(
        wavSegments: List<Pair<File, Long>>,
        title: String,
        format: AudioFormat,
        languageName: String,
        vocalProfileName: String,
        onProgress: (String) -> Unit
    ): ExportedAudioItem = withContext(Dispatchers.IO) {
        require(wavSegments.isNotEmpty()) { "No synthesized audio segments available" }

        onProgress("Polishing studio audio…")
        val decodedSegments = wavSegments.mapNotNull { (file, pauseAfterMs) ->
            if (file.exists() && file.length() > 44L) {
                WavReader.toMonoAndPolish(WavReader.readWav(file)) to pauseAfterMs
            } else {
                null
            }
        }
        require(decodedSegments.isNotEmpty()) { "Synthesized audio buffer was empty" }

        val targetRate = decodedSegments.first().first.sampleRate
        val combinedList = ArrayList<Short>(decodedSegments.sumOf { it.first.samples.size } + 16384)
        decodedSegments.forEachIndexed { index, (pcm, pauseMs) ->
            val resampled = if (pcm.sampleRate == targetRate) {
                pcm.samples
            } else {
                WavReader.resampleMono(pcm.samples, pcm.sampleRate, targetRate)
            }
            for (s in resampled) combinedList.add(s)
            if (index < decodedSegments.lastIndex && pauseMs > 0L) {
                val silenceSamples = ((targetRate * pauseMs) / 1000L).toInt().coerceIn(0, targetRate * 2)
                repeat(silenceSamples) { combinedList.add(0) }
            }
        }

        val combinedAudio = PcmAudio(
            samples = combinedList.toShortArray(),
            sampleRate = targetRate,
            channels = 1
        )

        val safeBase = title
            .trim()
            .replace(Regex("[^a-zA-Z0-9\\u0900-\\u097F_-]+"), "_")
            .trim('_')
            .take(36)
            .ifBlank { "vanivox_speech" }
        val stamp = System.currentTimeMillis()
        val fileName = "${safeBase}_${stamp % 100000}.${format.extension}"
        val outFile = File(exportsDir, fileName)

        when (format) {
            AudioFormat.WAV -> {
                onProgress("Writing lossless WAV…")
                WavReader.writeWav(combinedAudio, outFile)
            }
            AudioFormat.AAC -> {
                onProgress("Encoding compact AAC…")
                AacEncoder.encodeToAdtsAac(combinedAudio, outFile, bitRate = 128_000)
            }
            AudioFormat.MP3 -> {
                onProgress("Encoding high-fidelity MP3…")
                Mp3Encoder.encodeToMp3(combinedAudio, outFile, bitRateKbps = 128)
            }
        }

        // Clean up temporary synthesis wav files immediately
        wavSegments.forEach { (f, _) -> runCatching { f.delete() } }

        onProgress("Saving to offline library…")
        val downloadsUri = copyToPublicDownloads(outFile, fileName, format.mimeType)

        val item = ExportedAudioItem(
            id = UUID.randomUUID().toString(),
            title = title.ifBlank { "VaniVox Audio" },
            fileName = fileName,
            filePath = outFile.absolutePath,
            format = format,
            languageName = languageName,
            vocalProfileName = vocalProfileName,
            durationMs = combinedAudio.durationMs,
            fileSizeBytes = outFile.length(),
            createdAtMillis = stamp,
            downloadsUriString = downloadsUri?.toString()
        )

        val updated = listOf(item) + _exports.value
        _exports.value = updated
        persistIndex(updated)
        item
    }

    suspend fun saveItemToDownloads(item: ExportedAudioItem): Uri? = withContext(Dispatchers.IO) {
        val src = File(item.filePath)
        if (!src.exists()) return@withContext null
        val uri = copyToPublicDownloads(src, item.fileName, item.format.mimeType)
        if (uri != null) {
            val updated = _exports.value.map {
                if (it.id == item.id) it.copy(downloadsUriString = uri.toString()) else it
            }
            _exports.value = updated
            persistIndex(updated)
        }
        uri
    }

    private fun copyToPublicDownloads(file: File, displayName: String, mimeType: String): Uri? {
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, displayName)
                    put(MediaStore.Downloads.MIME_TYPE, mimeType)
                    put(
                        MediaStore.Downloads.RELATIVE_PATH,
                        "${Environment.DIRECTORY_DOWNLOADS}/VaniVox"
                    )
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: return null
                resolver.openOutputStream(uri)?.use { out ->
                    FileInputStream(file).use { input ->
                        input.copyTo(out)
                    }
                }
                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                uri
            } else {
                val musicDir = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC)
                val destDir = File(musicDir, "VaniVox").apply { mkdirs() }
                val destFile = File(destDir, displayName)
                file.copyTo(destFile, overwrite = true)
                Uri.fromFile(destFile)
            }
        }.getOrNull()
    }

    fun deleteItem(item: ExportedAudioItem) {
        runCatching { File(item.filePath).delete() }
        val updated = _exports.value.filterNot { it.id == item.id }
        _exports.value = updated
        persistIndex(updated)
    }

    fun buildShareIntent(item: ExportedAudioItem): Intent? {
        val file = File(item.filePath)
        if (!file.exists()) return null
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        return Intent(Intent.ACTION_SEND).apply {
            type = item.format.mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, item.title)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
