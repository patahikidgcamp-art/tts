package com.example

import com.example.audio.Mp3Encoder
import com.example.audio.PcmAudio
import com.example.audio.WavReader
import com.example.data.VoiceCatalog
import com.example.model.VoiceGender
import com.example.tts.TtsEngineManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

class VaniVoxAudioAndTtsTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun segmentTextNaturally_handlesHindiAndEnglishPunctuation() {
        val sample = "नमस्ते! यह पहला वाक्य है। And this is the second sentence."
        val segments = TtsEngineManager.segmentTextNaturally(sample, basePauseMs = 240L)

        assertEquals(3, segments.size)
        assertEquals("नमस्ते!", segments[0].text)
        assertEquals(240L, segments[0].pauseAfterMs)
        assertEquals("यह पहला वाक्य है।", segments[1].text)
        assertEquals(240L, segments[1].pauseAfterMs)
        assertEquals("And this is the second sentence.", segments[2].text)
    }

    @Test
    fun voiceCatalog_includesHindiEnglishAndMaleFemaleStyles() {
        assertTrue(VoiceCatalog.languages.any { it.tag == "hi-IN" })
        assertTrue(VoiceCatalog.languages.any { it.tag == "en-US" })
        assertTrue(VoiceCatalog.languages.any { it.tag == "en-IN" })
        assertTrue(VoiceCatalog.vocalProfiles.count { it.gender == VoiceGender.FEMALE } >= 3)
        assertTrue(VoiceCatalog.vocalProfiles.count { it.gender == VoiceGender.MALE } >= 3)
    }

    @Test
    fun wavRoundTrip_andMp3Encoding_produceValidAudioFiles() {
        val sampleRate = 22050
        val sampleCount = sampleRate / 2 // 0.5 seconds
        val samples = ShortArray(sampleCount) { i ->
            (sin(2.0 * PI * 440.0 * i / sampleRate) * 16000.0).roundToInt().toShort()
        }
        val audio = PcmAudio(samples = samples, sampleRate = sampleRate, channels = 1)

        val wavFile = tempFolder.newFile("test.wav")
        WavReader.writeWav(audio, wavFile)
        val parsed = WavReader.readWav(wavFile)
        assertEquals(sampleRate, parsed.sampleRate)
        assertEquals(1, parsed.channels)
        assertEquals(sampleCount, parsed.samples.size)

        val mp3File = tempFolder.newFile("test.mp3")
        Mp3Encoder.encodeToMp3(audio, mp3File, bitRateKbps = 128)
        val mp3Bytes = mp3File.readBytes()
        assertTrue("MP3 output should not be empty", mp3Bytes.size > 512)
        // MPEG frame sync starts with 0xFF and top 3 bits of byte 1 set (0xE0)
        val hasSync = (0 until mp3Bytes.size - 1).any { idx ->
            (mp3Bytes[idx].toInt() and 0xFF) == 0xFF &&
                (mp3Bytes[idx + 1].toInt() and 0xE0) == 0xE0
        }
        assertTrue("Encoded MP3 must contain valid MPEG frame sync", hasSync)
    }
}
