package com.koceng.oren

import com.badlogic.gdx.Game
import com.koceng.oren.audio.VoiceMimicBridge
import com.koceng.oren.screens.PetScreen

class KocengOrenGame(private val voiceBridge: VoiceMimicBridge) : Game() {

    override fun create() {
        setScreen(PetScreen(voiceBridge))
    }
}
