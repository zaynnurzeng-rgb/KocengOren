package com.koceng.oren.data

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Preferences

/**
 * Mengelola status kebutuhan Virtual Pet (Lapar, Energi, Kebahagiaan),
 * perhitungan offline decay, dan penyimpanan persisten via Gdx.app.getPreferences.
 */
class PetStateManager {

    companion object {
        private const val PREFS_NAME = "koceng_oren_prefs"
        private const val KEY_HUNGER = "hunger"
        private const val KEY_ENERGY = "energy"
        private const val KEY_HAPPINESS = "happiness"
        private const val KEY_TIMESTAMP = "last_timestamp"
        private const val KEY_IS_SLEEPING = "is_sleeping"

        // Laju penurunan status saat game aktif (per detik)
        private const val HUNGER_DECAY_RATE = 0.8f
        private const val ENERGY_DECAY_RATE = 0.5f
        private const val ENERGY_REGEN_RATE = 6.0f
        private const val HAPPINESS_DECAY_RATE = 0.4f

        // Laju penurunan status saat game ditutup / offline (per detik)
        private const val OFFLINE_HUNGER_DECAY = 0.05f
        private const val OFFLINE_ENERGY_DECAY = 0.03f
        private const val OFFLINE_ENERGY_REGEN = 0.15f
        private const val OFFLINE_HAPPINESS_DECAY = 0.04f
    }

    var hunger: Float = 85f
        private set

    var energy: Float = 90f
        private set

    var happiness: Float = 80f
        private set

    var isSleeping: Boolean = false
        private set

    init {
        loadAndApplyOfflineDecay()
    }

    /**
     * Membaca data tersimpan dan menghitung penurunan status
     * berdasarkan selisih waktu sejak game terakhir ditutup.
     */
    private fun loadAndApplyOfflineDecay() {
        val prefs: Preferences = Gdx.app.getPreferences(PREFS_NAME)

        val savedHunger = prefs.getFloat(KEY_HUNGER, 85f)
        val savedEnergy = prefs.getFloat(KEY_ENERGY, 90f)
        val savedHappiness = prefs.getFloat(KEY_HAPPINESS, 80f)
        val savedSleeping = prefs.getBoolean(KEY_IS_SLEEPING, false)
        val lastTimestamp = prefs.getLong(KEY_TIMESTAMP, 0L)

        val currentTime = System.currentTimeMillis()
        if (lastTimestamp > 0L && currentTime > lastTimestamp) {
            val elapsedSeconds = ((currentTime - lastTimestamp) / 1000f).coerceAtLeast(0f)

            // Hitung penurunan rasa lapar offline
            val offlineHungerLoss = elapsedSeconds * OFFLINE_HUNGER_DECAY
            hunger = (savedHunger - offlineHungerLoss).coerceIn(0f, 100f)

            // Hitung energi offline (bertambah jika ditinggal tidur, berkurang jika bangun)
            if (savedSleeping) {
                val offlineEnergyGain = elapsedSeconds * OFFLINE_ENERGY_REGEN
                energy = (savedEnergy + offlineEnergyGain).coerceIn(0f, 100f)
            } else {
                val offlineEnergyLoss = elapsedSeconds * OFFLINE_ENERGY_DECAY
                energy = (savedEnergy - offlineEnergyLoss).coerceIn(0f, 100f)
            }

            // Hitung kebahagiaan offline (berkurang lebih cepat jika lapar atau lelah)
            val penaltyMultiplier = if (hunger < 30f || energy < 30f) 1.5f else 1.0f
            val offlineHappinessLoss = elapsedSeconds * OFFLINE_HAPPINESS_DECAY * penaltyMultiplier
            happiness = (savedHappiness - offlineHappinessLoss).coerceIn(0f, 100f)

            isSleeping = savedSleeping
            Gdx.app.log(
                "PetStateManager",
                "Data dimuat. Offline: ${elapsedSeconds.toInt()} detik. Lapar: $hunger, Energi: $energy, Bahagia: $happiness"
            )
        } else {
            // Pertama kali atau data baru
            hunger = savedHunger
            energy = savedEnergy
            happiness = savedHappiness
            isSleeping = savedSleeping
        }

        save()
    }

    /**
     * Memperbarui status virtual pet secara real-time setiap frame tick
     */
    fun update(delta: Float) {
        // Rasa lapar berkurang perlahan
        hunger = (hunger - HUNGER_DECAY_RATE * delta).coerceIn(0f, 100f)

        // Energi pulih jika tidur, berkurang jika bangun
        if (isSleeping) {
            energy = (energy + ENERGY_REGEN_RATE * delta).coerceIn(0f, 100f)
        } else {
            energy = (energy - ENERGY_DECAY_RATE * delta).coerceIn(0f, 100f)
        }

        // Kebahagiaan berkurang pelan, lebih cepat jika lapar/mengantuk berat
        val moodMultiplier = if (hunger < 25f || energy < 25f) 2.0f else 1.0f
        happiness = (happiness - HAPPINESS_DECAY_RATE * moodMultiplier * delta).coerceIn(0f, 100f)
    }

    /**
     * Memberi makan kucing: menambah rasa lapar dan kebahagiaan
     */
    fun feed(amount: Float = 25f) {
        if (isSleeping) {
            wakeUp()
        }
        hunger = (hunger + amount).coerceIn(0f, 100f)
        happiness = (happiness + 5f).coerceIn(0f, 100f)
        save()
    }

    /**
     * Mengatur kondisi tidur kucing (toggle atau eksplisit)
     */
    fun sleep(sleeping: Boolean? = null) {
        isSleeping = sleeping ?: !isSleeping
        save()
    }

    fun wakeUp() {
        if (isSleeping) {
            isSleeping = false
            save()
        }
    }

    /**
     * Mengajak kucing bermain: menambah kebahagiaan, mengurangi sedikit energi dan lapar
     */
    fun play(amount: Float = 20f) {
        if (isSleeping) {
            wakeUp()
        }
        happiness = (happiness + amount).coerceIn(0f, 100f)
        energy = (energy - 10f).coerceIn(0f, 100f)
        hunger = (hunger - 5f).coerceIn(0f, 100f)
        save()
    }

    /**
     * Menyimpan status terkini ke Preferences
     */
    fun save() {
        try {
            val prefs: Preferences = Gdx.app.getPreferences(PREFS_NAME)
            prefs.putFloat(KEY_HUNGER, hunger)
            prefs.putFloat(KEY_ENERGY, energy)
            prefs.putFloat(KEY_HAPPINESS, happiness)
            prefs.putBoolean(KEY_IS_SLEEPING, isSleeping)
            prefs.putLong(KEY_TIMESTAMP, System.currentTimeMillis())
            prefs.flush()
        } catch (e: Exception) {
            Gdx.app.error("PetStateManager", "Gagal menyimpan data status", e)
        }
    }
}
