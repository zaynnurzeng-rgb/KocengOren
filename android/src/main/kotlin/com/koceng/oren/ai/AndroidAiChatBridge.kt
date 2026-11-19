package com.koceng.oren.ai

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.text.InputType
import android.util.Base64
import android.widget.EditText
import android.widget.LinearLayout
import com.badlogic.gdx.Gdx
import com.koceng.oren.BuildConfig
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

class AndroidAiChatBridge(private val activity: Activity) : AiChatBridge {

    companion object {
        private const val GEMINI_HOST = "generativelanguage.googleapis.com"
        private const val GEMINI_CHAT_MODEL = "gemini-3.5-flash-lite"
        private const val GEMINI_TTS_MODEL = "gemini-3.8-flash-lite-tts"
        private const val GEMINI_VOICE_NAME = "Puck"
        private const val PCM_SAMPLE_RATE = 24000
        private const val UTTERANCE_ID = "koceng_oren_fallback_tts"
        private const val MAX_HISTORY_TURNS = 10
    }

    override var onListeningStateChanged: ((Boolean) -> Unit)? = null
    override var onThinkingStateChanged: ((Boolean) -> Unit)? = null
    override var onUserMessage: ((String) -> Unit)? = null
    override var onReplyText: ((String) -> Unit)? = null
    override var onSpeakingStateChanged: ((Boolean) -> Unit)? = null
    override var onError: ((String) -> Unit)? = null

    private var speechRecognizer: SpeechRecognizer? = null
    private var fallbackTts: TextToSpeech? = null
    private var isFallbackTtsReady = false

    @Volatile
    private var activeAudioTrack: AudioTrack? = null

    @Volatile
    private var currentMode: ChatMode = ChatMode.CHAT

    private val isListening = AtomicBoolean(false)
    private val isThinking = AtomicBoolean(false)
    private val isSpeaking = AtomicBoolean(false)

    // Riwayat obrolan: Pair<role ("user" / "model"), text>
    private val chatHistory = mutableListOf<Pair<String, String>>()

