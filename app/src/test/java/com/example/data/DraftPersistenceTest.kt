package com.example.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.ui.Draft
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class DraftPersistenceTest {

    private lateinit var database: AppDatabase
    private lateinit var draftDao: DraftDao
    private lateinit var repository: MessengerRepository

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        draftDao = database.draftDao()
        val sharedPrefs = context.getSharedPreferences("test_prefs", Context.MODE_PRIVATE)
        val okHttpClient = NetworkModule.provideOkHttpClient(context) { _, _, _, _ -> }
        val webSocketManager = WebSocketManager(okHttpClient)

        repository = MessengerRepository(
            database.botDao(),
            database.userDao(),
            database.chatDao(),
            database.messageDao(),
            database.groupMemberDao(),
            draftDao,
            database.contactDao(),
            database.paymentTransactionDao(),
            database.queuedMessageDao(),
            sharedPrefs,
            webSocketManager
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun testSaveAndRetrieveDraft() = runBlocking {
        val chatId = "chat_test_draft_1"
        val draftContent = "Hey, let's meet tomorrow at 10 AM"

        // Save draft to Room
        repository.updateDraft(chatId, draftContent)

        // Retrieve draft
        val retrieved = repository.getDraft(chatId)
        assertNotNull(retrieved)
        assertEquals(chatId, retrieved?.chatId)
        assertEquals(draftContent, retrieved?.text)

        val allDrafts = repository.allDrafts.first()
        assertEquals(1, allDrafts.size)
        assertEquals(chatId, allDrafts[0].chatId)
        assertEquals(draftContent, allDrafts[0].text)
    }

    @Test
    fun testUpdateDraft() = runBlocking {
        val chatId = "chat_test_draft_2"
        
        repository.updateDraft(chatId, "Initial draft")
        assertEquals("Initial draft", repository.getDraft(chatId)?.text)

        // Modify draft
        repository.updateDraft(chatId, "Updated draft message")
        assertEquals("Updated draft message", repository.getDraft(chatId)?.text)
    }

    @Test
    fun testClearDraftOnBlankOrExplicitClear() = runBlocking {
        val chatId = "chat_test_draft_3"
        repository.updateDraft(chatId, "Temporary text")
        assertNotNull(repository.getDraft(chatId))

        // Saving null or blank clears the draft in Room
        repository.updateDraft(chatId, "")
        assertNull(repository.getDraft(chatId))

        val allDrafts = repository.allDrafts.first()
        assertEquals(0, allDrafts.size)
    }
}
