package com.koceng.oren.audio

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

/**
 * Utilitas untuk mengonversi raw PCM 16-bit mono audio buffer
 * menjadi file WAV standar RIFF yang didukung langsung oleh Gdx.audio.newSound().
 * Menggunakan penulisan little-endian manual untuk menghindari masalah tipe data.
 */
object WavUtils {

    /**
     * Membungkus byte PCM mentah dengan WAV RIFF header 44-byte (little-endian).
     */
    fun pcmToWav(
        pcmData: ByteArray,
        sampleRate: Int = 44100,
        channels: Int = 1,
        bitsPerSample: Int = 16
    ): ByteArray {
        val byteRate = sampleRate * channels * (bitsPerSample / 8)
        val blockAlign = channels * (bitsPerSample / 8)
        val dataSize = pcmData.size
        val chunkSize = 36 + dataSize

        val baos = ByteArrayOutputStream(44 + dataSize)

        // Helper: tulis Int 32-bit dalam format little-endian
        fun writeLE32(value: Int) {
            baos.write(value and 0xFF)
            baos.write((value shr 8) and 0xFF)
            baos.write((value shr 16) and 0xFF)
            baos.write((value shr 24) and 0xFF)
        }

        // Helper: tulis Int 16-bit dalam format little-endian
        fun writeLE16(value: Int) {
            baos.write(value and 0xFF)
            baos.write((value shr 8) and 0xFF)
        }

        // RIFF chunk descriptor
        baos.write("RIFF".toByteArray(Charsets.US_ASCII))
        writeLE32(chunkSize)
        baos.write("WAVE".toByteArray(Charsets.US_ASCII))

        // "fmt " sub-chunk
        baos.write("fmt ".toByteArray(Charsets.US_ASCII))
        writeLE32(16)            // Subchunk1Size = 16 for PCM
        writeLE16(1)             // AudioFormat = 1 (Linear PCM)
        writeLE16(channels)      // Channels: 1 = mono
        writeLE32(sampleRate)    // SampleRate
        writeLE32(byteRate)      // ByteRate = SampleRate * NumChannels * BitsPerSample/8
        writeLE16(blockAlign)    // BlockAlign = NumChannels * BitsPerSample/8
        writeLE16(bitsPerSample) // BitsPerSample

        // "data" sub-chunk
        baos.write("data".toByteArray(Charsets.US_ASCII))
        writeLE32(dataSize)
        baos.write(pcmData)

        return baos.toByteArray()
    }

    /**
     * Menulis buffer PCM ke file WAV lokal.
     */
    fun writePcmToWavFile(targetFile: File, pcmData: ByteArray, sampleRate: Int = 44100) {
        val wavBytes = pcmToWav(pcmData, sampleRate)
        FileOutputStream(targetFile).use { it.write(wavBytes) }
    }
}
