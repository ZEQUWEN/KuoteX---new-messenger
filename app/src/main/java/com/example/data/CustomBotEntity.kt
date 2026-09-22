package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.ui.botapi.BotCommand
import com.example.ui.botapi.CodeSnapshot
import com.example.ui.botapi.Collaborator
import com.example.ui.botapi.BotStats
import com.example.ui.botapi.LogEntry
import com.example.ui.botapi.CustomBot

@Entity(tableName = "custom_bots")
data class CustomBotEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String,
    val category: String,
    val longDescription: String,
    val commands: List<BotCommand>,
    val oauthToken: String?,
    val webhookUrl: String?,
    val rateLimit: Int,
    val code: String,
    val snapshots: MutableList<CodeSnapshot>,
    val collaborators: MutableList<Collaborator>,
    val stats: BotStats,
    val logs: MutableList<LogEntry>,
    val about: String,
    val botPicUri: String?,
    val descriptionPictureUri: String?,
    val keyboardLayoutConfig: String?,
    val customCommands: List<BotCommand>,
    val paymentProviderToken: String?,
    val paymentProviders: MutableMap<String, String>,
    val paymentsEnabled: Boolean,
    val domain: String?,
    val miniAppUrl: String?,
    val miniAppTitle: String?,
    val miniAppShortName: String?
) {
    fun toCustomBot(): CustomBot {
        return CustomBot(
            id = id,
            name = name,
            description = description,
            category = category,
            longDescription = longDescription,
            commands = commands,
            oauthToken = oauthToken,
            webhookUrl = webhookUrl,
            rateLimit = rateLimit,
            code = code,
            snapshots = snapshots,
            collaborators = collaborators,
            stats = stats,
            about = about,
            botPicUri = botPicUri,
            descriptionPictureUri = descriptionPictureUri,
            keyboardLayoutConfig = keyboardLayoutConfig,
            customCommands = customCommands,
            paymentProviderToken = paymentProviderToken,
            paymentsEnabled = paymentsEnabled,
            domain = domain,
            miniAppUrl = miniAppUrl,
            miniAppTitle = miniAppTitle,
            miniAppShortName = miniAppShortName
        ).apply {
            this.logs.clear()
            this.logs.addAll(this@CustomBotEntity.logs)
            this.paymentProviders.clear()
            this.paymentProviders.putAll(this@CustomBotEntity.paymentProviders)
        }
    }

    companion object {
        fun fromCustomBot(bot: CustomBot): CustomBotEntity {
            return CustomBotEntity(
                id = bot.id,
                name = bot.name,
                description = bot.description,
                category = bot.category,
                longDescription = bot.longDescription,
                commands = bot.commands,
                oauthToken = bot.oauthToken,
                webhookUrl = bot.webhookUrl,
                rateLimit = bot.rateLimit,
                code = bot.code,
                snapshots = bot.snapshots,
                collaborators = bot.collaborators,
                stats = bot.stats,
                logs = bot.logs,
                about = bot.about,
                botPicUri = bot.botPicUri,
                descriptionPictureUri = bot.descriptionPictureUri,
                keyboardLayoutConfig = bot.keyboardLayoutConfig,
                customCommands = bot.customCommands,
                paymentProviderToken = bot.paymentProviderToken,
                paymentProviders = bot.paymentProviders,
                paymentsEnabled = bot.paymentsEnabled,
                domain = bot.domain,
                miniAppUrl = bot.miniAppUrl,
                miniAppTitle = bot.miniAppTitle,
                miniAppShortName = bot.miniAppShortName
            )
        }
    }
}
