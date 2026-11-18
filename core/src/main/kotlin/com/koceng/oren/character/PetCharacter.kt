package com.koceng.oren.character

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.g3d.utils.AnimationController
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.math.collision.BoundingBox
import com.badlogic.gdx.utils.Disposable
import net.mgsx.gltf.loaders.glb.GLBLoader
import net.mgsx.gltf.scene3d.scene.Scene
import net.mgsx.gltf.scene3d.scene.SceneAsset

enum class PetState {
    IDLE,
    LISTENING,
    TALKING,
    DIZZY,    // Reaksi kepala dipukul/disentuh (Pusing)
    LAUGHING, // Reaksi perut dielus/disentuh (Tertawa)
    EATING,   // Reaksi makan
    SLEEPING  // Mode tidur
}

enum class HitZone {
    HEAD,
    BELLY,
    NONE
}

class PetCharacter(glbFilePath: String = "models/cartoon_cat.glb") : Disposable {

    val sceneAsset: SceneAsset = GLBLoader().load(Gdx.files.internal(glbFilePath))
    val scene: Scene = Scene(sceneAsset.scene)

    var currentState: PetState = PetState.IDLE
        private set

    // Bounding Box lokal untuk seluruh karakter, kepala, dan perut
    val fullModelBounds = BoundingBox()
    val localHeadBox = BoundingBox()
    val localBellyBox = BoundingBox()

    // Bounding Box di World Coordinates (diupdate setiap frame sesuai transform)
    val worldHeadBox = BoundingBox()
    val worldBellyBox = BoundingBox()

    // Peta nama animasi yang cocok dengan aset model
    private val animIdleName: String
    private val animListeningName: String
    private val animTalkingName: String
    private val animDizzyName: String
    private val animLaughName: String
    private val animEatingName: String
    private val animSleepingName: String

    // Listener untuk transisi kembali ke IDLE saat animasi one-shot selesai
    private val reactionListener = object : AnimationController.AnimationListener {
        override fun onEnd(animation: AnimationController.AnimationDesc?) {
            if (currentState != PetState.SLEEPING) {
                playIdle()
            }
        }

        override fun onLoop(animation: AnimationController.AnimationDesc?) {}
    }

    init {
        // Skala dan penyesuaian posisi default model
        scene.modelInstance.transform.setToTranslationAndScaling(0f, 0f, 0f, 1f, 1f, 1f)

        // Hitung bounding box dasar model
        scene.modelInstance.calculateBoundingBox(fullModelBounds)
        val min = fullModelBounds.min
        val max = fullModelBounds.max
        val height = max.y - min.y
        val width = max.x - min.x
        val depth = max.z - min.z

        Gdx.app.log("PetCharacter", "Model Bounds: min=$min, max=$max, height=$height")

        // Inisialisasi Bounding Box Kepala (bagian atas 65% - 100% tinggi model)
        localHeadBox.set(
            Vector3(min.x + width * 0.15f, min.y + height * 0.65f, min.z + depth * 0.15f),
            Vector3(max.x - width * 0.15f, max.y, max.z - depth * 0.15f)
        )

        // Inisialisasi Bounding Box Perut (bagian tengah 30% - 65% tinggi model)
        localBellyBox.set(
            Vector3(min.x + width * 0.2f, min.y + height * 0.30f, min.z + depth * 0.2f),
            Vector3(max.x - width * 0.2f, min.y + height * 0.65f, max.z - depth * 0.2f)
        )

        updateWorldBounds()

        // Deteksi animasi yang tersedia di dalam file GLB
        val animMap = scene.modelInstance.animations.map { it.id }.toSet()
        Gdx.app.log("PetCharacter", "Daftar animasi di GLB: $animMap")

        // Memetakan animasi ke aset model
        animIdleName = findAnimation(animMap, listOf("Idle", "Idle_Harmonic", "Idle_Dreamer", "Arm_Idle"))
        animListeningName = findAnimation(animMap, listOf("Listening", "Idle_Dreamer", "Pistol_Idle", "Idle_Harmonic"))
        animTalkingName = findAnimation(animMap, listOf("Talking", "Arm_Attack_Light", "Arm_Attack_Medium", "Idle_Invasive"))
        animDizzyName = findAnimation(animMap, listOf("Dizzy", "Hit", "Pusing", "Arm_Hit", "Pistol_Hit", "Sword_Hit"))
        animLaughName = findAnimation(animMap, listOf("Laugh", "Laughing", "Tertawa", "Arm_Attack_Hight", "Sword_Attack_Light"))
        animEatingName = findAnimation(animMap, listOf("Eating", "Eat", "Arm_Attack_Light", "Arm_Attack_End", "Arm_Attack_Medium"))
        animSleepingName = findAnimation(animMap, listOf("Sleeping", "Sleep", "Idle_Dreamer", "Arm_Death", "Idle_Invasive"))

        // Mainkan animasi awal (Idle)
        playIdle()
    }

    private fun findAnimation(available: Set<String>, candidates: List<String>): String {
        for (candidate in candidates) {
            if (available.contains(candidate)) return candidate
        }
        return available.firstOrNull() ?: ""
    }

    fun updateWorldBounds() {
        val transform = scene.modelInstance.transform
        worldHeadBox.set(localHeadBox).mul(transform)
        worldBellyBox.set(localBellyBox).mul(transform)
    }

    fun update(delta: Float) {
        scene.animationController.update(delta)
        updateWorldBounds()
    }

    fun playIdle() {
        currentState = PetState.IDLE
        if (animIdleName.isNotEmpty()) {
            scene.animationController.animate(animIdleName, -1, 1f, null, 0.25f)
        }
    }

    fun playListening() {
        if (currentState == PetState.DIZZY || currentState == PetState.LAUGHING || currentState == PetState.SLEEPING) return
        currentState = PetState.LISTENING
        if (animListeningName.isNotEmpty()) {
            scene.animationController.animate(animListeningName, -1, 1f, null, 0.2f)
        }
    }

    fun playTalking() {
        if (currentState == PetState.DIZZY || currentState == PetState.LAUGHING || currentState == PetState.SLEEPING) return
        currentState = PetState.TALKING
        if (animTalkingName.isNotEmpty()) {
            scene.animationController.animate(animTalkingName, -1, 1.2f, null, 0.15f)
        }
    }

    fun playDizzy() {
        if (currentState == PetState.SLEEPING) wakeUp()
        currentState = PetState.DIZZY
        if (animDizzyName.isNotEmpty()) {
            scene.animationController.animate(animDizzyName, 1, 1.3f, reactionListener, 0.1f)
        }
    }

    fun playLaughing() {
        if (currentState == PetState.SLEEPING) wakeUp()
        currentState = PetState.LAUGHING
        if (animLaughName.isNotEmpty()) {
            scene.animationController.animate(animLaughName, 1, 1.5f, reactionListener, 0.1f)
        }
    }

    fun playEating() {
        if (currentState == PetState.SLEEPING) wakeUp()
        currentState = PetState.EATING
        if (animEatingName.isNotEmpty()) {
            scene.animationController.animate(animEatingName, 1, 1.2f, reactionListener, 0.15f)
        }
    }

    fun playSleeping() {
        currentState = PetState.SLEEPING
        if (animSleepingName.isNotEmpty()) {
            scene.animationController.animate(animSleepingName, -1, 0.7f, null, 0.25f)
        }
    }

    fun wakeUp() {
        if (currentState == PetState.SLEEPING) {
            playIdle()
        }
    }

    override fun dispose() {
        sceneAsset.dispose()
    }
}
