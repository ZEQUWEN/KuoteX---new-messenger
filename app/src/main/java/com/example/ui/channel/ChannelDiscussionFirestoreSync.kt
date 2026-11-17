package com.example.ui.channel

import android.util.Log
import com.example.ui.Chat
import com.example.ui.Message
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

data class ChannelDiscussionLinkSnapshot(
    val channelId: String,
    val linkedDiscussionGroupId: String?,
    val linkedDiscussionGroupTitle: String,
    val onlyMembersCanSend: Boolean = false,
    val joinRequestsRequired: Boolean = false
)

/**
 * Resilient Firestore synchronization layer for:
 * 1. Persistent bidirectional Channel <-> Discussion Group linking (`channels` & `chats`).
 * 2. Per-post comments (`channels/{channelId}/posts/{postId}/comments`) and comment counts.
 * 3. Mirrored forwarded posts & comment replies in the linked discussion group (`chats/{groupId}/messages`).
 */
object ChannelDiscussionFirestoreSync {

    private const val TAG = "ChannelDiscussionSync"
    private const val COLLECTION_CHANNELS = "channels"
    private const val COLLECTION_CHATS = "chats"
    private const val SUBCOLLECTION_POSTS = "posts"
    private const val SUBCOLLECTION_COMMENTS = "comments"
    private const val SUBCOLLECTION_MESSAGES = "messages"

    private fun getFirestoreOrNull(): FirebaseFirestore? {
        return try {
            FirebaseFirestore.getInstance()
        } catch (t: Throwable) {
            Log.w(TAG, "Firestore unavailable: ${t.message}")
            null
        }
    }

