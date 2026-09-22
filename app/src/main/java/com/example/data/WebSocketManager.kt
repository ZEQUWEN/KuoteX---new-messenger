package com.example.data

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.*
import okhttp3.*
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import android.util.Log

sealed class InboundEvent {
    data class NewMessage(
        val messageId: String,
        val chatId: String,
        val senderId: String,
        val text: String,
        val timestamp: Long
    ) : InboundEvent()
    
    data class ReadReceipt(
        val chatId: String,
        val messageId: String
    ) : InboundEvent()
    
    data class UserTyping(
        val chatId: String,
        val userId: String
    ) : InboundEvent()

    data class PresenceUpdate(
        val userId: String,
        val isOnline: Boolean,
        val lastSeen: Long
    ) : InboundEvent()
}

class WebSocketManager(private val okHttpClient: OkHttpClient) {
    
    private var webSocket: WebSocket? = null
    
    private val _events = MutableSharedFlow<InboundEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<InboundEvent> = _events.asSharedFlow()
    
    private var isConnected = false
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    private val reconnectDelayMs = 3000L
    private val WSS_URL = "wss://echo.websocket.org" // Placeholder, in real app replace with actual wss url

    fun connect() {
        if (isConnected) return
        isConnected = true
        Log.d("WebSocketManager", "Connected to mock WS")
        
        scope.launch {
            while (isConnected) {
                delay(10000)
                // Optionally emit mock events here
            }
        }
    }
    
    // Ignored old connect body:
    fun old_connect() {
        val request = Request.Builder()
            .url(WSS_URL)
            .build()
            
        webSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                isConnected = true
                Log.d("WebSocketManager", "Connected to WS")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (text.trim().startsWith("{")) {
                    try {
                        val json = JSONObject(text)
                        when (json.optString("type")) {
                            "new_message" -> {
                                val data = json.getJSONObject("data")
                                scope.launch {
                                    _events.emit(
                                        InboundEvent.NewMessage(
                                            messageId = data.optString("message_id", data.optString("messageId")),
                                            chatId = data.optString("chat_id", data.optString("chatId")),
                                            senderId = data.optString("sender_id", data.optString("senderId")),
                                            text = data.optString("text"),
                                            timestamp = data.optLong("timestamp", System.currentTimeMillis())
                                        )
                                    )
                                }
                            }
                            "read_receipt" -> {
                                val data = json.getJSONObject("data")
                                scope.launch {
                                    _events.emit(
                                        InboundEvent.ReadReceipt(
                                            chatId = data.optString("chat_id", data.optString("chatId")),
                                            messageId = data.optString("message_id", data.optString("messageId"))
                                        )
                                    )
                                }
                            }
                            "user_typing" -> {
                                val data = json.getJSONObject("data")
                                scope.launch {
                                    _events.emit(
                                        InboundEvent.UserTyping(
                                            chatId = data.optString("chat_id", data.optString("chatId")),
                                            userId = data.optString("user_id", data.optString("userId"))
                                        )
                                    )
                                }
                            }
                            "presence_update" -> {
                                val data = json.getJSONObject("data")
                                scope.launch {
                                    _events.emit(
                                        InboundEvent.PresenceUpdate(
                                            userId = data.optString("user_id", data.optString("userId")),
                                            isOnline = data.optBoolean("is_online", data.optBoolean("isOnline")),
                                            lastSeen = data.optLong("last_seen", data.optLong("lastSeen", System.currentTimeMillis()))
                                        )
                                    )
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("WebSocketManager", "Failed to parse ws message: $text", e)
                    }
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                isConnected = false
                Log.d("WebSocketManager", "Closed WS")
                scheduleReconnect()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                isConnected = false
                Log.e("WebSocketManager", "WS Failure", t)
                scheduleReconnect()
            }
        })
    }
    
    private fun scheduleReconnect() {
        scope.launch {
            delay(reconnectDelayMs)
            connect()
        }
    }
    
    fun disconnect() {
        webSocket?.close(1000, "User offline")
        webSocket = null
        isConnected = false
    }
    
    fun sendMessage(text: String) {
        webSocket?.send(text)
    }
}
