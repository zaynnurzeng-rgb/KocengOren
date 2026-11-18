package com.koceng.oren.screens

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.ScreenAdapter
import com.badlogic.gdx.audio.Sound
import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.Timer
import com.koceng.oren.audio.VoiceMimicBridge
import com.koceng.oren.audio.VoiceMimicCallback
import com.koceng.oren.audio.WavUtils
import com.koceng.oren.character.HitZone
import com.koceng.oren.character.PetCharacter
import com.koceng.oren.input.PetTouchController
import net.mgsx.gltf.scene3d.lights.DirectionalLightEx
import net.mgsx.gltf.scene3d.scene.SceneManager
import net.mgsx.gltf.scene3d.utils.IBLBuilder

class PetScreen(private val voiceBridge: VoiceMimicBridge) : ScreenAdapter(), VoiceMimicCallback {

    private lateinit var camera: PerspectiveCamera
    private lateinit var sceneManager: SceneManager
    private lateinit var pet: PetCharacter
    private lateinit var touchController: PetTouchController

    private var currentSound: Sound? = null
    private val chipmunkPitch = 1.42f // Pitch dinaikkan ~42% untuk suara chipmunk Talking Tom

    override fun show() {
        // 1. Inisialisasi Kamera Perspektif
        camera = PerspectiveCamera(60f, Gdx.graphics.width.toFloat(), Gdx.graphics.height.toFloat()).apply {
            position.set(0f, 1.2f, 2.8f)
            lookAt(0f, 0.9f, 0f)
            near = 0.1f
            far = 100f
            update()
        }

        // 2. Inisialisasi SceneManager PBR dari gdx-gltf
        sceneManager = SceneManager()
        sceneManager.camera = camera

        // 3. Konfigurasi Pencahayaan PBR & IBL Dasar
        val sunLight = DirectionalLightEx().apply {
            direction.set(1f, -2.5f, 1.5f).nor()
            color.set(Color.WHITE)
            intensity = 2.5f
        }
        sceneManager.environment.add(sunLight)

        // Bangun pencahayaan ambient / IBL sintetis secara procedural
        val iblBuilder = IBLBuilder.createOutdoor(sunLight)
        val environmentCubemap = iblBuilder.buildEnvMap(512)
        val diffuseCubemap = iblBuilder.buildIrradianceMap(128)
        val specularCubemap = iblBuilder.buildRadianceMap(10)
        iblBuilder.dispose()

        sceneManager.setAmbientLight(0.35f)
        sceneManager.environment.set(net.mgsx.gltf.scene3d.attributes.PBRCubemapAttribute.createSpecularEnv(specularCubemap))
        sceneManager.environment.set(net.mgsx.gltf.scene3d.attributes.PBRCubemapAttribute.createDiffuseEnv(diffuseCubemap))

        // 4. Memuat Model Karakter 3D (.glb)
        pet = PetCharacter("models/cartoon_cat.glb")
        sceneManager.addScene(pet.scene)

        // 5. Pasang InputProcessor untuk Raycast Touch
        touchController = PetTouchController(camera, pet) { hitZone ->
            when (hitZone) {
                HitZone.HEAD -> Gdx.app.log("PetScreen", "Animasi Dizzy aktif")
                HitZone.BELLY -> Gdx.app.log("PetScreen", "Animasi Laughing aktif")
                HitZone.NONE -> {}
            }
        }
        Gdx.input.inputProcessor = touchController

        // 6. Hubungkan Voice Mimic Bridge (Native Mic)
        voiceBridge.setCallback(this)
        voiceBridge.checkAndRequestPermissions {
            Gdx.app.log("PetScreen", "Izin mic diberikan, memulai monitoring suara...")
            voiceBridge.startListening()
        }
    }

    override fun render(delta: Float) {
        // Update animasi & bounding box karakter
        pet.update(delta)

        // Bersihkan layar
        Gdx.gl.glViewport(0, 0, Gdx.graphics.width, Gdx.graphics.height)
        Gdx.gl.glClearColor(0.85f, 0.88f, 0.92f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT or GL20.GL_DEPTH_BUFFER_BIT)

        // Render Scene 3D dengan PBR Shaders
        sceneManager.update(delta)
        sceneManager.render()
    }

    override fun resize(width: Int, height: Int) {
        sceneManager.updateViewport(width.toFloat(), height.toFloat())
    }

    // --- Implementasi VoiceMimicCallback ---

    override fun onSpeechStarted() {
        Gdx.app.postRunnable {
            Gdx.app.log("PetScreen", "Suara terdeteksi! Memutar animasi Listening...")
            pet.playListening()
        }
    }

    override fun onSpeechCaptured(pcmData: ByteArray, sampleRate: Int, durationSeconds: Float) {
        Gdx.app.postRunnable {
            try {
                Gdx.app.log("PetScreen", "Rekaman selesai: ${pcmData.size} bytes (${durationSeconds}s). Memproses playback chipmunk...")

                // Buat file WAV lokal sementara dari buffer PCM mentah
                val tempWavFile: FileHandle = Gdx.files.local("mimic_cache.wav")
                val wavBytes = WavUtils.pcmToWav(pcmData, sampleRate)
                tempWavFile.writeBytes(wavBytes, false)

                // Hentikan suara sebelumnya bila masih berjalan
                currentSound?.stop()
                currentSound?.dispose()

                // Muat file WAV ke Sound API libGDX
                currentSound = Gdx.audio.newSound(tempWavFile)

                // Putar suara dengan pitch dinaikkan (efek chipmunk Talking Tom)
                currentSound?.play(1.0f, chipmunkPitch, 0.0f)

                // Mainkan animasi Talking saat audio diputar
                pet.playTalking()

                // Hitung durasi playback setelah pitch dinaikkan: durasiAsli / pitch
                val actualPlaybackDuration = durationSeconds / chipmunkPitch

                // Jadwalkan pengembalian ke Idle dan aktifkan mic kembali setelah selesai bicara
                Timer.schedule(object : Timer.Task() {
                    override fun run() {
                        Gdx.app.log("PetScreen", "Playback selesai, kembali ke Idle dan mendengarkan lagi.")
                        pet.playIdle()
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
    }
}
