package com.koceng.oren.ai

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.provider.MediaStore
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.io.ByteArrayOutputStream
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
import android.media.MediaPlayer
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.CountDownLatch
class AndroidAiChatBridge(private val activity: Activity) : AiChatBridge {

    companion object {
        private const val GEMINI_HOST = "generativelanguage.googleapis.com"
        private const val GEMINI_CHAT_MODEL = "gemini-3.5-flash-lite"
        private const val GEMINI_TTS_MODEL = "gemini-3.8-flash-lite-tts"
private val GEMINI_TTS_MODELS = listOf("gemini-3.8-flash-tts", "gemini-3.8-flash-lite-tts", "gemini-3.1-flash-tts-preview", "gemini-2.5-flash-preview-tts")
        private const val GEMINI_VOICE_NAME = "Puck"
        private const val PCM_SAMPLE_RATE = 24000
        private const val UTTERANCE_ID = "koceng_oren_fallback_tts"
        private const val MAX_HISTORY_TURNS = 10
private const val REQ_CAMERA_PHOTO = 9001
private const val REQ_GALLERY_PHOTO = 9002
private const val PREFS_NAME = "koceng_oren_prefs"
    }

    override var onListeningStateChanged: ((Boolean) -> Unit)? = null
    override var onThinkingStateChanged: ((Boolean) -> Unit)? = null
    override var onUserMessage: ((String) -> Unit)? = null
    override var onReplyText: ((String) -> Unit)? = null
    override var onSpeakingStateChanged: ((Boolean) -> Unit)? = null
    override var onError: ((String) -> Unit)? = null
    override var onRoomThemeChanged: ((RoomTheme) -> Unit)? = null
    override val useNativeHud: Boolean = true
    private var nativeHud: NativePetHudOverlay? = null
    fun createNativeHudOverlay(): View = NativePetHudOverlay(activity, this).also { nativeHud = it }.createView()
    override fun updateNativeBubble(userText: String?, replyText: String) { nativeHud?.updateBubble(userText, replyText) }
    private val prefs = activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    @Volatile private var selectedVoiceName: String = prefs.getString("voice_name", GEMINI_VOICE_NAME) ?: GEMINI_VOICE_NAME
    @Volatile private var attitudeLevel: Int = prefs.getInt("attitude_level", 1)
    @Volatile private var lastReplyText: String = ""

    private var speechRecognizer: SpeechRecognizer? = null
    private var fallbackTts: TextToSpeech? = null
    private var isFallbackTtsReady = false