    suspend fun syncChannelDiscussionLink(
        channelChat: Chat,
        groupChat: Chat?,
        discussionTitle: String,
        onlyMembersCanSend: Boolean = false,
        joinRequestsRequired: Boolean = false
    ) = withContext(Dispatchers.IO) {
        val db = getFirestoreOrNull() ?: return@withContext
        try {
            val channelPayload = hashMapOf<String, Any?>(
                "id" to channelChat.id,
                "title" to channelChat.title,
                "isChannel" to true,
                "linked_discussion_group_id" to groupChat?.id,
                "linked_discussion_group_title" to discussionTitle,
                "linkedDiscussionGroupId" to groupChat?.id,
                "linkedDiscussionGroupTitle" to discussionTitle,
                "discussion_only_members_can_send" to onlyMembersCanSend,
                "discussion_join_requests_required" to joinRequestsRequired,
                "updated_at" to FieldValue.serverTimestamp()
            )
            db.collection(COLLECTION_CHANNELS)
                .document(channelChat.id)
                .set(channelPayload, SetOptions.merge())
                .await()

            db.collection(COLLECTION_CHATS)
                .document(channelChat.id)
                .set(channelPayload, SetOptions.merge())
                .await()

            if (groupChat != null) {
                val groupPayload = hashMapOf<String, Any?>(
                    "id" to groupChat.id,
                    "title" to groupChat.title,
                    "isGroup" to true,
                    "isChannel" to false,
                    "linkedChannelId" to channelChat.id,
                    "linkedChannelTitle" to channelChat.title,
                    "updated_at" to FieldValue.serverTimestamp()
                )
                db.collection(COLLECTION_CHATS)
                    .document(groupChat.id)
                    .set(groupPayload, SetOptions.merge())
                    .await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to sync channel discussion link to Firestore: ${t.message}")
        }
    }

    suspend fun unlinkChannelDiscussionLink(
        channelId: String,
        previousGroupId: String?
    ) = withContext(Dispatchers.IO) {
        val db = getFirestoreOrNull() ?: return@withContext
        try {
            val clearChannelPayload = hashMapOf<String, Any?>(
                "linked_discussion_group_id" to null,
                "linked_discussion_group_title" to "",
                "linkedDiscussionGroupId" to null,
                "linkedDiscussionGroupTitle" to "",
                "discussion_only_members_can_send" to false,
                "discussion_join_requests_required" to false,
                "updated_at" to FieldValue.serverTimestamp()
            )
            db.collection(COLLECTION_CHANNELS)
                .document(channelId)
                .set(clearChannelPayload, SetOptions.merge())
                .await()
            db.collection(COLLECTION_CHATS)
                .document(channelId)
                .set(clearChannelPayload, SetOptions.merge())
                .await()

            if (!previousGroupId.isNullOrBlank()) {
                val clearGroupPayload = hashMapOf<String, Any?>(
                    "linkedChannelId" to null,
                    "linkedChannelTitle" to "",
                    "updated_at" to FieldValue.serverTimestamp()
                )
                db.collection(COLLECTION_CHATS)
                    .document(previousGroupId)
                    .set(clearGroupPayload, SetOptions.merge())
                    .await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to unlink channel discussion in Firestore: ${t.message}")
        }
    }

    fun observeChannelDiscussionLink(channelId: String): Flow<ChannelDiscussionLinkSnapshot?> = callbackFlow {
        val db = getFirestoreOrNull()
        if (db == null) {
            trySend(null)
            close()
            return@callbackFlow
        }
        val listener = db.collection(COLLECTION_CHANNELS)
            .document(channelId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(TAG, "Error observing channel discussion link: ${error.message}")
                    return@addSnapshotListener
                }
                if (snapshot == null || !snapshot.exists()) {
                    trySend(null)
                    return@addSnapshotListener
                }
                val groupId = snapshot.getString("linked_discussion_group_id")
                    ?: snapshot.getString("linkedDiscussionGroupId")
                val groupTitle = snapshot.getString("linked_discussion_group_title")
                    ?: snapshot.getString("linkedDiscussionGroupTitle")
                    ?: ""
                val onlyMembers = snapshot.getBoolean("discussion_only_members_can_send") ?: false
                val joinReq = snapshot.getBoolean("discussion_join_requests_required") ?: false
                trySend(
                    ChannelDiscussionLinkSnapshot(
                        channelId = channelId,
                        linkedDiscussionGroupId = groupId?.takeIf { it.isNotBlank() },
                        linkedDiscussionGroupTitle = groupTitle,
                        onlyMembersCanSend = onlyMembers,
                        joinRequestsRequired = joinReq
                    )
                )
            }
        awaitClose { listener.remove() }
    }

    suspend fun syncPostComment(
        comment: ChannelPostCommentEntity,
        updatedCommentCount: Int,
        mirroredGroupPostId: String?
    ) = withContext(Dispatchers.IO) {
        val db = getFirestoreOrNull() ?: return@withContext
        try {
            val postRef = db.collection(COLLECTION_CHANNELS)
                .document(comment.channelId)
                .collection(SUBCOLLECTION_POSTS)
                .document(comment.postId)

            val postMeta = hashMapOf<String, Any?>(
                "postId" to comment.postId,
                "channelId" to comment.channelId,
                "commentCount" to updatedCommentCount,
                "mirroredGroupId" to comment.mirroredGroupId,
                "mirroredGroupMessageId" to mirroredGroupPostId,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            postRef.set(postMeta, SetOptions.merge()).await()

            val commentDoc = hashMapOf<String, Any?>(
                "id" to comment.id,
                "channelId" to comment.channelId,
                "postId" to comment.postId,
                "senderId" to comment.senderId,
                "senderName" to comment.senderName,
                "senderAvatarUrl" to comment.senderAvatarUrl,
                "isChannelIdentity" to comment.isChannelIdentity,
                "text" to comment.text,
                "mediaPath" to comment.mediaPath,
                "mediaType" to comment.mediaType,
                "documentData" to comment.documentData,
                "replyToCommentId" to comment.replyToCommentId,
                "replyToAuthorName" to comment.replyToAuthorName,
                "replyToSnippet" to comment.replyToSnippet,
                "mirroredGroupId" to comment.mirroredGroupId,
                "mirroredGroupPostId" to comment.mirroredGroupPostId,
                "mirroredGroupReplyId" to comment.mirroredGroupReplyId,
                "timestamp" to comment.timestamp
            )
            postRef.collection(SUBCOLLECTION_COMMENTS)
                .document(comment.id)
                .set(commentDoc, SetOptions.merge())
                .await()
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to sync post comment to Firestore: ${t.message}")
        }
    }

    fun observePostComments(channelId: String, postId: String): Flow<List<ChannelPostCommentEntity>> = callbackFlow {
        val db = getFirestoreOrNull()
        if (db == null) {
            close()
            return@callbackFlow
        }
        val listener = db.collection(COLLECTION_CHANNELS)
            .document(channelId)
            .collection(SUBCOLLECTION_POSTS)
            .document(postId)
            .collection(SUBCOLLECTION_COMMENTS)
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(TAG, "Error observing post comments: ${error.message}")
                    return@addSnapshotListener
                }
                val list = snapshot?.documents?.mapNotNull { doc ->
                    val id = doc.getString("id") ?: doc.id
                    val text = doc.getString("text") ?: ""
                    ChannelPostCommentEntity(
                        id = id,
                        channelId = doc.getString("channelId") ?: channelId,
                        postId = doc.getString("postId") ?: postId,
                        senderId = doc.getString("senderId") ?: "",
                        senderName = doc.getString("senderName") ?: "Пользователь",
                        senderAvatarUrl = doc.getString("senderAvatarUrl"),
                        isChannelIdentity = doc.getBoolean("isChannelIdentity") ?: false,
                        text = text,
                        mediaPath = doc.getString("mediaPath"),
                        mediaType = doc.getString("mediaType"),
                        documentData = doc.getString("documentData"),
                        replyToCommentId = doc.getString("replyToCommentId"),
                        replyToAuthorName = doc.getString("replyToAuthorName"),
                        replyToSnippet = doc.getString("replyToSnippet"),
                        mirroredGroupId = doc.getString("mirroredGroupId"),
                        mirroredGroupPostId = doc.getString("mirroredGroupPostId"),
                        mirroredGroupReplyId = doc.getString("mirroredGroupReplyId"),
                        timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis()
                    )
                } ?: emptyList()
                trySend(list)
            }
        awaitClose { listener.remove() }
    }

