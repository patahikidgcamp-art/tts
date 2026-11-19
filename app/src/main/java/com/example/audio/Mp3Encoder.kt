package com.example.audio

import de.sciss.jump3r.Main
import java.io.File

object Mp3Encoder {

    fun encodeToMp3(audio: PcmAudio, outFile: File, bitRateKbps: Int = 128) {
        val targetRate = when (audio.sampleRate) {
            8000, 11025, 12000, 16000, 22050, 24000, 32000, 44100, 48000 -> audio.sampleRate
            else -> 44100
        }
        val samples = if (audio.sampleRate == targetRate) {
            audio.samples
        } else {
            WavReader.resampleMono(audio.samples, audio.sampleRate, targetRate)
        }

        val tempWav = File(
            outFile.parentFile ?: File("."),
            "tmp_mp3_${System.nanoTime()}.wav"
        )
        try {
            WavReader.writeWav(PcmAudio(samples, targetRate, 1), tempWav)
            val exitCode = Main().run(
                arrayOf(
                    "--quiet",
                    "-b",
                    bitRateKbps.toString(),
                    "-m",
                    "m",
                    "-q",
                    "2",
                    tempWav.absolutePath,
                    outFile.absolutePath
                )
            )
            check(exitCode == 0 && outFile.exists() && outFile.length() > 0L) {
                "MP3 encoder exited with status $exitCode"
            }
        } finally {
            runCatching { tempWav.delete() }
        }
    }
}
