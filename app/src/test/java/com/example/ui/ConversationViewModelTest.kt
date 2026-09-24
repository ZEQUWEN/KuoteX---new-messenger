package com.example.ui

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class ConversationViewModelTest {

    @Test
    fun initialState_isInitial() {
        val viewModel = ConversationViewModel(
            firestoreProvider = { throw IllegalStateException("Offline test") }
        )
        assertEquals(ConversationUiState.Initial, viewModel.uiState.value)
        assertEquals(FirestoreConnectionStatus.IDLE, viewModel.connectionStatus.value)
    }

    @Test
    fun observeConversation_blankId_emitsError() {
        val viewModel = ConversationViewModel(
            firestoreProvider = { throw IllegalStateException("Offline test") }
        )
        viewModel.observeConversation("   ")
        val state = viewModel.uiState.value
        assertTrue(state is ConversationUiState.Error)
        assertEquals("Conversation ID cannot be blank", (state as ConversationUiState.Error).message)
    }

    @Test
    fun observeConversation_unavailableFirestore_emitsError() {
        val viewModel = ConversationViewModel(
            firestoreProvider = { throw IllegalStateException("Firestore offline") }
        )
        viewModel.observeConversation("chat_123")
        val state = viewModel.uiState.value
        assertTrue(state is ConversationUiState.Error)
        assertEquals(FirestoreConnectionStatus.ERROR, viewModel.connectionStatus.value)
    }

    @Test
    fun stopObserving_resetsState() {
        val viewModel = ConversationViewModel(
            firestoreProvider = { throw IllegalStateException("Offline test") }
        )
        viewModel.observeConversation("chat_123")
        viewModel.stopObserving(resetState = true)

        assertEquals(ConversationUiState.Initial, viewModel.uiState.value)
        assertEquals(FirestoreConnectionStatus.IDLE, viewModel.connectionStatus.value)
    }

    @Test
    fun conversationDocumentModel_computedHelpersWork() {
        val conversation = ConversationDocument(
            id = "direct_u1_u2",
            title = "Alice",
            participants = listOf("user_1", "user_2"),
            participantNames = mapOf("user_1" to "Bob", "user_2" to "Alice"),
            lastMessage = "Hey there!",
            lastMessageTimestamp = System.currentTimeMillis(),
            unreadCounts = mapOf("user_1" to 2, "user_2" to 0)
        )

        assertEquals("user_2", conversation.getOtherParticipant("user_1"))
        assertEquals(2, conversation.getUnreadForUser("user_1"))
        assertEquals(0, conversation.getUnreadForUser("user_2"))
        assertEquals("Alice", conversation.getDisplayNameForUser("user_2"))
        assertNotNull(conversation.formattedLastMessageTime())
    }
}
