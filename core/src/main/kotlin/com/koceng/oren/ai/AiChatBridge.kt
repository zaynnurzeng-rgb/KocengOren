package com.koceng.oren.ai

/**
 * Interface Bridge komunikasi AI Chat (Speech-to-Text -> Gemini LLM -> Text-to-Speech)
 * antara libGDX Core logic dan platform native Android.
 */
interface AiChatBridge {

    /**
     * Callback saat status mikrofon/SpeechRecognizer berubah (aktif mendengarkan atau selesai)
     */
    var onListeningStateChanged: ((Boolean) -> Unit)?

    /**
     * Callback saat balasan teks dari AI Gemini berhasil diterima
     */
    var onReplyText: ((String) -> Unit)?

    /**
     * Callback saat TextToSpeech mulai atau selesai berbicara
     */
    var onSpeakingStateChanged: ((Boolean) -> Unit)?

    /**
     * Callback saat terjadi kesalahan (misal koneksi gagal, izin mikrofon tidak ada, API key belum disetel)
     */
    var onError: ((String) -> Unit)?

    /**
     * Memulai pendengaran suara pengguna melalui SpeechRecognizer
     */
    fun startListening()

    /**
     * Menghentikan semua proses aktif (STT, koneksi HTTP Gemini, TTS)
     */
    fun stopAll()

    /**
     * Memeriksa apakah bridge sedang sibuk (mendengarkan suara, request API, atau berbicara)
     */
    fun isBusy(): Boolean

    /**
     * Memperbarui status pet (lapar, energi, kebahagiaan) untuk disisipkan ke konteks prompt Gemini
     */
    fun updatePetStatus(hunger: Float, energy: Float, happiness: Float)

    /**
     * Mengosongkan riwayat obrolan multi-turn Gemini untuk memulai topik baru
     */
    fun resetChatHistory()
}
