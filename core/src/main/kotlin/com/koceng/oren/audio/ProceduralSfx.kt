package com.koceng.oren.audio

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.audio.Sound
import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.utils.Disposable
import java.util.Random
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * Generator SFX prosedural sintesis murni tanpa memerlukan file audio eksternal (.mp3/.wav).
 * Menghasilkan suara melalui perhitungan gelombang PCM 16-bit mono yang dibungkus WAV standar.
 */
class ProceduralSfx : Disposable {

    private val soundPoke: Sound
    private val soundPurr: Sound
    private val soundEat: Sound
    private val soundButton: Sound

    init {
        soundPoke = generateAndLoadSound("sfx_poke.wav") { generatePokePcm() }
        soundPurr = generateAndLoadSound("sfx_purr.wav") { generatePurrPcm() }
        soundEat = generateAndLoadSound("sfx_eat.wav") { generateEatPcm() }
        soundButton = generateAndLoadSound("sfx_btn.wav") { generateButtonPcm() }
    }

    private fun generateAndLoadSound(fileName: String, generator: () -> ByteArray): Sound {
        val file: FileHandle = Gdx.files.local(fileName)
        val pcm = generator()
        val wavBytes = WavUtils.pcmToWav(pcm, sampleRate = 22050, channels = 1, bitsPerSample = 16)
        file.writeBytes(wavBytes, false)
        return Gdx.audio.newSound(file)
    }

    /**
     * Suara "Boing" kartun saat kepala disentuh (Pitch ramp-up)
     */
    private fun generatePokePcm(): ByteArray {
        val sampleRate = 22050
        val duration = 0.16f
        val numSamples = (sampleRate * duration).toInt()
        val pcm = ByteArray(numSamples * 2)

        var phase = 0.0
        for (i in 0 until numSamples) {
            val t = i.toFloat() / sampleRate
            val progress = t / duration
            // Frekuensi naik dari 240Hz ke 620Hz
            val freq = 240.0 + 380.0 * progress
            val phaseInc = 2.0 * PI * freq / sampleRate
            phase += phaseInc

            val envelope = (1.0 - progress) * (1.0 - progress)
            val sampleVal = (sin(phase) * envelope * 26000.0).toInt().coerceIn(-32768, 32767)

            pcm[i * 2] = (sampleVal and 0xFF).toByte()
            pcm[i * 2 + 1] = ((sampleVal shr 8) and 0xFF).toByte()
        }
        return pcm
    }

    /**
     * Suara dengkuran kucing ceria saat perut diusap (Amplitudo modulasi getar ~26Hz)
     */
    private fun generatePurrPcm(): ByteArray {
        val sampleRate = 22050
        val duration = 0.42f
        val numSamples = (sampleRate * duration).toInt()
        val pcm = ByteArray(numSamples * 2)

        for (i in 0 until numSamples) {
            val t = i.toDouble() / sampleRate
            val progress = t / duration

            // Carrier 95Hz + harmonik 190Hz, dimodulasi getaran purr 25Hz
            val flutter = 0.5 + 0.5 * sin(2.0 * PI * 25.0 * t)
            val carrier = sin(2.0 * PI * 95.0 * t) + 0.35 * sin(2.0 * PI * 190.0 * t)
            val envelope = sin(PI * progress) // Fade in dan fade out mulus

            val sampleVal = (carrier * flutter * envelope * 24000.0).toInt().coerceIn(-32768, 32767)

            pcm[i * 2] = (sampleVal and 0xFF).toByte()
            pcm[i * 2 + 1] = ((sampleVal shr 8) and 0xFF).toByte()
        }
        return pcm
    }

    /**
     * Suara kunyah renyah kartun saat diberi makan (Kombinasi noise crunch dan chew thumps)
     */
    private fun generateEatPcm(): ByteArray {
        val sampleRate = 22050
        val duration = 0.22f
        val numSamples = (sampleRate * duration).toInt()
        val pcm = ByteArray(numSamples * 2)
        val random = Random(42)

        for (i in 0 until numSamples) {
            val t = i.toDouble() / sampleRate
            val progress = t / duration

            // Dua ketukan kunyah (double munch)
            val bite1Env = exp(-((t - 0.05) * 40.0) * ((t - 0.05) * 40.0))
            val bite2Env = exp(-((t - 0.15) * 40.0) * ((t - 0.15) * 40.0))
            val totalEnv = (bite1Env + bite2Env).coerceIn(0.0, 1.0)

            val noise = (random.nextDouble() * 2.0 - 1.0) * 0.65
            val tone = sin(2.0 * PI * 180.0 * t) * 0.35

            val sampleVal = ((noise + tone) * totalEnv * 25000.0).toInt().coerceIn(-32768, 32767)

            pcm[i * 2] = (sampleVal and 0xFF).toByte()
            pcm[i * 2 + 1] = ((sampleVal shr 8) and 0xFF).toByte()
        }
        return pcm
    }

    /**
     * Suara click/pop ringan untuk respon sentuhan tombol UI
     */
    private fun generateButtonPcm(): ByteArray {
        val sampleRate = 22050
        val duration = 0.045f
        val numSamples = (sampleRate * duration).toInt()
        val pcm = ByteArray(numSamples * 2)

        for (i in 0 until numSamples) {
            val t = i.toDouble() / sampleRate
            val envelope = exp(-t * 90.0)
            val tone = sin(2.0 * PI * 880.0 * t)

            val sampleVal = (tone * envelope * 22000.0).toInt().coerceIn(-32768, 32767)

            pcm[i * 2] = (sampleVal and 0xFF).toByte()
            pcm[i * 2 + 1] = ((sampleVal shr 8) and 0xFF).toByte()
        }
        return pcm
    }

    fun playPoke(volume: Float = 1.0f) {
        soundPoke.play(volume)
    }

    fun playPurr(volume: Float = 1.0f) {
        soundPurr.play(volume)
    }

    fun playEat(volume: Float = 1.0f) {
        soundEat.play(volume)
    }

    fun playButton(volume: Float = 0.85f) {
        soundButton.play(volume)
    }

    override fun dispose() {
        soundPoke.dispose()
        soundPurr.dispose()
        soundEat.dispose()
        soundButton.dispose()
    }
}
