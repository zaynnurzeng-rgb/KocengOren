package com.koceng.oren.ui

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener
import com.badlogic.gdx.scenes.scene2d.utils.Drawable
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable
import com.badlogic.gdx.utils.Align
import com.badlogic.gdx.utils.Disposable
import com.badlogic.gdx.utils.viewport.ExtendViewport
import kotlin.math.sin
import kotlin.math.sqrt

enum class HudAiState {
    IDLE,
    LISTENING,
    THINKING,
    SPEAKING
}

class PetHud : Disposable {

    val stage: Stage = Stage(ExtendViewport(480f, 800f))
    private val managedTextures = mutableListOf<Texture>()

    // Fonts
    private val titleFont: BitmapFont
    private val labelFont: BitmapFont
    private val chatFont: BitmapFont
    private val smallFont: BitmapFont

    // Callbacks ke PetScreen
    var onTalkClicked: (() -> Unit)? = null
    var onHomeworkToggleClicked: (() -> Unit)? = null
    var onTypeClicked: (() -> Unit)? = null
    var onResetChatClicked: (() -> Unit)? = null

    // Top Status Pill
    private val statusTextLabel: Label

    // Scrollable Chat Box
    private val chatScrollPane: ScrollPane
    private val userMsgLabel: Label
    private val replyLabel: Label

    // Bottom Glossy Circular Buttons
    private val homeworkBtn: GlossyCircleButton
    private val homeworkLabel: Label
    private val talkBtn: GlossyCircleButton
    private val talkLabel: Label
    private val typeBtn: GlossyCircleButton

    // Textures untuk variasi state tombol
    private val outerRingTex: Texture
    private val activeRingTex: Texture
    private val hwIdleTex: Texture
    private val hwActiveTex: Texture
    private val micIdleTex: Texture
    private val micListenTex: Texture
    private val micThinkTex: Texture
    private val micSpeakTex: Texture

    private var isHomeworkActive: Boolean = false
    private var currentState: HudAiState = HudAiState.IDLE

