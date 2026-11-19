package com.koceng.oren.ai

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.badlogic.gdx.Gdx
import com.koceng.oren.BuildConfig
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

class AndroidAiChatBridge(private val activity: Activity) : AiChatBridge {

    companion object {
        private const val TAG = "AndroidAiChatBridge"
        private const val GEMINI_MODEL = "gemini-2.5-flash"
        private const val UTTERANCE_ID = "koceng_oren_tts_utterance"
    }

    override var onListeningStateChanged: ((Boolean) -> Unit)? = null
    override var onReplyText: ((String) -> Unit)? = null
    override var onSpeakingStateChanged: ((Boolean) -> Unit)? = null
    override var onError: ((String) -> Unit)? = null

    private val mainHandler = Handler(Looper.getMainLooper())

    private var speechRecognizer: SpeechRecognizer? = null
    private var textToSpeech: TextToSpeech? = null
    private var isTtsReady = false

    private val isRecognizing = AtomicBoolean(false)
    private val isRequestingApi = AtomicBoolean(false)
    private val isSpeaking = AtomicBoolean(false)

    // Status pet yang disisipkan ke context AI
    private var currentHunger: Float = 85f
    private var currentEnergy: Float = 90f
    private var currentHappiness: Float = 80f

    init {
        // Inisialisasi Text-to-Speech pada UI thread
        mainHandler.post {
            textToSpeech = TextToSpeech(activity) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    val result = textToSpeech?.setLanguage(Locale("id", "ID"))
                    if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                        Log.w(TAG, "Bahasa Indonesia tidak didukung penuh TTS, mencoba fallback default locale.")
                        textToSpeech?.language = Locale.getDefault()
                    }
                    textToSpeech?.setPitch(1.35f)      // Suara kucing agak melengking/lucu
                    textToSpeech?.setSpeechRate(1.1f)   // Kecepatan sedikit lebih lincah
                    isTtsReady = true

                    setupTtsListener()
                } else {
                    Log.e(TAG, "Gagal menginisialisasi TextToSpeech: $status")
                }
            }
        }
    }

    private fun setupTtsListener() {
        textToSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                isSpeaking.set(true)
                postToGdx { onSpeakingStateChanged?.invoke(true) }
            }

            override fun onDone(utteranceId: String?) {
                isSpeaking.set(false)
                postToGdx { onSpeakingStateChanged?.invoke(false) }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                isSpeaking.set(false)
                postToGdx { onSpeakingStateChanged?.invoke(false) }
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                isSpeaking.set(false)
                postToGdx { onSpeakingStateChanged?.invoke(false) }
            }
        })
    }

    override fun updatePetStatus(hunger: Float, energy: Float, happiness: Float) {
        this.currentHunger = hunger
        this.currentEnergy = energy
        this.currentHappiness = happiness
    }

    override fun isBusy(): Boolean {
        return isRecognizing.get() || isRequestingApi.get() || isSpeaking.get()
    }

    override fun startListening() {
        if (isBusy()) {
            stopAll()
        }

        mainHandler.post {
            try {
                if (!SpeechRecognizer.isRecognitionAvailable(activity)) {
                    postToGdx { onError?.invoke("Fitur Speech Recognition tidak tersedia di perangkat ini.") }
                    return@post
                }

                speechRecognizer?.destroy()
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(activity).apply {
                    setRecognitionListener(createSpeechListener())
                }

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "id-ID")
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "id-ID")
                    putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, "id-ID")
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
                }

                isRecognizing.set(true)
                speechRecognizer?.startListening(intent)
                postToGdx { onListeningStateChanged?.invoke(true) }
                Log.i(TAG, "SpeechRecognizer mulai mendengarkan suara pengguna...")
            } catch (e: Exception) {
                Log.e(TAG, "Gagal memulai SpeechRecognizer", e)
                isRecognizing.set(false)
                postToGdx {
                    onListeningStateChanged?.invoke(false)
                    onError?.invoke("Gagal membuka mic: ${e.message}")
                }
            }
        }
    }

    private fun createSpeechListener(): RecognitionListener {
        return object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                Log.d(TAG, "Siap menerima suara...")
            }

            override fun onBeginningOfSpeech() {
                Log.d(TAG, "Pengguna mulai berbicara...")
            }

            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                Log.d(TAG, "Pengguna selesai berbicara.")
                isRecognizing.set(false)
                postToGdx { onListeningStateChanged?.invoke(false) }
            }

            override fun onError(error: Int) {
                isRecognizing.set(false)
                postToGdx {
                    onListeningStateChanged?.invoke(false)
                    val msg = when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH -> "Tidak ada suara yang terdeteksi."
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Waktu bicara habis."
                        SpeechRecognizer.ERROR_AUDIO -> "Masalah rekaman audio."
                        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Masalah koneksi jaringan STT."
                        else -> "Mic tidak menangkap kata yang jelas."
                    }
                    onError?.invoke(msg)
                }
            }

            override fun onResults(results: Bundle?) {
                isRecognizing.set(false)
                postToGdx { onListeningStateChanged?.invoke(false) }

                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val userSpeech = matches?.firstOrNull()?.trim() ?: ""

                if (userSpeech.isNotBlank()) {
                    Log.i(TAG, "Teks suara pengguna: \"$userSpeech\"")
                    requestGeminiAi(userSpeech)
                } else {
                    postToGdx { onError?.invoke("Suara tidak terdengar jelas.") }
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        }
    }

    /**
     * Mengirim pesan ke Gemini REST API (gemini-2.5-flash) di background thread
     */
    private fun requestGeminiAi(userMessage: String) {
        val apiKey = BuildConfig.GEMINI_API_KEY

        if (apiKey.isBlank()) {
            val fallback = "Meong! Kunci API Gemini belum dipasang di GitHub Secrets, tapi Koceng Oren tetap lucu kan?"
            postToGdx {
                onReplyText?.invoke(fallback)
            }
            speakReply(fallback)
            return
        }

        isRequestingApi.set(true)
        Thread({
            var connection: HttpURLConnection? = null
            try {
                val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/$GEMINI_MODEL:generateContent?key=$apiKey"
                val url = URL(endpoint)
                connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                    connectTimeout = 12000
                    readTimeout = 12000
                    doOutput = true
                }

                // Instruksi karakter Koceng Oren
                val systemPrompt = "Kamu adalah 'Koceng Oren', seekor kucing oranye virtual pet yang tengil, lucu, respons singkat (maksimal 2 kalimat bahasa Indonesia santai). Status kamu saat ini: Lapar: ${currentHunger.toInt()}%, Energi: ${currentEnergy.toInt()}%, Bahagia: ${currentHappiness.toInt()}%."

                // Susun payload JSON REST API Gemini
                val requestPayload = JSONObject().apply {
                    val contentsArray = JSONArray().apply {
                        val contentObj = JSONObject().apply {
                            put("role", "user")
                            val partsArray = JSONArray().apply {
                                put(JSONObject().put("text", userMessage))
                            }
                            put("parts", partsArray)
                        }
                        put(contentObj)
                    }
                    put("contents", contentsArray)

                    val systemInstruction = JSONObject().apply {
                        val parts = JSONArray().apply {
                            put(JSONObject().put("text", systemPrompt))
                        }
                        put("parts", parts)
                    }
                    put("systemInstruction", systemInstruction)
                }

                // Kirim request body
                connection.outputStream.use { os ->
                    os.write(requestPayload.toString().toByteArray(Charsets.UTF_8))
                }

                val responseCode = connection.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val responseStr = connection.inputStream.bufferedReader().use(BufferedReader::readText)
                    val respJson = JSONObject(responseStr)

                    val candidates = respJson.optJSONArray("candidates")
                    val candidate = candidates?.optJSONObject(0)
                    val content = candidate?.optJSONObject("content")
                    val parts = content?.optJSONArray("parts")
                    val reply = parts?.optJSONObject(0)?.optString("text")?.trim() ?: "Meong..."

                    Log.i(TAG, "Balasan Gemini: \"$reply\"")
                    postToGdx {
                        onReplyText?.invoke(reply)
                    }
                    speakReply(reply)
                } else {
                    val errorStream = connection.errorStream?.bufferedReader()?.use(BufferedReader::readText) ?: ""
                    Log.e(TAG, "Gemini HTTP $responseCode: $errorStream")
                    val friendlyError = if (responseCode == 403 || responseCode == 400) {
                        "Meong! API Key tidak valid atau kuota habis."
                    } else {
                        "Meong! Gagal ngobrol sama server (HTTP $responseCode)."
                    }
                    postToGdx { onError?.invoke(friendlyError) }
                }

            } catch (e: Exception) {
                Log.e(TAG, "Koneksi Gemini gagal", e)
                postToGdx { onError?.invoke("Meong! Jaringan internet bermasalah.") }
            } finally {
                isRequestingApi.set(false)
                connection?.disconnect()
            }
        }, "GeminiApiThread").start()
    }

    /**
     * Membacakan teks balasan melalui TextToSpeech
     */
    private fun speakReply(text: String) {
        mainHandler.post {
            if (!isTtsReady || textToSpeech == null) {
                Log.w(TAG, "TTS belum siap memproses suara.")
                return@post
            }
            try {
                val params = Bundle()
                textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, params, UTTERANCE_ID)
            } catch (e: Exception) {
                Log.e(TAG, "Gagal menjalankan speak TTS", e)
            }
        }
    }

    override fun stopAll() {
        isRecognizing.set(false)
        isRequestingApi.set(false)
        isSpeaking.set(false)

        mainHandler.post {
            try {
                speechRecognizer?.cancel()
                speechRecognizer?.destroy()
                speechRecognizer = null

                textToSpeech?.stop()
            } catch (e: Exception) {
                Log.w(TAG, "Error saat stopAll", e)
            }
        }

        postToGdx {
            onListeningStateChanged?.invoke(false)
            onSpeakingStateChanged?.invoke(false)
        }
    }

    private fun postToGdx(action: () -> Unit) {
        Gdx.app.postRunnable {
            try {
                action()
            } catch (e: Exception) {
                Log.e(TAG, "Error pada callback libGDX", e)
            }
        }
    }
}
