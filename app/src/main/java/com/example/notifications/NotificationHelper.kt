package com.example.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import com.example.MainActivity

object NotificationHelper {

    const val CHANNEL_MESSAGES_ID = "messages_channel"
    const val CHANNEL_MESSAGES_NAME = "Личные и групповые сообщения"
    const val CHANNEL_MENTIONS_ID = "mentions_channel"
    const val CHANNEL_MENTIONS_NAME = "Упоминания и ответы"

    const val KEY_TEXT_REPLY = "key_text_reply"
    const val ACTION_REPLY = "com.example.notifications.ACTION_REPLY"
    const val ACTION_MARK_AS_READ = "com.example.notifications.ACTION_MARK_AS_READ"

    const val EXTRA_CHAT_ID = "extra_chat_id"
    const val EXTRA_SENDER_ID = "extra_sender_id"
    const val EXTRA_SENDER_NAME = "extra_sender_name"
    const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
    const val EXTRA_REPLY_TEXT = "extra_reply_text"

    fun initNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // General messages channel with bubble permission
            val messagesChannel = NotificationChannel(
                CHANNEL_MESSAGES_ID,
                CHANNEL_MESSAGES_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Уведомления о новых сообщениях в чатах"
                enableLights(true)
                lightColor = Color.CYAN
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 150, 100, 150)
                setShowBadge(true)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    setAllowBubbles(true)
                }
            }

            // High-priority mentions channel
            val mentionsChannel = NotificationChannel(
                CHANNEL_MENTIONS_ID,
                CHANNEL_MENTIONS_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Срочные уведомления об упоминаниях (@username) и ответах"
                enableLights(true)
                lightColor = Color.MAGENTA
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 250, 120, 250)
                setShowBadge(true)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    setAllowBubbles(true)
                }
            }

            notificationManager.createNotificationChannels(listOf(messagesChannel, mentionsChannel))
        }
    }

    fun showMessageNotification(
        context: Context,
        chatId: String,
        senderId: String,
        senderName: String,
        text: String,
        isMention: Boolean = false,
        chatTitle: String? = null,
        senderAvatarUrl: String? = null,
        isGroup: Boolean = false,
        isChannel: Boolean = false
    ): Int {
        initNotificationChannels(context)

        val notificationId = (chatId.hashCode() and 0x7FFFFFFF) % 100000 + 1000

        // 1. PendingIntent to open Chat in MainActivity
        val contentIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("OPEN_CHAT_ID", chatId)
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 2. Direct Reply Action with RemoteInput
        val remoteInput = RemoteInput.Builder(KEY_TEXT_REPLY)
            .setLabel("Ответить...")
            .build()

        val replyIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = ACTION_REPLY
            putExtra(EXTRA_CHAT_ID, chatId)
            putExtra(EXTRA_SENDER_ID, senderId)
            putExtra(EXTRA_SENDER_NAME, senderName)
            putExtra(EXTRA_NOTIFICATION_ID, notificationId)
        }
        val replyPendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId * 10 + 1,
            replyIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
        )

        val replyAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_menu_send,
            "ОТВЕТИТЬ",
            replyPendingIntent
        )
            .addRemoteInput(remoteInput)
            .setAllowGeneratedReplies(true)
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
            .build()

        // 3. Mark As Read Action ("ОТМЕТИТЬ ПРОЧИТАННЫМ")
        val markReadIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = ACTION_MARK_AS_READ
            putExtra(EXTRA_CHAT_ID, chatId)
            putExtra(EXTRA_NOTIFICATION_ID, notificationId)
        }
        val markReadPendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId * 10 + 2,
            markReadIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val markReadAction = NotificationCompat.Action.Builder(
            android.R.drawable.checkbox_on_background,
            "ОТМЕТИТЬ ПРОЧИТАННЫМ",
            markReadPendingIntent
        )
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_MARK_AS_READ)
            .build()

        // Formatting matching screenshot:
        // Header: Group / Channel title (e.g. "morpheus empire 👑")
        // Sender: "Billy Herrington"
        // Body: text
        val isGroupOrChannel = isGroup || isChannel || !chatTitle.isNullOrBlank()
        val displayTitle = if (isGroupOrChannel && !chatTitle.isNullOrBlank()) chatTitle else senderName
        val contentText = if (isGroupOrChannel && !chatTitle.isNullOrBlank()) "$senderName: $text" else text
        val summaryText = when {
            isMention -> "@Упоминание"
            isChannel -> "Канал"
            isGroup -> "Группа"
            else -> "KuoteX"
        }

        val bigTextContent = if (isGroupOrChannel && !chatTitle.isNullOrBlank() && chatTitle != senderName) {
            "$senderName\n$text"
        } else {
            text
        }

        val channelId = if (isMention) CHANNEL_MENTIONS_ID else CHANNEL_MESSAGES_ID
        val largeAvatar = createAvatarBitmap(
            name = if (isGroupOrChannel && !chatTitle.isNullOrBlank()) chatTitle else senderName,
            isGroup = isGroupOrChannel
        )

        val smallIconRes = try {
            val resId = context.resources.getIdentifier("ic_notification_kuotex", "drawable", context.packageName)
            if (resId != 0) resId else android.R.drawable.ic_dialog_info
        } catch (e: Exception) {
            android.R.drawable.ic_dialog_info
        }

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(smallIconRes)
            .setLargeIcon(largeAvatar)
            .setContentTitle(displayTitle)
            .setContentText(contentText)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .setBigContentTitle(displayTitle)
                    .bigText(bigTextContent)
                    .setSummaryText(summaryText)
            )
            .setPriority(if (isMention) NotificationCompat.PRIORITY_MAX else NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setContentIntent(contentPendingIntent)
            .addAction(replyAction)
            .addAction(markReadAction)
            .setColor(if (isMention) Color.rgb(255, 179, 0) else Color.rgb(0, 195, 255))
            .setDefaults(NotificationCompat.DEFAULT_ALL)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            builder.setBubbleMetadata(
                NotificationCompat.BubbleMetadata.Builder(
                    contentPendingIntent,
                    androidx.core.graphics.drawable.IconCompat.createWithResource(context, smallIconRes)
                )
                    .setDesiredHeight(600)
                    .setAutoExpandBubble(false)
                    .setSuppressNotification(false)
                    .build()
            )
        }

        try {
            val notificationManager = NotificationManagerCompat.from(context)
            notificationManager.notify(notificationId, builder.build())
        } catch (e: SecurityException) {
            android.util.Log.e("NotificationHelper", "Permission missing for notification: ${e.message}")
        } catch (e: Exception) {
            android.util.Log.e("NotificationHelper", "Failed to show notification: ${e.message}")
        }

        return notificationId
    }

    private fun createAvatarBitmap(name: String, isGroup: Boolean = false): android.graphics.Bitmap {
        val size = 140
        val bitmap = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)

        // Palette color based on name hash
        val palette = if (isGroup) {
            listOf(
                Color.rgb(41, 128, 185),
                Color.rgb(142, 68, 173),
                Color.rgb(39, 174, 96),
                Color.rgb(211, 84, 0),
                Color.rgb(192, 57, 43)
            )
        } else {
            listOf(
                Color.rgb(52, 152, 219),
                Color.rgb(155, 89, 182),
                Color.rgb(46, 204, 113),
                Color.rgb(230, 126, 34),
                Color.rgb(231, 76, 60)
            )
        }
        val bgColor = palette[(name.hashCode() and 0x7FFFFFFF) % palette.size]

        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = bgColor
            style = android.graphics.Paint.Style.FILL
        }
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)

        val textPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 52f
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
            textAlign = android.graphics.Paint.Align.CENTER
        }

        val initials = name.trim().split(" ")
            .filter { it.isNotBlank() }
            .take(2)
            .mapNotNull { it.firstOrNull()?.uppercaseChar() }
            .joinToString("")
            .ifEmpty { if (isGroup) "G" else "U" }

        val yPos = (size / 2f - (textPaint.descent() + textPaint.ascent()) / 2)
        canvas.drawText(initials, size / 2f, yPos, textPaint)
        return bitmap
    }

    fun cancelNotification(context: Context, notificationId: Int) {
        try {
            val notificationManager = NotificationManagerCompat.from(context)
            notificationManager.cancel(notificationId)
        } catch (e: Exception) {
            android.util.Log.e("NotificationHelper", "Failed to cancel notification: ${e.message}")
        }
    }

    fun cancelChatNotifications(context: Context, chatId: String) {
        val notificationId = (chatId.hashCode() and 0x7FFFFFFF) % 100000 + 1000
        cancelNotification(context, notificationId)
    }
}