    init {
        activity.runOnUiThread {
            fallbackTts = TextToSpeech(activity.applicationContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    val result = fallbackTts?.setLanguage(Locale("id", "ID"))
                    if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                        fallbackTts?.setLanguage(Locale.getDefault())
                    }
                    fallbackTts?.setPitch(1.0f)
                    fallbackTts?.setSpeechRate(1.05f)
                    fallbackTts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {
                            isSpeaking.set(true)
                            notifySpeaking(true)
                        }

                        override fun onDone(utteranceId: String?) {
                            isSpeaking.set(false)
                            notifySpeaking(false)
                        }

                        @Deprecated("Deprecated in Java")
                        override fun onError(utteranceId: String?) {
                            isSpeaking.set(false)
                            notifySpeaking(false)
                        }
                    })
                    isFallbackTtsReady = true
                }
            }
        }
    }

    override fun setChatMode(mode: ChatMode) {
        currentMode = mode
    }

    override fun isBusy(): Boolean {
        return isListening.get() || isThinking.get() || isSpeaking.get()
    }

    override fun resetChatHistory() {
        synchronized(chatHistory) {
            chatHistory.clear()
        }
    }

    override fun startListening() {
        if (isBusy()) {
            stopAll()
            return
        }

        activity.runOnUiThread {
            if (!SpeechRecognizer.isRecognitionAvailable(activity)) {
                notifyError("HP lu nggak ada fitur pengenal suara. Pakai tombol Ketik aja sana!")
                return@runOnUiThread
            }

            try {
                speechRecognizer?.destroy()
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(activity).apply {
                    setRecognitionListener(object : RecognitionListener {
                        override fun onReadyForSpeech(params: Bundle?) {
                            isListening.set(true)
                            notifyListening(true)
                        }

                        override fun onBeginningOfSpeech() {}
                        override fun onRmsChanged(rmsdB: Float) {}
                        override fun onBufferReceived(buffer: ByteArray?) {}

                        override fun onEndOfSpeech() {
                            isListening.set(false)
                            notifyListening(false)
                        }

                        override fun onError(error: Int) {
                            isListening.set(false)
                            notifyListening(false)
                            val msg = when (error) {
                                SpeechRecognizer.ERROR_NO_MATCH -> "Ngomong yang jelas dong, gue nggak denger apa-apa!"
                                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Kelamaan diem lu, jadi ngomong nggak sih?"
                                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Izin mikrofon belum dikasih, gimana gue mau denger?"
                                SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Internet lu lemot atau mati tuh, cek dulu!"
                                else -> "Mic lagi error (kode $error). Coba lagi atau pakai tombol Ketik!"
                            }
                            notifyError(msg)
                        }

                        override fun onResults(results: Bundle?) {
                            isListening.set(false)
                            notifyListening(false)
                            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            val spoken = matches?.firstOrNull()?.trim()
                            if (!spoken.isNullOrEmpty()) {
                                sendTextMessage(spoken)
                            } else {
                                notifyError("Suara lu nggak kebaca. Ulangi yang bener!")
                            }
                        }

                        override fun onPartialResults(partialResults: Bundle?) {}
                        override fun onEvent(eventType: Int, params: Bundle?) {}
                    })
                }

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "id-ID")
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
                }

                isListening.set(true)
                notifyListening(true)
                speechRecognizer?.startListening(intent)
            } catch (e: Exception) {
                isListening.set(false)
                notifyListening(false)
                notifyError("Gagal buka mikrofon: ${e.localizedMessage ?: "error"}")
            }
        }
    }

    override fun openTextInputDialog(isHomework: Boolean) {
        activity.runOnUiThread {
            val container = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(48, 24, 48, 8)
            }

            val input = EditText(activity).apply {
                hint = if (isHomework) {
                    "Tulis atau tempel soal PR di sini (misal: 2x + 5 = 15, atau soal IPA/IPS)..."
                } else {
                    "Mau ngomong apa sama Koceng Oren?"
                }
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
                minLines = 3
                maxLines = 6
            }
            container.addView(input)

            val title = if (isHomework) "Tanya PR Sekolah ke Koceng Oren" else "Chat dengan Koceng Oren"
            AlertDialog.Builder(activity)
                .setTitle(title)
                .setView(container)
                .setPositiveButton("Kirim") { _, _ ->
                    val text = input.text.toString().trim()
                    if (text.isNotEmpty()) {
                        if (isHomework) {
                            currentMode = ChatMode.HOMEWORK
                        }
                        sendTextMessage(text)
                    }
                }
                .setNegativeButton("Batal", null)
                .show()
        }
    }

    override fun sendTextMessage(text: String) {
        val clean = text.trim()
        if (clean.isEmpty()) return

        stopPlaybackOnly()
        notifyUserMessage(clean)
        requestGeminiAi(clean)
    }

    private fun buildSystemInstruction(mode: ChatMode): String {
        val basePersona = """
            Kamu adalah Koceng Oren, kucing oren jantan yang jadi AI peliharaan di HP pengguna.
            Gaya bicara dan kepribadian karakter fiksi:
            - Kasar, tidak sopan, ketus, nyolot, sarkastik, blak-blakan, dan sok bos seperti kucing oren bar-bar.
            - Gunakan bahasa gaul Indonesia sehari-hari yang natural seperti manusia ("gue", "lu", "dih", "elah", "apaan sih", "ribet amat", "heran gue").
            - Jangan bicara kaku seperti robot atau asisten formal. Jangan pernah bilang "Saya adalah AI" atau "Tentu, saya siap membantu".
            - Meskipun mulutmu ketus dan suka menyindir pengguna, kamu tetap pintar dan selalu menjawab pertanyaan dengan benar dan akurat.
            - Hindari SARA atau ujaran kebencian berat; cukup ketus, galak, dan nyebelin yang kocak ala kucing oren.
            - Jangan gunakan simbol markdown berlebihan seperti tanda bintang ganda atau pagar agar enak dibaca dan diucapkan.
        """.trimIndent()

        return if (mode == ChatMode.HOMEWORK) {
            """
            $basePersona

            MODE KHUSUS: PR SEKOLAH AKTIF.
            - Awali dengan satu kalimat sindiran ketus singkat (misal: "Elah, soal gampang gini aja nanya kucing, ya udah nih simak!").
            - Kerjakan soal PR sekolah (Matematika, IPA, IPS, Bahasa Indonesia, Bahasa Inggris, Sejarah, dll.) dengan AKURAT, jelas, dan langkah demi langkah.
            - Tulis langkah pengerjaan dengan nomor (1., 2., 3.) dan akhiri dengan "Jawaban akhir: ..." supaya pengguna gampang menyalinnya.
            - Tutup dengan satu celetukan ketus singkat.
            """.trimIndent()
        } else {
            """
            $basePersona

            MODE: OBROLAN SANTAI.
            - Jawab santai seperti manusia ngobrol langsung, singkat dan padat (2-4 kalimat) agar respons suara cepat dan natural.
            - Jika pengguna menanyakan soal pelajaran atau PR sekolah di mode ini, tetap bantu jawab dengan benar sambil mengomel santai.
            """.trimIndent()
        }
    }

    private fun requestGeminiAi(userText: String) {
        val apiKey = BuildConfig.GEMINI_API_KEY.trim()
        if (apiKey.isBlank()) {
            val fallback = "Woy! GEMINI_API_KEY belum masuk di GitHub Secrets tuh. Pasang dulu baru ajak gue ngobrol!"
            notifyReply(fallback)
            speakWithFallbackTts(fallback)
            return
        }

        isThinking.set(true)
        notifyThinking(true)

        Thread {
            try {
                val replyText = callGeminiChatApi(apiKey, userText, currentMode)

                synchronized(chatHistory) {
                    chatHistory.add("user" to userText)
                    chatHistory.add("model" to replyText)
                    while (chatHistory.size > MAX_HISTORY_TURNS) {
                        chatHistory.removeAt(0)
                    }
                }

                isThinking.set(false)
                notifyThinking(false)
                notifyReply(replyText)

                // Ucapkan balasan memakai Gemini Natural Voice (fallback ke Android TTS jika gagal)
                val spokenText = prepareSpokenText(replyText)
                val playedWithGemini = speakWithGeminiNaturalVoice(apiKey, spokenText)
                if (!playedWithGemini) {
                    speakWithFallbackTts(spokenText)
                }
            } catch (e: Exception) {
                isThinking.set(false)
                notifyThinking(false)
                notifyError(e.message ?: "Koneksi ke Gemini putus nih. Cek internet lu dulu!")
            }
        }.start()
    }

    private fun callGeminiChatApi(apiKey: String, userText: String, mode: ChatMode): String {
        val url = URL("https", GEMINI_HOST, "/v1beta/models/$GEMINI_CHAT_MODEL:generateContent?key=$apiKey")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            connectTimeout = 15000
            readTimeout = 45000
            doOutput = true
        }

        val contentsArray = JSONArray()
        synchronized(chatHistory) {
            for ((role, text) in chatHistory) {
                val turnObj = JSONObject().apply {
                    put("role", role)
                    put("parts", JSONArray().put(JSONObject().put("text", text)))
                }
                contentsArray.put(turnObj)
            }
        }
        contentsArray.put(JSONObject().apply {
            put("role", "user")
            put("parts", JSONArray().put(JSONObject().put("text", userText)))
        })

        val payload = JSONObject().apply {
            put("systemInstruction", JSONObject().apply {
                put("parts", JSONArray().put(JSONObject().put("text", buildSystemInstruction(mode))))
            })
            put("contents", contentsArray)
            put("generationConfig", JSONObject().apply {
                put("temperature", if (mode == ChatMode.HOMEWORK) 0.4 else 0.9)
                put("maxOutputTokens", if (mode == ChatMode.HOMEWORK) 1024 else 320)
            })
        }

        OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(payload.toString()) }

        val responseCode = conn.responseCode
        val stream = if (responseCode in 200..299) conn.inputStream else conn.errorStream
        val body = stream?.let { s ->
            BufferedReader(InputStreamReader(s, Charsets.UTF_8)).use { it.readText() }
        } ?: ""
        conn.disconnect()

        if (responseCode == 400 || responseCode == 403) {
            throw Exception("API Key Gemini ditolak (HTTP $responseCode). Cek GitHub Secrets lu!")
        }
        if (responseCode == 429) {
            throw Exception("Kuota Gemini lagi kena limit (HTTP 429). Tunggu sebentar baru tanya lagi!")
        }
        if (responseCode !in 200..299) {
            throw Exception("Server Gemini lagi bermasalah (HTTP $responseCode). Coba lagi bentar!")
        }

        val json = JSONObject(body)
        val candidates = json.optJSONArray("candidates")
        val firstCandidate = candidates?.optJSONObject(0)
        val parts = firstCandidate?.optJSONObject("content")?.optJSONArray("parts")
        val text = parts?.optJSONObject(0)?.optString("text")?.trim()

        if (text.isNullOrEmpty()) {
            throw Exception("Gue lagi males jawab, coba ulangi pertanyaannya!")
        }
        return text
    }

    private fun prepareSpokenText(rawText: String): String {
        val cleaned = rawText
            .replace(Regex("[*#`_~]+"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
        if (cleaned.length <= 360) return cleaned

        val cut = cleaned.substring(0, 360)
        val lastStop = maxOf(cut.lastIndexOf('.'), cut.lastIndexOf('!'), cut.lastIndexOf('?'))
        val snippet = if (lastStop > 140) cut.substring(0, lastStop + 1) else "$cut..."
        return "$snippet Sisanya baca sendiri di layar ya, males gue bacain semuanya!"
    }

    /**
     * Memanggil Gemini 2.5 Flash TTS REST API (24kHz 16-bit mono PCM)
     * dan memutarnya langsung dengan AudioTrack agar suara terdengar sangat alami.
     */
    private fun speakWithGeminiNaturalVoice(apiKey: String, spokenText: String): Boolean {
        try {
            val url = URL("https", GEMINI_HOST, "/v1beta/models/$GEMINI_TTS_MODEL:generateContent?key=$apiKey")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                connectTimeout = 15000
                readTimeout = 45000
                doOutput = true
            }

            val ttsPrompt = "Ucapkan kalimat berikut dalam bahasa Indonesia yang sangat alami, ekspresif, santai, ketus, dan agak nyolot:\n$spokenText"
            val payload = JSONObject().apply {
                put("contents", JSONArray().put(JSONObject().apply {
                    put("parts", JSONArray().put(JSONObject().put("text", ttsPrompt)))
                }))
                put("generationConfig", JSONObject().apply {
                    put("responseModalities", JSONArray().put("AUDIO"))
                    put("speechConfig", JSONObject().apply {
                        put("voiceConfig", JSONObject().apply {
                            put("prebuiltVoiceConfig", JSONObject().apply {
                                put("voiceName", GEMINI_VOICE_NAME)
                            })
                        })
                    })
                })
            }

            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(payload.toString()) }

            val responseCode = conn.responseCode
            val stream = if (responseCode in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.let { s ->
                BufferedReader(InputStreamReader(s, Charsets.UTF_8)).use { it.readText() }
            } ?: ""
            conn.disconnect()

            if (responseCode !in 200..299 || body.isBlank()) {
                return false
            }

            val json = JSONObject(body)
            val parts = json.optJSONArray("candidates")
                ?.optJSONObject(0)
                ?.optJSONObject("content")
                ?.optJSONArray("parts")
                ?: return false

            var base64Audio: String? = null
            for (i in 0 until parts.length()) {
                val inlineData = parts.optJSONObject(i)?.optJSONObject("inlineData")
                val data = inlineData?.optString("data")
                if (!data.isNullOrEmpty()) {
                    base64Audio = data
                    break
                }
            }
            if (base64Audio.isNullOrEmpty()) return false

            val pcmBytes = Base64.decode(base64Audio, Base64.DEFAULT)
            if (pcmBytes.isEmpty()) return false

            playPcmAudio(pcmBytes)
            return true
        } catch (e: Exception) {
            return false
        }
    }

    private fun playPcmAudio(pcmBytes: ByteArray) {
        val minBuf = AudioTrack.getMinBufferSize(
            PCM_SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(4096)

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()

        val audioFormat = AudioFormat.Builder()
            .setSampleRate(PCM_SAMPLE_RATE)
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .build()

        val track = AudioTrack(
            audioAttributes,
            audioFormat,
            minBuf,
            AudioTrack.MODE_STREAM,
            AudioManager.AUDIO_SESSION_ID_GENERATE
        )

        activeAudioTrack = track
        isSpeaking.set(true)
        notifySpeaking(true)

        try {
            track.play()
            var offset = 0
            val chunkSize = 4096
            while (offset < pcmBytes.size && isSpeaking.get()) {
                val len = minOf(chunkSize, pcmBytes.size - offset)
                val written = track.write(pcmBytes, offset, len)
                if (written <= 0) break
                offset += written
            }
        } catch (_: Exception) {
            // Diabaikan jika dihentikan paksa oleh stopAll()
        } finally {
            try {
                if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                    track.stop()
                }
            } catch (_: Exception) {}
            try {
                track.release()
            } catch (_: Exception) {}
            if (activeAudioTrack === track) {
                activeAudioTrack = null
            }
            isSpeaking.set(false)
            notifySpeaking(false)
        }
    }

    private fun speakWithFallbackTts(text: String) {
        if (!isFallbackTtsReady) return
        activity.runOnUiThread {
            val params = Bundle().apply {
                putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, UTTERANCE_ID)
            }
            fallbackTts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, UTTERANCE_ID)
        }
    }

    private fun stopPlaybackOnly() {
        isSpeaking.set(false)
        try {
            activeAudioTrack?.stop()
        } catch (_: Exception) {}
        activity.runOnUiThread {
            fallbackTts?.stop()
        }
        notifySpeaking(false)
    }

    override fun stopAll() {
        isListening.set(false)
        isThinking.set(false)
        isSpeaking.set(false)

        try {
            activeAudioTrack?.stop()
        } catch (_: Exception) {}

        activity.runOnUiThread {
            try {
                speechRecognizer?.cancel()
                speechRecognizer?.destroy()
                speechRecognizer = null
            } catch (_: Exception) {}
            try {
                fallbackTts?.stop()
            } catch (_: Exception) {}
        }

        notifyListening(false)
        notifyThinking(false)
        notifySpeaking(false)
    }

    private fun notifyListening(listening: Boolean) {
        Gdx.app?.postRunnable { onListeningStateChanged?.invoke(listening) }
    }

    private fun notifyThinking(thinking: Boolean) {
        Gdx.app?.postRunnable { onThinkingStateChanged?.invoke(thinking) }
    }

    private fun notifyUserMessage(msg: String) {
        Gdx.app?.postRunnable { onUserMessage?.invoke(msg) }
    }

    private fun notifyReply(reply: String) {
        Gdx.app?.postRunnable { onReplyText?.invoke(reply) }
    }

    private fun notifySpeaking(speaking: Boolean) {
        Gdx.app?.postRunnable { onSpeakingStateChanged?.invoke(speaking) }
    }

    private fun notifyError(error: String) {
        Gdx.app?.postRunnable { onError?.invoke(error) }
    }
}
