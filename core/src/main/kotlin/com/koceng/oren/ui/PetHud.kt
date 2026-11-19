package com.koceng.oren.ui

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.ProgressBar
import com.badlogic.gdx.scenes.scene2d.ui.Skin
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener
import com.badlogic.gdx.scenes.scene2d.utils.Drawable
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable
import com.badlogic.gdx.utils.Disposable
import com.badlogic.gdx.utils.viewport.ScreenViewport

class PetHud : Disposable {

    val stage: Stage = Stage(ScreenViewport())
    private val uiSkin: Skin = createProceduralSkin()

    // Callbacks untuk dihubungkan ke PetScreen & Game Logic
    var onFeedClicked: (() -> Unit)? = null
    var onSleepClicked: (() -> Unit)? = null
    var onPlayClicked: (() -> Unit)? = null
    var onModeToggleClicked: (() -> Unit)? = null
    var onTalkClicked: (() -> Unit)? = null
    var onResetChatClicked: (() -> Unit)? = null

    // UI Widgets - Status Bars & Labels
    private val hungerBar: ProgressBar
    private val energyBar: ProgressBar
    private val happinessBar: ProgressBar

    private val hungerLabel: Label
    private val energyLabel: Label
    private val happinessLabel: Label

    // UI Widgets - Subtitle / Dialog Box
    private val subtitleBox: Table
    private val subtitleLabel: Label
    private var subtitleTimer: Float = 0f
    private var isSubtitleTimed: Boolean = false

    // UI Widgets - Action Buttons
    private val feedBtn: TextButton
    private val sleepBtn: TextButton
    private val playBtn: TextButton
    private val modeToggleBtn: TextButton
    private val talkBtn: TextButton
    private val resetChatBtn: TextButton

