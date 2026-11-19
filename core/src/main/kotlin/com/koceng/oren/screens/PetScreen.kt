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
import com.badlogic.gdx.utils.Timer
import com.koceng.oren.ai.AiChatBridge
import com.koceng.oren.audio.VoiceMimicBridge
import com.koceng.oren.audio.VoiceMimicCallback
import com.koceng.oren.audio.WavUtils
import com.koceng.oren.character.HitZone
import com.koceng.oren.character.PetCharacter
import com.koceng.oren.character.PetState
import com.koceng.oren.data.PetStateManager
import com.koceng.oren.input.PetTouchController
import com.koceng.oren.ui.PetHud
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

    // Lantai 3D (Pedestal)
    private var floorModel: Model? = null
    private var floorScene: Scene? = null

    // --- Virtual Pet State Manager & HUD ---
    private val petStateManager = PetStateManager()
    private lateinit var petHud: PetHud

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
            position.set(0f, 1.25f, 2.75f)
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

        val sunLight = DirectionalLightEx().apply {
            direction.set(1f, -2.5f, 1.5f).nor()
            color.set(Color.WHITE)
            intensity = 2.5f
        }
        sceneManager.environment.add(sunLight)
        sceneManager.setAmbientLight(0.6f)

        // ==========================================
        // 3. Tambahkan Lantai 3D (ModelBuilder)
        // ==========================================
        val modelBuilder = ModelBuilder()
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

        // ==========================================
        // 4. Memuat Model GLB Karakter
        // ==========================================
        pet = PetCharacter("models/cartoon_cat.glb")
        sceneManager.addScene(pet.scene)

        touchController = PetTouchController(camera, pet) { hitZone ->
            when (hitZone) {
                HitZone.HEAD -> {
                    petStateManager.play(5f)
                    petHud.showSubtitle("Aduh! Kepalaku pusing!", 2f)
                }
                HitZone.BELLY -> {
                    petStateManager.play(10f)
                    petHud.showSubtitle("Geli banget perutku, meong!", 2f)
                }
                HitZone.NONE -> {}
            }
        }

        // ==========================================
        // 5. Inisialisasi PetHud (Scene2D)
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
        }

        // ==========================================
        // 6. InputMultiplexer
        //    (PetHud Stage diprioritaskan PERTAMA)
        // ==========================================
        val multiplexer = InputMultiplexer()
        multiplexer.addProcessor(petHud.stage)       // Prioritas 1: UI Tombol & HUD
        multiplexer.addProcessor(touchController)   // Prioritas 2: Raycast 3D Karakter
        Gdx.input.inputProcessor = multiplexer

        // ==========================================
        // 7. Hubungkan Audio Mimic Bridge
        // ==========================================
        voiceBridge.setCallback(this)
        voiceBridge.checkAndRequestPermissions {
            if (!isAiMode) {
                voiceBridge.startListening()
            }
        }

        // ==========================================
        // 8. Hubungkan AI Chat Bridge
        // ==========================================
        setupAiChatCallbacks()
    }

    private fun setupHudCallbacks() {
        petHud.onFeedClicked = {
            petStateManager.feed(25f)
            pet.playEating()
            petHud.showSubtitle("Nyam nyam! Enak banget makannya!", 2.5f)
        }

        petHud.onSleepClicked = {
            petStateManager.sleep()
            if (petStateManager.isSleeping) {
                pet.playSleeping()
                petHud.showSubtitle("Zzz... Koceng Oren lagi tidur pulas.", 2.5f)
            } else {
                pet.wakeUp()
                petHud.showSubtitle("Hoam... Koceng Oren sudah bangun!", 2.5f)
            }
        }

        petHud.onPlayClicked = {
            petStateManager.play(20f)
            pet.playLaughing()
            petHud.showSubtitle("Meong! Asyik banget diajak main!", 2.5f)
        }

        petHud.onModeToggleClicked = {
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
            if (isAiMode) {
                if (aiChatBridge.isBusy()) {
                    aiChatBridge.stopAll()
                } else {
                    aiChatBridge.startListening()
                }
            }
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

        // 2. Update animasi 3D
        pet.update(delta)

        // 3. Clear buffer warna dan kedalaman
        Gdx.gl.glViewport(0, 0, Gdx.graphics.width, Gdx.graphics.height)
        Gdx.gl.glClearColor(0.85f, 0.88f, 0.92f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT or GL20.GL_DEPTH_BUFFER_BIT)

        // 4. RENDER LAYER 1: 3D Scene PBR (Lantai & Kucing)
        sceneManager.update(delta)
        sceneManager.render()

        // 5. RENDER LAYER 2: 2D Scene2D UI di atas 3D
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
        pet.dispose()
        floorModel?.dispose()
        sceneManager.dispose()
        petHud.dispose()
    }
}
