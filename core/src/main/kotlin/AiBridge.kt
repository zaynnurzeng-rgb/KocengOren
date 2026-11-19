package com.game.ai // Sesuaikan dengan package proyekmu

data class AiResponse(
    val dialog: String,
    val animasi: String
)

interface AiBridge {
    fun kirimPesan(
        teksPemain: String,
        onSukses: (AiResponse) -> Unit,
        onError: (String) -> Unit
    )
}