    init {
        titleFont = createFont(1.30f)
        labelFont = createFont(1.18f)
        chatFont = createFont(1.16f)
        smallFont = createFont(0.98f)

        outerRingTex = createOuterRingTexture(Color.WHITE)
        activeRingTex = createOuterRingTexture(Color(1f, 0.86f, 0.25f, 1f))

        val titleStyle = Label.LabelStyle(titleFont, Color.WHITE)
        val labelStyle = Label.LabelStyle(labelFont, Color.WHITE)
        val smallStyle = Label.LabelStyle(smallFont, Color(0.86f, 0.94f, 1f, 0.95f))
        val userChatStyle = Label.LabelStyle(chatFont, Color(0.55f, 0.90f, 1f, 1f))
        val replyChatStyle = Label.LabelStyle(chatFont, Color(1f, 0.96f, 0.86f, 1f))

        // Glossy disc textures
        hwIdleTex = createGlossyDiscTexture(
            Color(0.98f, 0.72f, 0.20f, 1f),
            Color(0.80f, 0.46f, 0.08f, 1f),
            IconType.BOOK
        )
        hwActiveTex = createGlossyDiscTexture(
            Color(0.22f, 0.84f, 0.44f, 1f),
            Color(0.08f, 0.56f, 0.22f, 1f),
            IconType.BOOK
        )
        micIdleTex = createGlossyDiscTexture(
            Color(0.22f, 0.74f, 0.98f, 1f),
            Color(0.08f, 0.44f, 0.80f, 1f),
            IconType.MIC
        )
        micListenTex = createGlossyDiscTexture(
            Color(0.98f, 0.34f, 0.32f, 1f),
            Color(0.74f, 0.14f, 0.14f, 1f),
            IconType.MIC
        )
        micThinkTex = createGlossyDiscTexture(
            Color(0.98f, 0.64f, 0.18f, 1f),
            Color(0.78f, 0.38f, 0.06f, 1f),
            IconType.THINK
        )
        micSpeakTex = createGlossyDiscTexture(
            Color(0.20f, 0.82f, 0.46f, 1f),
            Color(0.08f, 0.56f, 0.26f, 1f),
            IconType.STOP
        )
        val typeTex = createGlossyDiscTexture(
            Color(0.74f, 0.42f, 0.96f, 1f),
            Color(0.46f, 0.20f, 0.76f, 1f),
            IconType.CHAT
        )
        val resetTex = createGlossyDiscTexture(
            Color(0.92f, 0.34f, 0.34f, 1f),
            Color(0.68f, 0.14f, 0.16f, 1f),
            IconType.RESET
        )

        val rootTable = Table().apply {
            setFillParent(true)
            pad(16f, 16f, 20f, 16f)
        }
        stage.addActor(rootTable)

        // ==========================================
        // 1. TOP BAR: Pill Nama & Status AI + Tombol Reset
        // ==========================================
        val topBar = Table()

        val headerBg = createRoundedRectDrawable(
            width = 300,
            height = 68,
            radius = 28,
            fillColor = Color(0.08f, 0.12f, 0.20f, 0.76f),
            borderColor = Color(1f, 1f, 1f, 0.45f),
            borderWidth = 2
        )
        val headerPill = Table().apply {
            background = headerBg
            pad(8f, 18f, 8f, 18f)
        }
        headerPill.add(Label("KOCENG OREN AI", titleStyle)).left().row()
        statusTextLabel = Label("Mode: Obrolan Santai", smallStyle)
        headerPill.add(statusTextLabel).left().padTop(1f)

        val resetBtn = GlossyCircleButton(outerRingTex, resetTex).apply {
            onClick = { onResetChatClicked?.invoke() }
        }
        val resetCol = Table().apply {
            add(resetBtn).size(56f).row()
            add(Label("Reset", smallStyle)).padTop(2f)
        }

        topBar.add(headerPill).left().top()
        topBar.add().expandX()
        topBar.add(resetCol).right().top()

        rootTable.add(topBar).expandX().fillX().top().row()

        // ==========================================
        // 2. RUANG TENGAH TERBUKA UNTUK KARAKTER 3D
        // ==========================================
        rootTable.add().expand().row()

        // ==========================================
        // 3. PANEL CHAT SCROLLABLE (Untuk Obrolan & Jawaban PR Panjang)
        // ==========================================
        val chatCardBg = createRoundedRectDrawable(
            width = 420,
            height = 180,
            radius = 24,
            fillColor = Color(0.07f, 0.10f, 0.17f, 0.84f),
            borderColor = Color(1f, 1f, 1f, 0.50f),
            borderWidth = 3
        )
        val chatContent = Table().apply {
            pad(8f, 12f, 8f, 12f)
            top().left()
        }

        userMsgLabel = Label("", userChatStyle).apply {
            setWrap(true)
            setAlignment(Align.left)
            isVisible = false
        }
        replyLabel = Label("Apaan sih manggil-manggil? Mau ngobrol atau nyuruh gue ngerjain PR lu lagi?", replyChatStyle).apply {
            setWrap(true)
            setAlignment(Align.left)
        }

        chatContent.add(userMsgLabel).width(392f).left().padBottom(6f).row()
        chatContent.add(replyLabel).width(392f).left()

        chatScrollPane = ScrollPane(chatContent, ScrollPane.ScrollPaneStyle()).apply {
            setScrollingDisabled(true, false)
            setFadeScrollBars(true)
            setOverscroll(false, true)
        }

        val chatBox = Table().apply {
            background = chatCardBg
            pad(10f, 12f, 10f, 12f)
            add(chatScrollPane).width(416f).maxHeight(168f).minHeight(78f)
        }

        rootTable.add(chatBox).center().padBottom(14f).row()

        // ==========================================
        // 4. BOTTOM DOCK: PR Sekolah | Bicara (Utama) | Ketik
        // ==========================================
        val bottomDock = Table()

        homeworkBtn = GlossyCircleButton(outerRingTex, hwIdleTex).apply {
            onClick = { onHomeworkToggleClicked?.invoke() }
        }
        homeworkLabel = Label("PR Sekolah", labelStyle)

        talkBtn = GlossyCircleButton(outerRingTex, micIdleTex).apply {
            onClick = { onTalkClicked?.invoke() }
        }
        talkLabel = Label("Bicara", labelStyle)

        typeBtn = GlossyCircleButton(outerRingTex, typeTex).apply {
            onClick = { onTypeClicked?.invoke() }
        }

        val leftCol = Table().apply {
            add(homeworkBtn).size(78f).row()
            add(homeworkLabel).padTop(5f)
        }
        val centerCol = Table().apply {
            add(talkBtn).size(98f).row()
            add(talkLabel).padTop(5f)
        }
        val rightCol = Table().apply {
            add(typeBtn).size(78f).row()
            add(Label("Ketik", labelStyle)).padTop(5f)
        }

        bottomDock.add(leftCol).expandX().bottom()
        bottomDock.add(centerCol).expandX().bottom()
        bottomDock.add(rightCol).expandX().bottom()

        rootTable.add(bottomDock).expandX().fillX().bottom()
    }

