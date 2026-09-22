package com.example.data

import androidx.room.TypeConverter
import com.example.ui.botapi.BotCommand
import com.example.ui.botapi.CodeSnapshot
import com.example.ui.botapi.Collaborator
import com.example.ui.botapi.BotStats
import com.example.ui.botapi.LogEntry
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

class BotConverters {
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()

    @TypeConverter
    fun fromBotCommandList(value: List<BotCommand>?): String {
        if (value == null) return "[]"
        val type = Types.newParameterizedType(List::class.java, BotCommand::class.java)
        return moshi.adapter<List<BotCommand>>(type).toJson(value)
    }

    @TypeConverter
    fun toBotCommandList(value: String): List<BotCommand> {
        if (value.isBlank()) return emptyList()
        val type = Types.newParameterizedType(List::class.java, BotCommand::class.java)
        return moshi.adapter<List<BotCommand>>(type).fromJson(value) ?: emptyList()
    }

    @TypeConverter
    fun fromCodeSnapshotList(value: MutableList<CodeSnapshot>?): String {
        if (value == null) return "[]"
        val type = Types.newParameterizedType(MutableList::class.java, CodeSnapshot::class.java)
        return moshi.adapter<MutableList<CodeSnapshot>>(type).toJson(value)
    }

    @TypeConverter
    fun toCodeSnapshotList(value: String): MutableList<CodeSnapshot> {
        if (value.isBlank()) return mutableListOf()
        val type = Types.newParameterizedType(MutableList::class.java, CodeSnapshot::class.java)
        return moshi.adapter<MutableList<CodeSnapshot>>(type).fromJson(value) ?: mutableListOf()
    }

    @TypeConverter
    fun fromCollaboratorList(value: MutableList<Collaborator>?): String {
        if (value == null) return "[]"
        val type = Types.newParameterizedType(MutableList::class.java, Collaborator::class.java)
        return moshi.adapter<MutableList<Collaborator>>(type).toJson(value)
    }

    @TypeConverter
    fun toCollaboratorList(value: String): MutableList<Collaborator> {
        if (value.isBlank()) return mutableListOf()
        val type = Types.newParameterizedType(MutableList::class.java, Collaborator::class.java)
        return moshi.adapter<MutableList<Collaborator>>(type).fromJson(value) ?: mutableListOf()
    }

    @TypeConverter
    fun fromBotStats(value: BotStats?): String {
        if (value == null) return moshi.adapter(BotStats::class.java).toJson(BotStats())
        return moshi.adapter(BotStats::class.java).toJson(value)
    }

    @TypeConverter
    fun toBotStats(value: String): BotStats {
        if (value.isBlank()) return BotStats()
        return moshi.adapter(BotStats::class.java).fromJson(value) ?: BotStats()
    }

    @TypeConverter
    fun fromLogEntryList(value: MutableList<LogEntry>?): String {
        if (value == null) return "[]"
        val type = Types.newParameterizedType(MutableList::class.java, LogEntry::class.java)
        return moshi.adapter<MutableList<LogEntry>>(type).toJson(value)
    }

    @TypeConverter
    fun toLogEntryList(value: String): MutableList<LogEntry> {
        if (value.isBlank()) return mutableListOf()
        val type = Types.newParameterizedType(MutableList::class.java, LogEntry::class.java)
        return moshi.adapter<MutableList<LogEntry>>(type).fromJson(value) ?: mutableListOf()
    }

    @TypeConverter
    fun fromStringMap(value: MutableMap<String, String>?): String {
        if (value == null) return "{}"
        val type = Types.newParameterizedType(MutableMap::class.java, String::class.java, String::class.java)
        return moshi.adapter<MutableMap<String, String>>(type).toJson(value)
    }

    @TypeConverter
    fun toStringMap(value: String): MutableMap<String, String> {
        if (value.isBlank()) return mutableMapOf()
        val type = Types.newParameterizedType(MutableMap::class.java, String::class.java, String::class.java)
        return moshi.adapter<MutableMap<String, String>>(type).fromJson(value) ?: mutableMapOf()
    }
}
