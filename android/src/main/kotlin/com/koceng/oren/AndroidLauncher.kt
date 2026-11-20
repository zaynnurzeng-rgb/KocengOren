package com.koceng.oren

import android.os.Bundle
import android.content.Intent
import android.widget.FrameLayout
import com.badlogic.gdx.backends.android.AndroidApplication
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration
import com.koceng.oren.ai.AndroidAiChatBridge
import com.koceng.oren.audio.AndroidVoiceMimicBridge

class AndroidLauncher : AndroidApplication() {

    private lateinit var voiceBridge: AndroidVoiceMimicBridge
    private lateinit var aiChatBridge: AndroidAiChatBridge

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Konfigurasi backend Android libGDX
        val config = AndroidApplicationConfiguration().apply {
            useAccelerometer = false
            useCompass = false
            useImmersiveMode = true
            // Konfigurasi buffer untuk rendering 3D PBR
            r = 8
            g = 8
            b = 8
            a = 8
            depth = 16
            numSamples = 2 // MSAA 2x untuk visual halus
        }

        voiceBridge = AndroidVoiceMimicBridge(this)
        aiChatBridge = AndroidAiChatBridge(this)

        val gdxView = initializeForView(KocengOrenGame(voiceBridge, aiChatBridge), config)
        val rootLayout = FrameLayout(this).apply {
            addView(
                gdxView,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            )
            addView(
                aiChatBridge.createNativeHudOverlay(),
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            )
        }
        setContentView(rootLayout)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        voiceBridge.onPermissionResult(requestCode, grantResults)
    }

    override fun onPause() {
        super.onPause()
        voiceBridge.stopListening()
        aiChatBridge.stopAll()
    }

    override fun onDestroy() {
        super.onDestroy()
        voiceBridge.stopListening()
        aiChatBridge.stopAll()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (::aiChatBridge.isInitialized) {
            aiChatBridge.onActivityResult(requestCode, resultCode, data)
        }
    }
}