    init {
        val rootTable = Table().apply {
            setFillParent(true)
        }
        stage.addActor(rootTable)

        // ==========================================
        // 1. BAGIAN ATAS: Status Bars (Lapar, Energi, Bahagia)
        // ==========================================
        val topTable = Table().apply {
            pad(16f)
            background = uiSkin.getDrawable("panel-bg")
        }

        hungerLabel = Label("Lapar: 85%", uiSkin)
        hungerBar = ProgressBar(0f, 100f, 0.5f, false, uiSkin, "hunger-bar").apply {
            setValue(85f)
            setAnimateDuration(0.2f)
        }

        energyLabel = Label("Energi: 90%", uiSkin)
        energyBar = ProgressBar(0f, 100f, 0.5f, false, uiSkin, "energy-bar").apply {
            setValue(90f)
            setAnimateDuration(0.2f)
        }

        happinessLabel = Label("Bahagia: 80%", uiSkin)
        happinessBar = ProgressBar(0f, 100f, 0.5f, false, uiSkin, "happiness-bar").apply {
            setValue(80f)
            setAnimateDuration(0.2f)
        }

        // Baris Lapar
        topTable.add(hungerLabel).left().padRight(10f)
        topTable.add(hungerBar).width(180f).height(20f).row()

        // Baris Energi
        topTable.add(energyLabel).left().padRight(10f).padTop(6f)
        topTable.add(energyBar).width(180f).height(20f).padTop(6f).row()

        // Baris Bahagia
        topTable.add(happinessLabel).left().padRight(10f).padTop(6f)
        topTable.add(happinessBar).width(180f).height(20f).padTop(6f)

        rootTable.add(topTable).top().expandX().fillX().pad(12f).row()

        // ==========================================
        // 2. BAGIAN TENGAH-BAWAH: Kotak Subtitle Transparan
        // ==========================================
        rootTable.add().expand().row() // Spacer fleksibel

        subtitleBox = Table().apply {
            background = uiSkin.getDrawable("subtitle-bg")
            pad(12f, 20f, 12f, 20f)
        }

        subtitleLabel = Label("Meong! Halo, aku Koceng Oren!", uiSkin, "subtitle-text").apply {
            setWrap(true)
            setAlignment(com.badlogic.gdx.utils.Align.center)
        }

        subtitleBox.add(subtitleLabel).width(360f)
        rootTable.add(subtitleBox).center().padBottom(16f).row()

        // ==========================================
        // 3. BAGIAN BAWAH: Tombol Aksi & Mode
        // ==========================================
        val bottomTable = Table().apply {
            pad(10f)
        }

        // Baris 1: Tombol Kebutuhan (Makan, Tidur, Main)
        val actionRow = Table()

        feedBtn = TextButton("Beri Makan", uiSkin).apply {
            addListener(object : ClickListener() {
                override fun clicked(event: InputEvent?, x: Float, y: Float) {
                    onFeedClicked?.invoke()
                }
            })
        }

        sleepBtn = TextButton("Tidur", uiSkin).apply {
            addListener(object : ClickListener() {
                override fun clicked(event: InputEvent?, x: Float, y: Float) {
                    onSleepClicked?.invoke()
                }
            })
        }

        playBtn = TextButton("Ajak Main", uiSkin).apply {
            addListener(object : ClickListener() {
                override fun clicked(event: InputEvent?, x: Float, y: Float) {
                    onPlayClicked?.invoke()
                }
            })
        }

        actionRow.add(feedBtn).width(115f).height(50f).padRight(8f)
        actionRow.add(sleepBtn).width(115f).height(50f).padRight(8f)
        actionRow.add(playBtn).width(115f).height(50f)

        bottomTable.add(actionRow).row()

        // Baris 2: Mode Toggle & Tombol Bicara AI
        val modeRow = Table().apply {
            padTop(10f)
        }

        modeToggleBtn = TextButton("Mode: Tiru Suara", uiSkin, "mode-btn").apply {
            addListener(object : ClickListener() {
                override fun clicked(event: InputEvent?, x: Float, y: Float) {
                    onModeToggleClicked?.invoke()
                }
            })
        }

        talkBtn = TextButton("Bicara AI", uiSkin, "talk-btn").apply {
            isVisible = false // Awalnya disembunyikan sampai mode AI dipilih
            addListener(object : ClickListener() {
                override fun clicked(event: InputEvent?, x: Float, y: Float) {
                    onTalkClicked?.invoke()
                }
            })
        }

        resetChatBtn = TextButton("Reset", uiSkin, "reset-btn").apply {
            isVisible = false
            addListener(object : ClickListener() {
                override fun clicked(event: InputEvent?, x: Float, y: Float) {
                    onResetChatClicked?.invoke()
                }
            })
        }

        modeRow.add(modeToggleBtn).width(180f).height(50f).padRight(8f)
        modeRow.add(talkBtn).width(110f).height(50f).padRight(8f)
        modeRow.add(resetChatBtn).width(75f).height(50f)

        bottomTable.add(modeRow).padBottom(16f)

        rootTable.add(bottomTable).bottom().pad(8f)
    }

    /**
     * Memperbarui visual bar dan label status virtual pet
     */
    fun updateStatus(hunger: Float, energy: Float, happiness: Float, isSleeping: Boolean) {
        hungerBar.setValue(hunger)
        energyBar.setValue(energy)
        happinessBar.setValue(happiness)

        hungerLabel.setText("Lapar: ${hunger.toInt()}%")
        energyLabel.setText("Energi: ${energy.toInt()}%")
        happinessLabel.setText("Bahagia: ${happiness.toInt()}%")

        sleepBtn.setText(if (isSleeping) "Bangun" else "Tidur")
    }

    /**
     * Mengatur tampilan mode (Tiru Suara vs Ngobrol AI)
     */
    fun setMode(isAiMode: Boolean) {
        if (isAiMode) {
            modeToggleBtn.setText("Mode: Ngobrol AI")
            talkBtn.isVisible = true
            resetChatBtn.isVisible = true
            showSubtitle("Mode Ngobrol AI aktif! Tekan tombol Bicara untuk mengobrol.", 4f)
        } else {
            modeToggleBtn.setText("Mode: Tiru Suara")
            talkBtn.isVisible = false
            resetChatBtn.isVisible = false
            showSubtitle("Mode Tiru Suara aktif. Ucapkan sesuatu ke mic!", 3f)
        }
    }

    fun setTalkButtonState(isListening: Boolean) {
        if (isListening) {
            talkBtn.setText("Mendengar...")
            showSubtitle("Mendengarkan suaramu...", 0f)
        } else {
            talkBtn.setText("Bicara AI")
        }
    }

