package com.example.ui.botapi

import android.util.Log
import com.example.botapi.BotApiMoshi
import com.example.botapi.InlineKeyboardButton
import com.example.botapi.InlineKeyboardMarkup
import com.example.botapi.Invoice
import com.example.botapi.PaymentWebhookPayload
import com.example.botapi.VipSubscriptionPlan
import com.example.crypto.SignalProtocolManager
import com.example.data.MessengerRepository
import com.example.data.ecosystem.KuoteXEcosystemFirestoreManager
import com.example.ui.Chat
import com.example.utils.MessageSanitizer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * KuoteX Automated VIP Subscription & Payment Fulfillment Bot
 * Uses Moshi serialization to generate Telegram-compliant invoices, processes callback queries,
 * and updates user status in Firestore real-time.
 */
class KuoteXVipBot : Bot {
    override val id = "kuotex_vip_bot"
    override val name = "KuoteX VIP Bot"
    override val description = "Активация и управление подпиской KuoteX VIP"
    override val category = "Payments & VIP"
    override val longDescription = "Официальный бот подписок KuoteX. Позволяет оформить VIP-статус, получить буст-голоса и эксклюзивные привилегии."
    override val commands = listOf(
        BotCommand("/start", "Главное меню и статус подписки"),
        BotCommand("/vip", "Оформить подписку KuoteX VIP (300 ⭐️)"),
        BotCommand("/status", "Проверить статус подписки и доступные бусты"),
        BotCommand("/help", "Информация о привилегиях VIP")
    )

    private val defaultPlan = VipSubscriptionPlan(
        planId = "kuotex_vip_30d",
        title = "KuoteX VIP на 30 дней",
        durationDays = 30,
        priceStars = 300L,
        boostVotesGranted = 4
    )

    override suspend fun onMessageReceived(
        messageText: String,
        chat: Chat,
        repository: MessengerRepository,
        signalProtocolManager: SignalProtocolManager
    ) {
        val trimmed = messageText.trim()
        when {
            trimmed == "/start" || trimmed == "/vip" -> {
                sendVipInvoice(chat, repository, signalProtocolManager)
            }
            trimmed == "/status" -> {
                sendStatusReport(chat, repository, signalProtocolManager)
            }
            trimmed == "/help" -> {
                val helpText = """
                    🌟 **Привилегии KuoteX VIP:**
                    
                    • ⭐️ **4 голоса буста** для прокачки любых каналов и групп
                    • 👑 **Эксклюзивный значок VIP** рядом с именем
                    • 🎁 **До 6 закрепленных анимированных подарков** в шапке профиля
                    • 🎨 **Кастомизация каналов**: цвета, эмодзи-статусы, обои
                    • ⚡️ **Увеличенная скорость** отправки медиа и лимит историй
                    
                    Для оформления нажмите **/vip**.
                """.trimIndent()
                sendReply(helpText, chat.id, repository, signalProtocolManager)
            }
            else -> {
                sendReply("Используйте команду /vip для оформления подписки или /status для проверки.", chat.id, repository, signalProtocolManager)
            }
        }
    }

    private suspend fun sendVipInvoice(
        chat: Chat,
        repository: MessengerRepository,
        signalProtocolManager: SignalProtocolManager
    ) {
        val markup = InlineKeyboardMarkup(
            inlineKeyboard = listOf(
                listOf(
                    InlineKeyboardButton(text = "⭐️ Оплатить 300 Stars (30 дней)", callbackData = "pay_vip_300")
                ),
                listOf(
                    InlineKeyboardButton(text = "ℹ️ Подробнее о привилегиях", callbackData = "info_vip")
                )
            )
        )

        val adapter = BotApiMoshi.moshi.adapter(InlineKeyboardMarkup::class.java)
        val jsonMarkup = adapter.toJson(markup)

        val invoice = Invoice(
            title = defaultPlan.title,
            description = "Активация статуса KuoteX VIP на 30 дней с начислением 4 буст-голосов.",
            startParameter = "vip_sub_30d",
            currency = "XTR",
            totalAmount = defaultPlan.priceStars.toInt()
        )

        val invoiceText = """
            🧾 **Счет на оплату: ${invoice.title}**
            
            ${invoice.description}
            
            Стоимость: **${invoice.totalAmount} ⭐️ Stars**
            Срок действия: **30 дней**
            Бонус: **+4 буст-голоса**
            
            Нажмите кнопку ниже для подтверждения оплаты:
        """.trimIndent()

        sendReplyWithMarkup(invoiceText, jsonMarkup, chat.id, repository, signalProtocolManager)
    }