    @Volatile
    private var activeAudioTrack: AudioTrack? = null
    @Volatile private var activeMediaPlayer: MediaPlayer? = null
    
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
                    fallbackTts?.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
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
    private fun dp(value: Int): Int = (value * activity.resources.displayMetrics.density).toInt()
        private fun buildCartoonCardContainer(title: String): Pair<LinearLayout, LinearLayout> {
val card = LinearLayout(activity).apply {
orientation = LinearLayout.VERTICAL
setPadding(dp(20), dp(20), dp(20), dp(20))
background = GradientDrawable().apply {
cornerRadius = dp(24).toFloat()
setColor(Color.parseColor("#FFFBF0"))
setStroke(dp(3), Color.parseColor("#52290F"))
}
}
val titleView = TextView(activity).apply {
text = title
textSize = 19f
setTypeface(null, Typeface.BOLD)
setTextColor(Color.parseColor("#52290F"))
setPadding(0, 0, 0, dp(10))
}
card.addView(titleView)
val divider = View(activity).apply {
setBackgroundColor(Color.parseColor("#EADBC8"))
layoutParams = LinearLayout.LayoutParams(
LinearLayout.LayoutParams.MATCH_PARENT,
dp(1)
).apply {
bottomMargin = dp(14)
}
}
card.addView(divider)
val content = LinearLayout(activity).apply {
orientation = LinearLayout.VERTICAL
}
card.addView(content)
return Pair(card, content)
}

private fun createCartoonButton(text: String, isPrimary: Boolean, onClick: () -> Unit): Button {
return Button(activity).apply {
this.text = text
isAllCaps = false
textSize = 14f
setTypeface(null, if (isPrimary) Typeface.BOLD else Typeface.NORMAL)
setTextColor(if (isPrimary) Color.WHITE else Color.parseColor("#52290F"))
background = GradientDrawable().apply {
cornerRadius = dp(18).toFloat()
setColor(Color.parseColor(if (isPrimary) "#FF8C1A" else "#EFE4D4"))
}
layoutParams = LinearLayout.LayoutParams(
LinearLayout.LayoutParams.MATCH_PARENT,
dp(44)
)
setOnClickListener { onClick() }
}
}

private fun showCustomDialog(card: LinearLayout): AlertDialog {
val dialog = AlertDialog.Builder(activity)
.setView(card)
.create()
dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
dialog.show()
return dialog
}

private fun showCartoonOptionListDialog(
title: String,
items: Array<String>,
selectedIndex: Int = -1,
onSelect: (Int) -> Unit
) {
val (card, content) = buildCartoonCardContainer(title)
var dialogRef: AlertDialog? = null
items.forEachIndexed { i, label ->
val isSelected = selectedIndex == i
val btn = Button(activity).apply {
text = if (isSelected) "$label ✓" else label
isAllCaps = false
textSize = 14f
gravity = Gravity.START or Gravity.CENTER_VERTICAL
setPadding(dp(16), 0, dp(16), 0)
setTypeface(null, if (isSelected) Typeface.BOLD else Typeface.NORMAL)
setTextColor(if (isSelected) Color.WHITE else Color.parseColor("#52290F"))
background = GradientDrawable().apply {
cornerRadius = dp(18).toFloat()
if (isSelected) {
setColor(Color.parseColor("#FF8C1A"))
} else {
setColor(Color.parseColor("#FFF3DC"))
setStroke(dp(1), Color.parseColor("#D8C3A5"))
}
}
layoutParams = LinearLayout.LayoutParams(
LinearLayout.LayoutParams.MATCH_PARENT,
dp(44)
).apply {
bottomMargin = dp(8)
}
setOnClickListener {
dialogRef?.dismiss()
onSelect(i)
}
}
content.addView(btn)
}
val cancelBtn = createCartoonButton("Batal", false) {
dialogRef?.dismiss()
}.apply {
(layoutParams as LinearLayout.LayoutParams).topMargin = dp(4)
}
content.addView(cancelBtn)
dialogRef = showCustomDialog(card)
}

