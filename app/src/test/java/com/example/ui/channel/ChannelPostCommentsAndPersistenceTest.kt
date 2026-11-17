package com.example.ui.channel

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.ChatDao
import com.example.data.MessageDao
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
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Calendar

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ChannelPostCommentsAndPersistenceTest {

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var chatDao: ChatDao
    private lateinit var messageDao: MessageDao
    private lateinit var commentDao: ChannelPostCommentDao

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        chatDao = database.chatDao()
        messageDao = database.messageDao()
        commentDao = database.channelPostCommentDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `formatCommentsFooterLabel handles Russian pluralization rules`() {
        assertEquals("Прокомментировать", formatCommentsFooterLabel(0))
        assertEquals("Прокомментировать", formatCommentsFooterLabel(-2))
        assertEquals("1 комментарий", formatCommentsFooterLabel(1))
        assertEquals("2 комментария", formatCommentsFooterLabel(2))
        assertEquals("4 комментария", formatCommentsFooterLabel(4))
        assertEquals("5 комментариев", formatCommentsFooterLabel(5))
        assertEquals("11 комментариев", formatCommentsFooterLabel(11))
        assertEquals("14 комментариев", formatCommentsFooterLabel(14))
        assertEquals("21 комментарий", formatCommentsFooterLabel(21))
        assertEquals("22 комментария", formatCommentsFooterLabel(22))
        assertEquals("100 комментариев", formatCommentsFooterLabel(100))
    }

    @Test
    fun `formatPostDatePill formats Russian day and month`() {
        val cal = Calendar.getInstance().apply {
            set(2026, Calendar.NOVEMBER, 16, 20, 16, 0)
        }
        val formatted = formatPostDatePill(cal.timeInMillis)
        assertTrue(formatted.contains("16"))
        assertTrue(formatted.lowercase().contains("нояб"))
    }

    @Test
    fun `discussion link persists in SharedPreferences across ChannelCustomizationManager reload`() {
        val channelId = "persist_ch_1"
        val groupId = "persist_grp_1"

        ChannelCustomizationManager.init(context)
        ChannelCustomizationManager.initializeCreatedChannel(
            chatId = channelId,
            description = "Persistent channel",
            isPublic = true,
            inviteLink = "t.me/persist_ch_1",
            avatarUrl = null
        )
        ChannelCustomizationManager.linkDiscussionChat(channelId, groupId, "KuoteX Chat")
        ChannelCustomizationManager.updateDiscussionSettings(
            chatId = channelId,
            onlyMembersCanSend = true,
            joinRequestsRequired = false
        )

        // Verify persisted JSON directly in SharedPreferences
        val prefs = context.getSharedPreferences("kuotex_channel_appearance_prefs", Context.MODE_PRIVATE)
        val rawJson = prefs.getString("appearance_map_json", null)
        assertNotNull(rawJson)
        assertTrue(rawJson!!.contains(groupId))
        assertTrue(rawJson.contains("KuoteX Chat"))

        // Clear memory entry and force reload by invoking loadPersistedAppearances via reflection
        val reloadMethod = ChannelCustomizationManager::class.java.getDeclaredMethod(
            "loadPersistedAppearances"
        )
        reloadMethod.isAccessible = true
        reloadMethod.invoke(ChannelCustomizationManager)

        val restored = ChannelCustomizationManager.getCustomization(channelId)
        assertEquals(groupId, restored.discussionChatId)
        assertEquals("KuoteX Chat", restored.discussionChatTitle)
        assertTrue(restored.discussionOnlyMembersCanSend)
        assertFalse(restored.discussionJoinRequestsRequired)

        // Unlink and confirm persistence clears cleanly
        ChannelCustomizationManager.unlinkDiscussionChat(channelId)
        reloadMethod.invoke(ChannelCustomizationManager)
        val afterUnlink = ChannelCustomizationManager.getCustomization(channelId)
        assertNull(afterUnlink.discussionChatId)
        assertEquals("", afterUnlink.discussionChatTitle)
    }

    @Test
    fun `room stores bidirectional channel-group link and tracks post comments accurately`() = runBlocking {
        val channelId = "room_channel_1"
        val groupId = "room_group_1"
        val postId = "room_post_1"

        chatDao.insertChat(
            Chat(
                id = channelId,
                title = "KuoteX Officiall",
                isChannel = true,
                isGroup = false,
                lastMessage = ""
            )
        )
        chatDao.insertChat(
            Chat(
                id = groupId,
                title = "KuoteX Discussion",
                isChannel = false,
                isGroup = true,
                lastMessage = ""
            )
        )

        chatDao.updateLinkedDiscussionGroup(channelId, groupId)
        chatDao.updateLinkedChannelId(groupId, channelId)

        val allChats = chatDao.getAllChats().first()
        val storedChannel = allChats.find { it.id == channelId }!!
        val storedGroup = allChats.find { it.id == groupId }!!
        assertEquals(groupId, storedChannel.linkedDiscussionGroupId)
        assertEquals(channelId, storedGroup.linkedChannelId)

        messageDao.insertMessage(
            Message(
                id = postId,
                chatId = channelId,
                senderId = "creator_1",
                text = "Первый пост в канале",
                timestamp = 1000L
            )
        )

        commentDao.insertComment(
            ChannelPostCommentEntity(
                id = "comment_1",
                channelId = channelId,
                postId = postId,
                senderId = channelId,
                senderName = "KuoteX Officiall",
                isChannelIdentity = true,
                text = "Комментарий от канала",
                mirroredGroupId = groupId,
                mirroredGroupPostId = "mirror_post_1",
                mirroredGroupReplyId = "mirror_reply_1",
                timestamp = 2000L
            )
        )
        commentDao.insertComment(
            ChannelPostCommentEntity(
                id = "comment_2",
                channelId = channelId,
                postId = postId,
                senderId = "user_2",
                senderName = "Алекс",
                isChannelIdentity = false,
                text = "Отличная новость!",
                replyToCommentId = "comment_1",
                replyToAuthorName = "KuoteX Officiall",
                replyToSnippet = "Комментарий от канала",
                mirroredGroupId = groupId,
                mirroredGroupPostId = "mirror_post_1",
                mirroredGroupReplyId = "mirror_reply_2",
                timestamp = 3000L
            )
        )
        messageDao.updateCommentCount(postId, 2)
        messageDao.updateMirroredGroupMessageId(postId, "mirror_post_1")

        val updatedPost = messageDao.observeMessageById(postId).first()
        assertNotNull(updatedPost)
        assertEquals(2, updatedPost!!.commentCount)
        assertEquals("mirror_post_1", updatedPost.mirroredGroupMessageId)

        val comments = commentDao.observeCommentsForPost(channelId, postId).first()
        assertEquals(2, comments.size)
        assertEquals("comment_1", comments[0].id)
        assertTrue(comments[0].isChannelIdentity)
        assertEquals("comment_2", comments[1].id)
        assertEquals("comment_1", comments[1].replyToCommentId)

        val counts = commentDao.observeCommentCountsForChannel(channelId).first()
        assertEquals(1, counts.size)
        assertEquals(postId, counts[0].postId)
        assertEquals(2, counts[0].commentCount)
    }

    @Test
    fun `mirrored channel post in group is authored as channel and auto-pinned with chat pinnedMessageId`() = runBlocking {
        val channelId = "ch_auto_pin"
        val groupId = "grp_auto_pin"
        val mirroredId = "mirrored_post_auto_pin"

        chatDao.insertChat(
            Chat(
                id = channelId,
                title = "KuoteX Officiall",
                isChannel = true,
                isGroup = false,
                lastMessage = ""
            )
        )
        chatDao.insertChat(
            Chat(
                id = groupId,
                title = "KuoteX chat✨",
                isChannel = false,
                isGroup = true,
                lastMessage = "",
                linkedChannelId = channelId
            )
        )

        val mirroredPost = Message(
            id = mirroredId,
            chatId = groupId,
            senderId = "creator_1",
            text = "Новое обновление",
            timestamp = 5000L,
            isDelivered = true,
            isPinned = true,
            isForwarded = false,
            forwardOriginalSenderId = null,
            forwardOriginalSenderName = null,
            senderIdentityId = channelId,
            senderIdentityType = "CHANNEL",
            senderDisplayName = "KuoteX Officiall",
            senderCustomTag = "канал"
        )
        messageDao.insertMessage(mirroredPost)
        messageDao.updatePinStatus(mirroredId, true)
        chatDao.updatePinnedMessage(groupId, mirroredId)

        val storedGroup = chatDao.getChatById(groupId)
        val storedMsg = messageDao.observeMessageById(mirroredId).first()
        assertNotNull(storedGroup)
        assertNotNull(storedMsg)
        assertEquals(mirroredId, storedGroup!!.pinnedMessageId)
        assertTrue(storedMsg!!.isPinned)
        assertFalse(storedMsg.isForwarded)
        assertEquals("CHANNEL", storedMsg.senderIdentityType)
        assertEquals("канал", storedMsg.senderCustomTag)
        assertEquals(channelId, storedMsg.senderIdentityId)

        messageDao.updatePinStatus(mirroredId, false)
        chatDao.updatePinnedMessage(groupId, null)
        assertFalse(messageDao.observeMessageById(mirroredId).first()!!.isPinned)
        assertNull(chatDao.getChatById(groupId)!!.pinnedMessageId)
    }
}
