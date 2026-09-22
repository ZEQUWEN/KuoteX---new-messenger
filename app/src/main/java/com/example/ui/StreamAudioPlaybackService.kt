package com.example.ui

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.sin

class StreamAudioPlaybackService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var audioTrack: AudioTrack? = null
    private var audioJob: Job? = null

    companion object {
        const val CHANNEL_ID = "stream_audio_playback_channel"
        const val NOTIFICATION_ID = 4099

        const val ACTION_START = "com.example.action.START_STREAM_AUDIO"
        const val ACTION_STOP = "com.example.action.STOP_STREAM_AUDIO"
        const val ACTION_TOGGLE_MUTE = "com.example.action.TOGGLE_STREAM_MUTE"
        const val ACTION_TOGGLE_PLAY = "com.example.action.TOGGLE_STREAM_PLAY"

        const val EXTRA_STREAM_ID = "extra_stream_id"
        const val EXTRA_STREAM_TITLE = "extra_stream_title"
        const val EXTRA_STREAM_HOST_NAME = "extra_stream_host_name"
        const val EXTRA_STREAM_HOST_USER_ID = "extra_stream_host_user_id"
        const val EXTRA_STREAM_AVATAR = "extra_stream_avatar"

        private val _currentSession = MutableStateFlow<LiveStreamSession?>(null)
        val currentSession: StateFlow<LiveStreamSession?> = _currentSession.asStateFlow()

        private val _isPlaying = MutableStateFlow(false)
        val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

        private val _isMuted = MutableStateFlow(false)
        val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()

        fun start(context: Context, session: LiveStreamSession) {
            val intent = Intent(context, StreamAudioPlaybackService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_STREAM_ID, session.id)
                putExtra(EXTRA_STREAM_TITLE, session.title)
                putExtra(EXTRA_STREAM_HOST_NAME, session.hostDisplayName.ifBlank { session.hostUsername })
                putExtra(EXTRA_STREAM_HOST_USER_ID, session.hostUserId)
                putExtra(EXTRA_STREAM_AVATAR, session.hostAvatarUrl)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, StreamAudioPlaybackService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }

        fun toggleMute(context: Context) {
            val intent = Intent(context, StreamAudioPlaybackService::class.java).apply {
                action = ACTION_TOGGLE_MUTE
            }
            context.startService(intent)
        }

        fun togglePlay(context: Context) {
            val intent = Intent(context, StreamAudioPlaybackService::class.java).apply {
                action = ACTION_TOGGLE_PLAY
            }
            context.startService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val streamId = intent.getStringExtra(EXTRA_STREAM_ID) ?: "stream_1"
                val streamTitle = intent.getStringExtra(EXTRA_STREAM_TITLE) ?: "KuoteX Live Stream 🚀"
                val hostName = intent.getStringExtra(EXTRA_STREAM_HOST_NAME) ?: "Alice"
                val hostUserId = intent.getStringExtra(EXTRA_STREAM_HOST_USER_ID) ?: "2"
                val avatar = intent.getStringExtra(EXTRA_STREAM_AVATAR) ?: ""

                val session = LiveStreamSession(
                    id = streamId,
                    hostUserId = hostUserId,
                    hostDisplayName = hostName,
                    hostUsername = "@${hostName.lowercase()}",
                    hostAvatarUrl = avatar,
                    title = streamTitle,
                    isLive = true
                )
                _currentSession.value = session
                _isPlaying.value = true
                _isMuted.value = false

                startForegroundServiceWithNotification(session)
                startAudioLoop()
            }
            ACTION_TOGGLE_MUTE -> {
                _isMuted.value = !_isMuted.value
                val session = _currentSession.value
                if (session != null) {
                    updateNotification(session)
                }
            }
            ACTION_TOGGLE_PLAY -> {
                _isPlaying.value = !_isPlaying.value
                if (_isPlaying.value) {
                    startAudioLoop()
                } else {
                    stopAudioLoop()
                }
                val session = _currentSession.value
                if (session != null) {
                    updateNotification(session)
                }
            }
            ACTION_STOP -> {
                stopPlaybackAndService()
            }
        }
        return START_NOT_STICKY
    }

    private fun startForegroundServiceWithNotification(session: LiveStreamSession) {
        val notification = buildNotification(session)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification(session: LiveStreamSession) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, buildNotification(session))
    }

    private fun buildNotification(session: LiveStreamSession): Notification {
        // PendingIntent to return to the active broadcast in MainActivity
        val contentIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("OPEN_STREAM_HOST_ID", session.hostUserId)
        }
        val contentPendingIntent = PendingIntent.getActivity(
            this,
            0,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Play/Pause Action Intent
        val playToggleIntent = Intent(this, StreamAudioPlaybackService::class.java).apply {
            action = ACTION_TOGGLE_PLAY
        }
        val playTogglePendingIntent = PendingIntent.getService(
            this,
            1,
            playToggleIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Mute/Unmute Action Intent
        val muteToggleIntent = Intent(this, StreamAudioPlaybackService::class.java).apply {
            action = ACTION_TOGGLE_MUTE
        }
        val muteTogglePendingIntent = PendingIntent.getService(
            this,
            2,
            muteToggleIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Stop Action Intent
        val stopIntent = Intent(this, StreamAudioPlaybackService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            3,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val isCurrentlyPlaying = _isPlaying.value
        val isCurrentlyMuted = _isMuted.value

        val statusSubtitle = when {
            !isCurrentlyPlaying -> "Приостановлено"
            isCurrentlyMuted -> "Звук выключен (Без звука)"
            else -> "Прямой эфир • ${session.hostDisplayName}"
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(session.title)
            .setContentText(statusSubtitle)
            .setSubText("Фоновое аудио")
            .setContentIntent(contentPendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(
                if (isCurrentlyPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                if (isCurrentlyPlaying) "Пауза" else "Воспроизвести",
                playTogglePendingIntent
            )
            .addAction(
                if (isCurrentlyMuted) android.R.drawable.ic_lock_silent_mode_off else android.R.drawable.ic_lock_silent_mode,
                if (isCurrentlyMuted) "Вкл звук" else "Без звука",
                muteTogglePendingIntent
            )
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Закрыть",
                stopPendingIntent
            )
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Фоновое аудио трансляций",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Воспроизведение звука прямых трансляций в фоновом режиме"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun startAudioLoop() {
        stopAudioLoop()
        audioJob = serviceScope.launch {
            try {
                val sampleRate = 44100
                val minBufferSize = AudioTrack.getMinBufferSize(
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                )

                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(minBufferSize)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()

                audioTrack = track
                track.play()

                val buffer = ShortArray(minBufferSize / 2)
                var phase = 0.0
                val freq = 220.0 // Soft low ambient presence

                while (isActive && _isPlaying.value) {
                    val muted = _isMuted.value
                    for (i in buffer.indices) {
                        if (muted) {
                            buffer[i] = 0
                        } else {
                            // Gentle subtle ambient sound wave (simulated live stream atmosphere)
                            val sample = (sin(phase) * 600.0).toInt().toShort()
                            buffer[i] = sample
                            phase += 2 * Math.PI * freq / sampleRate
                            if (phase > 2 * Math.PI) phase -= 2 * Math.PI
                        }
                    }
                    track.write(buffer, 0, buffer.size)
                    delay(30)
                }
            } catch (e: Exception) {
                // Audio loop handled safely
            }
        }
    }

    private fun stopAudioLoop() {
        audioJob?.cancel()
        audioJob = null
        try {
            audioTrack?.pause()
            audioTrack?.flush()
            audioTrack?.stop()
            audioTrack?.release()
        } catch (e: Exception) {
            // Ignored
        }
        audioTrack = null
    }

    private fun stopPlaybackAndService() {
        stopAudioLoop()
        _isPlaying.value = false
        _currentSession.value = null
        serviceScope.cancel()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    override fun onDestroy() {
        stopPlaybackAndService()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
