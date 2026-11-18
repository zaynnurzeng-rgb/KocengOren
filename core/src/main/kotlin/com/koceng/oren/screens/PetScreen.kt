package com.koceng.oren.screens

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.InputMultiplexer
import com.badlogic.gdx.ScreenAdapter
import com.badlogic.gdx.audio.Sound
import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.PerspectiveCamera
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
import com.badlogic.gdx.utils.Timer
import com.badlogic.gdx.utils.viewport.ScreenViewport
import com.koceng.oren.audio.VoiceMimicBridge
import com.koceng.oren.audio.VoiceMimicCallback
import com.koceng.oren.audio.WavUtils
import com.koceng.oren.character.HitZone
import com.koceng.oren.character.PetCharacter
import com.koceng.oren.character.PetState
import com.koceng.oren.input.PetTouchController
import net.mgsx.gltf.scene3d.lights.DirectionalLightEx
import net.mgsx.gltf.scene3d.scene.SceneManager

class PetScreen(private val voiceBridge: VoiceMimicBridge) : ScreenAdapter(), VoiceMimicCallback {

    // --- 3D Scene Components ---
    private lateinit var camera: PerspectiveCamera
    private lateinit var sceneManager: SceneManager
    private lateinit var pet: PetCharacter
    private lateinit var touchController: PetTouchController

    // --- 2D Scene2D UI Components ---
    private lateinit var stage: Stage
    private lateinit var uiSkin: Skin

    // Status Virtual Pet (0f - 100f)
    private var hunger: Float = 85f
    private var energy: Float = 90f
    private val hungerDecayRate = 1.0f  // Berkurang 1% per detik
    private val energyDecayRate = 0.8f  // Berkurang 0.8% per detik saat bangun
    private val energyRegenRate = 6.0f  // Bertambah 6% per detik saat tidur

    // UI Widgets
    private lateinit var hungerBar: ProgressBar
    private lateinit var energyBar: ProgressBar
    private lateinit var hungerLabel: Label
    private lateinit var energyLabel: Label
    private lateinit var sleepButton: TextButton

    // --- Audio Mimic ---
    private var currentSound: Sound? = null
    private val chipmunkPitch = 1.42f

    override fun show() {
        // ==========================================
        // 1. Inisialisasi Kamera & Scene 3D (PBR)
        // ==========================================
        camera = PerspectiveCamera(60f, Gdx.graphics.width.toFloat(), Gdx.graphics.height.toFloat()).apply {
            position.set(0f, 1.2f, 2.8f)
            lookAt(0f, 0.9f, 0f)
            near = 0.1f
            far = 100f
            update()
        }

        // SceneManager dengan kapasitas 64 bones (cartoon_cat.glb memiliki 58 bones)
        sceneManager = SceneManager(64)
        sceneManager.camera = camera

        val sunLight = DirectionalLightEx().apply {
            direction.set(1f, -2.5f, 1.5f).nor()
            color.set(Color.WHITE)
            intensity = 2.5f
        }
        sceneManager.environment.add(sunLight)
        sceneManager.setAmbientLight(0.6f)

        pet = PetCharacter("models/cartoon_cat.glb")
        sceneManager.addScene(pet.scene)

        touchController = PetTouchController(camera, pet) { hitZone ->
            when (hitZone) {
                HitZone.HEAD -> Gdx.app.log("PetScreen", "Kepala disentuh: Dizzy")
                HitZone.BELLY -> Gdx.app.log("PetScreen", "Perut disentuh: Laughing")
                HitZone.NONE -> {}
            }
        }

        // ==========================================
        // 2. Inisialisasi Scene2D Stage & UI Skin
        // ==========================================
        stage = Stage(ScreenViewport())
        uiSkin = createCustomSkin()

        setupUI()

        // ==========================================
        // 3. Konfigurasi InputMultiplexer
        //    (Stage diletakkan PERTAMA agar klik UI
        //     tidak tembus menjadi raycast 3D)
        // ==========================================
        val multiplexer = InputMultiplexer()
        multiplexer.addProcessor(stage)           // Prioritas 1: UI Scene2D
        multiplexer.addProcessor(touchController) // Prioritas 2: Raycast Model 3D
        Gdx.input.inputProcessor = multiplexer

        // ==========================================
        // 4. Inisialisasi Voice Mimic Bridge (Mic)
        // ==========================================
        voiceBridge.setCallback(this)
        voiceBridge.checkAndRequestPermissions {
            Gdx.app.log("PetScreen", "Izin mic diberikan, memulai monitoring suara...")
            voiceBridge.startListening()
        }
    }

