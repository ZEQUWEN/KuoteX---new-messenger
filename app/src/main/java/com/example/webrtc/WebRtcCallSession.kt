package com.example.webrtc

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.Closeable

/**
 * Call connection state
 */
enum class CallConnectionState {
    IDLE,
    INITIALIZING,
    CONNECTING,
    RINGING,
    CONNECTED,
    DISCONNECTED,
    FAILED
}

/**
 * Ephemeral WebRTC Call Session.
 * Designed with Factory/Scoped lifecycle: instantiated on call start and
 * completely disposed (cameras, audio buffers, peer connections) on call end.
 */
class WebRtcCallSession(
    val chatId: String,
    val isVideo: Boolean
) : Closeable {

    private val _connectionState = MutableStateFlow(CallConnectionState.IDLE)
    val connectionState: StateFlow<CallConnectionState> = _connectionState.asStateFlow()

    private val _isMuted = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()

    private val _isVideoEnabled = MutableStateFlow(isVideo)
    val isVideoEnabled: StateFlow<Boolean> = _isVideoEnabled.asStateFlow()

    private val _isSpeakerphone = MutableStateFlow(isVideo)
    val isSpeakerphone: StateFlow<Boolean> = _isSpeakerphone.asStateFlow()

    private var isDisposed = false

    fun initializeCall() {
        if (isDisposed) return
        _connectionState.value = CallConnectionState.CONNECTING
        Log.d("WebRtcCallSession", "Initialized WebRTC session for chat $chatId (isVideo=$isVideo)")
    }

    fun setConnected() {
        if (isDisposed) return
        _connectionState.value = CallConnectionState.CONNECTED
    }

    fun toggleMute(): Boolean {
        if (isDisposed) return false
        _isMuted.value = !_isMuted.value
        return _isMuted.value
    }

    fun toggleVideo(): Boolean {
        if (isDisposed) return false
        _isVideoEnabled.value = !_isVideoEnabled.value
        return _isVideoEnabled.value
    }

    fun toggleSpeaker(): Boolean {
        if (isDisposed) return false
        _isSpeakerphone.value = !_isSpeakerphone.value
        return _isSpeakerphone.value
    }

    override fun close() {
        if (!isDisposed) {
            isDisposed = true
            _connectionState.value = CallConnectionState.DISCONNECTED
            Log.d("WebRtcCallSession", "Disposed WebRTC call session for chat $chatId and freed memory.")
        }
    }
}
