package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BotDao {
    @Query("SELECT * FROM custom_bots")
    fun getAllCustomBotsFlow(): Flow<List<CustomBotEntity>>

    @Query("SELECT * FROM custom_bots")
    fun getAllCustomBotsSync(): List<CustomBotEntity>

    @Query("SELECT * FROM custom_bots WHERE id = :id LIMIT 1")
    suspend fun getCustomBotById(id: String): CustomBotEntity?

    @Query("SELECT * FROM custom_bots WHERE name LIKE '%' || :query || '%' OR id LIKE '%' || :query || '%'")
    fun searchCustomBots(query: String): Flow<List<CustomBotEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCustomBot(bot: CustomBotEntity)

    @Query("DELETE FROM custom_bots WHERE id = :id")
    suspend fun deleteCustomBot(id: String)
}
