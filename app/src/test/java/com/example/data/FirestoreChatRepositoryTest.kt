package com.example.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.data.repository.FirestoreChatRepository
import com.example.data.repository.FirestoreChatRepositoryImpl
import com.example.ui.Chat
import com.example.ui.Message
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class FirestoreChatRepositoryTest {

    private lateinit var database: AppDatabase
    private lateinit var chatDao: ChatDao
    private lateinit var messageDao: MessageDao
    private lateinit var queuedMessageDao: QueuedMessageDao
    private lateinit var draftDao: DraftDao
    private lateinit var firestoreChatRepository: FirestoreChatRepository

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        chatDao = database.chatDao()
        messageDao = database.messageDao()
        queuedMessageDao = database.queuedMessageDao()
        draftDao = database.draftDao()

        // Pass null Firestore provider for pure local Room fallback testing in unit tests
        firestoreChatRepository = FirestoreChatRepositoryImpl(
            chatDao = chatDao,
            messageDao = messageDao,
            queuedMessageDao = queuedMessageDao,
            draftDao = draftDao,
            firestoreProvider = { throw IllegalStateException("Unit test offline mode") }
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun testGetDirectChatId_isDeterministic() {
        val id1 = FirestoreChatRepositoryImpl.getDirectChatId("user_alice", "user_bob")
        val id2 = FirestoreChatRepositoryImpl.getDirectChatId("user_bob", "user_alice")

        assertEquals(id1, id2)
        assertEquals("direct_user_alice_user_bob", id1)
    }

    @Test
    fun testGetOrCreateDirectChat_createsLocalChatRecord() = runBlocking {
        val result = firestoreChatRepository.getOrCreateDirectChat(
            currentUserId = "alice123",
            otherUserId = "bob456",
            currentUserName = "Alice",
            otherUserName = "Bob"
        )

        assertTrue(result.isSuccess)
        val chat = result.getOrNull()
        assertNotNull(chat)
        assertEquals("direct_alice123_bob456", chat?.id)
        assertEquals("Bob", chat?.title)

        // Verify it was stored in local Room DAO
        val dbChat = chatDao.getChatById("direct_alice123_bob456")
        assertNotNull(dbChat)
        assertEquals("Bob", dbChat?.title)
    }

    @Test
    fun testSendMessage_persistsLocallyAndQueuesWhenOffline() = runBlocking {
        val chatId = "direct_user1_user2"
        chatDao.insertChat(Chat(id = chatId, title = "User 2", lastMessage = ""))

        val result = firestoreChatRepository.sendMessage(
            chatId = chatId,
            senderId = "user1",
            receiverId = "user2",
            text = "Hello via Firestore repository!"
        )

        assertTrue(result.isSuccess)
        val sentMsg = result.getOrNull()
        assertNotNull(sentMsg)

        // Verify local Room persistence
        val messages = messageDao.getMessagesForChat(chatId).first()
        assertTrue(messages.isNotEmpty())

        // Verify offline queue entry was created when cloud was unavailable
        val queued = queuedMessageDao.getPendingQueueList()
        assertTrue(queued.any { it.text == "Hello via Firestore repository!" })
    }

    @Test
    fun testDraftLifecycle() = runBlocking {
        val chatId = "chat_draft_test"
        firestoreChatRepository.saveDraft(chatId, "Unsent draft text")

        val draft = firestoreChatRepository.getDraft(chatId)
        assertNotNull(draft)
        assertEquals("Unsent draft text", draft?.text)

        firestoreChatRepository.clearDraft(chatId)
        val cleared = firestoreChatRepository.getDraft(chatId)
        assertNull(cleared)
    }

    @Test
    fun testMarkMessagesAsRead_updatesLocalChatUnread() = runBlocking {
        val chatId = "chat_read_test"
        chatDao.insertChat(Chat(id = chatId, title = "Test", lastMessage = "", unreadCount = 5))

        firestoreChatRepository.markMessagesAsRead(chatId, "alice", "bob")

        val updatedChat = chatDao.getChatById(chatId)
        assertEquals(0, updatedChat?.unreadCount)
    }
}