    fun setHomeworkMode(enabled: Boolean) {
        isHomeworkActive = enabled
        if (enabled) {
            homeworkBtn.outerTex = activeRingTex
            homeworkBtn.innerTex = hwActiveTex
            homeworkLabel.setText("PR: AKTIF")
            if (currentState == HudAiState.IDLE) {
                statusTextLabel.setText("Mode: PR Sekolah (Siap)")
            }
        } else {
            homeworkBtn.outerTex = outerRingTex
            homeworkBtn.innerTex = hwIdleTex
            homeworkLabel.setText("PR Sekolah")
            if (currentState == HudAiState.IDLE) {
                statusTextLabel.setText("Mode: Obrolan Santai")
            }
        }
    }

    fun setStatusState(state: HudAiState) {
        currentState = state
        when (state) {
            HudAiState.IDLE -> {
                talkBtn.innerTex = micIdleTex
                talkBtn.isPulsing = false
                talkLabel.setText("Bicara")
                statusTextLabel.setText(if (isHomeworkActive) "Mode: PR Sekolah (Siap)" else "Mode: Obrolan Santai")
            }
            HudAiState.LISTENING -> {
                talkBtn.innerTex = micListenTex
                talkBtn.isPulsing = true
                talkLabel.setText("Dengar...")
                statusTextLabel.setText("Lagi dengerin lu ngomong...")
            }
            HudAiState.THINKING -> {
                talkBtn.innerTex = micThinkTex
                talkBtn.isPulsing = true
                talkLabel.setText("Mikir...")
                statusTextLabel.setText(if (isHomeworkActive) "Lagi ngerjain PR lu..." else "Bentar, lagi mikir...")
            }
            HudAiState.SPEAKING -> {
                talkBtn.innerTex = micSpeakTex
                talkBtn.isPulsing = false
                talkLabel.setText("Stop")
                statusTextLabel.setText("Koceng Oren lagi ngomong...")
            }
        }
    }

    fun showUserMessage(text: String) {
        userMsgLabel.setText("Kamu: $text")
        userMsgLabel.isVisible = true
        chatScrollPane.scrollY = 0f
    }

    fun showReply(text: String) {
        replyLabel.setText(text)
        chatScrollPane.layout()
        chatScrollPane.scrollY = 0f
    }

    fun clearChat() {
        userMsgLabel.setText("")
        userMsgLabel.isVisible = false
        replyLabel.setText("Ingatan obrolan udah gue hapus! Mau bahas apa lagi sekarang?")
        chatScrollPane.scrollY = 0f
    }

    fun render(delta: Float) {
        stage.act(delta)
        stage.draw()
    }

    fun resize(width: Int, height: Int) {
        stage.viewport.update(width, height, true)
    }

    override fun dispose() {
        stage.dispose()
        titleFont.dispose()
        labelFont.dispose()
        chatFont.dispose()
        smallFont.dispose()
        managedTextures.forEach { it.dispose() }
        managedTextures.clear()
    }