    private suspend fun sendStatusReport(
        chat: Chat,
        repository: MessengerRepository,
        signalProtocolManager: SignalProtocolManager
    ) {
        val currentUser = KuoteXEcosystemFirestoreManager.currentUserState.value
        val isVip = currentUser?.isVipActive() == true
        val expirationText = if (currentUser != null && currentUser.vipExpiration > 0) {
            val date = java.text.SimpleDateFormat("dd.MM.yyyy HH:mm", java.util.Locale.getDefault())
                .format(java.util.Date(currentUser.vipExpiration))
            "до $date"
        } else if (isVip) "Бессрочно (Admin/Dev)" else "Не активна"

        val votes = currentUser?.availableBoostVotes ?: 0
        val balance = currentUser?.balance ?: 1000L

        val text = """
            👤 **Статус аккаунта KuoteX:**
            
            • VIP-статус: **${if (isVip) "АКТИВЕН 🌟" else "Не активен ⚪️"}**
            • Срок действия: **$expirationText**
            • Доступно буст-голосов: **$votes 🚀**
            • Баланс: **$balance ⭐️ Stars**
            
            ${if (!isVip) "Оформить подписку: /vip" else "Спасибо за поддержку KuoteX!"}
        """.trimIndent()

        sendReply(text, chat.id, repository, signalProtocolManager)
    }

    override suspend fun onCallbackQuery(
        callbackData: String,
        messageId: String,
        chat: Chat,
        repository: MessengerRepository,
        signalProtocolManager: SignalProtocolManager
    ) {
        when (callbackData) {
            "pay_vip_300" -> {
                // Process atomic transaction via KuoteXEcosystemFirestoreManager
                val userId = KuoteXEcosystemFirestoreManager.currentUserState.value?.userId ?: chat.id
                val idempotencyKey = "vip_pay_${UUID.randomUUID()}"

                CoroutineScope(Dispatchers.IO).launch {
                    val result = KuoteXEcosystemFirestoreManager.activateVipSubscriptionAtomic(
                        userId = userId,
                        months = 1,
                        idempotencyKey = idempotencyKey,
                        price = 300L
                    )

                    result.fold(
                        onSuccess = { userDoc ->
                            val successMsg = """
                                🎉 **Оплата прошла успешно!**
                                
                                Подписка **KuoteX VIP** успешно активирована на 30 дней!
                                
                                ⭐️ **Начислено +4 буст-голоса** для прокачки каналов.
                                👑 Значок VIP и расширенные возможности профиля уже доступны в вашем аккаунте.
                            """.trimIndent()
                            sendReply(successMsg, chat.id, repository, signalProtocolManager)
                        },
                        onFailure = { error ->
                            val errorMsg = "❌ Не удалось завершить транзакцию: ${error.localizedMessage ?: "Недостаточно средств на балансе"}. Пополните баланс Stars и повторите попытку."
                            sendReply(errorMsg, chat.id, repository, signalProtocolManager)
                        }
                    )
                }
            }
            "info_vip" -> {
                val helpText = """
                    🌟 **Привилегии KuoteX VIP:**
                    
                    • ⭐️ **4 голоса буста** для прокачки любых каналов и групп
                    • 👑 **Эксклюзивный значок VIP** рядом с именем
                    • 🎁 **До 6 закрепленных анимированных подарков** в шапке профиля
                    • 🎨 **Кастомизация каналов**: цвета, эмодзи-статусы, обои
                    • ⚡️ **Увеличенная скорость** отправки медиа и лимит историй
                """.trimIndent()
                sendReply(helpText, chat.id, repository, signalProtocolManager)
            }
        }
    }

    private suspend fun sendReplyWithMarkup(
        text: String,
        jsonMarkup: String,
        chatId: String,
        repository: MessengerRepository,
        signalProtocolManager: SignalProtocolManager
    ) {
        val sanitizedText = MessageSanitizer.sanitize(text)
        val encryptedReply = signalProtocolManager.encryptMessage(sanitizedText)
        val replyMsg = com.example.ui.Message(
            id = UUID.randomUUID().toString(),
            chatId = chatId,
            senderId = "bot_$id",
            text = encryptedReply,
            buttonsData = jsonMarkup,
            timestamp = System.currentTimeMillis()
        )
        repository.insertMessageAndUpdateChat(replyMsg, sanitizedText, name)
    }
}
