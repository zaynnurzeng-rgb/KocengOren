package com.koceng.oren.ai

enum class ChatMode {
    CHAT,
    HOMEWORK
}

enum class RoomTheme { DAY, NIGHT, STUDY }

/**
 * Interface jembatan (Bridge) antara Core LibGDX dan Native Android
 * untuk fitur AI Pet (Speech-to-Text / Text Input -> Gemini AI -> Gemini Natural Voice).
 */
interface AiChatBridge {
    var onListeningStateChanged: ((Boolean) -> Unit)?
    var onThinkingStateChanged: ((Boolean) -> Unit)?
    var onUserMessage: ((String) -> Unit)?
    var onReplyText: ((String) -> Unit)?
    var onSpeakingStateChanged: ((Boolean) -> Unit)?
    var onError: ((String) -> Unit)?
    var onRoomThemeChanged: ((RoomTheme) -> Unit)?

    fun startListening()
    fun sendTextMessage(text: String)
    fun openTextInputDialog(isHomework: Boolean)
    fun setChatMode(mode: ChatMode)
    fun stopAll()
    fun isBusy(): Boolean
    fun resetChatHistory()
    fun speakText(text: String)
    fun openPhotoHomeworkPicker()
    fun copyLastReplyToClipboard()
    fun openChatHistoryDialog()
    fun openSettingsDialog()
    fun getSavedRoomTheme(): RoomTheme
    val useNativeHud: Boolean
    fun updateNativeBubble(userText: String?, replyText: String)
}    