    /**
     * Membangun antarmuka Scene2D: Status Bar di atas dan Tombol Aksi di bawah
     */
    private fun setupUI() {
        val rootTable = Table().apply {
            setFillParent(true)
        }
        stage.addActor(rootTable)

        // --- Container Bagian Atas: Status Bar (Lapar & Energi) ---
        val topTable = Table().apply {
            pad(20f)
        }

        hungerLabel = Label("Lapar: ${hunger.toInt()}%", uiSkin)
        hungerBar = ProgressBar(0f, 100f, 0.5f, false, uiSkin, "hunger-bar").apply {
            value = hunger
            animateDuration = 0.2f
        }

        energyLabel = Label("Energi: ${energy.toInt()}%", uiSkin)
        energyBar = ProgressBar(0f, 100f, 0.5f, false, uiSkin, "energy-bar").apply {
            value = energy
            animateDuration = 0.2f
        }

        topTable.add(hungerLabel).left().padRight(12f)
        topTable.add(hungerBar).width(200f).height(24f).row()
        topTable.add(energyLabel).left().padRight(12f).padTop(10f)
        topTable.add(energyBar).width(200f).height(24f).padTop(10f)

        rootTable.add(topTable).top().expandX().fillX().row()

        // Ruang kosong fleksibel di tengah agar pet 3D tetap leluasa terlihat
        rootTable.add().expand().row()

        // --- Container Bagian Bawah: Action Buttons ---
        val bottomTable = Table().apply {
            padBottom(36f)
        }

        val feedButton = TextButton("Beri Makan", uiSkin).apply {
            addListener(object : ClickListener() {
                override fun clicked(event: InputEvent?, x: Float, y: Float) {
                    onFeedClicked()
                }
            })
        }

        sleepButton = TextButton("Tidur", uiSkin).apply {
            addListener(object : ClickListener() {
                override fun clicked(event: InputEvent?, x: Float, y: Float) {
                    onSleepClicked()
                }
            })
        }

        bottomTable.add(feedButton).width(160f).height(60f).padRight(20f)
        bottomTable.add(sleepButton).width(160f).height(60f)

        rootTable.add(bottomTable).bottom().pad(16f)
    }

    /**
     * Logika Tombol "Beri Makan"
     */
    private fun onFeedClicked() {
        Gdx.app.log("GameLogic", "Tombol Beri Makan ditekan!")
        // Tambah status lapar (+25, maksimal 100)
        hunger = (hunger + 25f).coerceAtMost(100f)

        // Jika kucing sedang tidur, bangunkan lalu makan
        if (pet.currentState == PetState.SLEEPING) {
            sleepButton.setText("Tidur")
        }

        // Picu animasi makan pada model GLB
        pet.playEating()
    }

    /**
     * Logika Tombol "Tidur / Bangun"
     */
    private fun onSleepClicked() {
        if (pet.currentState == PetState.SLEEPING) {
            Gdx.app.log("GameLogic", "Kucing dibangunkan.")
            pet.wakeUp()
            sleepButton.setText("Tidur")
        } else {
            Gdx.app.log("GameLogic", "Kucing mulai tidur.")
            pet.playSleeping()
            sleepButton.setText("Bangun")
        }
    }

    override fun render(delta: Float) {
        // 1. Update logika status virtual pet
        updatePetStatus(delta)

        // 2. Update animasi & bounding box 3D
        pet.update(delta)

        // 3. Bersihkan layar buffer warna dan kedalaman
        Gdx.gl.glViewport(0, 0, Gdx.graphics.width, Gdx.graphics.height)
        Gdx.gl.glClearColor(0.85f, 0.88f, 0.92f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT or GL20.GL_DEPTH_BUFFER_BIT)

        // 4. RENDER LAYER 1: 3D Scene PBR
        sceneManager.update(delta)
        sceneManager.render()

        // 5. RENDER LAYER 2: 2D Scene2D UI di atas layer 3D
        stage.act(delta)
        stage.draw()
    }

    /**
     * Pengurangan status lapar dan energi seiring berjalannya waktu
     */
    private fun updatePetStatus(delta: Float) {
        // Tingkat lapar berkurang perlahan
        hunger = (hunger - hungerDecayRate * delta).coerceIn(0f, 100f)

        // Energi bertambah jika tidur, berkurang jika bangun
        if (pet.currentState == PetState.SLEEPING) {
            energy = (energy + energyRegenRate * delta).coerceIn(0f, 100f)
        } else {
            energy = (energy - energyDecayRate * delta).coerceIn(0f, 100f)
        }

        // Sinkronisasi nilai ke elemen UI
        hungerBar.value = hunger
        energyBar.value = energy
        hungerLabel.setText("Lapar: ${hunger.toInt()}%")
        energyLabel.setText("Energi: ${energy.toInt()}%")
    }

