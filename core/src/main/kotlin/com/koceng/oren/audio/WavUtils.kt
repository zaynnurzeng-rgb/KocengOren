package com.koceng.oren.audio

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream

/**
 * Utilitas untuk mengonversi raw PCM 16-bit mono audio buffer
 * menjadi file WAV standar RIFF yang didukung langsung oleh Gdx.audio.newSound()
 */
object WavUtils {

    /**
     * Membungkus byte PCM mentah dengan WAV RIFF header 44-byte
     */
    fun pcmToWav(pcmData: ByteArray, sampleRate: Int = 44100, channels: Short = 1, bitsPerSample: Short = 16): ByteArray {
        val byteRate = sampleRate * channels * (bitsPerSample / 8)
        val blockAlign = (channels * (bitsPerSample / 8)).toShort()
        val dataSize = pcmData.size
        val totalSize = 36 + dataSize

        val baos = ByteArrayOutputStream(44 + dataSize)
        val out = DataOutputStream(baos)

        // RIFF chunk descriptor
        out.writeBytes("RIFF")
        out.writeInt(Integer.reverseBytes(totalSize))
        out.writeBytes("WAVE")

        // "fmt " sub-chunk
        out.writeBytes("fmt ")
        out.writeInt(Integer.reverseBytes(16)) // Subchunk1Size for PCM = 16
        out.writeShort(java.lang.Short.reverseBytes(1)) // AudioFormat 1 = PCM
        out.writeShort(java.lang.Short.reverseBytes(channels))
        out.writeInt(Integer.reverseBytes(sampleRate))
        out.writeInt(Integer.reverseBytes(byteRate))
        out.writeShort(java.lang.Short.reverseBytes(blockAlign))
        out.writeShort(java.lang.Short.reverseBytes(bitsPerSample))

        // "data" sub-chunk
        out.writeBytes("data")
        out.writeInt(Integer.reverseBytes(dataSize))
        out.write(pcmData)

        out.flush()
        return baos.toByteArray()
    }

    /**
     * Menulis buffer PCM ke file WAV lokal
     */
    fun writePcmToWavFile(targetFile: File, pcmData: ByteArray, sampleRate: Int = 44100) {
        val wavBytes = pcmToWav(pcmData, sampleRate)
        FileOutputStream(targetFile).use { it.write(wavBytes) }
    }
}
