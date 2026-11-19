package com.example.audio

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class PcmAudio(
    val samples: ShortArray,
    val sampleRate: Int,
    val channels: Int
) {
    val durationMs: Long
        get() {
            if (sampleRate <= 0 || channels <= 0) return 0L
            val frames = samples.size / channels
            return (frames * 1000L) / sampleRate
        }
}

object WavReader {

    fun readWav(file: File): PcmAudio {
        require(file.exists() && file.length() >= 44L) { "Invalid WAV file: ${file.name}" }
        BufferedInputStream(FileInputStream(file)).use { input ->
            val bytes = input.readBytes()
            val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

            val riff = ByteArray(4)
            buf.get(riff)
            require(String(riff, Charsets.US_ASCII) == "RIFF") { "Not a RIFF file" }
            buf.int // chunk size
            val wave = ByteArray(4)
            buf.get(wave)
            require(String(wave, Charsets.US_ASCII) == "WAVE") { "Not a WAVE file" }

            var audioFormat = 1
            var channels = 1
            var sampleRate = 22050
            var bitsPerSample = 16
            var dataBytes: ByteArray? = null

            while (buf.remaining() >= 8) {
                val idBytes = ByteArray(4)
                buf.get(idBytes)
                val chunkId = String(idBytes, Charsets.US_ASCII)
                val chunkSize = buf.int.coerceAtLeast(0)
                if (chunkSize > buf.remaining()) break

                when (chunkId) {
                    "fmt " -> {
                        val startPos = buf.position()
                        audioFormat = buf.short.toInt() and 0xFFFF
                        channels = buf.short.toInt() and 0xFFFF
                        sampleRate = buf.int
                        buf.int // byteRate
                        buf.short // blockAlign
                        bitsPerSample = buf.short.toInt() and 0xFFFF
                        buf.position(startPos + chunkSize)
                    }
                    "data" -> {
                        val data = ByteArray(chunkSize)
                        buf.get(data)
                        dataBytes = data
                        break
                    }
                    else -> {
                        buf.position(buf.position() + chunkSize)
                    }
                }
                if (chunkSize % 2 == 1 && buf.hasRemaining()) {
                    buf.get() // word alignment pad
                }
            }

            val raw = dataBytes ?: throw IllegalStateException("WAV data chunk missing")
            require(audioFormat == 1) { "Unsupported WAV encoding: $audioFormat" }

            val samples = when (bitsPerSample) {
                16 -> {
                    val count = raw.size / 2
                    val out = ShortArray(count)
                    ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(out)
                    out
                }
                8 -> {
                    ShortArray(raw.size) { i ->
                        (((raw[i].toInt() and 0xFF) - 128) shl 8).toShort()
                    }
                }
                else -> throw IllegalStateException("Unsupported bitsPerSample: $bitsPerSample")
            }

            return PcmAudio(
                samples = samples,
                sampleRate = sampleRate.coerceIn(8000, 96000),
                channels = channels.coerceIn(1, 2)
            )
        }
    }

    fun toMonoAndPolish(audio: PcmAudio): PcmAudio {
        val mono = if (audio.channels == 1) {
            audio.samples.copyOf()
        } else {
            val frames = audio.samples.size / audio.channels
            ShortArray(frames) { i ->
                var sum = 0
                for (c in 0 until audio.channels) {
                    sum += audio.samples[i * audio.channels + c].toInt()
                }
                (sum / audio.channels).toShort()
            }
        }

        if (mono.isEmpty()) return PcmAudio(mono, audio.sampleRate, 1)

        // Gentle peak normalization to -1.5 dBFS for crisp speaker playback
        var peak = 0
        for (s in mono) {
            val a = abs(s.toInt())
            if (a > peak) peak = a
        }
        if (peak in 1000..26000) {
            val gain = min(2.2f, 27500f / peak.toFloat())
            for (i in mono.indices) {
                mono[i] = (mono[i] * gain).roundToInt().coerceIn(-32767, 32767).toShort()
            }
        }

        // 8ms click-free fade-in and fade-out
        val fadeSamples = min(mono.size / 4, (audio.sampleRate * 0.008f).roundToInt())
        if (fadeSamples > 0) {
            for (i in 0 until fadeSamples) {
                val factor = i.toFloat() / fadeSamples.toFloat()
                mono[i] = (mono[i] * factor).roundToInt().toShort()
                val tailIdx = mono.size - 1 - i
                mono[tailIdx] = (mono[tailIdx] * factor).roundToInt().toShort()
            }
        }

        return PcmAudio(mono, audio.sampleRate, 1)
    }

    fun resampleMono(samples: ShortArray, srcRate: Int, dstRate: Int): ShortArray {
        if (srcRate == dstRate || samples.isEmpty()) return samples
        val ratio = srcRate.toDouble() / dstRate.toDouble()
        val outLen = max(1, (samples.size / ratio).roundToInt())
        return ShortArray(outLen) { i ->
            val srcPos = i * ratio
            val idx0 = srcPos.toInt().coerceIn(0, samples.lastIndex)
            val idx1 = (idx0 + 1).coerceIn(0, samples.lastIndex)
            val frac = srcPos - idx0
            val v = samples[idx0] * (1.0 - frac) + samples[idx1] * frac
            v.roundToInt().coerceIn(-32767, 32767).toShort()
        }
    }

    fun writeWav(audio: PcmAudio, outFile: File) {
        val byteData = ByteArray(audio.samples.size * 2)
        ByteBuffer.wrap(byteData).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(audio.samples)

        val byteRate = audio.sampleRate * audio.channels * 2
        val blockAlign = audio.channels * 2
        val dataSize = byteData.size
        val chunkSize = 36 + dataSize

        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray(Charsets.US_ASCII))
            putInt(chunkSize)
            put("WAVE".toByteArray(Charsets.US_ASCII))
            put("fmt ".toByteArray(Charsets.US_ASCII))
            putInt(16)
            putShort(1) // PCM
            putShort(audio.channels.toShort())
            putInt(audio.sampleRate)
            putInt(byteRate)
            putShort(blockAlign.toShort())
            putShort(16)
            put("data".toByteArray(Charsets.US_ASCII))
            putInt(dataSize)
        }.array()

        BufferedOutputStream(FileOutputStream(outFile)).use { out ->
            out.write(header)
            out.write(byteData)
            out.flush()
        }
    }
}