    override fun openTextInputDialog(isHomework: Boolean) {
        activity.runOnUiThread {
            val title = if (isHomework) "Tanya PR Sekolah ke Koceng Oren" else "Chat dengan Koceng Oren"
val (card, content) = buildCartoonCardContainer(title)
val input = EditText(activity).apply {
hint = if (isHomework) {
"Tulis soal PR di sini (Matematika, IPA, B. Inggris, dll)..."
} else {
"Ketik pesan buat Koceng Oren..."
}
inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
minLines = 3
maxLines = 6
gravity = Gravity.TOP or Gravity.START
setTextColor(Color.parseColor("#52290F"))
setHintTextColor(Color.parseColor("#9E7E5F"))
setPadding(dp(14), dp(14), dp(14), dp(14))
background = GradientDrawable().apply {
cornerRadius = dp(16).toFloat()
setColor(Color.parseColor("#FFF5E4"))
setStroke(dp(1), Color.parseColor("#D8C3A5"))
}
layoutParams = LinearLayout.LayoutParams(
LinearLayout.LayoutParams.MATCH_PARENT,
LinearLayout.LayoutParams.WRAP_CONTENT
).apply {
bottomMargin = dp(14)
}
}
content.addView(input)

var dialogRef: AlertDialog? = null
val btnRow = LinearLayout(activity).apply {
orientation = LinearLayout.HORIZONTAL
layoutParams = LinearLayout.LayoutParams(
LinearLayout.LayoutParams.MATCH_PARENT,
LinearLayout.LayoutParams.WRAP_CONTENT
)
}
val cancelBtn = createCartoonButton("Batal", false) {
dialogRef?.dismiss()
}.apply {
layoutParams = LinearLayout.LayoutParams(0, dp(44), 1f).apply {
marginEnd = dp(8)
}
}
val sendBtn = createCartoonButton("Kirim", true) {
val text = input.text.toString().trim()
if (text.isNotEmpty()) {
if (isHomework) {
currentMode = ChatMode.HOMEWORK
}
sendTextMessage(text)
}
dialogRef?.dismiss()
}.apply {
layoutParams = LinearLayout.LayoutParams(0, dp(44), 1f).apply {
marginStart = dp(8)
}
}
btnRow.addView(cancelBtn)
btnRow.addView(sendBtn)
content.addView(btnRow)
dialogRef = showCustomDialog(card)
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

        val attitudeRule = when (attitudeLevel) {
            0 -> "Level ketus: agak cuek dan santai, tidak terlalu galak."
            2 -> "Level ketus: super bar-bar, sarkastik, galak maksimal ala koceng oren!"
            else -> "Level ketus: ketus, blak-blakan, nyolot standar."
        }
        return if (mode == ChatMode.HOMEWORK) {
            """
            $basePersona
            $attitudeRule

            MODE KHUSUS: PR SEKOLAH AKTIF.
            - Awali dengan satu kalimat sindiran ketus singkat (misal: "Elah, soal gampang gini aja nanya kucing, ya udah nih simak!").
            - Kerjakan soal PR sekolah (Matematika, IPA, IPS, Bahasa Indonesia, Bahasa Inggris, Sejarah, dll.) dengan AKURAT, jelas, dan langkah demi langkah.
            - Tulis langkah pengerjaan dengan nomor (1., 2., 3.) dan akhiri dengan "Jawaban akhir: ..." supaya pengguna gampang menyalinnya.
            - Tutup dengan satu celetukan ketus singkat.
            """.trimIndent()
        } else {
            """
            $basePersona
            $attitudeRule

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
        val modelsToTry = listOf(GEMINI_CHAT_MODEL, GEMINI_CHAT_MODEL, "gemini-3.8-flash")
      var responseCode = 0
      var body = ""
      for ((index, model) in modelsToTry.withIndex()) {
               val url = URL("https", GEMINI_HOST, "/v1beta/models/$model:generateContent?key=$apiKey")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            connectTimeout = 15000
            readTimeout = 45000
            doOutput = true
        }

   
        OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(payload.toString()) }

       responseCode = conn.responseCode
        val stream = if (responseCode in 200..299) conn.inputStream else conn.errorStream
       body = stream?.let { s ->
            BufferedReader(InputStreamReader(s, Charsets.UTF_8)).use { it.readText() }
        } ?: ""
        conn.disconnect()

                  if (responseCode in 200..299) {
                     break
                  }
          if (responseCode in setOf(500, 502, 503, 504) && index < modelsToTry.lastIndex) {
             Thread.sleep(1200)
             continue
          }
          break
      }
        if (responseCode == 400 || responseCode == 403) {
            throw Exception("API Key Gemini ditolak (HTTP $responseCode). Cek GitHub Secrets lu!")
        }
        if (responseCode == 429) {
            throw Exception("Kuota Gemini lagi kena limit (HTTP 429). Tunggu sebentar baru tanya lagi!")
        }
        if (responseCode == 503) {
           throw Exception("Server Gemini lagi rame banget nih (HTTP 503), coba tanya sekali lagi bentar lagi!")
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
var lastError = "unknown"
for (model in GEMINI_TTS_MODELS) {
        try {
            val url = URL("https", GEMINI_HOST, "/v1beta/models/$model:generateContent?key=$apiKey")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                connectTimeout = 15000
                readTimeout = 45000
                doOutput = true
            }

            val payload = JSONObject().apply {
                put("contents", JSONArray().put(JSONObject().apply {
                    put("parts", JSONArray().put(JSONObject().put("text", spokenText)))
                }))
                put("generationConfig", JSONObject().apply {
                    put("responseModalities", JSONArray().put("AUDIO"))
                    put("speechConfig", JSONObject().apply {
                        put("voiceConfig", JSONObject().apply {
                            put("prebuiltVoiceConfig", JSONObject().apply {
                                put("voiceName",selectedVoiceName) //  GEMINI_VOICE_NAME)
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
    lastError = "HTTP $responseCode"
    continue
}

val json = JSONObject(body)
val parts = json.optJSONArray("candidates")
    ?.optJSONObject(0)
    ?.optJSONObject("content")
    ?.optJSONArray("parts")
if (parts == null) {
    lastError = "empty_parts"
    continue
}

var base64Audio: String? = null
var mimeType = ""
for (i in 0 until parts.length()) {
    val inlineData = parts.optJSONObject(i)?.optJSONObject("inlineData")
    val data = inlineData?.optString("data")
    if (!data.isNullOrEmpty()) {
        base64Audio = data
        mimeType = inlineData.optString("mimeType", "").lowercase()
        break
    }
}
if (base64Audio.isNullOrEmpty()) {
    lastError = "empty_audio"
    continue
}

val audioBytes = Base64.decode(base64Audio, Base64.DEFAULT)
if (audioBytes.isEmpty()) {
    lastError = "empty_bytes"
    continue
}

if (mimeType.contains("wav") || mimeType.contains("mp3") || mimeType.contains("mpeg") || mimeType.contains("ogg")) {
    val audioFile = File(activity.cacheDir, "gemini_reply.audio")
    FileOutputStream(audioFile).use { it.write(audioBytes) }
    playAudioFile(audioFile)
} else {
    val sampleRate = Regex("rate=(\\d+)").find(mimeType)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: PCM_SAMPLE_RATE
    playPcmAudio(audioBytes, sampleRate)
}
return true
} catch (e: Exception) {
    lastError = e.javaClass.simpleName
}
}
activity.runOnUiThread {
    Toast.makeText(activity, "Suara Gemini gagal ($lastError), pakai suara cadangan", Toast.LENGTH_SHORT).show()
}
return false
}

   private fun playPcmAudio(pcmBytes: ByteArray, sampleRate: Int = PCM_SAMPLE_RATE) {
        val byteRate = sampleRate * 2
        val dataSize = pcmBytes.size
        val chunkSize = 36 + dataSize
        val header = byteArrayOf('R'.code.toByte(), 'I'.code.toByte(), 'F'.code.toByte(), 'F'.code.toByte(), (chunkSize and 0xff).toByte(), ((chunkSize shr 8) and 0xff).toByte(), ((chunkSize shr 16) and 0xff).toByte(), ((chunkSize shr 24) and 0xff).toByte(), 'W'.code.toByte(), 'A'.code.toByte(), 'V'.code.toByte(), 'E'.code.toByte(), 'f'.code.toByte(), 'm'.code.toByte(), 't'.code.toByte(), ' '.code.toByte(), 16, 0, 0, 0, 1, 0, 1, 0, (sampleRate and 0xff).toByte(), ((sampleRate shr 8) and 0xff).toByte(), ((sampleRate shr 16) and 0xff).toByte(), ((sampleRate shr 24) and 0xff).toByte(), (byteRate and 0xff).toByte(), ((byteRate shr 8) and 0xff).toByte(), ((byteRate shr 16) and 0xff).toByte(), ((byteRate shr 24) and 0xff).toByte(), 2, 0, 16, 0, 'd'.code.toByte(), 'a'.code.toByte(), 't'.code.toByte(), 'a'.code.toByte(), (dataSize and 0xff).toByte(), ((dataSize shr 8) and 0xff).toByte(), ((dataSize shr 16) and 0xff).toByte(), ((dataSize shr 24) and 0xff).toByte())
        val wavFile = File(activity.cacheDir, "gemini_reply.wav")
        FileOutputStream(wavFile).use { it.write(header); it.write(pcmBytes) }
playAudioFile(wavFile)
}

private fun playAudioFile(audioFile: File) {
        val player = MediaPlayer(); activeMediaPlayer = player; isSpeaking.set(true); notifySpeaking(true); val latch = CountDownLatch(1)
        try { player.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()); player.setVolume(1.0f, 1.0f); player.setDataSource(audioFile.absolutePath); player.setOnCompletionListener { latch.countDown() }; player.setOnErrorListener { _, _, _ -> latch.countDown(); true }; player.prepare(); player.start(); latch.await() } catch (_: Exception) {} finally { try { if (player.isPlaying) player.stop() } catch (_: Exception) {}; try { player.release() } catch (_: Exception) {}; if (activeMediaPlayer === player) activeMediaPlayer = null; isSpeaking.set(false); notifySpeaking(false) }
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
        try { activeMediaPlayer?.stop() } catch (_: Exception) {}
        try { activeMediaPlayer?.release() } catch (_: Exception) {}
        activeMediaPlayer = null
        activity.runOnUiThread {
            fallbackTts?.stop()
        }
        notifySpeaking(false)
    }
    override fun speakText(text: String) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        stopPlaybackOnly()
        val apiKey = BuildConfig.GEMINI_API_KEY.trim()
        val spokenText = prepareSpokenText(clean)
        if (apiKey.isBlank()) {
            speakWithFallbackTts(spokenText)
            return
        }
        Thread {
            val playedWithGemini = speakWithGeminiNaturalVoice(apiKey, spokenText)
            if (!playedWithGemini) {
                speakWithFallbackTts(spokenText)
            }
        }.start()
    }
    
    override fun stopAll() {
        isListening.set(false)
        isThinking.set(false)
        isSpeaking.set(false)

        try {
            activeAudioTrack?.stop()
        } catch (_: Exception) {}
        try { activeMediaPlayer?.stop() } catch (_: Exception) {}
        try { activeMediaPlayer?.release() } catch (_: Exception) {}
                activeMediaPlayer = null
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
        nativeHud?.setListening(listening)
    }

    private fun notifyThinking(thinking: Boolean) {
        Gdx.app?.postRunnable { onThinkingStateChanged?.invoke(thinking) }
        nativeHud?.setThinking(thinking)
    }

    private fun notifyUserMessage(msg: String) {
        Gdx.app?.postRunnable { onUserMessage?.invoke(msg) }
        nativeHud?.updateUserMessage(msg)
    }

    private fun notifyReply(reply: String) {
        lastReplyText = reply
        Gdx.app?.postRunnable { onReplyText?.invoke(reply) }
        nativeHud?.updateBubble(null, reply)
    }

    private fun notifySpeaking(speaking: Boolean) {
        Gdx.app?.postRunnable { onSpeakingStateChanged?.invoke(speaking) }
        nativeHud?.setSpeaking(speaking)
    }

    private fun notifyError(error: String) {
        Gdx.app?.postRunnable { onError?.invoke(error) }
        nativeHud?.updateBubble(null, error)
    }

    override fun getSavedRoomTheme(): RoomTheme {
        val name = prefs.getString("room_theme", RoomTheme.DAY.name)
        return when (name) {
            RoomTheme.NIGHT.name -> RoomTheme.NIGHT
            RoomTheme.STUDY.name -> RoomTheme.STUDY
            else -> RoomTheme.DAY
        }
    }

    override fun openPhotoHomeworkPicker() {
        activity.runOnUiThread {
                 showCartoonOptionListDialog("Foto Soal PR", arrayOf("Ambil Foto (Kamera)", "Pilih dari Galeri (Lebih Tajam)")) { which ->
                    try {
                        if (which == 0) {
                            val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
                            activity.startActivityForResult(intent, REQ_CAMERA_PHOTO)
                        } else {
                            val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                                type = "image/*"
                            }
                            activity.startActivityForResult(intent, REQ_GALLERY_PHOTO)
                        }
                    } catch (e: Exception) {
                        Toast.makeText(activity, "Gagal membuka foto: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
        }
    }

    fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (resultCode != Activity.RESULT_OK) return
        if (requestCode != REQ_CAMERA_PHOTO && requestCode != REQ_GALLERY_PHOTO) return

        val bitmap: Bitmap? = try {
            if (requestCode == REQ_CAMERA_PHOTO) {
                @Suppress("DEPRECATION")
                data?.extras?.get("data") as? Bitmap
            } else {
                val uri = data?.data
                uri?.let {
                    activity.contentResolver.openInputStream(it)?.use { stream ->
                        BitmapFactory.decodeStream(stream)
                    }
                }
            }
        } catch (e: Exception) {
            notifyError("Gagal membaca gambar: ${e.message}")
            null
        }

        if (bitmap == null) {
            notifyError("Gambar tidak dapat dimuat.")
            return
        }

        val maxDim = 1280
        val scaled = if (bitmap.width > maxDim || bitmap.height > maxDim) {
            val scale = minOf(maxDim.toFloat() / bitmap.width, maxDim.toFloat() / bitmap.height)
            val w = (bitmap.width * scale).toInt().coerceAtLeast(1)
            val h = (bitmap.height * scale).toInt().coerceAtLeast(1)
            Bitmap.createScaledBitmap(bitmap, w, h, true)
        } else {
            bitmap
        }

        val baos = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 82, baos)
        val base64Jpeg = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
        requestGeminiVisionHomework(base64Jpeg)
    }

    private fun requestGeminiVisionHomework(base64Jpeg: String) {
        val apiKey = BuildConfig.GEMINI_API_KEY.trim()
        if (apiKey.isEmpty()) {
            notifyError("API Key Gemini belum diisi.")
            return
        }
        if (!isThinking.compareAndSet(false, true)) {
            notifyError("Tunggu bentar, masih mikir nih.")
            return
        }
        notifyUserMessage("[Foto Soal PR]")
        notifyThinking(true)
        Thread {
            try {
                val host = "generativelanguage." + "googleapis.com"
                val prompt = "Baca soal PR pada gambar ini dengan teliti, lalu kerjakan dan jelaskan jawabannya langkah demi langkah dengan jelas."
                val payload = JSONObject().apply {
                    put("systemInstruction", JSONObject().apply {
                        put("parts", JSONArray().put(JSONObject().put("text", buildSystemInstruction(ChatMode.HOMEWORK))))
                    })
                    put("contents", JSONArray().put(JSONObject().apply {
                        put("role", "user")
                        put("parts", JSONArray().apply {
                            put(JSONObject().put("inlineData", JSONObject().apply {
                                put("mimeType", "image/jpeg")
                                put("data", base64Jpeg)
                            }))
                            put(JSONObject().put("text", prompt))
                        })
                    }))
                    put("generationConfig", JSONObject().apply {
                        put("temperature", 0.5)
                        put("maxOutputTokens", 1024)
                    })
                }
              val modelsToTry = listOf(GEMINI_CHAT_MODEL, GEMINI_CHAT_MODEL, "gemini-3.8-flash")
                var code = 0
                var body = ""
                for ((index, model) in modelsToTry.withIndex()) {
                                 val url = URL("https://$host/v1beta/models/$model:generateContent?key=$apiKey")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                    connectTimeout = 15000
                    readTimeout = 30000
                    doOutput = true
                }
           conn.outputStream.use { os ->
                    os.write(payload.toString().toByteArray(Charsets.UTF_8))
                }
                code = conn.responseCode
                body = (if (code in 200..299) conn.inputStream else conn.errorStream)
                    ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                conn.disconnect()
                if (code in 200..299) { break }
                    if (code in setOf(500, 502, 503, 504) && index < modelsToTry.lastIndex) { Thread.sleep(1200); continue } 
                    break
                }
                    if (code !in 200..299) {
                   if (code == 503) notifyError("Server Gemini lagi rame banget nih (HTTP 503), coba tanya sekali lagi bentar lagi!") else notifyError("Gagal memproses foto PR (HTTP $code)")
                    return@Thread
                }
                val json = JSONObject(body)
                val replyText = json.optJSONArray("candidates")
                    ?.optJSONObject(0)
                    ?.optJSONObject("content")
                    ?.optJSONArray("parts")
                    ?.optJSONObject(0)
                    ?.optString("text")
                    ?.trim()
                    .orEmpty()
                if (replyText.isEmpty()) {
                    notifyError("Gemini tidak memberikan jawaban untuk foto PR.")
                    return@Thread
                }
                synchronized(chatHistory) {
                    chatHistory.add("user" to "[Foto Soal PR]")
                    chatHistory.add("model" to replyText)
                    while (chatHistory.size > MAX_HISTORY_TURNS) {
                        chatHistory.removeAt(0)
                    }
                }
                notifyReply(replyText)
                val spokenText = prepareSpokenText(replyText)
                val playedWithGemini = speakWithGeminiNaturalVoice(apiKey, spokenText)
                if (!playedWithGemini) {
                    speakWithFallbackTts(spokenText)
                }
            } catch (e: Exception) {
                notifyError("Error foto PR: ${e.message}")
            } finally {
                isThinking.set(false)
                notifyThinking(false)
            }
        }.start()
    }

    override fun copyLastReplyToClipboard() {
        activity.runOnUiThread {
            if (lastReplyText.isBlank()) {
                Toast.makeText(activity, "Belum ada jawaban untuk disalin.", Toast.LENGTH_SHORT).show()
                return@runOnUiThread
            }
            val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Jawaban Koceng Oren", lastReplyText)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(activity, "Jawaban berhasil disalin!", Toast.LENGTH_SHORT).show()
        }
    }

    override fun openChatHistoryDialog() {
        activity.runOnUiThread {
            val historyText = synchronized(chatHistory) {
                if (chatHistory.isEmpty()) {
                    "Belum ada riwayat obrolan."
                } else {
                    chatHistory.joinToString("\n\n") { (role, text) ->
                        val label = if (role == "user") "Kamu" else "Koceng Oren"
                        "$label:\n$text"
                    }
                }
            }
     val (card, content) = buildCartoonCardContainer("Riwayat Obrolan")
val textView = TextView(activity).apply {
text = historyText
setTextIsSelectable(true)
setTextColor(Color.parseColor("#52290F"))
textSize = 14f
setPadding(dp(12), dp(12), dp(12), dp(12))
}
val scrollView = ScrollView(activity).apply {
addView(textView)
background = GradientDrawable().apply {
cornerRadius = dp(16).toFloat()
setColor(Color.parseColor("#FFF5E4"))
setStroke(dp(1), Color.parseColor("#D8C3A5"))
}
layoutParams = LinearLayout.LayoutParams(
LinearLayout.LayoutParams.MATCH_PARENT,
dp(260)
).apply {
bottomMargin = dp(14)
}
}
content.addView(scrollView)
var dialogRef: AlertDialog? = null
val btnRow = LinearLayout(activity).apply {
orientation = LinearLayout.HORIZONTAL
layoutParams = LinearLayout.LayoutParams(
LinearLayout.LayoutParams.MATCH_PARENT,
LinearLayout.LayoutParams.WRAP_CONTENT
)
}
val copyBtn = createCartoonButton("Salin Terakhir", true) {
copyLastReplyToClipboard()
dialogRef?.dismiss()
}.apply {
layoutParams = LinearLayout.LayoutParams(0, dp(44), 1f).apply {
marginEnd = dp(8)
}
}
val closeBtn = createCartoonButton("Tutup", false) {
dialogRef?.dismiss()
}.apply {
layoutParams = LinearLayout.LayoutParams(0, dp(44), 1f).apply {
marginStart = dp(8)
}
}
btnRow.addView(copyBtn)
btnRow.addView(closeBtn)
content.addView(btnRow)
dialogRef = showCustomDialog(card)
        }
    }    override fun openSettingsDialog() {
activity.runOnUiThread {
val items = arrayOf("Ganti Suara Gemini", "Atur Level Ketus", "Ganti Tema Ruangan")
showCartoonOptionListDialog("Pengaturan Koceng Oren", items) { which ->
when (which) {
0 -> showVoicePickerDialog()
1 -> showAttitudePickerDialog()
2 -> showRoomThemePickerDialog()
}
}
}
}

private fun showVoicePickerDialog() {
val voices = arrayOf("Puck", "Fenrir", "Charon", "Aoede", "Kore")
val checked = voices.indexOf(selectedVoiceName).coerceAtLeast(0)
showCartoonOptionListDialog("Pilih Suara Gemini", voices, checked) { idx ->
val chosen = voices[idx]
selectedVoiceName = chosen
prefs.edit().putString("voice_name", chosen).apply()
Toast.makeText(activity, "Suara diganti ke $chosen", Toast.LENGTH_SHORT).show()
Thread {
val apiKey = BuildConfig.GEMINI_API_KEY.trim()
val sample = "Meow, ini suara $chosen."
val played = if (apiKey.isNotEmpty()) speakWithGeminiNaturalVoice(apiKey, sample) else false
if (!played) speakWithFallbackTts(sample)
}.start()
}
}

private fun showAttitudePickerDialog() {
val levels = arrayOf("Agak Cuek", "Ketus Standar", "Super Bar-Bar")
val checked = attitudeLevel.coerceIn(0, 2)
showCartoonOptionListDialog("Atur Level Ketus", levels, checked) { idx ->
attitudeLevel = idx
prefs.edit().putInt("attitude_level", idx).apply()
Toast.makeText(activity, "Level ketus: ${levels[idx]}", Toast.LENGTH_SHORT).show()
}
}

private fun showRoomThemePickerDialog() {
val themeLabels = arrayOf("Siang Cerah", "Malam Lampu Tidur", "Ruang Belajar Hangat")
val themes = arrayOf(RoomTheme.DAY, RoomTheme.NIGHT, RoomTheme.STUDY)
val current = getSavedRoomTheme()
val checked = themes.indexOf(current).coerceAtLeast(0)
showCartoonOptionListDialog("Ganti Tema Ruangan", themeLabels, checked) { idx ->
val chosenTheme = themes[idx]
prefs.edit().putString("room_theme", chosenTheme.name).apply()
Gdx.app?.postRunnable { onRoomThemeChanged?.invoke(chosenTheme) }
Toast.makeText(activity, "Tema: ${themeLabels[idx]}", Toast.LENGTH_SHORT).show()
}
}
}
