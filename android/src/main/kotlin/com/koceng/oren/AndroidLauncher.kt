package com.koceng.oren

import android.os.Bundle
import com.badlogic.gdx.backends.android.AndroidApplication
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration
import com.koceng.oren.audio.AndroidVoiceMimicBridge

class AndroidLauncher : AndroidApplication() {

    private lateinit var voiceBridge: AndroidVoiceMimicBridge

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Konfigurasi backend Android libGDX
        val config = AndroidApplicationConfiguration().apply {
            useAccelerometer = false
            useCompass = false
            useImmersiveMode = true
            // Konfigurasi kedalaman buffer untuk rendering 3D PBR
            r = 8
            g = 8
            b = 8
            a = 8
            depth = 16
            numSamples = 2 // MSAA untuk visual 3D yang halus
        }

        voiceBridge = AndroidVoiceMimicBridge(this)
        initialize(KocengOrenGame(voiceBridge), config)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        voiceBridge.onPermissionResult(requestCode, grantResults)
    }

    override fun onPause() {
        super.onPause()
        voiceBridge.stopListening()
    }

    override fun onDestroy() {
        super.onDestroy()
        voiceBridge.stopListening()
    }
}