    /**
     * Menampilkan kotak teks subtitel / balasan Koceng Oren
     * @param text pesan yang ditampilkan
     * @param durationSeconds durasi tampilan sebelum otomatis hilang (0f = tetap tampil)
     */
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
        uiSkin.dispose()
    }

    // ========================================================
    // Generator Skin Prosedural (Murni Tanpa File JSON/PNG)
    // ========================================================
    private fun createProceduralSkin(): Skin {
        val skin = Skin()

        val font = BitmapFont().apply {
            data.setScale(1.1f)
        }
        skin.add("default-font", font)

        val subtitleFont = BitmapFont().apply {
            data.setScale(1.2f)
        }
        skin.add("subtitle-font", subtitleFont)

        fun createDrawable(color: Color): Drawable {
            val pixmap = Pixmap(1, 1, Pixmap.Format.RGBA8888).apply {
                setColor(color)
                fill()
            }
            val texture = Texture(pixmap)
            pixmap.dispose()
            return TextureRegionDrawable(TextureRegion(texture))
        }

        // Background Panels
        skin.add("panel-bg", createDrawable(Color(0.08f, 0.12f, 0.18f, 0.65f)), Drawable::class.java)
        skin.add("subtitle-bg", createDrawable(Color(0.05f, 0.06f, 0.10f, 0.82f)), Drawable::class.java)

        // Label Styles
        skin.add("default", Label.LabelStyle(font, Color.WHITE))
        skin.add("subtitle-text", Label.LabelStyle(subtitleFont, Color(1f, 0.95f, 0.6f, 1f)))

        // Progress Bar Drawables
        val barBg = createDrawable(Color(0.18f, 0.20f, 0.25f, 0.85f))
        val hungerFill = createDrawable(Color(0.95f, 0.55f, 0.15f, 1.0f))   // Oranye
        val energyFill = createDrawable(Color(0.20f, 0.80f, 0.40f, 1.0f))   // Hijau
        val happinessFill = createDrawable(Color(0.95f, 0.35f, 0.65f, 1.0f)) // Pink / Magenta

        skin.add("hunger-bar", ProgressBar.ProgressBarStyle(barBg, null).apply { knobBefore = hungerFill })
        skin.add("energy-bar", ProgressBar.ProgressBarStyle(barBg, null).apply { knobBefore = energyFill })
        skin.add("happiness-bar", ProgressBar.ProgressBarStyle(barBg, null).apply { knobBefore = happinessFill })

        // Button Styles
        // Standard Action Button (Biru)
        val btnUp = createDrawable(Color(0.22f, 0.45f, 0.80f, 0.92f))
        val btnDown = createDrawable(Color(0.14f, 0.30f, 0.60f, 1.0f))
        skin.add("default", TextButton.TextButtonStyle(btnUp, btnDown, btnUp, font).apply {
            fontColor = Color.WHITE
            downFontColor = Color.LIGHT_GRAY
        })

        // Mode Toggle Button (Ungu / Indigo)
        val modeUp = createDrawable(Color(0.45f, 0.30f, 0.75f, 0.92f))
        val modeDown = createDrawable(Color(0.30f, 0.18f, 0.55f, 1.0f))
        skin.add("mode-btn", TextButton.TextButtonStyle(modeUp, modeDown, modeUp, font).apply {
            fontColor = Color(1f, 1f, 0.85f, 1f)
            downFontColor = Color.LIGHT_GRAY
        })

        // Talk AI Button (Hijau Emerald)
        val talkUp = createDrawable(Color(0.15f, 0.65f, 0.45f, 0.95f))
        val talkDown = createDrawable(Color(0.10f, 0.45f, 0.30f, 1.0f))
        skin.add("talk-btn", TextButton.TextButtonStyle(talkUp, talkDown, talkUp, font).apply {
            fontColor = Color.WHITE
            downFontColor = Color.LIGHT_GRAY
        })

        // Reset Chat Button (Merah Coral)
        val resetUp = createDrawable(Color(0.70f, 0.25f, 0.25f, 0.92f))
        val resetDown = createDrawable(Color(0.48f, 0.15f, 0.15f, 1.0f))
        skin.add("reset-btn", TextButton.TextButtonStyle(resetUp, resetDown, resetUp, font).apply {
            fontColor = Color.WHITE
            downFontColor = Color.LIGHT_GRAY
        })

        return skin
    }
}