    override fun resize(width: Int, height: Int) {
        // Update viewport 3D dan viewport 2D Stage secara proporsional
        sceneManager.updateViewport(width.toFloat(), height.toFloat())
        stage.viewport.update(width, height, true)
    }

    // --- Implementasi VoiceMimicCallback ---

    override fun onSpeechStarted() {
        Gdx.app.postRunnable {
            if (pet.currentState != PetState.SLEEPING) {
                pet.playListening()
            }
        }
    }

    override fun onSpeechCaptured(pcmData: ByteArray, sampleRate: Int, durationSeconds: Float) {
        Gdx.app.postRunnable {
            try {
                if (pet.currentState == PetState.SLEEPING) {
                    voiceBridge.startListening()
                    return@postRunnable
                }

                val tempWavFile: FileHandle = Gdx.files.local("mimic_cache.wav")
                val wavBytes = WavUtils.pcmToWav(pcmData, sampleRate)
                tempWavFile.writeBytes(wavBytes, false)

                currentSound?.stop()
                currentSound?.dispose()

                currentSound = Gdx.audio.newSound(tempWavFile)
                currentSound?.play(1.0f, chipmunkPitch, 0.0f)

                pet.playTalking()

                val actualPlaybackDuration = durationSeconds / chipmunkPitch
                Timer.schedule(object : Timer.Task() {
                    override fun run() {
                        if (pet.currentState != PetState.SLEEPING) {
                            pet.playIdle()
                        }
                        voiceBridge.startListening()
                    }
                }, actualPlaybackDuration)

            } catch (e: Exception) {
                Gdx.app.error("PetScreen", "Gagal memutar audio peniru", e)
                pet.playIdle()
                voiceBridge.startListening()
            }
        }
    }

    override fun onError(errorMessage: String) {
        Gdx.app.error("PetScreen", "Error mic/audio: $errorMessage")
    }

    override fun pause() {
        voiceBridge.stopListening()
    }

    override fun resume() {
        voiceBridge.startListening()
    }

    override fun dispose() {
        voiceBridge.stopListening()
        voiceBridge.setCallback(null)
        currentSound?.dispose()
        pet.dispose()
        sceneManager.dispose()
        stage.dispose()
        uiSkin.dispose()
    }

    // ========================================================
    // Helper: Pembuatan UI Skin secara programatik (Murni Kode)
    //         Mencegah crash karena file uiskin.json tidak ada
    // ========================================================
    private fun createCustomSkin(): Skin {
        val skin = Skin()
        val font = BitmapFont().apply {
            data.setScale(1.2f) // Skala font agar mudah dibaca di layar HP
        }
        skin.add("default-font", font)

        fun createSolidDrawable(color: Color): Drawable {
            val pixmap = Pixmap(1, 1, Pixmap.Format.RGBA8888).apply {
                setColor(color)
                fill()
            }
            val texture = Texture(pixmap)
            pixmap.dispose()
            return TextureRegionDrawable(TextureRegion(texture))
        }

        // Style Label
        val labelStyle = Label.LabelStyle(font, Color.WHITE)
        skin.add("default", labelStyle)

        // Style ProgressBar (Lapar & Energi)
        val bgDrawable = createSolidDrawable(Color(0.15f, 0.15f, 0.15f, 0.75f))
        val hungerFill = createSolidDrawable(Color(0.95f, 0.55f, 0.15f, 1.0f)) // Warna Oranye
        val energyFill = createSolidDrawable(Color(0.20f, 0.75f, 0.35f, 1.0f)) // Warna Hijau

        val hungerStyle = ProgressBar.ProgressBarStyle(bgDrawable, hungerFill).apply {
            knobBefore = hungerFill
        }
        skin.add("hunger-bar", hungerStyle)

        val energyStyle = ProgressBar.ProgressBarStyle(bgDrawable, energyFill).apply {
            knobBefore = energyFill
        }
        skin.add("energy-bar", energyStyle)

        // Style TextButton (Aksi)
        val btnUp = createSolidDrawable(Color(0.20f, 0.45f, 0.85f, 0.90f))   // Biru
        val btnDown = createSolidDrawable(Color(0.12f, 0.30f, 0.65f, 1.0f))  // Biru gelap saat ditekan
        val buttonStyle = TextButton.TextButtonStyle(btnUp, btnDown, btnUp, font).apply {
            fontColor = Color.WHITE
            downFontColor = Color.LIGHT_GRAY
        }
        skin.add("default", buttonStyle)

        return skin
    }
}
