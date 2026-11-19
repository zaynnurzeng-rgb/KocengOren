package com.koceng.oren.screens

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.InputMultiplexer
import com.badlogic.gdx.ScreenAdapter
import com.badlogic.gdx.audio.Sound
import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.graphics.VertexAttributes
import com.badlogic.gdx.graphics.g3d.Material
import com.badlogic.gdx.graphics.g3d.Model
import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.Timer
import com.koceng.oren.ai.AiChatBridge
import com.koceng.oren.audio.ProceduralSfx
import com.koceng.oren.audio.VoiceMimicBridge
import com.koceng.oren.audio.VoiceMimicCallback
import com.koceng.oren.audio.WavUtils
import com.koceng.oren.character.HitZone
import com.koceng.oren.character.PetCharacter
import com.koceng.oren.character.PetState
import com.koceng.oren.data.PetStateManager
import com.koceng.oren.input.PetTouchController
import com.koceng.oren.ui.PetHud
import com.koceng.oren.ui.PetParticleOverlay
import net.mgsx.gltf.scene3d.attributes.PBRColorAttribute
import net.mgsx.gltf.scene3d.lights.DirectionalLightEx
import net.mgsx.gltf.scene3d.scene.Scene
import net.mgsx.gltf.scene3d.scene.SceneManager

