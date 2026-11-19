package com.koceng.oren.ui

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Stack
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener
import com.badlogic.gdx.scenes.scene2d.utils.Drawable
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable
import com.badlogic.gdx.utils.Align
import com.badlogic.gdx.utils.Disposable
import com.badlogic.gdx.utils.viewport.ExtendViewport
import kotlin.math.sqrt

class PetHud : Disposable {

    val stage: Stage = Stage(ExtendViewport(480f, 800f))
    private val shapeRenderer = ShapeRenderer()
    private val managedTextures = mutableListOf<Texture>()

    // Fonts
    private val labelFont: BitmapFont
    private val titleFont: BitmapFont
    private val subtitleFont: BitmapFont
    private val smallFont: BitmapFont

    // Callbacks untuk dihubungkan ke PetScreen & Game Logic
    var onFeedClicked: (() -> Unit)? = null
    var onSleepClicked: (() -> Unit)? = null
    var onPlayClicked: (() -> Unit)? = null
    var onModeToggleClicked: (() -> Unit)? = null
    var onTalkClicked: (() -> Unit)? = null
    var onResetChatClicked: (() -> Unit)? = null

    // Widgets - Top HUD
    private val moodBadgeBtn: CircularActionButton
    private val moodValueLabel: Label
    private val modePillLabel: Label
    private val modeToggleBtn: CircularActionButton
    private val modeSideLabel: Label
    private val resetChatContainer: Table

    // Widgets - Side Mic Button (AI Mode)
    private val talkContainer: Table
    private val talkBtn: CircularActionButton
    private val talkLabel: Label
    private val talkIdleTex: Texture
    private val talkListenTex: Texture

    // Widgets - Subtitle Bubble
    private val subtitleBox: Table
    private val subtitleLabel: Label
    private var subtitleTimer: Float = 0f
    private var isSubtitleTimed: Boolean = false

    // Widgets - Bottom Circular Status Buttons
    private val feedBtn: CircularActionButton
    private val sleepBtn: CircularActionButton
    private val playBtn: CircularActionButton
    private val feedPctLabel: Label
    private val sleepTitleLabel: Label
    private val sleepPctLabel: Label
    private val playPctLabel: Label

    // Shared outer ring texture
    private val outerRingTex: Texture

