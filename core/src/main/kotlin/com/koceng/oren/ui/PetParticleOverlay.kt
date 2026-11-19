package com.koceng.oren.ui

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.utils.Array
import com.badlogic.gdx.utils.Disposable
import kotlin.math.cos
import kotlin.math.sin

enum class ParticleType {
    HEART,
    STAR,
    CRUMB,
    ZZZ
}

class PetParticle {
    var x: Float = 0f
    var y: Float = 0f
    var vx: Float = 0f
    var vy: Float = 0f
    var rotation: Float = 0f
    var rotSpeed: Float = 0f
    var scale: Float = 1f
    var alpha: Float = 1f
    var life: Float = 1f
    var maxLife: Float = 1f
    val color: Color = Color(Color.WHITE)
    var type: ParticleType = ParticleType.HEART
    var initialX: Float = 0f
    var wobbleSpeed: Float = 5f
}

/**
 * Overlay sistem partikel visual interaksi ringan murni kode tanpa aset gambar eksternal.
 */
class PetParticleOverlay : Disposable {

    private val batch: SpriteBatch = SpriteBatch()
    private val particles = Array<PetParticle>()
    private val pool = Array<PetParticle>()

    // Tekstur prosedural partikel
    private val heartRegion: TextureRegion
    private val starRegion: TextureRegion
    private val crumbRegion: TextureRegion
    private val zzzFont: BitmapFont

    private val heartTexture: Texture
    private val starTexture: Texture
    private val crumbTexture: Texture

    private val projectionMatrix = Matrix4()

    init {
        // 1. Gambar tekstur hati prosedural (24x24)
        val heartPixmap = Pixmap(24, 24, Pixmap.Format.RGBA8888).apply {
            setColor(0f, 0f, 0f, 0f)
            fill()
            setColor(Color.WHITE)
            fillCircle(7, 8, 6)
            fillCircle(16, 8, 6)
            fillTriangle(2, 9, 21, 9, 11, 22)
        }
        heartTexture = Texture(heartPixmap)
        heartPixmap.dispose()
        heartRegion = TextureRegion(heartTexture)

        // 2. Gambar tekstur bintang prosedural (24x24)
        val starPixmap = Pixmap(24, 24, Pixmap.Format.RGBA8888).apply {
            setColor(0f, 0f, 0f, 0f)
            fill()
            setColor(Color.WHITE)
            fillTriangle(12, 1, 15, 22, 9, 22)
            fillTriangle(1, 12, 22, 15, 22, 9)
            fillTriangle(3, 4, 20, 19, 18, 21)
            fillTriangle(20, 4, 3, 19, 5, 21)
            fillCircle(12, 12, 5)
        }
        starTexture = Texture(starPixmap)
        starPixmap.dispose()
        starRegion = TextureRegion(starTexture)

        // 3. Gambar tekstur remah renyah (12x12)
        val crumbPixmap = Pixmap(12, 12, Pixmap.Format.RGBA8888).apply {
            setColor(0f, 0f, 0f, 0f)
            fill()
            setColor(Color.WHITE)
            fillCircle(6, 6, 5)
        }
        crumbTexture = Texture(crumbPixmap)
        crumbPixmap.dispose()
        crumbRegion = TextureRegion(crumbTexture)

        // 4. Font untuk teks partikel Zzz
        zzzFont = BitmapFont().apply {
            data.setScale(1.2f)
        }
    }

    private fun obtainParticle(): PetParticle {
        return if (pool.size > 0) pool.pop() else PetParticle()
    }

    /**
     * Muncul partikel hati merah muda saat perut diusap atau tombol Main ditekan
     */
    fun spawnHearts(centerX: Float, centerY: Float, count: Int = 7) {
        for (i in 0 until count) {
            val p = obtainParticle().apply {
                type = ParticleType.HEART
                x = centerX + MathUtils.random(-35f, 35f)
                y = centerY + MathUtils.random(-25f, 25f)
                initialX = x
                vx = MathUtils.random(-20f, 20f)
                vy = MathUtils.random(75f, 135f)
                rotation = MathUtils.random(-15f, 15f)
                rotSpeed = MathUtils.random(-30f, 30f)
                scale = MathUtils.random(0.85f, 1.35f)
                maxLife = MathUtils.random(1.2f, 1.8f)
                life = maxLife
                alpha = 1f
                wobbleSpeed = MathUtils.random(3.5f, 6.0f)
                color.set(1.0f, MathUtils.random(0.40f, 0.65f), MathUtils.random(0.65f, 0.85f), 1f)
            }
            particles.add(p)
        }
    }

    /**
     * Muncul partikel bintang kuning berputar saat kepala di-tap
     */
    fun spawnStars(centerX: Float, centerY: Float, count: Int = 8) {
        for (i in 0 until count) {
            val angle = (i.toFloat() / count) * MathUtils.PI2 + MathUtils.random(-0.3f, 0.3f)
            val speed = MathUtils.random(80f, 150f)
            val p = obtainParticle().apply {
                type = ParticleType.STAR
                x = centerX + MathUtils.random(-15f, 15f)
                y = centerY + MathUtils.random(-10f, 15f)
                initialX = x
                vx = cos(angle) * speed
                vy = sin(angle) * speed * 0.7f + 30f
                rotation = MathUtils.random(0f, 360f)
                rotSpeed = MathUtils.random(180f, 360f) * if (MathUtils.randomBoolean()) 1 else -1
                scale = MathUtils.random(0.7f, 1.25f)
                maxLife = MathUtils.random(0.8f, 1.3f)
                life = maxLife
                alpha = 1f
                color.set(1.0f, MathUtils.random(0.82f, 0.98f), 0.20f, 1f)
            }
            particles.add(p)
        }
    }