    // ==========================================
    // Helper Font & Generator Tekstur UI Glossy
    // ==========================================
    private fun createFont(scale: Float): BitmapFont {
        return BitmapFont().apply {
            region.texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear)
            data.setScale(scale)
        }
    }

    private fun registerTexture(pm: Pixmap): Texture {
        val tex = Texture(pm).apply {
            setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear)
        }
        pm.dispose()
        managedTextures.add(tex)
        return tex
    }

    private fun createOuterRingTexture(ringColor: Color): Texture {
        val size = 160
        val cx = size / 2f
        val cy = size / 2f
        val pm = Pixmap(size, size, Pixmap.Format.RGBA8888)
        pm.blending = Pixmap.Blending.SourceOver

        for (y in 0 until size) {
            for (x in 0 until size) {
                val dx = x - cx
                val dy = y - (cy + 5f)
                val d = sqrt(dx * dx + dy * dy)
                if (d < 74f) {
                    val a = ((74f - d) / 14f).coerceIn(0f, 1f) * 0.40f
                    pm.drawPixel(x, y, Color.rgba8888(0.04f, 0.06f, 0.10f, a))
                }
            }
        }

        pm.setColor(0.12f, 0.15f, 0.22f, 0.92f)
        pm.fillCircle(size / 2, size / 2 - 2, 72)
        pm.setColor(ringColor)
        pm.fillCircle(size / 2, size / 2 - 2, 69)

        return registerTexture(pm)
    }

    private enum class IconType { MIC, BOOK, CHAT, RESET, THINK, STOP }

    private fun createGlossyDiscTexture(
        topColor: Color,
        bottomColor: Color,
        iconType: IconType
    ): Texture {
        val size = 160
        val cx = size / 2f
        val cy = size / 2f
        val radius = 74f
        val pm = Pixmap(size, size, Pixmap.Format.RGBA8888)
        pm.blending = Pixmap.Blending.SourceOver

        val c = Color()
        for (y in 0 until size) {
            for (x in 0 until size) {
                val dx = x - cx
                val dy = y - cy
                val d = sqrt(dx * dx + dy * dy)
                if (d <= radius) {
                    val t = (y.toFloat() / size).coerceIn(0f, 1f)
                    c.set(topColor).lerp(bottomColor, t)

                    // Kilau glossy di atas
                    val gx = dx / (radius * 0.72f)
                    val gy = (y - (cy - radius * 0.42f)) / (radius * 0.36f)
                    val gd = gx * gx + gy * gy
                    if (gd < 1f) {
                        c.lerp(Color.WHITE, (1f - gd) * 0.42f)
                    }

                    val alpha = (radius - d).coerceIn(0f, 1f)
                    pm.drawPixel(x, y, Color.rgba8888(c.r, c.g, c.b, alpha))
                }
            }
        }

        val ic = size / 2
        when (iconType) {
            IconType.MIC -> {
                pm.setColor(Color.WHITE)
                pm.fillCircle(ic, ic - 18, 13)
                pm.fillRectangle(ic - 13, ic - 18, 26, 26)
                pm.fillCircle(ic, ic + 8, 13)
                pm.fillRectangle(ic - 20, ic + 6, 5, 12)
                pm.fillRectangle(ic + 15, ic + 6, 5, 12)
                pm.fillRectangle(ic - 20, ic + 16, 40, 5)
                pm.fillRectangle(ic - 3, ic + 20, 6, 14)
                pm.fillRectangle(ic - 14, ic + 32, 28, 5)
            }
            IconType.BOOK -> {
                // Ikon buku terbuka untuk PR Sekolah
                pm.setColor(Color.WHITE)
                pm.fillRectangle(ic - 30, ic - 22, 27, 44)
                pm.fillRectangle(ic + 3, ic - 22, 27, 44)
                pm.fillCircle(ic, ic + 20, 6)
                pm.setColor(bottomColor)
                pm.fillRectangle(ic - 24, ic - 12, 16, 4)
                pm.fillRectangle(ic - 24, ic - 2, 16, 4)
                pm.fillRectangle(ic - 24, ic + 8, 12, 4)
                pm.fillRectangle(ic + 8, ic - 12, 16, 4)
                pm.fillRectangle(ic + 8, ic - 2, 16, 4)
                pm.fillRectangle(ic + 8, ic + 8, 12, 4)
            }
            IconType.CHAT -> {
                // Ikon balon ketik pesan
                pm.setColor(Color.WHITE)
                pm.fillCircle(ic - 10, ic - 4, 21)
                pm.fillCircle(ic + 12, ic - 4, 18)
                pm.fillRectangle(ic - 10, ic - 22, 24, 36)
                pm.fillTriangle(ic - 16, ic + 12, ic - 26, ic + 28, ic - 2, ic + 16)
                pm.setColor(bottomColor)
                pm.fillCircle(ic - 10, ic - 4, 4)
                pm.fillCircle(ic + 2, ic - 4, 4)
                pm.fillCircle(ic + 14, ic - 4, 4)
            }
            IconType.THINK -> {
                // Ikon 3 titik berpikir
                pm.setColor(Color.WHITE)
                pm.fillCircle(ic - 22, ic, 8)
                pm.fillCircle(ic, ic, 8)
                pm.fillCircle(ic + 22, ic, 8)
            }
            IconType.STOP -> {
                // Ikon kotak stop
                pm.setColor(Color.WHITE)
                pm.fillRectangle(ic - 18, ic - 18, 36, 36)
            }
            IconType.RESET -> {
                pm.setColor(Color.WHITE)
                pm.fillCircle(ic, ic, 26)
                pm.setColor(topColor.cpy().lerp(bottomColor, 0.5f))
                pm.fillCircle(ic, ic, 17)
                pm.fillRectangle(ic, ic - 30, 30, 26)
                pm.setColor(Color.WHITE)
                pm.fillTriangle(ic + 4, ic - 30, ic + 24, ic - 16, ic + 4, ic - 4)
            }
        }

        return registerTexture(pm)
    }

    private fun createRoundedRectDrawable(
        width: Int,
        height: Int,
        radius: Int,
        fillColor: Color,
        borderColor: Color,
        borderWidth: Int
    ): Drawable {
        val pm = Pixmap(width, height, Pixmap.Format.RGBA8888)
        pm.blending = Pixmap.Blending.SourceOver

        fun drawRounded(x: Int, y: Int, w: Int, h: Int, r: Int, col: Color) {
            val rad = r.coerceAtMost(minOf(w, h) / 2)
            pm.setColor(col)
            pm.fillRectangle(x + rad, y, w - 2 * rad, h)
            pm.fillRectangle(x, y + rad, w, h - 2 * rad)
            pm.fillCircle(x + rad, y + rad, rad)
            pm.fillCircle(x + w - rad - 1, y + rad, rad)
            pm.fillCircle(x + rad, y + h - rad - 1, rad)
            pm.fillCircle(x + w - rad - 1, y + h - rad - 1, rad)
        }

        drawRounded(0, 0, width, height, radius, borderColor)
        drawRounded(
            borderWidth,
            borderWidth,
            width - borderWidth * 2,
            height - borderWidth * 2,
            (radius - borderWidth).coerceAtLeast(2),
            fillColor
        )

        return TextureRegionDrawable(TextureRegion(registerTexture(pm)))
    }

    // ==========================================
    // Custom Actor: Tombol Bulat Glossy dengan Efek Tekan & Pulse
    // ==========================================
    private class GlossyCircleButton(
        var outerTex: Texture,
        var innerTex: Texture
    ) : Actor() {

        var onClick: (() -> Unit)? = null
        var isPulsing: Boolean = false

        private var pressScale: Float = 1f
        private var pulseTime: Float = 0f
        private val clickListener: ClickListener

        init {
            clickListener = object : ClickListener() {
                override fun clicked(event: InputEvent?, x: Float, y: Float) {
                    onClick?.invoke()
                }
            }
            addListener(clickListener)
        }

        override fun act(delta: Float) {
            super.act(delta)
            val targetScale = if (clickListener.isPressed) 0.90f else 1.0f
            pressScale = MathUtils.lerp(pressScale, targetScale, delta * 18f)
            if (isPulsing) {
                pulseTime += delta * 6f
            } else {
                pulseTime = 0f
            }
        }

        override fun draw(batch: Batch, parentAlpha: Float) {
            val pulseBoost = if (isPulsing) 1f + 0.05f * sin(pulseTime) else 1f
            val size = minOf(width, height) * pressScale * pulseBoost
            val cx = x + width * 0.5f
            val cy = y + height * 0.5f

            batch.setColor(1f, 1f, 1f, color.a * parentAlpha)
            batch.draw(outerTex, cx - size * 0.5f, cy - size * 0.5f, size, size)

            val innerSize = size * 0.82f
            batch.draw(
                innerTex,
                cx - innerSize * 0.5f,
                cy + size * 0.012f - innerSize * 0.5f,
                innerSize,
                innerSize
            )
        }
    }
}
