package com.example.botapi

import com.squareup.moshi.JsonClass
import com.squareup.moshi.Json
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

@JsonClass(generateAdapter = true)
data class Update(
    val updateId: Int,
    val message: Message? = null,
    val callbackQuery: CallbackQuery? = null
)

@JsonClass(generateAdapter = true)
data class Message(
    val messageId: Int,
    val from: User?,
    val date: Int,
    val chat: Chat,
    val text: String? = null,
    val replyMarkup: InlineKeyboardMarkup? = null
)

@JsonClass(generateAdapter = true)
data class User(
    val id: Long,
    val isBot: Boolean,
    val firstName: String,
    val username: String? = null
)

@JsonClass(generateAdapter = true)
data class Chat(
    val id: Long,
    val type: String,
    val title: String? = null,
    val username: String? = null
)

@JsonClass(generateAdapter = true)
data class CallbackQuery(
    val id: String,
    val from: User,
    val message: Message?,
    val inlineMessageId: String? = null,
    val chatInstance: String,
    val data: String? = null
)

@JsonClass(generateAdapter = true)
data class InlineKeyboardMarkup(
    @Json(name = "inline_keyboard") val inlineKeyboard: List<List<InlineKeyboardButton>>
)

@JsonClass(generateAdapter = true)
data class InlineKeyboardButton(
    val text: String,
    @Json(name = "callback_data") val callbackData: String? = null,
    val url: String? = null
)

@JsonClass(generateAdapter = true)
data class LabeledPrice(
    val label: String,
    val amount: Int
)

@JsonClass(generateAdapter = true)
data class Invoice(
    val title: String,
    val description: String,
    val startParameter: String,
    val currency: String,
    val totalAmount: Int
)

@JsonClass(generateAdapter = true)
data class PreCheckoutQuery(
    val id: String,
    val from: User,
    val currency: String,
    val totalAmount: Int,
    val invoicePayload: String
)

object BotApiMoshi {
    val moshi: Moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()
}