    init {
        labelFont = createFont(1.22f)
        titleFont = createFont(1.48f)
        subtitleFont = createFont(1.25f)
        smallFont = createFont(0.98f)

        outerRingTex = createOuterRingTexture()

        val whiteLabelStyle = Label.LabelStyle(labelFont, Color.WHITE)
        val titleLabelStyle = Label.LabelStyle(titleFont, Color.WHITE)
        val subtitleLabelStyle = Label.LabelStyle(subtitleFont, Color(1f, 0.96f, 0.84f, 1f))
        val smallLabelStyle = Label.LabelStyle(smallFont, Color(0.96f, 0.98f, 1f, 0.95f))

        // Glossy textures dengan ikon kartun
        val moodInnerTex = createGlossyDiscTexture(
            topColor = Color(0.28f, 0.72f, 0.98f, 1f),
            bottomColor = Color(0.10f, 0.44f, 0.82f, 1f),
            iconType = IconType.NONE
        )
        val feedInnerTex = createGlossyDiscTexture(
            topColor = Color(0.24f, 0.86f, 0.34f, 1f),
            bottomColor = Color(0.08f, 0.62f, 0.18f, 1f),
            iconType = IconType.FOOD
        )
        val sleepInnerTex = createGlossyDiscTexture(
            topColor = Color(0.98f, 0.78f, 0.18f, 1f),
            bottomColor = Color(0.76f, 0.50f, 0.06f, 1f),
            iconType = IconType.MOON
        )
        val playInnerTex = createGlossyDiscTexture(
            topColor = Color(0.26f, 0.84f, 0.44f, 1f),
            bottomColor = Color(0.09f, 0.60f, 0.24f, 1f),
            iconType = IconType.BALL
        )
        val modeInnerTex = createGlossyDiscTexture(
            topColor = Color(0.76f, 0.44f, 0.96f, 1f),
            bottomColor = Color(0.48f, 0.20f, 0.76f, 1f),
            iconType = IconType.MODE
        )
        talkIdleTex = createGlossyDiscTexture(
            topColor = Color(0.22f, 0.78f, 0.94f, 1f),
            bottomColor = Color(0.08f, 0.50f, 0.76f, 1f),
            iconType = IconType.MIC
        )
        talkListenTex = createGlossyDiscTexture(
            topColor = Color(0.98f, 0.36f, 0.34f, 1f),
            bottomColor = Color(0.74f, 0.14f, 0.14f, 1f),
            iconType = IconType.MIC
        )
        val resetInnerTex = createGlossyDiscTexture(
            topColor = Color(0.96f, 0.36f, 0.34f, 1f),
            bottomColor = Color(0.72f, 0.14f, 0.16f, 1f),
            iconType = IconType.RESET
        )

        val rootTable = Table().apply {
            setFillParent(true)
            pad(14f, 16f, 18f, 16f)
        }
        stage.addActor(rootTable)

        // ==========================================
        // 1. BARIS ATAS: Badge Mood (Kiri), Pill Mode (Tengah), Tombol Mode & Reset (Kanan)
        // ==========================================
        val topBar = Table()

        // Kiri Atas: Badge Mood bergaya LVL ring
        moodBadgeBtn = CircularActionButton(
            outerRingTex = outerRingTex,
            innerTex = moodInnerTex,
            shapeRenderer = shapeRenderer,
            hasProgressRing = true,
            fixedRingColor = Color(0.22f, 0.78f, 1f, 1f)
        )
        val moodTextTable = Table().apply {
            touchable = Touchable.disabled
        }
        moodTextTable.add(Label("MOOD", smallLabelStyle)).row()
        moodValueLabel = Label("85%", titleLabelStyle)
        moodTextTable.add(moodValueLabel).padTop(-4f)

        val moodStack = Stack().apply {
            add(moodBadgeBtn)
            add(moodTextTable)
        }

        // Tengah Atas: Pill indikator mode aktif
        val pillBg = createRoundedRectDrawable(
            width = 210,
            height = 46,
            radius = 22,
            fillColor = Color(0.10f, 0.14f, 0.22f, 0.72f),
            borderColor = Color(1f, 1f, 1f, 0.45f),
            borderWidth = 2
        )
        val modePill = Table().apply {
            background = pillBg
            pad(6f, 18f, 6f, 18f)
        }
        modePillLabel = Label("Mode: Tiru Suara", whiteLabelStyle).apply {
            setAlignment(Align.center)
        }
        modePill.add(modePillLabel)

        // Kanan Atas: Tombol Ganti Mode + Reset Chat (di bawahnya)
        val rightTopCol = Table()
        modeToggleBtn = CircularActionButton(
            outerRingTex = outerRingTex,
            innerTex = modeInnerTex,
            shapeRenderer = shapeRenderer,
            hasProgressRing = false
        ).apply {
            onClick = { onModeToggleClicked?.invoke() }
        }
        modeSideLabel = Label("Tiru", smallLabelStyle).apply {
            setAlignment(Align.center)
        }
        rightTopCol.add(modeToggleBtn).size(64f).row()
        rightTopCol.add(modeSideLabel).padTop(2f).row()

        val resetBtn = CircularActionButton(
            outerRingTex = outerRingTex,
            innerTex = resetInnerTex,
            shapeRenderer = shapeRenderer,
            hasProgressRing = false
        ).apply {
            onClick = { onResetChatClicked?.invoke() }
        }
        resetChatContainer = Table().apply {
            isVisible = false
            padTop(8f)
            add(resetBtn).size(56f).row()
            add(Label("Reset", smallLabelStyle)).padTop(2f)
        }
        rightTopCol.add(resetChatContainer)

        topBar.add(moodStack).size(82f).top().left()
        topBar.add(modePill).expandX().top().padTop(12f)
        topBar.add(rightTopCol).top().right()

        rootTable.add(topBar).expandX().fillX().top().row()

        // ==========================================
        // 2. BAGIAN TENGAH: Tombol Mic AI di Sisi Kiri + Ruang Luas untuk Kucing 3D
        // ==========================================
        val middleArea = Table()
        talkBtn = CircularActionButton(
            outerRingTex = outerRingTex,
            innerTex = talkIdleTex,
            shapeRenderer = shapeRenderer,
            hasProgressRing = false
        ).apply {
            onClick = { onTalkClicked?.invoke() }
        }
        talkLabel = Label("Bicara", smallLabelStyle).apply {
            setAlignment(Align.center)
        }
        talkContainer = Table().apply {
            isVisible = false
            add(talkBtn).size(70f).row()
            add(talkLabel).padTop(2f)
        }
        middleArea.add(talkContainer).left().bottom().padBottom(16f)
        middleArea.add().expand()

        rootTable.add(middleArea).expand().fill().row()

        // ==========================================
        // 3. KOTAK SUBTITLE / BALON UCAPAN
        // ==========================================
        val subtitleBg = createRoundedRectDrawable(
            width = 360,
            height = 76,
            radius = 22,
            fillColor = Color(0.08f, 0.11f, 0.18f, 0.80f),
            borderColor = Color(1f, 1f, 1f, 0.55f),
            borderWidth = 3
        )
        subtitleBox = Table().apply {
            background = subtitleBg
            pad(12f, 20f, 12f, 20f)
        }
        subtitleLabel = Label("Meong! Halo, aku Koceng Oren!", subtitleLabelStyle).apply {
            setWrap(true)
            setAlignment(Align.center)
        }
        subtitleBox.add(subtitleLabel).width(400f)
        rootTable.add(subtitleBox).center().padBottom(14f).row()

        // ==========================================
        // 4. BARIS BAWAH: 3 Tombol Bulat Glossy + Ring Progress (Makan, Tidur, Main)
        // ==========================================
        val bottomDock = Table()

        feedBtn = CircularActionButton(
            outerRingTex = outerRingTex,
            innerTex = feedInnerTex,
            shapeRenderer = shapeRenderer,
            hasProgressRing = true
        ).apply {
            onClick = { onFeedClicked?.invoke() }
        }
        feedPctLabel = Label("85%", smallLabelStyle)

        sleepBtn = CircularActionButton(
            outerRingTex = outerRingTex,
            innerTex = sleepInnerTex,
            shapeRenderer = shapeRenderer,
            hasProgressRing = true
        ).apply {
            onClick = { onSleepClicked?.invoke() }
        }
        sleepTitleLabel = Label("Tidur", whiteLabelStyle)
        sleepPctLabel = Label("90%", smallLabelStyle)

        playBtn = CircularActionButton(
            outerRingTex = outerRingTex,
            innerTex = playInnerTex,
            shapeRenderer = shapeRenderer,
            hasProgressRing = true
        ).apply {
            onClick = { onPlayClicked?.invoke() }
        }
        playPctLabel = Label("80%", smallLabelStyle)

        fun buildDockItem(btn: CircularActionButton, titleLabel: Label, pctLabel: Label): Table {
            return Table().apply {
                add(btn).size(92f).row()
                add(titleLabel).padTop(4f).row()
                add(pctLabel).padTop(-2f)
            }
        }

        bottomDock.add(buildDockItem(feedBtn, Label("Makan", whiteLabelStyle), feedPctLabel)).expandX()
        bottomDock.add(buildDockItem(sleepBtn, sleepTitleLabel, sleepPctLabel)).expandX()
        bottomDock.add(buildDockItem(playBtn, Label("Main", whiteLabelStyle), playPctLabel)).expandX()

        rootTable.add(bottomDock).expandX().fillX().bottom()
    }

