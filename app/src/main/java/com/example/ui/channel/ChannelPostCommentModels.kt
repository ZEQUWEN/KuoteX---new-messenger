package com.example.ui.channel

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Entity(
    tableName = "channel_post_comments",
    indices = [Index("channelId"), Index("postId")]
)
data class ChannelPostCommentEntity(
    @PrimaryKey val id: String,
    val channelId: String,
    val postId: String,
    val senderId: String,
    val senderName: String,
    val senderAvatarUrl: String? = null,
    val isChannelIdentity: Boolean = false,
    val text: String,
    val mediaPath: String? = null,
    val mediaType: String? = null,
    val documentData: String? = null,
    val replyToCommentId: String? = null,
    val replyToAuthorName: String? = null,
    val replyToSnippet: String? = null,
    val mirroredGroupId: String? = null,
    val mirroredGroupPostId: String? = null,
    val mirroredGroupReplyId: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

data class PostCommentCountRow(
    val postId: String,
    val commentCount: Int
)

@Dao
interface ChannelPostCommentDao {
    @Query("SELECT * FROM channel_post_comments WHERE channelId = :channelId AND postId = :postId ORDER BY timestamp ASC")
    fun observeCommentsForPost(channelId: String, postId: String): Flow<List<ChannelPostCommentEntity>>

    @Query("SELECT postId, COUNT(*) AS commentCount FROM channel_post_comments WHERE channelId = :channelId GROUP BY postId")
    fun observeCommentCountsForChannel(channelId: String): Flow<List<PostCommentCountRow>>

    @Query("SELECT * FROM channel_post_comments WHERE channelId = :channelId AND postId = :postId ORDER BY timestamp ASC")
    suspend fun getCommentsForPostSync(channelId: String, postId: String): List<ChannelPostCommentEntity>

    @Query("SELECT COUNT(*) FROM channel_post_comments WHERE channelId = :channelId AND postId = :postId")
    suspend fun getCommentCountSync(channelId: String, postId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertComment(comment: ChannelPostCommentEntity)

    @Query("DELETE FROM channel_post_comments WHERE id = :commentId")
    suspend fun deleteComment(commentId: String)
}

/**
 * Formats the post comment footer label following Russian pluralization rules.
 * - 0 -> "Прокомментировать"
 * - 1, 21 -> "1 комментарий"
 * - 2..4 -> "2 комментария"
 * - 5..20 -> "5 комментариев"
 */
fun formatCommentsFooterLabel(count: Int): String {
    if (count <= 0) return "Прокомментировать"
    val mod100 = count % 100
    val mod10 = count % 10
    val noun = when {
        mod100 in 11..14 -> "комментариев"
        mod10 == 1 -> "комментарий"
        mod10 in 2..4 -> "комментария"
        else -> "комментариев"
    }
    return "$count $noun"
}

private val russianLocale = Locale("ru", "RU")
private val commentDatePillFormatter = SimpleDateFormat("d MMMM", russianLocale)

fun formatPostDatePill(timestamp: Long): String {
    return try {
        commentDatePillFormatter.format(Date(timestamp))
    } catch (_: Exception) {
        "Сегодня"
    }
}
