package com.example.ui.botapi

import com.example.botapi.*
import com.example.crypto.SignalProtocolManager
import com.example.data.MessengerRepository
import com.example.ui.Chat
import java.util.UUID
import kotlinx.coroutines.flow.first

import com.example.data.PaymentTransaction

class ShopBot : Bot {
    override val id = "shopbot"
    override val name = "Shop Bot"
    override val description = "Buy virtual goods."
    override val category = "Payments"
    override val longDescription = "An example bot that demonstrates the Payments API in Telegram."
    override val commands = listOf(
        BotCommand("/start", "Start shopping"),
        BotCommand("/buy", "Buy a test item")
    )

    override suspend fun onMessageReceived(
        messageText: String,
        chat: Chat,
        repository: MessengerRepository,
        signalProtocolManager: SignalProtocolManager
    ) {
        if (messageText == "/start") {
            sendReply("Welcome to the shop! Use /buy to get a test item.", chat.id, repository, signalProtocolManager)
        } else if (messageText == "/buy") {
            // Send an invoice
            val markup = InlineKeyboardMarkup(
                inlineKeyboard = listOf(
                    listOf(InlineKeyboardButton(text = "Pay $10.00", callbackData = "pay_1000"))
                )
            )
            
            val adapter = BotApiMoshi.moshi.adapter(InlineKeyboardMarkup::class.java)
            val jsonMarkup = adapter.toJson(markup)
            
            val invoice = Invoice(
                title = "Test Product",
                description = "A very useful test product.",
                startParameter = "test_invoice",
                currency = "USD",
                totalAmount = 1000
            )
            val invoiceText = "🧾 Invoice: ${invoice.title}\n\n${invoice.description}\n\nPrice: $10.00"
            
            sendReplyWithMarkup(invoiceText, jsonMarkup, chat.id, repository, signalProtocolManager)
            
            // Log pending transaction
            val transaction = PaymentTransaction(
                id = UUID.randomUUID().toString(),
                botId = id,
                userId = chat.id,
                provider = "Stripe",
                amount = 1000L,
                currency = "USD",
                status = "pending"
            )
            repository.insertPaymentTransaction(transaction)
            
        } else {
            sendReply("Unknown command.", chat.id, repository, signalProtocolManager)
        }
    }

    override suspend fun onCallbackQuery(
        callbackData: String,
        messageId: String,
        chat: Chat,
        repository: MessengerRepository,
        signalProtocolManager: SignalProtocolManager
    ) {
        if (callbackData == "pay_1000") {
            // Simulate PreCheckoutQuery / Answer
            
            // Mark last pending transaction as completed
            val transactions = repository.getPaymentTransactions(id).first()
            val pendingTx = transactions.firstOrNull { it.status == "pending" && it.userId == chat.id }
            if (pendingTx != null) {
                repository.updatePaymentTransactionStatus(pendingTx.id, "completed")
            }
            
            sendReply("Payment successful! Thank you for purchasing Test Product.", chat.id, repository, signalProtocolManager)
        }
    }
    
    private suspend fun sendReplyWithMarkup(
        text: String,
        jsonMarkup: String,
        chatId: String,
        repository: MessengerRepository,
        signalProtocolManager: SignalProtocolManager
    ) {
        val sanitizedText = com.example.utils.MessageSanitizer.sanitize(text)
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