    /**
     * Memperbarui visual ring progress dan label status virtual pet
     */
    fun updateStatus(hunger: Float, energy: Float, happiness: Float, isSleeping: Boolean) {
        val hPct = hunger.coerceIn(0f, 100f)
        val ePct = energy.coerceIn(0f, 100f)
        val hapPct = happiness.coerceIn(0f, 100f)
        val overall = (hPct + ePct + hapPct) / 3f

        feedBtn.progress = hPct / 100f
        sleepBtn.progress = ePct / 100f
        playBtn.progress = hapPct / 100f
        moodBadgeBtn.progress = overall / 100f

        moodValueLabel.setText("${overall.toInt()}%")
        feedPctLabel.setText("${hPct.toInt()}%")
        sleepPctLabel.setText("${ePct.toInt()}%")
        playPctLabel.setText("${hapPct.toInt()}%")
        sleepTitleLabel.setText(if (isSleeping) "Bangun" else "Tidur")
    }

    /**
     * Mengatur tampilan mode (Tiru Suara vs Ngobrol AI)
     */
    fun setMode(isAiMode: Boolean) {
        if (isAiMode) {
            modePillLabel.setText("Mode: Ngobrol AI")
            modeSideLabel.setText("AI")
            talkContainer.isVisible = true
            resetChatContainer.isVisible = true
            showSubtitle("Mode Ngobrol AI aktif! Tekan tombol Bicara di kiri untuk mengobrol.", 4f)
        } else {
            modePillLabel.setText("Mode: Tiru Suara")
            modeSideLabel.setText("Tiru")
            talkContainer.isVisible = false
            resetChatContainer.isVisible = false
            showSubtitle("Mode Tiru Suara aktif. Ucapkan sesuatu ke mic!", 3f)
        }
    }

