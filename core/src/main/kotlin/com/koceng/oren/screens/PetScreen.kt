package com.koceng.oren.screens

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.InputMultiplexer
import com.badlogic.gdx.ScreenAdapter
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Vector3
import com.koceng.oren.ai.AiChatBridge
import com.koceng.oren.ai.ChatMode
import com.koceng.oren.ai.RoomTheme
import com.koceng.oren.audio.ProceduralSfx
import com.koceng.oren.audio.VoiceMimicBridge
import com.koceng.oren.character.HitZone
import com.koceng.oren.character.PetCharacter
import com.koceng.oren.character.PetState
import com.koceng.oren.input.PetTouchController
import com.koceng.oren.ui.HudAiState
import com.koceng.oren.ui.PetHud
import com.koceng.oren.ui.PetParticleOverlay
import net.mgsx.gltf.scene3d.lights.DirectionalLightEx
import net.mgsx.gltf.scene3d.scene.SceneManager

class PetScreen(
    private val voiceBridge: VoiceMimicBridge,
    private val aiChatBridge: AiChatBridge
) : ScreenAdapter() {

    // -- 3D Scene Components --
    private lateinit var camera: PerspectiveCamera
    private lateinit var sceneManager: SceneManager
    private lateinit var pet: PetCharacter
    private lateinit var touchController: PetTouchController
    private lateinit var sunLight: DirectionalLightEx

    // -- 2D Cartoon Room Backdrop --
    private lateinit var backdropBatch: SpriteBatch
    private lateinit var roomBackdropTexture: Texture

    // Efek Kamera (Jarak ideal agar seluruh tubuh kucing terlihat utuh)
    private val defaultCameraPos = Vector3(0f, 1.40f, 5.20f)
    private val zoomCameraPos = Vector3(0f, 1.35f, 4.65f)
    private val cameraLookAtY = 0.88f
    private val targetCameraPos = Vector3(defaultCameraPos)
    private val tempVec3 = Vector3()

    // --- HUD & Efek Audio/Partikel ---
    private lateinit var petHud: PetHud
    private lateinit var sfx: ProceduralSfx
    private lateinit var particleOverlay: PetParticleOverlay

    // Mode AI: Obrolan Santai vs PR Sekolah
    private var isHomeworkMode: Boolean = false
    private var currentBackdropTint = Color(1f, 1f, 1f, 1f)
    private var targetBackdropTint = Color(1f, 1f, 1f, 1f)
    private var targetLightIntensity = 2.6f
    private var currentLightIntensity = 2.6f
    private var targetAmbient = 0.72f
    private var currentAmbient = 0.72f
    private var isAiThinking = false
    private var thinkingParticleTimer = 0f
    override fun show() {
        // ==========================================
        // 1. Inisialisasi Kamera 3D
        // ==========================================
        camera = PerspectiveCamera(50f, Gdx.graphics.width.toFloat(), Gdx.graphics.height.toFloat()).apply {
            position.set(defaultCameraPos)
            lookAt(0f, cameraLookAtY, 0f)
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
            intensity = 2.6f
        }
        sceneManager.environment.add(sunLight)
        sceneManager.setAmbientLight(0.72f)

        // ==========================================
        // 3. Inisialisasi Latar Belakang Ruangan Kartun
        // ==========================================
        backdropBatch = SpriteBatch()
        roomBackdropTexture = if (Gdx.files.internal("bg_room.png").exists()) {
            Texture(Gdx.files.internal("bg_room.png")).apply {
                setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear)
            }
        } else {
            createCartoonRoomTexture()
        }

        // ==========================================
        // 4. Inisialisasi SFX & Partikel Prosedural
        // ==========================================
        sfx = ProceduralSfx()
        particleOverlay = PetParticleOverlay()

        // ==========================================
        // 5. Memuat Model GLB Karakter & Interaksi Sentuh
        // ==========================================
        pet = PetCharacter("models/cartoon_cat.glb")
        sceneManager.addScene(pet.scene)

        touchController = PetTouchController(camera, pet) { hitZone ->
            when (hitZone) {
                HitZone.HEAD -> {
                    sfx.playPoke()
                    val headPos = getHeadScreenPos()
                    particleOverlay.spawnStars(headPos.x, headPos.y)
                 particleOverlay.spawnAngerMarks(headPos.x, headPos.y + 20f)
                    if (!aiChatBridge.isBusy()) {
                        val reaction = listOf(
                            "Woy! Jangan colek-colek kepala gue, nggak sopan banget sih lu!",
                            "Aduh! Tangan lu nggak bisa diem ya, pusing nih kepala gue!",
                            "Heh! Kepala gue bukan tombol, jangan asal pencet!"
                            ).random()
                        showAndSpeak(reaction)
                    }
                }
                HitZone.BELLY -> {
                    sfx.playPurr()
                    val bellyPos = getBellyScreenPos()
                    particleOverlay.spawnHearts(bellyPos.x, bellyPos.y)
                    if (!aiChatBridge.isBusy()) {
                        val reaction = listOf(
                            "Apaan sih pegang-pegang perut gue? Geli woy, minggir sana!",
                            "Heh! Jangan colek perut gue, emang lu kira gue boneka?",
                            "Idih, tangan lu gatel amat megang-pegang perut gue!"
                            ).random()
                        showAndSpeak(reaction)
                    }
                }
                HitZone.NONE -> {}
            }
        }

        // ==========================================
        // 6. Inisialisasi PetHud (AI Pet UI)
        // ==========================================
        petHud = PetHud()
        setupHudCallbacks()

        // ==========================================
        // 7. InputMultiplexer (UI diprioritaskan pertama)
        // ==========================================
        val multiplexer = InputMultiplexer()
        if (!aiChatBridge.useNativeHud) multiplexer.addProcessor(petHud.stage)
        multiplexer.addProcessor(touchController)
        Gdx.input.inputProcessor = multiplexer

        // ==========================================
        // 8. Minta Izin Mikrofon & Hubungkan Gemini AI Bridge
        // ==========================================
        voiceBridge.stopListening()
        voiceBridge.checkAndRequestPermissions { }

        aiChatBridge.setChatMode(ChatMode.CHAT)
        setupAiChatCallbacks()
        aiChatBridge.speakText("Apaan sih manggil-manggil? Mau ngobrol atau nyuruh gue ngerjain PR lu lagi?")
        applyRoomTheme(aiChatBridge.getSavedRoomTheme())
    }

    private fun applyRoomTheme(theme: RoomTheme) {
        when (theme) {
            RoomTheme.DAY -> { targetBackdropTint.set(1f, 1f, 1f, 1f); targetLightIntensity = 2.6f; targetAmbient = 0.72f }
            RoomTheme.NIGHT -> { targetBackdropTint.set(0.32f, 0.38f, 0.60f, 1f); targetLightIntensity = 0.9f; targetAmbient = 0.28f }
            RoomTheme.STUDY -> { targetBackdropTint.set(1.0f, 0.90f, 0.72f, 1f); targetLightIntensity = 2.2f; targetAmbient = 0.62f }
        }
    }
    private fun getHeadScreenPos(): Vector3 {
        tempVec3.set(0f, 1.55f, 0f)
        return camera.project(tempVec3)
    }

    private fun getBellyScreenPos(): Vector3 {
        tempVec3.set(0f, 0.85f, 0f)
        return camera.project(tempVec3)
    }
    private fun showAndSpeak(text: String) {
        if (aiChatBridge.useNativeHud) aiChatBridge.updateNativeBubble(null, text) else petHud.showReply(text)
        aiChatBridge.speakText(text)
    }
    
    private fun setupHudCallbacks() {
        petHud.onTalkClicked = {
            sfx.playButton()
            if (aiChatBridge.isBusy()) {
                aiChatBridge.stopAll()
                petHud.setStatusState(HudAiState.IDLE)
                pet.playIdle()
            } else {
                aiChatBridge.startListening()
            }
        }

        petHud.onHomeworkToggleClicked = {
            sfx.playButton()
            isHomeworkMode = !isHomeworkMode
            aiChatBridge.setChatMode(if (isHomeworkMode) ChatMode.HOMEWORK else ChatMode.CHAT)
            petHud.setHomeworkMode(isHomeworkMode)
            if (isHomeworkMode) {
               showAndSpeak("Mode PR Sekolah aktif! Ya udah, sebutin atau pencet Ketik buat kasih soal PR lu sekarang.")
            } else {
               showAndSpeak("Mode obrolan santai balik lagi. Mau ngomong apaan lu?")
            }
        }

        petHud.onTypeClicked = {
            sfx.playButton()
            aiChatBridge.openTextInputDialog(isHomeworkMode)
        }

        petHud.onResetChatClicked = {
            sfx.playButton()
            aiChatBridge.stopAll()
            aiChatBridge.resetChatHistory()
            petHud.setStatusState(HudAiState.IDLE)
            petHud.clearChat()
            aiChatBridge.speakText("Ingatan obrolan udah gue hapus! Mau bahas apa lagi sekarang?")
            pet.playIdle()
        }
        petHud.onPhotoHomeworkClicked = { sfx.playButton(); isHomeworkMode = true; petHud.setHomeworkMode(true); aiChatBridge.setChatMode(ChatMode.HOMEWORK); aiChatBridge.openPhotoHomeworkPicker() }
        petHud.onCopyClicked = { sfx.playButton(); aiChatBridge.copyLastReplyToClipboard() }
    petHud.onHistoryClicked = { sfx.playButton(); aiChatBridge.openChatHistoryDialog() }
    petHud.onSettingsClicked = { sfx.playButton(); aiChatBridge.openSettingsDialog() }
    }

    private fun setupAiChatCallbacks() {
        aiChatBridge.onListeningStateChanged = { isListening ->
            if (isListening) {
                petHud.setStatusState(HudAiState.LISTENING)
                pet.playListening()
            } else if (!aiChatBridge.isBusy()) {
                petHud.setStatusState(HudAiState.IDLE)
                pet.playIdle()
            }
        }

        aiChatBridge.onThinkingStateChanged = { isThinking ->
            isAiThinking = isThinking
            thinkingParticleTimer = 0f
            if (isThinking) {
                petHud.setStatusState(HudAiState.THINKING)
                pet.playListening()
            }
        }

        aiChatBridge.onUserMessage = { msg ->
            petHud.showUserMessage(msg)
        }

        aiChatBridge.onReplyText = { reply ->
            petHud.showReply(reply)
        if (isHomeworkMode || reply.contains("Jawaban akhir", ignoreCase = true)) { val hp = getHeadScreenPos(); particleOverlay.spawnIdeaBulbs(hp.x, hp.y + 30f) }
        }

        aiChatBridge.onSpeakingStateChanged = { isSpeaking ->
            if (isSpeaking) {
                petHud.setStatusState(HudAiState.SPEAKING)
                pet.playTalking()
            } else {
                petHud.setStatusState(HudAiState.IDLE)
                pet.playIdle()
            }
        }

        aiChatBridge.onError = { error ->
            petHud.setStatusState(HudAiState.IDLE)
            petHud.showReply(error)
            pet.playIdle()
        }
        aiChatBridge.onRoomThemeChanged = { theme -> applyRoomTheme(theme) }
    }

    override fun render(delta: Float) {
        // 1. Efek Kamera Subtle Zoom saat Mendengar / Berbicara / Bereaksi
        val shouldZoom = pet.currentState == PetState.TALKING ||
                       pet.currentState == PetState.DIZZY ||
                       pet.currentState == PetState.LAUGHING ||
                       pet.currentState == PetState.LISTENING
        targetCameraPos.set(if (shouldZoom) zoomCameraPos else defaultCameraPos)
        camera.position.lerp(targetCameraPos, delta * 3.0f)
        camera.lookAt(0f, cameraLookAtY, 0f)
        camera.update()

        // 2. Update animasi karakter
        pet.update(delta)
        currentBackdropTint.lerp(targetBackdropTint, delta * 3.5f); currentLightIntensity += (targetLightIntensity - currentLightIntensity) * minOf(1f, delta * 3.5f); currentAmbient += (targetAmbient - currentAmbient) * minOf(1f, delta * 3.5f); sunLight.intensity = currentLightIntensity; sceneManager.setAmbientLight(currentAmbient)
                if (isAiThinking) { thinkingParticleTimer += delta; if (thinkingParticleTimer >= 0.9f) { val hp = getHeadScreenPos(); particleOverlay.spawnQuestionMarks(hp.x, hp.y + 30f); thinkingParticleTimer = 0f } }
        // 3. Clear buffer warna & kedalaman
        val w = Gdx.graphics.width.toFloat()
        val h = Gdx.graphics.height.toFloat()
        Gdx.gl.glViewport(0, 0, Gdx.graphics.width, Gdx.graphics.height)
        Gdx.gl.glClearColor(0.35f, 0.65f, 0.85f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT or GL20.GL_DEPTH_BUFFER_BIT)

        // 4. RENDER LAYER 0: Latar Belakang Ruangan Kartun 2D
        backdropBatch.projectionMatrix.setToOrtho2D(0f, 0f, w, h)
        backdropBatch.color = currentBackdropTint
        backdropBatch.begin()
        backdropBatch.draw(roomBackdropTexture, 0f, 0f, w, h)
        backdropBatch.end()

        // Reset depth buffer agar model 3D kucing selalu tampil di atas backdrop
        Gdx.gl.glClear(GL20.GL_DEPTH_BUFFER_BIT)

        // 5. RENDER LAYER 1: 3D Scene PBR (Karakter Kucing)
        sceneManager.update(delta)
        sceneManager.render()

        // 6. RENDER LAYER 2: Efek Partikel Visual Prosedural
        particleOverlay.render(delta, w, h)

        // 7. RENDER LAYER 3: 2D Scene2D UI (HUD AI Pet)
        if (!aiChatBridge.useNativeHud) petHud.render(delta)
    }

    override fun resize(width: Int, height: Int) {
        sceneManager.updateViewport(width.toFloat(), height.toFloat())
        petHud.resize(width, height)
    }
    // --- Generator Latar Belakang Ruangan Kartun Hangat (Tanpa Aset Eksternal) ---
    private fun createCartoonRoomTexture(): Texture {
        val w = 540
        val h = 960
        val pm = Pixmap(w, h, Pixmap.Format.RGBA8888)
        pm.blending = Pixmap.Blending.SourceOver

        fun fillVGrad(x: Int, y: Int, rw: Int, rh: Int, top: Color, bottom: Color) {
            val c = Color()
            for (dy in 0 until rh) {
                val t = dy.toFloat() / (rh - 1).coerceAtLeast(1)
                c.set(top).lerp(bottom, t)
                pm.setColor(c)
                pm.fillRectangle(x, y + dy, rw, 1)
            }
        }

        fun fillRoundRect(x: Int, y: Int, rw: Int, rh: Int, r: Int, color: Color) {
            val rad = r.coerceAtMost(minOf(rw, rh) / 2)
            pm.setColor(color)
            pm.fillRectangle(x + rad, y, rw - 2 * rad, rh)
            pm.fillRectangle(x, y + rad, rw, rh - 2 * rad)
            pm.fillCircle(x + rad, y + rad, rad)
            pm.fillCircle(x + rw - rad - 1, y + rad, rad)
            pm.fillCircle(x + rad, y + rh - rad - 1, rad)
            pm.fillCircle(x + rw - rad - 1, y + rh - rad - 1, rad)
        }

        val wallBottomY = (h * 0.65f).toInt()
        val ceilingH = (h * 0.11f).toInt()
        val cornerX = (w * 0.44f).toInt()

        // 1. Langit-langit krem hangat
        fillVGrad(0, 0, w, ceilingH, Color(0.95f, 0.90f, 0.82f, 1f), Color(0.89f, 0.82f, 0.72f, 1f))

        // 2. Dinding biru kartun
        fillVGrad(0, ceilingH, cornerX, wallBottomY - ceilingH, Color(0.36f, 0.69f, 0.89f, 1f), Color(0.45f, 0.77f, 0.93f, 1f))
        fillVGrad(cornerX, ceilingH, w - cornerX, wallBottomY - ceilingH, Color(0.44f, 0.75f, 0.93f, 1f), Color(0.55f, 0.83f, 0.96f, 1f))

        pm.setColor(0.28f, 0.56f, 0.76f, 0.45f)
        pm.fillRectangle(cornerX - 2, ceilingH, 4, wallBottomY - ceilingH)
        pm.setColor(0.84f, 0.76f, 0.66f, 1f)
        pm.fillRectangle(0, ceilingH - 4, w, 7)

        // 3. Jendela kiri
        val winX = 52
        val winY = ceilingH + 54
        val winW = 156
        val winH = 210
        pm.setColor(0.24f, 0.48f, 0.66f, 0.35f)
        pm.fillRectangle(winX + 6, winY + 8, winW, winH)
        fillRoundRect(winX - 8, winY - 8, winW + 16, winH + 16, 10, Color(0.96f, 0.95f, 0.92f, 1f))
        fillVGrad(winX + 4, winY + 4, winW - 8, winH - 8, Color(0.48f, 0.82f, 0.98f, 1f), Color(0.82f, 0.95f, 1f, 1f))
        pm.setColor(1f, 1f, 1f, 0.92f)
        pm.fillCircle(winX + 46, winY + 56, 18)
        pm.fillCircle(winX + 66, winY + 50, 22)
        pm.fillCircle(winX + 88, winY + 58, 16)
        pm.setColor(0.45f, 0.78f, 0.34f, 1f)
        pm.fillCircle(winX + 36, winY + winH - 28, 36)
        pm.fillCircle(winX + 90, winY + winH - 18, 42)
        pm.fillCircle(winX + 135, winY + winH - 34, 30)
        pm.setColor(0.96f, 0.95f, 0.92f, 1f)
        pm.fillRectangle(winX + winW / 2 - 5, winY, 10, winH)
        pm.fillRectangle(winX, winY + winH / 2 - 5, winW, 10)
        fillRoundRect(winX - 18, winY - 26, winW + 36, 10, 5, Color(0.76f, 0.52f, 0.28f, 1f))

        // 4. Jendela kanan
        val rWinX = w - 132
        val rWinY = ceilingH + 68
        val rWinW = 140
        val rWinH = 200
        fillRoundRect(rWinX - 8, rWinY - 8, rWinW + 16, rWinH + 16, 10, Color(0.96f, 0.95f, 0.92f, 1f))
        fillVGrad(rWinX + 4, rWinY + 4, rWinW - 8, rWinH - 8, Color(0.50f, 0.84f, 0.98f, 1f), Color(0.84f, 0.96f, 1f, 1f))
        pm.setColor(0.48f, 0.80f, 0.36f, 1f)
        pm.fillCircle(rWinX + 44, rWinY + rWinH - 26, 36)
        pm.fillCircle(rWinX + 102, rWinY + rWinH - 18, 42)
        pm.setColor(0.96f, 0.95f, 0.92f, 1f)
        pm.fillRectangle(rWinX + 58, rWinY, 10, rWinH)
        pm.fillRectangle(rWinX, rWinY + rWinH / 2 - 5, rWinW, 10)

        // 5. Bingkai foto di dinding belakang
        fun drawFrame(fx: Int, fy: Int, fw: Int, fh: Int, bg: Color, accent: Color) {
            pm.setColor(0.20f, 0.42f, 0.60f, 0.30f)
            pm.fillRectangle(fx + 4, fy + 5, fw, fh)
            fillRoundRect(fx, fy, fw, fh, 6, Color(0.76f, 0.52f, 0.28f, 1f))
            pm.setColor(bg)
            pm.fillRectangle(fx + 6, fy + 6, fw - 12, fh - 12)
            pm.setColor(accent)
            pm.fillCircle(fx + fw / 2, fy + fh / 2 + 2, (fw - 24) / 2)
        }
        drawFrame(cornerX + 46, ceilingH + 86, 54, 56, Color(0.98f, 0.94f, 0.84f, 1f), Color(0.95f, 0.62f, 0.25f, 1f))
        drawFrame(cornerX + 110, ceilingH + 74, 58, 66, Color(0.95f, 0.97f, 0.92f, 1f), Color(0.65f, 0.52f, 0.44f, 1f))
        drawFrame(cornerX + 52, ceilingH + 154, 56, 56, Color(0.92f, 0.95f, 0.99f, 1f), Color(0.94f, 0.54f, 0.24f, 1f))

        // 6. Baseboard
        pm.setColor(0.94f, 0.93f, 0.90f, 1f)
        pm.fillRectangle(0, wallBottomY - 16, w, 16)
        pm.setColor(0.78f, 0.76f, 0.72f, 1f)
        pm.fillRectangle(0, wallBottomY - 16, w, 2)

        // 7. Lantai kayu
        fillVGrad(0, wallBottomY, w, h - wallBottomY, Color(0.89f, 0.63f, 0.36f, 1f), Color(0.76f, 0.48f, 0.24f, 1f))
        pm.setColor(0.64f, 0.39f, 0.18f, 0.35f)
        for (i in 1..6) {
            val py = wallBottomY + i * ((h - wallBottomY) / 7)
            pm.fillRectangle(0, py, w, 2)
        }

        // 8. Lemari kayu & tanaman pot di kiri
        val cabY = wallBottomY - 98
        fillRoundRect(-12, cabY, 118, 128, 10, Color(0.76f, 0.50f, 0.27f, 1f))
        fillRoundRect(-16, cabY - 6, 126, 16, 6, Color(0.84f, 0.58f, 0.34f, 1f))
        pm.setColor(0.60f, 0.37f, 0.18f, 1f)
        pm.fillRectangle(8, cabY + 24, 84, 3)
        pm.fillRectangle(8, cabY + 72, 84, 3)
        pm.fillCircle(50, cabY + 46, 5)
        pm.fillCircle(50, cabY + 94, 5)
        fillRoundRect(22, cabY - 46, 46, 42, 8, Color(0.84f, 0.42f, 0.22f, 1f))
        fillRoundRect(18, cabY - 52, 54, 12, 5, Color(0.90f, 0.48f, 0.26f, 1f))
        pm.setColor(0.32f, 0.68f, 0.25f, 1f)
        pm.fillCircle(32, cabY - 76, 22)
        pm.fillCircle(58, cabY - 72, 20)
        pm.setColor(0.44f, 0.78f, 0.32f, 1f)
        pm.fillCircle(45, cabY - 94, 24)

        // 9. Sofa krem & bantal di kanan
        val sofaX = w - 165
        val sofaY = wallBottomY - 82
        fillRoundRect(sofaX, sofaY, 185, 118, 22, Color(0.90f, 0.81f, 0.70f, 1f))
        fillRoundRect(sofaX - 12, sofaY + 42, 42, 76, 18, Color(0.86f, 0.76f, 0.64f, 1f))
        fillRoundRect(sofaX + 18, sofaY + 56, 155, 56, 16, Color(0.94f, 0.86f, 0.76f, 1f))
        fillRoundRect(sofaX + 34, sofaY + 18, 52, 48, 12, Color(0.97f, 0.66f, 0.20f, 1f))
        fillRoundRect(sofaX + 102, sofaY + 24, 52, 44, 12, Color(0.30f, 0.72f, 0.92f, 1f))

        // 10. Karpet merah
        val rugTopY = wallBottomY + 26
        val rugBotY = (h * 0.89f).toInt()
        for (ry in rugTopY..rugBotY) {
            val t = (ry - rugTopY).toFloat() / (rugBotY - rugTopY).coerceAtLeast(1)
            val halfW = MathUtils.lerp(w * 0.34f, w * 0.56f, t).toInt()
            val rx = w / 2 - halfW
            pm.setColor(0.45f, 0.20f, 0.10f, 0.35f)
            pm.fillRectangle(rx - 4, ry + 4, halfW * 2 + 8, 2)
            val rCol = MathUtils.lerp(0.84f, 0.74f, t)
            val gCol = MathUtils.lerp(0.18f, 0.13f, t)
            val bCol = MathUtils.lerp(0.18f, 0.14f, t)
            pm.setColor(rCol, gCol, bCol, 1f)
            pm.fillRectangle(rx, ry, halfW * 2, 2)
        }

        // 11. Bayangan lembut di bawah kaki kucing
        val shadowCx = w / 2
        val shadowCy = (h * 0.74f).toInt()
        for (sy in -26..26) {
            val ny = sy / 26f
            val halfW = (92f * kotlin.math.sqrt((1f - ny * ny).coerceAtLeast(0f))).toInt()
            val alpha = 0.26f * (1f - ny * ny)
            pm.setColor(0.25f, 0.05f, 0.05f, alpha)
            pm.fillRectangle(shadowCx - halfW, shadowCy + sy, halfW * 2, 1)
        }

        val tex = Texture(pm).apply {
            setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear)
        }
        pm.dispose()
        return tex
    }

    override fun pause() {
        voiceBridge.stopListening()
        aiChatBridge.stopAll()
    }

    override fun resume() {
        // AI Pet menunggu tombol Bicara / Ketik agar tidak bentrok mikrofon
    }

    override fun dispose() {
        voiceBridge.stopListening()
        voiceBridge.setCallback(null)
        aiChatBridge.stopAll()

        sfx.dispose()
        particleOverlay.dispose()
        pet.dispose()
        backdropBatch.dispose()
        roomBackdropTexture.dispose()
        sceneManager.dispose()
        petHud.dispose()
    }
}