    /**
     * Muncul partikel remah/bintang hijau ceria saat tombol Makan ditekan
     */
    fun spawnCrumbs(centerX: Float, centerY: Float, count: Int = 10) {
        for (i in 0 until count) {
            val p = obtainParticle().apply {
                type = ParticleType.CRUMB
                x = centerX + MathUtils.random(-25f, 25f)
                y = centerY + MathUtils.random(-15f, 15f)
                initialX = x
                vx = MathUtils.random(-70f, 70f)
                vy = MathUtils.random(40f, 110f)
                rotation = MathUtils.random(0f, 360f)
                rotSpeed = MathUtils.random(-90f, 90f)
                scale = MathUtils.random(0.7f, 1.2f)
                maxLife = MathUtils.random(0.7f, 1.1f)
                life = maxLife
                alpha = 1f
                color.set(MathUtils.random(0.35f, 0.55f), MathUtils.random(0.85f, 1.0f), 0.35f, 1f)
            }
            particles.add(p)
        }
    }

    /**
     * Muncul teks Zzz melayang pelan saat mode Tidur aktif
     */
    fun spawnZzz(centerX: Float, centerY: Float) {
        val p = obtainParticle().apply {
            type = ParticleType.ZZZ
            x = centerX + MathUtils.random(-20f, 20f)
            y = centerY + MathUtils.random(-10f, 10f)
            initialX = x
            vx = MathUtils.random(15f, 35f)
            vy = MathUtils.random(40f, 65f)
            rotation = MathUtils.random(-8f, 8f)
            rotSpeed = MathUtils.random(-12f, 12f)
            scale = MathUtils.random(0.9f, 1.25f)
            maxLife = MathUtils.random(1.8f, 2.4f)
            life = maxLife
            alpha = 1f
            wobbleSpeed = 3.0f
            color.set(0.65f, 0.85f, 1.0f, 1f)
        }
        particles.add(p)
    }

    fun render(delta: Float, screenWidth: Float, screenHeight: Float) {
        if (particles.size == 0) return

        projectionMatrix.setToOrtho2D(0f, 0f, screenWidth, screenHeight)
        batch.projectionMatrix = projectionMatrix
        batch.begin()

        for (i in particles.size - 1 downTo 0) {
            val p = particles.get(i)
            p.life -= delta

            if (p.life <= 0f) {
                particles.removeIndex(i)
                pool.add(p)
                continue
            }

            val progress = 1f - (p.life / p.maxLife)
            p.alpha = (1f - progress).coerceIn(0f, 1f)

            // Update posisi & efek fisika
            when (p.type) {
                ParticleType.HEART -> {
                    p.y += p.vy * delta
                    p.x = p.initialX + sin(progress * MathUtils.PI2 * p.wobbleSpeed) * 16f
                    p.rotation += p.rotSpeed * delta
                }
                ParticleType.STAR -> {
                    p.x += p.vx * delta
                    p.y += p.vy * delta
                    p.vy -= 90f * delta // gravitasi halus
                    p.rotation += p.rotSpeed * delta
                }
                ParticleType.CRUMB -> {
                    p.x += p.vx * delta
                    p.y += p.vy * delta
                    p.vy -= 180f * delta // gravitasi remah jatuh
                    p.rotation += p.rotSpeed * delta
                }
                ParticleType.ZZZ -> {
                    p.x += p.vx * delta + sin(progress * MathUtils.PI2) * 8f * delta
                    p.y += p.vy * delta
                }
            }

            // Gambar partikel
            when (p.type) {
                ParticleType.HEART -> {
                    batch.setColor(p.color.r, p.color.g, p.color.b, p.alpha)
                    val w = heartRegion.regionWidth * p.scale
                    val h = heartRegion.regionHeight * p.scale
                    batch.draw(heartRegion, p.x - w / 2, p.y - h / 2, w / 2, h / 2, w, h, 1f, 1f, p.rotation)
                }
                ParticleType.STAR -> {
                    batch.setColor(p.color.r, p.color.g, p.color.b, p.alpha)
                    val w = starRegion.regionWidth * p.scale
                    val h = starRegion.regionHeight * p.scale
                    batch.draw(starRegion, p.x - w / 2, p.y - h / 2, w / 2, h / 2, w, h, 1f, 1f, p.rotation)
                }
                ParticleType.CRUMB -> {
                    batch.setColor(p.color.r, p.color.g, p.color.b, p.alpha)
                    val w = crumbRegion.regionWidth * p.scale
                    val h = crumbRegion.regionHeight * p.scale
                    batch.draw(crumbRegion, p.x - w / 2, p.y - h / 2, w / 2, h / 2, w, h, 1f, 1f, p.rotation)
                }
                ParticleType.ZZZ -> {
                    zzzFont.color.set(p.color.r, p.color.g, p.color.b, p.alpha)
                    zzzFont.data.setScale(1.1f * p.scale)
                    zzzFont.draw(batch, "Zzz", p.x, p.y)
                }
            }
        }

        batch.setColor(Color.WHITE)
        batch.end()
    }

    override fun dispose() {
        batch.dispose()
        heartTexture.dispose()
        starTexture.dispose()
        crumbTexture.dispose()
        zzzFont.dispose()
        particles.clear()
        pool.clear()
    }
}