    fun setTalkButtonState(isListening: Boolean) {
        if (isListening) {
            talkBtn.innerTex = talkListenTex
            talkLabel.setText("Dengar...")
            showSubtitle("Mendengarkan suaramu...", 0f)
        } else {
            talkBtn.innerTex = talkIdleTex
            talkLabel.setText("Bicara")
        }
    }

    fun showSubtitle(text: String, durationSeconds: Float = 0f) {
        subtitleLabel.setText(text)
        subtitleBox.isVisible = true
        if (durationSeconds > 0f) {
            subtitleTimer = durationSeconds
            isSubtitleTimed = true
        } else {
            isSubtitleTimed = false
        }
    }

    fun hideSubtitle() {
        subtitleBox.isVisible = false
        isSubtitleTimed = false
    }

    fun render(delta: Float) {
        if (isSubtitleTimed) {
            subtitleTimer -= delta
            if (subtitleTimer <= 0f) {
                isSubtitleTimed = false
                subtitleBox.isVisible = false
            }
        }

        stage.act(delta)
        stage.draw()
    }

    fun resize(width: Int, height: Int) {
        stage.viewport.update(width, height, true)
    }

    override fun dispose() {
        stage.dispose()
        shapeRenderer.dispose()
        labelFont.dispose()
        titleFont.dispose()
        subtitleFont.dispose()
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

    private fun createOuterRingTexture(): Texture {
        val size = 160
        val cx = size / 2f
        val cy = size / 2f
        val pm = Pixmap(size, size, Pixmap.Format.RGBA8888)
        pm.blending = Pixmap.Blending.SourceOver

        // Bayangan lembut di bawah lingkaran
        for (y in 0 until size) {
            for (x in 0 until size) {
                val dx = x - cx
                val dy = y - (cy + 5f)
                val d = sqrt(dx * dx + dy * dy)
                if (d < 74f) {
                    val a = ((74f - d) / 14f).coerceIn(0f, 1f) * 0.38f
                    pm.drawPixel(x, y, Color.rgba8888(0.05f, 0.06f, 0.10f, a))
                }
            }
        }

        // Bingkai putih tegas + outline gelap tipis
        pm.setColor(0.14f, 0.16f, 0.22f, 0.90f)
        pm.fillCircle(size / 2, size / 2 - 2, 72)
        pm.setColor(1f, 1f, 1f, 1f)
        pm.fillCircle(size / 2, size / 2 - 2, 69)

        return registerTexture(pm)
    }

    private enum class IconType { NONE, FOOD, MOON, BALL, MODE, MIC, RESET }

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

                    // Kilau glossy di bagian atas
                    val gx = dx / (radius * 0.72f)
                    val gy = (y - (cy - radius * 0.42f)) / (radius * 0.36f)
                    val gd = gx * gx + gy * gy
                    if (gd < 1f) {
                        val sheen = (1f - gd) * 0.42f
                        c.lerp(Color.WHITE, sheen)
                    }

                    // Anti-alias tepi lingkaran
                    val alpha = (radius - d).coerceIn(0f, 1f)
                    pm.drawPixel(x, y, Color.rgba8888(c.r, c.g, c.b, alpha))
                }
            }
        }

        // Gambar ikon kartun di tengah disc
        val ic = size / 2
        when (iconType) {
            IconType.FOOD -> {
                // Sendok (kiri) & Garpu (kanan) putih
                pm.setColor(Color.WHITE)
                // Sendok
                pm.fillCircle(ic - 20, ic - 22, 13)
                pm.fillRectangle(ic - 24, ic - 12, 8, 46)
                pm.fillCircle(ic - 20, ic + 34, 4)
                // Garpu
                pm.fillRectangle(ic + 12, ic - 34, 5, 22)
                pm.fillRectangle(ic + 20, ic - 34, 5, 22)
                pm.fillRectangle(ic + 28, ic - 34, 5, 22)
                pm.fillRectangle(ic + 12, ic - 14, 21, 9)
                pm.fillRectangle(ic + 19, ic - 6, 8, 40)
                pm.fillCircle(ic + 23, ic + 34, 4)
            }
            IconType.MOON -> {
                // Bulan sabit kuning terang + 2 bintang kecil
                pm.setColor(1f, 0.98f, 0.80f, 1f)
                pm.fillCircle(ic - 4, ic, 30)
                pm.setColor(topColor.cpy().lerp(bottomColor, 0.5f))
                pm.fillCircle(ic + 12, ic - 8, 25)
                pm.setColor(1f, 0.98f, 0.82f, 1f)
                pm.fillCircle(ic + 20, ic - 16, 6)
                pm.fillCircle(ic + 28, ic + 4, 4)
            }
            IconType.BALL -> {
                // Bola pantai warna-warni
                pm.setColor(0.98f, 0.30f, 0.28f, 1f)
                pm.fillCircle(ic, ic, 30)
                pm.setColor(0.20f, 0.72f, 0.96f, 1f)
                pm.fillCircle(ic - 10, ic - 10, 18)
                pm.setColor(1f, 0.88f, 0.22f, 1f)
                pm.fillCircle(ic + 12, ic + 12, 15)
                pm.setColor(Color.WHITE)
                pm.fillCircle(ic, ic, 7)
            }
            IconType.MIC -> {
                // Ikon mikrofon putih
                pm.setColor(Color.WHITE)
                pm.fillCircle(ic, ic - 18, 13)
                pm.fillRectangle(ic - 13, ic - 18, 26, 26)
                pm.fillCircle(ic, ic + 8, 13)
                // Penyangga mic
                pm.fillRectangle(ic - 20, ic + 6, 5, 12)
                pm.fillRectangle(ic + 15, ic + 6, 5, 12)
                pm.fillRectangle(ic - 20, ic + 16, 40, 5)
                pm.fillRectangle(ic - 3, ic + 20, 6, 14)
                pm.fillRectangle(ic - 14, ic + 32, 28, 5)
            }
            IconType.MODE -> {
                // Ikon balon chat + sparkle AI
                pm.setColor(Color.WHITE)
                pm.fillCircle(ic - 8, ic - 4, 22)
                pm.fillCircle(ic + 12, ic - 4, 18)
                pm.fillRectangle(ic - 10, ic - 22, 24, 36)
                pm.fillTriangle(ic - 16, ic + 12, ic - 26, ic + 28, ic - 2, ic + 16)
                // Titik obrolan
                pm.setColor(bottomColor)
                pm.fillCircle(ic - 10, ic - 4, 4)
                pm.fillCircle(ic + 2, ic - 4, 4)
                pm.fillCircle(ic + 14, ic - 4, 4)
            }
            IconType.RESET -> {
                // Ikon panah reset melingkar
                pm.setColor(Color.WHITE)
                pm.fillCircle(ic, ic, 26)
                pm.setColor(topColor.cpy().lerp(bottomColor, 0.5f))
                pm.fillCircle(ic, ic, 17)
                pm.fillRectangle(ic, ic - 30, 30, 26)
                pm.setColor(Color.WHITE)
                pm.fillTriangle(ic + 4, ic - 30, ic + 24, ic - 16, ic + 4, ic - 4)
            }
            IconType.NONE -> {}
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
    // Custom Actor: Tombol Bulat Glossy + Ring Progress
    // ==========================================
    private class CircularActionButton(
        private val outerRingTex: Texture,
        var innerTex: Texture,
        private val shapeRenderer: ShapeRenderer,
        private val hasProgressRing: Boolean,
        private val fixedRingColor: Color? = null
    ) : Actor() {

        var progress: Float = 1f
        var onClick: (() -> Unit)? = null

        private var displayProgress: Float = 1f
        private var pressScale: Float = 1f
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
            displayProgress = MathUtils.lerp(displayProgress, progress.coerceIn(0f, 1f), delta * 6f)
            val targetScale = if (clickListener.isPressed) 0.90f else 1.0f
            pressScale = MathUtils.lerp(pressScale, targetScale, delta * 18f)
        }

        override fun draw(batch: Batch, parentAlpha: Float) {
            val size = minOf(width, height) * pressScale
            val cx = x + width * 0.5f
            val cy = y + height * 0.5f
            val drawX = cx - size * 0.5f
            val drawY = cy - size * 0.5f

            batch.setColor(1f, 1f, 1f, color.a * parentAlpha)
            batch.draw(outerRingTex, drawX, drawY, size, size)

            if (hasProgressRing) {
                batch.end()
                Gdx.gl.glEnable(GL20.GL_BLEND)
                Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
                shapeRenderer.projectionMatrix = batch.projectionMatrix
                shapeRenderer.transformMatrix = batch.transformMatrix
                shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)

                val ringRadius = size * 0.42f
                val ringCy = cy + size * 0.012f

                // Track gelap di belakang ring
                shapeRenderer.setColor(0.18f, 0.22f, 0.28f, 0.85f * parentAlpha)
                shapeRenderer.circle(cx, ringCy, ringRadius, 40)

                // Warna ring otomatis (hijau / kuning / merah) atau warna tetap
                val ringColor = fixedRingColor ?: when {
                    displayProgress >= 0.55f -> Color(0.16f, 0.86f, 0.26f, 1f)
                    displayProgress >= 0.28f -> Color(0.98f, 0.78f, 0.14f, 1f)
                    else -> Color(0.96f, 0.24f, 0.22f, 1f)
                }
                shapeRenderer.setColor(ringColor.r, ringColor.g, ringColor.b, parentAlpha)
                shapeRenderer.arc(cx, ringCy, ringRadius, 90f, -360f * displayProgress.coerceIn(0.02f, 1f), 48)
                shapeRenderer.end()
                batch.begin()
                batch.setColor(1f, 1f, 1f, color.a * parentAlpha)
            }

            // Disc glossy & ikon di tengah (menutup tengah arc menjadi cincin rapi)
            val innerFactor = if (hasProgressRing) 0.70f else 0.80f
            val innerSize = size * innerFactor
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
