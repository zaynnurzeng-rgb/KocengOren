package com.koceng.oren.input

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.InputAdapter
import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.math.Intersector
import com.badlogic.gdx.math.Vector3
import com.koceng.oren.character.HitZone
import com.koceng.oren.character.PetCharacter

/**
 * Controller untuk menangani Raycasting sentuhan 2D layar ke Bounding Box 3D
 */
class PetTouchController(
    private val camera: Camera,
    private val pet: PetCharacter,
    private val onHitListener: ((HitZone) -> Unit)? = null
) : InputAdapter() {

    private val intersectionPointHead = Vector3()
    private val intersectionPointBelly = Vector3()

    override fun touchDown(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
        // 1. Buat Ray 3D dari koordinat layar sentuh (screenX, screenY)
        val ray = camera.getPickRay(screenX.toFloat(), screenY.toFloat())

        // 2. Cek tabrakan sinar ke Bounding Box Kepala & Perut
        val hitHead = Intersector.intersectRayBounds(ray, pet.worldHeadBox, intersectionPointHead)
        val hitBelly = Intersector.intersectRayBounds(ray, pet.worldBellyBox, intersectionPointBelly)

        // 3. Evaluasi zona yang terkena
        var hitZone = HitZone.NONE

        if (hitHead && hitBelly) {
            // Jika kedua box terkena, pilih yang jarak tabrakannya paling dekat dengan posisi kamera
            val distHead = ray.origin.dst2(intersectionPointHead)
            val distBelly = ray.origin.dst2(intersectionPointBelly)
            hitZone = if (distHead < distBelly) HitZone.HEAD else HitZone.BELLY
        } else if (hitHead) {
            hitZone = HitZone.HEAD
        } else if (hitBelly) {
            hitZone = HitZone.BELLY
        }

        // 4. Eksekusi reaksi karakter sesuai zona yang disentuh
        when (hitZone) {
            HitZone.HEAD -> {
                Gdx.app.log("Raycast", "Kepala disentuh! Memicu animasi Pusing.")
                try { Gdx.input.vibrate(40) } catch (_: Exception) {}
                pet.playDizzy()
                onHitListener?.invoke(HitZone.HEAD)
                return true
            }
            HitZone.BELLY -> {
                Gdx.app.log("Raycast", "Perut disentuh! Memicu animasi Tertawa.")
                try { Gdx.input.vibrate(40) } catch (_: Exception) {}
                pet.playLaughing()
                onHitListener?.invoke(HitZone.BELLY)
                return true
            }
            HitZone.NONE -> {
                // Sentuhan di luar area interaktif
                return false
            }
        }
    }
}
