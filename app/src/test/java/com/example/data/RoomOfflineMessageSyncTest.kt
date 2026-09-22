package com.example.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.ui.Chat
import com.example.ui.Message
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class RoomOfflineMessageSyncTest {

    private lateinit var database: AppDatabase
    private lateinit var queuedMessageDao: QueuedMessageDao
    private lateinit var messageDao: MessageDao
    private lateinit var chatDao: ChatDao
    private lateinit var repository: MessengerRepository

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        queuedMessageDao = database.queuedMessageDao()
        messageDao = database.messageDao()
        chatDao = database.chatDao()

        val sharedPrefs = context.getSharedPreferences("test_prefs", Context.MODE_PRIVATE)
        val okHttpClient = NetworkModule.provideOkHttpClient(context) { _, _, _, _ -> }
        val webSocketManager = WebSocketManager(okHttpClient)

        repository = MessengerRepository(
            database.botDao(),
            database.userDao(),
            chatDao,
            messageDao,
            database.groupMemberDao(),
            database.draftDao(),
            database.contactDao(),
            database.paymentTransactionDao(),
            queuedMessageDao,
            sharedPrefs,
            webSocketManager
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun testCacheOutgoingMessageInRoom() = runBlocking {
        val chatId = "chat_test_123"
        val messageId = "msg_offline_001"
        val queuedMsg = QueuedMessage(
            id = messageId,
            chatId = chatId,
            senderId = "me",
            text = "Hello while offline",
            status = "QUEUED"
        )

        // Cache in Room
        repository.insertQueuedMessage(queuedMsg)

        val pending = repository.getPendingQueueList()
        assertEquals(1, pending.size)
        assertEquals(messageId, pending[0].id)
        assertEquals("Hello while offline", pending[0].text)

        val count = repository.getQueuedCount().first()
        assertEquals(1, count)

        val countForChat = repository.getQueuedCountForChat(chatId).first()
        assertEquals(1, countForChat)
    }

    @Test
    fun testAutoPushRoomCachedMessagesToFirebase() = runBlocking {
        val chatId = "chat_test_firebase"
        val messageId1 = "msg_fb_001"
        val messageId2 = "msg_fb_002"

        // Insert chat in Room
        chatDao.insertChat(
            Chat(
                id = chatId,
                title = "Test Chat",
                lastMessage = "",
                lastMessageTimestamp = System.currentTimeMillis()
            )
        )

        // Insert initial undelivered messages in Room
        messageDao.insertMessage(
            Message(
                id = messageId1,
                chatId = chatId,
                senderId = "me",
                text = "First offline message",
                timestamp = System.currentTimeMillis(),
                isDelivered = false
            )
        )
        messageDao.insertMessage(
            Message(
                id = messageId2,
                chatId = chatId,
                senderId = "me",
                text = "Second offline message",
                timestamp = System.currentTimeMillis(),
                isDelivered = false
            )
        )

        // Cache in Room queued_messages table
        repository.insertQueuedMessage(
            QueuedMessage(id = messageId1, chatId = chatId, senderId = "me", text = "First offline message")
        )
        repository.insertQueuedMessage(
            QueuedMessage(id = messageId2, chatId = chatId, senderId = "me", text = "Second offline message")
        )

        assertEquals(2, repository.getPendingQueueList().size)

        // Execute Firebase sync push
        val syncedCount = FirebaseMessageSyncManager.syncAllCachedMessages(
            repository = repository,
            signalProtocolManager = null
        )

        assertEquals(2, syncedCount)

        // Verify queue is purged from Room
        val remainingQueue = repository.getPendingQueueList()
        assertTrue(remainingQueue.isEmpty())

        // Verify message delivery status in Room messages table is updated to delivered
        val msg1 = messageDao.getMessageById(messageId1)
        val msg2 = messageDao.getMessageById(messageId2)
        assertNotNull(msg1)
        assertNotNull(msg2)
        assertTrue(msg1!!.isDelivered)
        assertTrue(msg2!!.isDelivered)
    }
}
