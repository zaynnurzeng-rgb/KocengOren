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
import kotlin.math.abs
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
    private val titleFont: BitmapFont = createFont(1.20f)
    private val labelFont: BitmapFont = createFont(1.16f)
    private val chatFont: BitmapFont = createFont(1.18f)
    private val smallFont: BitmapFont = createFont(0.90f)

    // Callbacks ke PetScreen
    var onTalkClicked: (() -> Unit)? = null
    var onHomeworkToggleClicked: (() -> Unit)? = null
    var onTypeClicked: (() -> Unit)? = null
    var onResetChatClicked: (() -> Unit)? = null
    var onSettingsClicked: (() -> Unit)? = null
        var onHistoryClicked: (() -> Unit)? = null
    var onCopyClicked: (() -> Unit)? = null
    var onPhotoHomeworkClicked: (() -> Unit)? = null
    // Top Status Badge
    private val statusTextLabel: Label

    // Comic Speech Bubble + Pop Animation
    private val speechBubbleContainer: Table
    private val chatScrollPane: ScrollPane
    private val userMsgLabel: Label
    private val replyLabel: Label
    private var bubblePopTimer: Float = 0f

    // Bottom Chunky 3D Cartoon Buttons
    private val homeworkBtn: CartoonCircleButton
    private val homeworkLabel: Label
    private val talkBtn: CartoonCircleButton
    private val talkLabel: Label
    private val typeBtn: CartoonCircleButton

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
        val comicOutline = Color(0.32f, 0.16f, 0.06f, 1f)
        outerRingTex = createCartoonOuterRingTexture(Color(1f, 0.98f, 0.94f, 1f), comicOutline)
        activeRingTex = createCartoonOuterRingTexture(Color(1f, 0.90f, 0.28f, 1f), comicOutline)

        val titleStyle = Label.LabelStyle(titleFont, Color.WHITE)
        val pillLabelStyle = Label.LabelStyle(labelFont, comicOutline)
        val statusSubStyle = Label.LabelStyle(smallFont, Color(1f, 0.97f, 0.82f, 1f))
        val userChatStyle = Label.LabelStyle(chatFont, Color(0.12f, 0.42f, 0.86f, 1f))
        val replyChatStyle = Label.LabelStyle(chatFont, Color(0.22f, 0.11f, 0.04f, 1f))

        // Glossy cartoon disc textures
        hwIdleTex = createGlossyDiscTexture(
            Color(1.00f, 0.78f, 0.18f, 1f),
            Color(0.92f, 0.48f, 0.06f, 1f),
            IconType.BOOK
        )
        hwActiveTex = createGlossyDiscTexture(
            Color(0.34f, 0.92f, 0.36f, 1f),
            Color(0.10f, 0.64f, 0.18f, 1f),
            IconType.BOOK
        )
        micIdleTex = createGlossyDiscTexture(
            Color(1.00f, 0.60f, 0.10f, 1f),
            Color(0.90f, 0.36f, 0.00f, 1f),
            IconType.MIC
        )
        micListenTex = createGlossyDiscTexture(
            Color(1.00f, 0.36f, 0.34f, 1f),
            Color(0.80f, 0.14f, 0.16f, 1f),
            IconType.MIC
        )
        micThinkTex = createGlossyDiscTexture(
            Color(1.00f, 0.68f, 0.16f, 1f),
            Color(0.84f, 0.40f, 0.04f, 1f),
            IconType.THINK
        )
        micSpeakTex = createGlossyDiscTexture(
            Color(0.26f, 0.88f, 0.46f, 1f),
            Color(0.08f, 0.60f, 0.24f, 1f),
            IconType.STOP
        )
        val typeTex = createGlossyDiscTexture(
            Color(0.86f, 0.42f, 0.82f, 1f),
            Color(0.58f, 0.18f, 0.56f, 1f),
            IconType.CHAT
        )
        val resetTex = createGlossyDiscTexture(
            Color(1.00f, 0.48f, 0.40f, 1f),
            Color(0.84f, 0.24f, 0.18f, 1f),
            IconType.RESET
        )
        val photoTex = createGlossyDiscTexture(Color(0.20f, 0.86f, 0.78f, 1f), Color(0.06f, 0.58f, 0.52f, 1f), IconType.CAMERA)
                val historyTex = createGlossyDiscTexture(Color(1.00f, 0.90f, 0.68f, 1f), Color(0.94f, 0.66f, 0.24f, 1f), IconType.HISTORY)
                        val settingsTex = createGlossyDiscTexture(Color(1.00f, 0.78f, 0.24f, 1f), Color(0.92f, 0.50f, 0.08f, 1f), IconType.SETTINGS)
        val rootTable = Table().apply {
            setFillParent(true)
            pad(14f, 16f, 18f, 16f)
        }
        stage.addActor(rootTable)

        // ==========================================
        // 1. TOP BAR: Badge Kartun Oranye Glossy + Tombol Reset Chunky
        // ==========================================
        val topBar = Table()

        val headerBg = createCartoonPillDrawable(
            width = 300,
            height = 72,
            radius = 30,
            topColor = Color(1.00f, 0.66f, 0.14f, 1f),
            bottomColor = Color(0.92f, 0.42f, 0.06f, 1f),
            outlineColor = comicOutline,
            outlineWidth = 4
        )
        val headerPill = Table().apply {
            background = headerBg
            pad(8f, 14f, 12f, 14f)
        }
        headerPill.add(Label("KOCENG OREN", titleStyle)).left().row()
        statusTextLabel = Label("Obrolan Santai", statusSubStyle)
        headerPill.add(statusTextLabel).left().padTop(-1f)

        val labelPillBg = createCartoonPillDrawable(
            width = 120,
            height = 32,
            radius = 15,
            topColor = Color(1f, 0.98f, 0.94f, 1f),
            bottomColor = Color(0.98f, 0.92f, 0.80f, 1f),
            outlineColor = comicOutline,
            outlineWidth = 3
        )

        val resetBtn = CartoonCircleButton(outerRingTex, resetTex).apply {
            onClick = { onResetChatClicked?.invoke() }
        }
        val resetLabelPill = Table().apply {
            background = labelPillBg
            pad(2f, 6f, 4f, 6f)
            add(Label("Reset", Label.LabelStyle(smallFont, comicOutline)))
        }
        val resetCol = Table().apply {
            add(resetBtn).size(58f).row()
            add(resetLabelPill).padTop(3f)
        }
        val historyBtn = CartoonCircleButton(outerRingTex, historyTex).apply { onClick = { onHistoryClicked?.invoke() } }; val historyLabelPill = Table().apply { background = labelPillBg; pad(2f, 6f, 4f, 6f); add(Label("Riwayat", Label.LabelStyle(smallFont, comicOutline))) }; val historyCol = Table().apply { add(historyBtn).size(44f).row(); add(historyLabelPill).padTop(3f) }; val settingsBtn = CartoonCircleButton(outerRingTex, settingsTex).apply { onClick = { onSettingsClicked?.invoke() } }; val settingsLabelPill = Table().apply { background = labelPillBg; pad(2f, 6f, 4f, 6f); add(Label("Atur", Label.LabelStyle(smallFont, comicOutline))) }; val settingsCol = Table().apply { add(settingsBtn).size(44f).row(); add(settingsLabelPill).padTop(3f) }; resetCol.clearChildren(); resetCol.add(resetBtn).size(44f).row(); resetCol.add(resetLabelPill).padTop(3f); val topRightRow = Table().apply { add(historyCol).padRight(6f); add(settingsCol).padRight(6f); add(resetCol) }
        topBar.add(headerPill).left().top()
        topBar.add().expandX()
        topBar.add(resetCol).right().top()
        topBar.clearChildren(); topBar.add(headerPill).width(205f).left().top(); topBar.add().expandX(); topBar.add(topRightRow).right().top()
        
        rootTable.add(topBar).expandX().fillX().top().row()

        // ==========================================
        // 2. RUANG TENGAH LEGA UNTUK KARAKTER 3D
        // ==========================================
        rootTable.add().expand().row()

        // ==========================================
        // 3. BALON KOMIK KARTUN (Dengan Ekor Panah ke Atas & Pop Bounce)
        // ==========================================
        val bubbleBg = createComicSpeechBubbleDrawable(
            width = 432,
            height = 166,
            radius = 28,
            tailHeight = 16,
            fillColor = Color(1.00f, 0.99f, 0.95f, 0.97f),
            outlineColor = comicOutline,
            outlineWidth = 5
        )
        val chatContent = Table().apply {
            pad(6f, 12f, 6f, 12f)
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

        chatContent.add(userMsgLabel).width(386f).left().padBottom(5f).row()
        chatContent.add(replyLabel).width(386f).left()

        chatScrollPane = ScrollPane(chatContent, ScrollPane.ScrollPaneStyle()).apply {
            setScrollingDisabled(true, false)
            setFadeScrollBars(true)
            setOverscroll(false, true)
        }

        speechBubbleContainer = Table().apply {
            background = bubbleBg
            isTransform = true
            pad(24f, 16f, 14f, 16f)
            add(chatScrollPane).width(406f).maxHeight(124f).minHeight(60f)
        }
        val copyPill = Table().apply { background = createCartoonPillDrawable(width = 120, height = 32, radius = 15, topColor = Color(1f, 0.62f, 0.14f, 1f), bottomColor = Color(0.92f, 0.42f, 0.06f, 1f), outlineColor = comicOutline, outlineWidth = 3); pad(3f, 12f, 5f, 12f); add(Label("Salin", Label.LabelStyle(smallFont, Color.WHITE))); touchable = com.badlogic.gdx.scenes.scene2d.Touchable.enabled; addListener(object : com.badlogic.gdx.scenes.scene2d.utils.ClickListener() { override fun clicked(event: com.badlogic.gdx.scenes.scene2d.InputEvent?, x: Float, y: Float) { onCopyClicked?.invoke() } }) }; speechBubbleContainer.row(); speechBubbleContainer.add(copyPill).right().padTop(4f)
        rootTable.add(speechBubbleContainer).center().padBottom(12f).row()

        // ==========================================
        // 4. BOTTOM DOCK: 3 Tombol Kartun 3D Chunky + Label Pill Tegas
        // ==========================================
        val bottomDock = Table()

        homeworkBtn = CartoonCircleButton(outerRingTex, hwIdleTex).apply {
            onClick = { onHomeworkToggleClicked?.invoke() }
        }
        homeworkLabel = Label("PR Sekolah", pillLabelStyle)

        talkBtn = CartoonCircleButton(outerRingTex, micIdleTex, idleFloat = true).apply {
            onClick = { onTalkClicked?.invoke() }
        }
        talkLabel = Label("Bicara", pillLabelStyle)

        typeBtn = CartoonCircleButton(outerRingTex, typeTex).apply {
            onClick = { onTypeClicked?.invoke() }
        }

        fun wrapLabelPill(lbl: Label): Table = Table().apply {
            background = labelPillBg
            pad(3f, 12f, 5f, 12f)
            add(lbl)
        }

        val leftCol = Table().apply {
            add(homeworkBtn).size(82f).row()
            add(wrapLabelPill(homeworkLabel)).padTop(4f)
        }
        val centerCol = Table().apply {
            add(talkBtn).size(104f).row()
            add(wrapLabelPill(talkLabel)).padTop(4f)
        }
        val rightCol = Table().apply {
            add(typeBtn).size(82f).row()
            add(wrapLabelPill(Label("Ketik", pillLabelStyle))).padTop(4f)
        }

        bottomDock.add(leftCol).expandX().bottom()
        bottomDock.add(centerCol).expandX().bottom()
        bottomDock.add(rightCol).expandX().bottom()
        val photoBtn = CartoonCircleButton(outerRingTex, photoTex).apply { onClick = { onPhotoHomeworkClicked?.invoke() } }; val photoCol = Table().apply { add(photoBtn).size(72f).row(); add(wrapLabelPill(Label("Foto PR", pillLabelStyle))).padTop(4f) }; leftCol.clearChildren(); leftCol.add(homeworkBtn).size(72f).row(); leftCol.add(wrapLabelPill(homeworkLabel)).padTop(4f); centerCol.clearChildren(); centerCol.add(talkBtn).size(96f).row(); centerCol.add(wrapLabelPill(talkLabel)).padTop(4f); rightCol.clearChildren(); rightCol.add(typeBtn).size(72f).row(); rightCol.add(wrapLabelPill(Label("Ketik", pillLabelStyle))).padTop(4f); bottomDock.clearChildren(); bottomDock.add(leftCol).expandX().bottom(); bottomDock.add(photoCol).expandX().bottom(); bottomDock.add(centerCol).expandX().bottom(); bottomDock.add(rightCol).expandX().bottom()
        rootTable.add(bottomDock).expandX().fillX().bottom()
    }

    fun setHomeworkMode(enabled: Boolean) {
        isHomeworkActive = enabled
        if (enabled) {
            homeworkBtn.outerTex = activeRingTex
            homeworkBtn.innerTex = hwActiveTex
            homeworkLabel.setText("PR: AKTIF")
            if (currentState == HudAiState.IDLE) {
                statusTextLabel.setText("Mode PR Sekolah!")
            }
        } else {
            homeworkBtn.outerTex = outerRingTex
            homeworkBtn.innerTex = hwIdleTex
            homeworkLabel.setText("PR Sekolah")
            if (currentState == HudAiState.IDLE) {
                statusTextLabel.setText("Obrolan Santai")
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
                statusTextLabel.setText(if (isHomeworkActive) "Mode PR Sekolah!" else "Obrolan Santai")
            }
            HudAiState.LISTENING -> {
                talkBtn.innerTex = micListenTex
                talkBtn.isPulsing = true
                talkLabel.setText("Dengar...")
                statusTextLabel.setText("Lagi Dengerin...")
            }
            HudAiState.THINKING -> {
                talkBtn.innerTex = micThinkTex
                talkBtn.isPulsing = true
                talkLabel.setText("Mikir...")
                statusTextLabel.setText(if (isHomeworkActive) "Ngerjain PR..." else "Lagi Mikir...")
            }
            HudAiState.SPEAKING -> {
                talkBtn.innerTex = micSpeakTex
                talkBtn.isPulsing = true
                talkLabel.setText("Stop")
                statusTextLabel.setText("Lagi Ngomong...")
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
        bubblePopTimer = 0.28f
    }

    fun clearChat() {
        userMsgLabel.setText("")
        userMsgLabel.isVisible = false
        replyLabel.setText("Ingatan obrolan udah gue hapus! Mau bahas apa lagi sekarang?")
        chatScrollPane.scrollY = 0f
        bubblePopTimer = 0.28f
    }

    fun render(delta: Float) {
        if (bubblePopTimer > 0f) {
            bubblePopTimer = (bubblePopTimer - delta).coerceAtLeast(0f)
            val progress = 1f - (bubblePopTimer / 0.28f)
            val overshoot = 1f + sin(progress * MathUtils.PI) * 0.06f
            speechBubbleContainer.setOrigin(Align.center)
            speechBubbleContainer.setScale(overshoot)
        } else if (speechBubbleContainer.scaleX != 1f) {
            speechBubbleContainer.setScale(1f)
        }

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
    // Helper Font & Generator Tekstur Kartun Playful
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

    private fun createCartoonOuterRingTexture(ringColor: Color, outlineColor: Color): Texture {
        val size = 168
        val pm = Pixmap(size, size, Pixmap.Format.RGBA8888)
        pm.blending = Pixmap.Blending.SourceOver

        // Bayangan 3D chunky di bawah tombol
        pm.setColor(outlineColor.r, outlineColor.g, outlineColor.b, 0.85f)
        pm.fillCircle(size / 2, size / 2 + 6, 74)

        // Outline komik tebal + bingkai putih cerah
        pm.setColor(outlineColor)
        pm.fillCircle(size / 2, size / 2 - 2, 74)
        pm.setColor(ringColor)
        pm.fillCircle(size / 2, size / 2 - 2, 69)

        return registerTexture(pm)
    }

    private enum class IconType { MIC, BOOK, CHAT, RESET, THINK, STOP, CAMERA, HISTORY, SETTINGS }

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

                    // Kilau glossy kartun besar di atas
                    val gx = dx / (radius * 0.72f)
                    val gy = (y - (cy - radius * 0.44f)) / (radius * 0.34f)
                    val gd = gx * gx + gy * gy
                    if (gd < 1f) {
                        c.lerp(Color.WHITE, (1f - gd) * 0.52f)
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
                pm.fillCircle(ic, ic - 18, 14)
                pm.fillRectangle(ic - 14, ic - 18, 28, 26)
                pm.fillCircle(ic, ic + 8, 14)
                pm.fillRectangle(ic - 22, ic + 6, 6, 13)
                pm.fillRectangle(ic + 16, ic + 6, 6, 13)
                pm.fillRectangle(ic - 22, ic + 16, 44, 6)
                pm.fillRectangle(ic - 4, ic + 20, 8, 14)
                pm.fillRectangle(ic - 16, ic + 32, 32, 6)
            }
            IconType.BOOK -> {
                pm.setColor(Color.WHITE)
                pm.fillRectangle(ic - 31, ic - 22, 28, 44)
                pm.fillRectangle(ic + 3, ic - 22, 28, 44)
                pm.fillCircle(ic, ic + 20, 6)
                pm.setColor(bottomColor)
                pm.fillRectangle(ic - 25, ic - 12, 16, 4)
                pm.fillRectangle(ic - 25, ic - 2, 16, 4)
                pm.fillRectangle(ic - 25, ic + 8, 12, 4)
                pm.fillRectangle(ic + 9, ic - 12, 16, 4)
                pm.fillRectangle(ic + 9, ic - 2, 16, 4)
                pm.fillRectangle(ic + 9, ic + 8, 12, 4)
            }
            IconType.CHAT -> {
                pm.setColor(Color.WHITE)
                pm.fillCircle(ic - 10, ic - 4, 22)
                pm.fillCircle(ic + 12, ic - 4, 19)
                pm.fillRectangle(ic - 10, ic - 22, 24, 36)
                pm.fillTriangle(ic - 16, ic + 12, ic - 26, ic + 29, ic - 2, ic + 16)
                pm.setColor(bottomColor)
                pm.fillCircle(ic - 10, ic - 4, 4)
                pm.fillCircle(ic + 2, ic - 4, 4)
                pm.fillCircle(ic + 14, ic - 4, 4)
            }
            IconType.THINK -> {
                pm.setColor(Color.WHITE)
                pm.fillCircle(ic - 22, ic, 9)
                pm.fillCircle(ic, ic, 9)
                pm.fillCircle(ic + 22, ic, 9)
            }
            IconType.STOP -> {
                pm.setColor(Color.WHITE)
                pm.fillRectangle(ic - 18, ic - 18, 36, 36)
            }
            IconType.RESET -> {
                pm.setColor(Color.WHITE)
                pm.fillCircle(ic, ic, 26)
                pm.setColor(topColor.cpy().lerp(bottomColor, 0.5f))
                pm.fillCircle(ic, ic, 16)
                pm.fillRectangle(ic, ic - 30, 30, 26)
                pm.setColor(Color.WHITE)
                pm.fillTriangle(ic + 4, ic - 30, ic + 25, ic - 16, ic + 4, ic - 3)
            } 
          IconType.CAMERA -> {
pm.setColor(Color.WHITE)
pm.fillRectangle(ic - 28, ic - 16, 56, 38)
pm.fillRectangle(ic - 14, ic - 26, 28, 12)
pm.setColor(topColor.cpy().lerp(bottomColor, 0.5f))
pm.fillCircle(ic, ic + 3, 14)
pm.setColor(Color.WHITE)
pm.fillCircle(ic, ic + 3, 7)
          }
          IconType.HISTORY -> {
    pm.setColor(Color.WHITE)
    pm.fillRectangle(ic - 24, ic - 18, 48, 8)
    pm.fillRectangle(ic - 24, ic - 4, 48, 8)
    pm.fillRectangle(ic - 24, ic + 10, 48, 8)
}
IconType.SETTINGS -> {
    pm.setColor(Color.WHITE)
    pm.fillCircle(ic, ic, 22)
    pm.fillRectangle(ic - 6, ic - 30, 12, 60)
    pm.fillRectangle(ic - 30, ic - 6, 60, 12)
    pm.setColor(topColor.cpy().lerp(bottomColor, 0.5f))
    pm.fillCircle(ic, ic, 10)
}
                
        }

        return registerTexture(pm)
    }

    private fun createCartoonPillDrawable(
        width: Int,
        height: Int,
        radius: Int,
        topColor: Color,
        bottomColor: Color,
        outlineColor: Color,
        outlineWidth: Int
    ): Drawable {
        val pm = Pixmap(width, height, Pixmap.Format.RGBA8888)
        pm.blending = Pixmap.Blending.None

        val lip = 5f
        val bodyH = height - lip
        val halfW = width * 0.5f
        val halfH = bodyH * 0.5f
        val r = radius.toFloat().coerceAtMost(minOf(halfW, halfH) - 1f)
        val bw = outlineWidth.toFloat()
        val c = Color()

        for (y in 0 until height) {
            for (x in 0 until width) {
                val sy = abs(y + 0.5f - (halfH + lip)) - (halfH - r)
                val sx = abs(x + 0.5f - halfW) - (halfW - r)
                val sDist = sqrt(sx.coerceAtLeast(0f) * sx.coerceAtLeast(0f) + sy.coerceAtLeast(0f) * sy.coerceAtLeast(0f)) +
                    minOf(maxOf(sx, sy), 0f) - r

                val py = abs(y + 0.5f - halfH) - (halfH - r)
                val px = abs(x + 0.5f - halfW) - (halfW - r)
                val dist = sqrt(px.coerceAtLeast(0f) * px.coerceAtLeast(0f) + py.coerceAtLeast(0f) * py.coerceAtLeast(0f)) +
                    minOf(maxOf(px, py), 0f) - r

                if (dist <= 0.5f) {
                    val edgeAlpha = (0.5f - dist).coerceIn(0f, 1f)
                    if (dist >= -bw) {
                        pm.drawPixel(x, y, Color.rgba8888(outlineColor.r, outlineColor.g, outlineColor.b, outlineColor.a * edgeAlpha))
                    } else {
                        val t = (y / bodyH).coerceIn(0f, 1f)
                        c.set(topColor).lerp(bottomColor, t)
                        if (t < 0.36f) {
                            c.lerp(Color.WHITE, (0.36f - t) * 0.55f)
                        }
                        pm.drawPixel(x, y, Color.rgba8888(c.r, c.g, c.b, c.a * edgeAlpha))
                    }
                } else if (sDist <= 0.5f) {
                    val sAlpha = (0.5f - sDist).coerceIn(0f, 1f) * 0.85f
                    pm.drawPixel(x, y, Color.rgba8888(outlineColor.r, outlineColor.g, outlineColor.b, sAlpha))
                }
            }
        }

        return TextureRegionDrawable(TextureRegion(registerTexture(pm)))
    }

    private fun createComicSpeechBubbleDrawable(
        width: Int,
        height: Int,
        radius: Int,
        tailHeight: Int,
        fillColor: Color,
        outlineColor: Color,
        outlineWidth: Int
    ): Drawable {
        val pm = Pixmap(width, height, Pixmap.Format.RGBA8888)
        pm.blending = Pixmap.Blending.None

        val lip = 6f
        val topOffset = tailHeight.toFloat()
        val bodyH = height - topOffset - lip
        val halfW = width * 0.5f
        val centerY = topOffset + bodyH * 0.5f
        val halfH = bodyH * 0.5f
        val r = radius.toFloat().coerceAtMost(minOf(halfW, halfH) - 1f)
        val bw = outlineWidth.toFloat()

        for (y in 0 until height) {
            for (x in 0 until width) {
                val py = abs(y + 0.5f - centerY) - (halfH - r)
                val px = abs(x + 0.5f - halfW) - (halfW - r)
                val dist = sqrt(px.coerceAtLeast(0f) * px.coerceAtLeast(0f) + py.coerceAtLeast(0f) * py.coerceAtLeast(0f)) +
                    minOf(maxOf(px, py), 0f) - r

                val sy = abs(y + 0.5f - (centerY + lip)) - (halfH - r)
                val sDist = sqrt(px.coerceAtLeast(0f) * px.coerceAtLeast(0f) + sy.coerceAtLeast(0f) * sy.coerceAtLeast(0f)) +
                    minOf(maxOf(px, sy), 0f) - r

                val tailHalfSpan = ((y.toFloat() / topOffset) * 18f).coerceIn(0f, 18f)
                val inTailOuter = y <= topOffset + bw + 2f && abs(x - halfW) <= tailHalfSpan + bw
                val inTailInner = y > bw && y <= topOffset + bw + 4f && abs(x - halfW) <= (tailHalfSpan - 1.5f).coerceAtLeast(0f)

                if (inTailInner) {
                    pm.drawPixel(x, y, Color.rgba8888(fillColor.r, fillColor.g, fillColor.b, fillColor.a))
                } else if (dist <= 0.5f) {
                    val edgeAlpha = (0.5f - dist).coerceIn(0f, 1f)
                    if (dist >= -bw) {
                        pm.drawPixel(x, y, Color.rgba8888(outlineColor.r, outlineColor.g, outlineColor.b, outlineColor.a * edgeAlpha))
                    } else {
                        pm.drawPixel(x, y, Color.rgba8888(fillColor.r, fillColor.g, fillColor.b, fillColor.a * edgeAlpha))
                    }
                } else if (inTailOuter) {
                    pm.drawPixel(x, y, Color.rgba8888(outlineColor.r, outlineColor.g, outlineColor.b, outlineColor.a))
                } else if (sDist <= 0.5f) {
                    val sAlpha = (0.5f - sDist).coerceIn(0f, 1f) * 0.78f
                    pm.drawPixel(x, y, Color.rgba8888(outlineColor.r, outlineColor.g, outlineColor.b, sAlpha))
                }
            }
        }

        return TextureRegionDrawable(TextureRegion(registerTexture(pm)))
    }

    // ==========================================
    // Custom Actor: Tombol Kartun 3D Chunky dengan Bounce & Idle Float
    // ==========================================
    private class CartoonCircleButton(
        var outerTex: Texture,
        var innerTex: Texture,
        private val idleFloat: Boolean = false
    ) : Actor() {

        var onClick: (() -> Unit)? = null
        var isPulsing: Boolean = false

        private var pressScale: Float = 1f
        private var pressOffsetY: Float = 0f
        private var animTime: Float = 0f
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
            animTime += delta
            val targetScale = if (clickListener.isPressed) 0.88f else 1.0f
            val targetOffsetY = if (clickListener.isPressed) -4f else 0f
            pressScale = MathUtils.lerp(pressScale, targetScale, delta * 20f)
            pressOffsetY = MathUtils.lerp(pressOffsetY, targetOffsetY, delta * 22f)
        }

        override fun draw(batch: Batch, parentAlpha: Float) {
            val pulseBoost = if (isPulsing) 1f + 0.06f * sin(animTime * 6.5f) else 1f
            val floatY = if (idleFloat && !clickListener.isPressed) sin(animTime * 2.8f) * 3.5f else 0f

            val size = minOf(width, height) * pressScale * pulseBoost
            val cx = x + width * 0.5f
            val cy = y + height * 0.5f + pressOffsetY + floatY

            batch.setColor(1f, 1f, 1f, color.a * parentAlpha)
            batch.draw(outerTex, cx - size * 0.5f, cy - size * 0.5f, size, size)

            val innerSize = size * 0.80f
            batch.draw(
                innerTex,
                cx - innerSize * 0.5f,
                cy + size * 0.015f - innerSize * 0.5f,
                innerSize,
                innerSize
            )
        }
    }
}