class PetScreen(
    private val voiceBridge: VoiceMimicBridge,
    private val aiChatBridge: AiChatBridge
) : ScreenAdapter(), VoiceMimicCallback {

    // --- 3D Scene Components ---
    private lateinit var camera: PerspectiveCamera
    private lateinit var sceneManager: SceneManager
    private lateinit var pet: PetCharacter
    private lateinit var touchController: PetTouchController
    private lateinit var sunLight: DirectionalLightEx

    // Ruangan 3D (Lantai & Dinding)
    private var floorModel: Model? = null
    private var floorScene: Scene? = null
    private var wallModel: Model? = null
    private var wallScene: Scene? = null

    // Efek Kamera Subtle Zoom
    private val defaultCameraPos = Vector3(0f, 1.25f, 2.75f)
    private val zoomCameraPos = Vector3(0f, 1.15f, 2.25f)
    private val targetCameraPos = Vector3(defaultCameraPos)
    private val tempVec3 = Vector3()

    // Mode Pencahayaan Ruangan (Siang vs Malam / Lampu Tidur)
    private var currentLightIntensity = 2.5f
    private var targetLightIntensity = 2.5f
    private var currentAmbient = 0.6f
    private var targetAmbient = 0.6f
    private val dayClearColor = Color(0.85f, 0.88f, 0.92f, 1f)
    private val nightClearColor = Color(0.06f, 0.08f, 0.14f, 1f)
    private val currentClearColor = Color(dayClearColor)

    // --- Virtual Pet State Manager & HUD ---
    private val petStateManager = PetStateManager()
    private lateinit var petHud: PetHud

    // --- Efek Audio & Partikel Prosedural Tanpa Aset Eksternal ---
    private lateinit var sfx: ProceduralSfx
    private lateinit var particleOverlay: PetParticleOverlay
    private var zzzTimer = 0f

    // Mode: Tiru Suara (false) vs Ngobrol AI (true)
    private var isAiMode: Boolean = false

    // --- Audio Mimic ---
    private var currentSound: Sound? = null
    private val chipmunkPitch = 1.42f

    override fun show() {
        // ==========================================
        // 1. Inisialisasi Kamera 3D
        // ==========================================
        camera = PerspectiveCamera(60f, Gdx.graphics.width.toFloat(), Gdx.graphics.height.toFloat()).apply {
            position.set(defaultCameraPos)
            lookAt(0f, 0.95f, 0f)
            near = 0.1f
            far = 100f
            update()
        }

        // ==========================================
        // 2. Inisialisasi SceneManager PBR (64 bones)
        // ==========================================
        sceneManager = SceneManager(64)
        sceneManager.camera = camera

        sunLight = DirectionalLightEx().apply {
            direction.set(1f, -2.5f, 1.5f).nor()
            color.set(Color.WHITE)
            intensity = currentLightIntensity
        }
        sceneManager.environment.add(sunLight)
        sceneManager.setAmbientLight(currentAmbient)

        // ==========================================
        // 3. Tambahkan Ruangan 3D (Lantai & Dinding Belakang)
        // ==========================================
        val modelBuilder = ModelBuilder()

        // Lantai Pedestal Bundar
        val floorMaterial = Material(
            PBRColorAttribute.createBaseColorFactor(Color(0.80f, 0.84f, 0.90f, 1f))
        )
        floorModel = modelBuilder.createCylinder(
            3.6f, 0.12f, 3.6f, 32,
            floorMaterial,
            (VertexAttributes.Usage.Position or VertexAttributes.Usage.Normal).toLong()
        )
        val floorInstance = ModelInstance(floorModel).apply {
            transform.setToTranslation(0f, -0.06f, 0f)
        }
        floorScene = Scene(floorInstance)
        sceneManager.addScene(floorScene)

        // Dinding Belakang Ruangan
        val wallMaterial = Material(
            PBRColorAttribute.createBaseColorFactor(Color(0.92f, 0.90f, 0.86f, 1f))
        )
        wallModel = modelBuilder.createBox(
            5.0f, 3.6f, 0.1f,
            wallMaterial,
            (VertexAttributes.Usage.Position or VertexAttributes.Usage.Normal).toLong()
        )
        val wallInstance = ModelInstance(wallModel).apply {
            transform.setToTranslation(0f, 1.70f, -1.80f)
        }
        wallScene = Scene(wallInstance)
        sceneManager.addScene(wallScene)

        // ==========================================
        // 4. Inisialisasi SFX & Partikel Prosedural
        // ==========================================
        sfx = ProceduralSfx()
        particleOverlay = PetParticleOverlay()

        // ==========================================
        // 5. Memuat Model GLB Karakter
        // ==========================================
        pet = PetCharacter("models/cartoon_cat.glb")
        sceneManager.addScene(pet.scene)

        touchController = PetTouchController(camera, pet) { hitZone ->
            when (hitZone) {
                HitZone.HEAD -> {
                    sfx.playPoke()
                    val headPos = getHeadScreenPos()
                    particleOverlay.spawnStars(headPos.x, headPos.y)
                    petStateManager.play(5f)
                    petHud.showSubtitle("Aduh! Kepalaku pusing, meong!", 2f)
                }
                HitZone.BELLY -> {
                    sfx.playPurr()
                    val bellyPos = getBellyScreenPos()
                    particleOverlay.spawnHearts(bellyPos.x, bellyPos.y)
                    petStateManager.play(10f)
                    petHud.showSubtitle("Geli banget perutku, purr... meong!", 2f)
                }
                HitZone.NONE -> {}
            }
        }

        // ==========================================
        // 6. Inisialisasi PetHud (Scene2D)
        // ==========================================
        petHud = PetHud()
        setupHudCallbacks()

        // Sinkronisasi status awal dari preferences
        petHud.updateStatus(
            petStateManager.hunger,
            petStateManager.energy,
            petStateManager.happiness,
            petStateManager.isSleeping
        )
        if (petStateManager.isSleeping) {
            pet.playSleeping()
            targetLightIntensity = 0.35f
            targetAmbient = 0.12f
        }

        // ==========================================
        // 7. InputMultiplexer
        //    (PetHud Stage diprioritaskan PERTAMA)
        // ==========================================
        val multiplexer = InputMultiplexer()
        multiplexer.addProcessor(petHud.stage)       // Prioritas 1: UI Tombol & HUD
        multiplexer.addProcessor(touchController)   // Prioritas 2: Raycast 3D Karakter
        Gdx.input.inputProcessor = multiplexer

        // ==========================================
        // 8. Hubungkan Audio Mimic Bridge
        // ==========================================
        voiceBridge.setCallback(this)
        voiceBridge.checkAndRequestPermissions {
            if (!isAiMode) {
                voiceBridge.startListening()
            }
        }

        // ==========================================
        // 9. Hubungkan AI Chat Bridge
        // ==========================================
        setupAiChatCallbacks()
    }

    private fun getHeadScreenPos(): Vector3 {
        tempVec3.set(0f, 1.15f, 0f)
        return camera.project(tempVec3)
    }

    private fun getBellyScreenPos(): Vector3 {
        tempVec3.set(0f, 0.65f, 0f)
        return camera.project(tempVec3)
    }

    private fun setupHudCallbacks() {
        petHud.onFeedClicked = {
            sfx.playButton()
            sfx.playEat()
            val bellyPos = getBellyScreenPos()
            particleOverlay.spawnCrumbs(bellyPos.x, bellyPos.y + 40f)
            petStateManager.feed(25f)
            pet.playEating()
            petHud.showSubtitle("Nyam nyam! Enak banget makannya!", 2.5f)
        }

        petHud.onSleepClicked = {
            sfx.playButton()
            petStateManager.sleep()
            if (petStateManager.isSleeping) {
                pet.playSleeping()
                targetLightIntensity = 0.35f
                targetAmbient = 0.12f
                petHud.showSubtitle("Lampu dimatikan... Zzz... Koceng Oren tidur pulas.", 2.5f)
            } else {
                pet.wakeUp()
                targetLightIntensity = 2.5f
                targetAmbient = 0.6f
                petHud.showSubtitle("Lampu dinyalakan! Hoam... Koceng Oren bangun!", 2.5f)
            }
        }

        petHud.onPlayClicked = {
            sfx.playButton()
            sfx.playPurr()
            val bellyPos = getBellyScreenPos()
            particleOverlay.spawnHearts(bellyPos.x, bellyPos.y)
            petStateManager.play(20f)
            pet.playLaughing()
            petHud.showSubtitle("Meong! Asyik banget diajak main!", 2.5f)
        }

        petHud.onModeToggleClicked = {
            sfx.playButton()
            isAiMode = !isAiMode
            petHud.setMode(isAiMode)

            // Anti-bentrok Mikrofon
            if (isAiMode) {
                voiceBridge.stopListening()
                aiChatBridge.stopAll()
            } else {
                aiChatBridge.stopAll()
                voiceBridge.startListening()
            }
        }

        petHud.onTalkClicked = {
            sfx.playButton()
            if (isAiMode) {
                if (aiChatBridge.isBusy()) {
                    aiChatBridge.stopAll()
                } else {
                    aiChatBridge.startListening()
                }
            }
        }

        petHud.onResetChatClicked = {
            sfx.playButton()
            aiChatBridge.resetChatHistory()
            petHud.showSubtitle("Ingatan obrolan di-reset! Meong siap ngobrol topik baru.", 3f)
        }
    }

    private fun setupAiChatCallbacks() {
        aiChatBridge.onListeningStateChanged = { isListening ->
            petHud.setTalkButtonState(isListening)
            if (isListening) {
                pet.playListening()
            }
        }

        aiChatBridge.onReplyText = { reply ->
            petHud.showSubtitle(reply, 0f)
        }

        aiChatBridge.onSpeakingStateChanged = { isSpeaking ->
            if (isSpeaking) {
                pet.playTalking()
            } else {
                if (pet.currentState != PetState.SLEEPING) {
                    pet.playIdle()
                }
            }
        }

        aiChatBridge.onError = { error ->
            petHud.showSubtitle(error, 4f)
            if (pet.currentState != PetState.SLEEPING) {
                pet.playIdle()
            }
        }
    }

    override fun render(delta: Float) {
        // 1. Update logika kebutuhan virtual pet
        petStateManager.update(delta)
        petHud.updateStatus(
            petStateManager.hunger,
            petStateManager.energy,
            petStateManager.happiness,
            petStateManager.isSleeping
        )
        aiChatBridge.updatePetStatus(
            petStateManager.hunger,
            petStateManager.energy,
            petStateManager.happiness
        )

        // 2. Partikel Zzz berkala saat kucing tidur
        if (petStateManager.isSleeping) {
            zzzTimer += delta
            if (zzzTimer >= 1.25f) {
                zzzTimer = 0f
                val headPos = getHeadScreenPos()
                particleOverlay.spawnZzz(headPos.x, headPos.y + 20f)
            }
        } else {
            zzzTimer = 0f
        }

        // 3. Efek Transisi Pencahayaan Halus (Siang vs Malam / Lampu Tidur)
        val lightLerp = delta * 3.5f
        currentLightIntensity = MathUtils.lerp(currentLightIntensity, targetLightIntensity, lightLerp)
        currentAmbient = MathUtils.lerp(currentAmbient, targetAmbient, lightLerp)
        sunLight.intensity = currentLightIntensity
        sceneManager.setAmbientLight(currentAmbient)

        val targetClearColor = if (petStateManager.isSleeping) nightClearColor else dayClearColor
        currentClearColor.lerp(targetClearColor, lightLerp)

        // 4. Efek Kamera Subtle Zoom saat Berbicara atau Bereaksi
        val shouldZoom = pet.currentState == PetState.TALKING ||
                         pet.currentState == PetState.DIZZY ||
                         pet.currentState == PetState.LAUGHING ||
                         pet.currentState == PetState.LISTENING
        targetCameraPos.set(if (shouldZoom) zoomCameraPos else defaultCameraPos)
        camera.position.lerp(targetCameraPos, delta * 3.0f)
        camera.lookAt(0f, 0.95f, 0f)
        camera.update()

        // 5. Update animasi karakter (Klip GLB + Animasi Prosedural)
        pet.update(delta)

        // 6. Clear buffer warna dan kedalaman
        Gdx.gl.glViewport(0, 0, Gdx.graphics.width, Gdx.graphics.height)
        Gdx.gl.glClearColor(currentClearColor.r, currentClearColor.g, currentClearColor.b, currentClearColor.a)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT or GL20.GL_DEPTH_BUFFER_BIT)

        // 7. RENDER LAYER 1: 3D Scene PBR (Lantai, Dinding, & Karakter Kucing)
        sceneManager.update(delta)
        sceneManager.render()

        // 8. RENDER LAYER 2: Efek Partikel Visual Prosedural
        particleOverlay.render(delta, Gdx.graphics.width.toFloat(), Gdx.graphics.height.toFloat())

        // 9. RENDER LAYER 3: 2D Scene2D UI (HUD, Subtitle, & Tombol)
        petHud.render(delta)
    }

    override fun resize(width: Int, height: Int) {
        sceneManager.updateViewport(width.toFloat(), height.toFloat())
        petHud.resize(width, height)
    }

    // --- Callback Voice Mimic (Mode Tiru Suara) ---

    override fun onSpeechStarted() {
        if (isAiMode || pet.currentState == PetState.SLEEPING) return

        Gdx.app.postRunnable {
            pet.playListening()
            petHud.showSubtitle("Mendengarkan...", 0f)
        }
    }

    override fun onSpeechCaptured(pcmData: ByteArray, sampleRate: Int, durationSeconds: Float) {
        if (isAiMode || pet.currentState == PetState.SLEEPING) {
            voiceBridge.startListening()
            return
        }

        Gdx.app.postRunnable {
            try {
                val tempWavFile: FileHandle = Gdx.files.local("mimic_cache.wav")
                val wavBytes = WavUtils.pcmToWav(pcmData, sampleRate)
                tempWavFile.writeBytes(wavBytes, false)

                currentSound?.stop()
                currentSound?.dispose()

                currentSound = Gdx.audio.newSound(tempWavFile)
                currentSound?.play(1.0f, chipmunkPitch, 0.0f)

                pet.playTalking()
                petHud.showSubtitle("Menirukan suara!", durationSeconds / chipmunkPitch)

                val actualDuration = durationSeconds / chipmunkPitch
                Timer.schedule(object : Timer.Task() {
                    override fun run() {
                        if (pet.currentState != PetState.SLEEPING) {
                            pet.playIdle()
                        }
                        if (!isAiMode) {
                            voiceBridge.startListening()
                        }
                    }
                }, actualDuration)
            } catch (e: Exception) {
                pet.playIdle()
                if (!isAiMode) {
                    voiceBridge.startListening()
                }
            }
        }
    }

    override fun onError(errorMessage: String) {
        if (!isAiMode) {
            petHud.showSubtitle(errorMessage, 3f)
        }
    }

    override fun pause() {
        petStateManager.save()
        voiceBridge.stopListening()
        aiChatBridge.stopAll()
    }

    override fun resume() {
        if (!isAiMode) {
            voiceBridge.startListening()
        }
    }

    override fun dispose() {
        petStateManager.save()
        voiceBridge.stopListening()
        voiceBridge.setCallback(null)
        aiChatBridge.stopAll()

        currentSound?.dispose()
        sfx.dispose()
        particleOverlay.dispose()
        pet.dispose()
        floorModel?.dispose()
        wallModel?.dispose()
        sceneManager.dispose()
        petHud.dispose()
    }
}
