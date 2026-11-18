package com.koceng.oren.audio

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.sqrt

/**
 * Implementasi Android Native AudioRecorder dengan deteksi aktivitas suara (VAD) otomatis.
 */
class AndroidVoiceMimicBridge(private val activity: Activity) : VoiceMimicBridge {

    companion object {
        private const val TAG = "VoiceMimicBridge"
        private const val SAMPLE_RATE = 44100
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT

        // Ambang batas RMS untuk mendeteksi mulai bicara dan hening
        private const val VOICE_THRESHOLD_RMS = 1400.0   // Suara manusia aktif
        private const val SILENCE_THRESHOLD_RMS = 750.0  // Suara kembali hening
        private const val SILENCE_TIMEOUT_MS = 800L      // Jeda hening 0.8 detik menandakan selesai bicara
        private const val MIN_SPEECH_DURATION_MS = 500L  // Minimal durasi suara valid
        private const val MAX_SPEECH_DURATION_MS = 8000L // Batas maksimal rekaman
    }

    private var callback: VoiceMimicCallback? = null
    private val isRunning = AtomicBoolean(false)
    private var recordThread: Thread? = null

    private var permissionCallback: (() -> Unit)? = null

    override fun setCallback(callback: VoiceMimicCallback?) {
        this.callback = callback
    }

    override fun isListening(): Boolean = isRunning.get()

    override fun checkAndRequestPermissions(onGranted: () -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (activity.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                onGranted()
            } else {
                permissionCallback = onGranted
                activity.requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1001)
            }
        } else {
            onGranted()
        }
    }

    fun onPermissionResult(requestCode: Int, grantResults: IntArray) {
        if (requestCode == 1001 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            permissionCallback?.invoke()
            permissionCallback = null
        } else {
            callback?.onError("Izin mikrofon (RECORD_AUDIO) ditolak pengguna.")
        }
    }

    override fun startListening() {
        if (isRunning.get()) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            activity.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "Tidak dapat memulai: izin RECORD_AUDIO belum diberikan.")
            return
        }

        isRunning.set(true)
        recordThread = Thread({ runAudioLoop() }, "AudioRecordVADThread").apply {
            priority = Thread.MAX_PRIORITY
            start()
        }
    }

    override fun stopListening() {
        isRunning.set(false)
        try {
            recordThread?.join(500)
        } catch (e: InterruptedException) {
            Log.e(TAG, "Gagal menghentikan thread recorder", e)
        }
        recordThread = null
    }

    private fun runAudioLoop() {
        val minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        val bufferSize = maxOf(minBufferSize, 2048)

        var audioRecord: AudioRecord? = null
        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
            )

            if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
                callback?.onError("Gagal menginisialisasi AudioRecord hardware!")
                isRunning.set(false)
                return
            }

            audioRecord.startRecording()
            Log.i(TAG, "AudioRecord aktif, mendengarkan input mic...")

            val audioBuffer = ShortArray(bufferSize / 2)
            val byteBuffer = ByteArray(bufferSize)

            var isSpeechActive = false
            var speechStartTime = 0L
            var lastVoiceDetectedTime = 0L
            val pcmStream = ByteArrayOutputStream()

            while (isRunning.get()) {
                val shortsRead = audioRecord.read(audioBuffer, 0, audioBuffer.size)
                if (shortsRead <= 0) continue

                // Konversi short[] ke byte[] (little-endian 16-bit PCM)
                for (i in 0 until shortsRead) {
                    val sample = audioBuffer[i]
                    byteBuffer[i * 2] = (sample.toInt() and 0xFF).toByte()
                    byteBuffer[i * 2 + 1] = ((sample.toInt() shr 8) and 0xFF).toByte()
                }
                val bytesRead = shortsRead * 2

                // Hitung RMS (Volume Amplitude)
                val rms = calculateRms(audioBuffer, shortsRead)
                val now = System.currentTimeMillis()

                if (!isSpeechActive) {
                    // Cek apakah suara pengguna melampaui ambang batas bicara
                    if (rms > VOICE_THRESHOLD_RMS) {
                        isSpeechActive = true
                        speechStartTime = now
                        lastVoiceDetectedTime = now
                        pcmStream.reset()
                        pcmStream.write(byteBuffer, 0, bytesRead)
                        Log.d(TAG, "Voice Activity Terdeteksi! RMS: $rms")
                        callback?.onSpeechStarted()
                    }
                } else {
                    // Pengguna sedang bicara: tampung audio buffer
                    pcmStream.write(byteBuffer, 0, bytesRead)

                    if (rms > SILENCE_THRESHOLD_RMS) {
                        lastVoiceDetectedTime = now
                    }

                    val speechDuration = now - speechStartTime
                    val silenceDuration = now - lastVoiceDetectedTime

                    // Syarat selesai bicara: hening melebihi batas ATAU durasi maksimal tercapai
                    val silenceTriggered = silenceDuration >= SILENCE_TIMEOUT_MS && speechDuration >= MIN_SPEECH_DURATION_MS
                    val maxDurationReached = speechDuration >= MAX_SPEECH_DURATION_MS

                    if (silenceTriggered || maxDurationReached) {
                        Log.i(TAG, "Selesai merekam! Durasi: ${speechDuration}ms, Buffer: ${pcmStream.size()} bytes")
                        val capturedPcm = pcmStream.toByteArray()
                        val durationSeconds = speechDuration / 1000f

                        // Hentikan mic sementara selama playback berlangsung
                        isRunning.set(false)

                        callback?.onSpeechCaptured(capturedPcm, SAMPLE_RATE, durationSeconds)
                        break
                    }
                }
            }

        } catch (e: SecurityException) {
            callback?.onError("Izin mikrofon dicabut: ${e.message}")
        } catch (e: Exception) {
            callback?.onError("AudioRecord error: ${e.message}")
        } finally {
            try {
                audioRecord?.stop()
                audioRecord?.release()
            } catch (e: Exception) {
                Log.w(TAG, "Error saat menutup AudioRecord", e)
            }
        }
    }

    private fun calculateRms(buffer: ShortArray, readSize: Int): Double {
        var sum = 0.0
        for (i in 0 until readSize) {
            val sample = buffer[i].toDouble()
            sum += sample * sample
        }
        return sqrt(sum / readSize)
    }
}
