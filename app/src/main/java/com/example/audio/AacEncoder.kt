package com.example.audio

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

object AacEncoder {

    private val aacSampleRates = intArrayOf(
        96000, 88200, 64000, 48000, 44100, 32000,
        24000, 22050, 16000, 12000, 11025, 8000, 7350
    )

    fun encodeToAdtsAac(audio: PcmAudio, outFile: File, bitRate: Int = 128_000): Unit {
        val targetRate = if (audio.sampleRate in aacSampleRates) audio.sampleRate else 44100
        val monoSamples = if (audio.sampleRate == targetRate) {
            audio.samples
        } else {
            WavReader.resampleMono(audio.samples, audio.sampleRate, targetRate)
        }

        val pcmBytes = ByteArray(monoSamples.size * 2)
        ByteBuffer.wrap(pcmBytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(monoSamples)

        val format = MediaFormat.createAudioFormat(
            MediaFormat.MIMETYPE_AUDIO_AAC,
            targetRate,
            1
        ).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)
        }

        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        val freqIndex = aacSampleRates.indexOf(targetRate).takeIf { it >= 0 } ?: 4

        try {
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()

            BufferedOutputStream(FileOutputStream(outFile)).use { out ->
                val bufferInfo = MediaCodec.BufferInfo()
                var offset = 0
                var sawInputEos = false
                var sawOutputEos = false

                while (!sawOutputEos) {
                    if (!sawInputEos) {
                        val inIdx = codec.dequeueInputBuffer(10_000)
                        if (inIdx >= 0) {
                            val inBuf = codec.getInputBuffer(inIdx)
                            inBuf?.clear()
                            if (offset >= pcmBytes.size) {
                                codec.queueInputBuffer(
                                    inIdx, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM
                                )
                                sawInputEos = true
                            } else {
                                val chunk = minOf(inBuf?.remaining() ?: 0, pcmBytes.size - offset)
                                inBuf?.put(pcmBytes, offset, chunk)
                                val ptsUs = (offset.toLong() * 1_000_000L) / (targetRate * 2L)
                                offset += chunk
                                codec.queueInputBuffer(inIdx, 0, chunk, ptsUs, 0)
                            }
                        }
                    }

                    val outIdx = codec.dequeueOutputBuffer(bufferInfo, 10_000)
                    when {
                        outIdx >= 0 -> {
                            val outBuf = codec.getOutputBuffer(outIdx)
                            val isConfig = (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0
                            if (outBuf != null && bufferInfo.size > 0 && !isConfig) {
                                outBuf.position(bufferInfo.offset)
                                outBuf.limit(bufferInfo.offset + bufferInfo.size)
                                val packet = ByteArray(bufferInfo.size)
                                outBuf.get(packet)
                                out.write(buildAdtsHeader(packet.size, freqIndex, 1))
                                out.write(packet)
                            }
                            codec.releaseOutputBuffer(outIdx, false)
                            if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                                sawOutputEos = true
                            }
                        }
                    }
                }
                out.flush()
            }
        } finally {
            runCatching { codec.stop() }
            runCatching { codec.release() }
        }
    }

    private fun buildAdtsHeader(aacPayloadLength: Int, freqIdx: Int, channelConfig: Int): ByteArray {
        val packetLen = aacPayloadLength + 7
        val profile = 2 // AAC LC
        return byteArrayOf(
            0xFF.toByte(),
            0xF1.toByte(),
            (((profile - 1) shl 6) + (freqIdx shl 2) + (channelConfig shr 2)).toByte(),
            (((channelConfig and 3) shl 6) + (packetLen shr 11)).toByte(),
            ((packetLen and 0x7FF) shr 3).toByte(),
            (((packetLen and 7) shl 5) + 0x1F).toByte(),
            0xFC.toByte()
        )
    }
}
