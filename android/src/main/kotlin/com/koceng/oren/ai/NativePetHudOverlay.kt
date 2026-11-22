package com.koceng.oren.ai

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class NativePetHudOverlay(
    private val activity: Activity,
    private val bridge: AndroidAiChatBridge
) {

    private companion object {
        val COLOR_ORANGE = Color.parseColor("#FF8C1A")
        val COLOR_ORANGE_LIGHT = Color.parseColor("#FFE2B8")
        val COLOR_CREAM = Color.parseColor("#FFFBF0")
        val COLOR_TAN = Color.parseColor("#EFE4D4")
        val COLOR_BROWN = Color.parseColor("#52290F")
        val COLOR_BROWN_MUTED = Color.parseColor("#7A4A28")
        val COLOR_WHITE = Color.parseColor("#FFFFFF")
        val COLOR_ACTIVE_GREEN = Color.parseColor("#2E9E5B")
        val COLOR_ACTIVE_RED = Color.parseColor("#E04F33")
    }

    private var rootView: FrameLayout? = null
    private var statusTextView: TextView? = null
    private var userMessageView: TextView? = null
    private var replyMessageView: TextView? = null
    private var bubbleScrollView: ScrollView? = null

    private var homeworkButton: TextView? = null
    private var photoButton: TextView? = null
    private var voiceButton: TextView? = null
    private var typeButton: TextView? = null

    private var homeworkActive = false
    private var isListening = false
    private var isThinking = false
    private var isSpeaking = false
    private var currentUserMsg: String? = null

    fun createView(): View {
        val root = FrameLayout(activity).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            isClickable = false
            isFocusable = false
            setPadding(dp(12), dp(10), dp(12), dp(14))
        }

        val topSection = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP
            )
        }

        topSection.addView(buildTopBar())
        root.addView(topSection)

        val bottomContainer = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM
            )
        }
        val bubbleCard = buildComicBubble().apply {
            (layoutParams as LinearLayout.LayoutParams).bottomMargin = dp(10)
        }
        bottomContainer.addView(bubbleCard)
        bottomContainer.addView(buildBottomBar())
        root.addView(bottomContainer)

        rootView = root
        refreshHomeworkButton()
        refreshVoiceButton()
        refreshStatusLabel()
        return root
    }

    private fun buildTopBar(): LinearLayout {
        val bar = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = cartoonDrawable(COLOR_ORANGE, COLOR_BROWN, 18, 2)
            setPadding(dp(14), dp(8), dp(10), dp(8))
            elevation = dp(4).toFloat()
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(8)
            }
        }

        val titleView = TextView(activity).apply {
            text = "KOCENG OREN"
            setTextColor(COLOR_WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.04f
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            )
        }
        bar.addView(titleView)

        val historyBtn = createCompactTopButton("Riwayat") {
            bridge.openChatHistoryDialog()
        }
        val settingsBtn = createCompactTopButton("Atur") {
            bridge.openSettingsDialog()
        }
        val resetBtn = createCompactTopButton("Reset") {
            bridge.resetChatHistory()
            currentUserMsg = null
            userMessageView?.visibility = View.GONE
            replyMessageView?.text = "Meow~ Siap ngobrol lagi!"
            refreshStatusLabel()
        }

        bar.addView(historyBtn)
        bar.addView(settingsBtn)
        bar.addView(resetBtn)
        return bar
    }

    private fun createCompactTopButton(label: String, onClick: () -> Unit): TextView {
        return TextView(activity).apply {
            text = label
            setTextColor(COLOR_BROWN)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            background = cartoonDrawable(COLOR_CREAM, COLOR_BROWN, 999, 2)
            setPadding(dp(10), dp(5), dp(10), dp(5))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                leftMargin = dp(6)
            }
            setOnClickListener { onClick() }
        }
    }

    private fun buildComicBubble(): LinearLayout {
        val bubble = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            background = cartoonDrawable(COLOR_CREAM, COLOR_BROWN, 20, 2)
            setPadding(dp(14), dp(10), dp(14), dp(12))
            elevation = dp(4).toFloat()
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val headerRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(6)
            }
        }

        val status = TextView(activity).apply {
            text = "Siap membantu"
            setTextColor(COLOR_BROWN_MUTED)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            )
        }
        statusTextView = status
        headerRow.addView(status)

        val copyBtn = TextView(activity).apply {
            text = "Salin"
            setTextColor(COLOR_WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            background = cartoonDrawable(COLOR_ORANGE, COLOR_BROWN, 999, 2)
            setPadding(dp(12), dp(4), dp(12), dp(4))
            setOnClickListener {
                bridge.copyLastReplyToClipboard()
            }
        }
        headerRow.addView(copyBtn)
        bubble.addView(headerRow)

        val scroll = ScrollView(activity).apply {
            isVerticalScrollBarEnabled = true
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(92)
            )
        }

        val contentCol = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val userTv = TextView(activity).apply {
            visibility = View.GONE
            setTextColor(COLOR_BROWN_MUTED)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, dp(4))
        }
        userMessageView = userTv
        contentCol.addView(userTv)

        val replyTv = TextView(activity).apply {
            text = "Meow~ Ada yang bisa Koceng bantu hari ini?"
            setTextColor(COLOR_BROWN)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setLineSpacing(dp(2).toFloat(), 1.1f)
        }
        replyMessageView = replyTv
        contentCol.addView(replyTv)

        scroll.addView(contentCol)
        bubbleScrollView = scroll
        bubble.addView(scroll)

        return bubble
    }

    private fun buildBottomBar(): LinearLayout {
        val bar = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            weightSum = 4f
        }

        val hwBtn = createBottomButton("PR Sekolah") {
            val next = !homeworkActive
            setHomeworkActive(next)
            bridge.setChatMode(if (next) ChatMode.HOMEWORK else ChatMode.CHAT)
        }
        homeworkButton = hwBtn

        val photoBtn = createBottomButton("Foto PR") {
            bridge.openPhotoHomeworkPicker()
        }
        photoButton = photoBtn

        val voiceBtn = createBottomButton("Bicara") {
            if (isListening || bridge.isBusy()) {
                bridge.stopAll()
            } else {
                bridge.startListening()
            }
        }
        voiceButton = voiceBtn

        val ketikBtn = createBottomButton("Ketik") {
            bridge.openTextInputDialog(homeworkActive)
        }
        typeButton = ketikBtn

        bar.addView(hwBtn)
        bar.addView(photoBtn)
        bar.addView(voiceBtn)
        bar.addView(ketikBtn)
        return bar
    }

    private fun createBottomButton(label: String, onClick: () -> Unit): TextView {
        return TextView(activity).apply {
            text = label
            setTextColor(COLOR_BROWN)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            background = cartoonDrawable(COLOR_CREAM, COLOR_BROWN, 999, 2)
            elevation = dp(4).toFloat()
            setPadding(dp(8), dp(12), dp(8), dp(12))
            layoutParams = LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            ).apply {
                val margin = dp(4)
                setMargins(margin, 0, margin, 0)
            }
            setOnClickListener { onClick() }
        }
    }

    fun setHomeworkActive(active: Boolean) {
        activity.runOnUiThread {
            homeworkActive = active
            refreshHomeworkButton()
            refreshStatusLabel()
        }
    }

    fun updateUserMessage(msg: String) {
        activity.runOnUiThread {
            currentUserMsg = msg
            if (msg.isNotBlank()) {
                userMessageView?.text = "Kamu: $msg"
                userMessageView?.visibility = View.VISIBLE
            } else {
                userMessageView?.visibility = View.GONE
            }
            scrollBubbleToBottom()
        }
    }

    fun updateBubble(userMsg: String?, reply: String) {
        activity.runOnUiThread {
            if (!userMsg.isNullOrBlank()) {
                currentUserMsg = userMsg
                userMessageView?.text = "Kamu: $userMsg"
                userMessageView?.visibility = View.VISIBLE
            } else if (currentUserMsg.isNullOrBlank()) {
                userMessageView?.visibility = View.GONE
            }
            replyMessageView?.text = reply
            scrollBubbleToBottom()
        }
    }

    fun setListening(listening: Boolean) {
        activity.runOnUiThread {
            isListening = listening
            refreshVoiceButton()
            refreshStatusLabel()
        }
    }

    fun setThinking(thinking: Boolean) {
        activity.runOnUiThread {
            isThinking = thinking
            refreshStatusLabel()
        }
    }

    fun setSpeaking(speaking: Boolean) {
        activity.runOnUiThread {
            isSpeaking = speaking
            refreshStatusLabel()
        }
    }

    private fun refreshHomeworkButton() {
        homeworkButton?.apply {
            if (homeworkActive) {
                background = cartoonDrawable(COLOR_ORANGE, COLOR_BROWN, 999, 2)
                setTextColor(COLOR_WHITE)
            } else {
                background = cartoonDrawable(COLOR_CREAM, COLOR_BROWN, 999, 2)
                setTextColor(COLOR_BROWN)
            }
        }
    }

    private fun refreshVoiceButton() {
        voiceButton?.apply {
            if (isListening) {
                text = "Stop"
                background = cartoonDrawable(COLOR_ACTIVE_RED, COLOR_BROWN, 999, 2)
                setTextColor(COLOR_WHITE)
            } else {
                text = "Bicara"
                background = cartoonDrawable(COLOR_ORANGE, COLOR_BROWN, 999, 2)
                setTextColor(COLOR_WHITE)
            }
        }
    }

    private fun refreshStatusLabel() {
        val status = when {
            isListening -> "Mendengarkan..."
            isThinking -> "Koceng lagi mikir..."
            isSpeaking -> "Koceng berbicara..."
            homeworkActive -> "Mode PR Sekolah aktif"
            else -> "Siap membantu"
        }
        statusTextView?.text = status
        statusTextView?.setTextColor(
            when {
                isListening -> COLOR_ACTIVE_RED
                isThinking -> COLOR_ORANGE
                isSpeaking -> COLOR_ACTIVE_GREEN
                homeworkActive -> COLOR_ORANGE
                else -> COLOR_BROWN_MUTED
            }
        )
    }

    private fun scrollBubbleToBottom() {
        bubbleScrollView?.post {
            bubbleScrollView?.fullScroll(View.FOCUS_DOWN)
        }
    }

    private fun cartoonDrawable(
        fillColor: Int,
        strokeColor: Int,
        radiusDp: Int,
        strokeDp: Int
    ): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(fillColor)
            cornerRadius = dp(radiusDp).toFloat()
            setStroke(dp(strokeDp), strokeColor)
        }
    }

    private fun dp(value: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            activity.resources.displayMetrics
        ).toInt()
    }
}
