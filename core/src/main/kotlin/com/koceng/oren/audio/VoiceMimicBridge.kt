package com.koceng.oren.audio

/**
 * Listener callback dari Audio Recorder native ke libGDX Core logic
 */
interface VoiceMimicCallback {
    /**
     * Dipanggil saat mic mendeteksi pengguna mulai berbicara (Voice Activity Detected)
     */
    fun onSpeechStarted()

    /**
     * Dipanggil saat pengguna selesai berbicara (Silence detected setelah ada suara)
     * @param pcmData byte array mentah dari rekaman suara (PCM 16-bit mono)
     * @param sampleRate frekuensi sampling (umumnya 44100 Hz)
     * @param durationSeconds durasi rekaman dalam detik
     */
    fun onSpeechCaptured(pcmData: ByteArray, sampleRate: Int, durationSeconds: Float)

    /**
     * Penanganan error (misal izin mikrofon ditolak)
     */
    fun onError(errorMessage: String)
}

/**
 * Interface Bridge yang menghubungkan LibGDX Core ke platform native Android
 */
interface VoiceMimicBridge {
    /**
     * Memulai pemantauan background mic (VAD loop)
     */
    fun startListening()

    /**
     * Menghentikan mic
     */
    fun stopListening()

    /**
     * Status apakah mic sedang aktif memantau
     */
    fun isListening(): Boolean

    /**
     * Mendaftarkan callback penangkap suara
     */
    fun setCallback(callback: VoiceMimicCallback?)

    /**
     * Memeriksa dan meminta izin RECORD_AUDIO di runtime Android
     */
    fun checkAndRequestPermissions(onGranted: () -> Unit)
}