    suspend fun syncMirroredGroupMessage(
        groupId: String,
        message: Message,
        plainText: String
    ) = withContext(Dispatchers.IO) {
        val db = getFirestoreOrNull() ?: return@withContext
        try {
            val doc = hashMapOf<String, Any?>(
                "id" to message.id,
                "chatId" to groupId,
                "senderId" to message.senderId,
                "text" to plainText,
                "mediaPath" to message.mediaPath,
                "mediaType" to message.mediaType,
                "documentData" to message.documentData,
                "replyToMessageId" to message.replyToMessageId,
                "replyToMessageText" to message.replyToMessageText,
                "isPinned" to message.isPinned,
                "isForwarded" to message.isForwarded,
                "forwardOriginalSenderId" to message.forwardOriginalSenderId,
                "forwardOriginalSenderName" to message.forwardOriginalSenderName,
                "senderIdentityId" to message.senderIdentityId,
                "senderIdentityType" to message.senderIdentityType,
                "senderDisplayName" to message.senderDisplayName,
                "senderAvatarUrl" to message.senderAvatarUrl,
                "senderCustomTag" to message.senderCustomTag,
                "senderEmojiStatus" to message.senderEmojiStatus,
                "timestamp" to message.timestamp
            )
            db.collection(COLLECTION_CHATS)
                .document(groupId)
                .collection(SUBCOLLECTION_MESSAGES)
                .document(message.id)
                .set(doc, SetOptions.merge())
                .await()

            if (message.isPinned) {
                db.collection(COLLECTION_CHATS)
                    .document(groupId)
                    .set(
                        hashMapOf<String, Any?>(
                            "pinnedMessageId" to message.id,
                            "updated_at" to FieldValue.serverTimestamp()
                        ),
                        SetOptions.merge()
                    )
                    .await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to sync mirrored group message to Firestore: ${t.message}")
        }
    }
}
