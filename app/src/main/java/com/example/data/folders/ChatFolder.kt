package com.example.data.folders

import com.example.ui.Chat
import org.json.JSONArray
import org.json.JSONObject

data class ChatFolder(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String,
    val emoji: String? = null,
    val colorHex: String = "#2196F3",
    val includedChatIds: List<String> = emptyList(),
    val includePersonal: Boolean = false,
    val includeGroups: Boolean = false,
    val includeChannels: Boolean = false,
    val includeBots: Boolean = false,
    val includeUnreadOnly: Boolean = false,
    val excludedChatIds: List<String> = emptyList(),
    val excludeMuted: Boolean = false,
    val excludeRead: Boolean = false,
    val excludeArchived: Boolean = true,
    val order: Int = 0
) {
    fun toJson(): JSONObject {
        val json = JSONObject()
        json.put("id", id)
        json.put("name", name)
        json.put("emoji", emoji ?: "")
        json.put("colorHex", colorHex)

        val incArray = JSONArray()
        includedChatIds.forEach { incArray.put(it) }
        json.put("includedChatIds", incArray)

        json.put("includePersonal", includePersonal)
        json.put("includeGroups", includeGroups)
        json.put("includeChannels", includeChannels)
        json.put("includeBots", includeBots)
        json.put("includeUnreadOnly", includeUnreadOnly)

        val excArray = JSONArray()
        excludedChatIds.forEach { excArray.put(it) }
        json.put("excludedChatIds", excArray)

        json.put("excludeMuted", excludeMuted)
        json.put("excludeRead", excludeRead)
        json.put("excludeArchived", excludeArchived)
        json.put("order", order)
        return json
    }

    companion object {
        fun fromJson(json: JSONObject): ChatFolder {
            val incList = mutableListOf<String>()
            val incArray = json.optJSONArray("includedChatIds")
            if (incArray != null) {
                for (i in 0 until incArray.length()) {
                    incList.add(incArray.getString(i))
                }
            }

            val excList = mutableListOf<String>()
            val excArray = json.optJSONArray("excludedChatIds")
            if (excArray != null) {
                for (i in 0 until excArray.length()) {
                    excList.add(excArray.getString(i))
                }
            }

            val emojiStr = json.optString("emoji", "")
            return ChatFolder(
                id = json.optString("id", java.util.UUID.randomUUID().toString()),
                name = json.optString("name", "Папка"),
                emoji = if (emojiStr.isNotEmpty()) emojiStr else null,
                colorHex = json.optString("colorHex", "#2196F3"),
                includedChatIds = incList,
                includePersonal = json.optBoolean("includePersonal", false),
                includeGroups = json.optBoolean("includeGroups", false),
                includeChannels = json.optBoolean("includeChannels", false),
                includeBots = json.optBoolean("includeBots", false),
                includeUnreadOnly = json.optBoolean("includeUnreadOnly", false),
                excludedChatIds = excList,
                excludeMuted = json.optBoolean("excludeMuted", false),
                excludeRead = json.optBoolean("excludeRead", false),
                excludeArchived = json.optBoolean("excludeArchived", true),
                order = json.optInt("order", 0)
            )
        }

        fun listToJson(list: List<ChatFolder>): String {
            val array = JSONArray()
            list.forEach { array.put(it.toJson()) }
            return array.toString()
        }

        fun listFromJson(jsonStr: String?): List<ChatFolder> {
            if (jsonStr.isNullOrBlank()) return emptyList()
            return try {
                val array = JSONArray(jsonStr)
                val list = mutableListOf<ChatFolder>()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    list.add(fromJson(obj))
                }
                list.sortedBy { it.order }
            } catch (e: Exception) {
                emptyList()
            }
        }

        fun recommendedNew(): ChatFolder {
            return ChatFolder(
                id = "recommended_new",
                name = "Новые",
                emoji = "🔔",
                colorHex = "#2196F3",
                includeUnreadOnly = true,
                excludeArchived = true,
                order = 100
            )
        }

        fun recommendedPersonal(): ChatFolder {
            return ChatFolder(
                id = "recommended_personal",
                name = "Личные",
                emoji = "👤",
                colorHex = "#4CAF50",
                includePersonal = true,
                excludeArchived = true,
                order = 101
            )
        }
    }
}

fun ChatFolder.matches(chat: Chat): Boolean {
    // 1. Check exclusions
    if (chat.id in excludedChatIds) return false
    if (excludeArchived && chat.isArchived) return false
    if (excludeMuted && chat.isMuted) return false
    if (excludeRead && chat.unreadCount == 0) return false

    // 2. Check explicit included chat IDs
    if (chat.id in includedChatIds) return true

    // 3. Check categories
    if (includePersonal && !chat.isGroup && !chat.isChannel && !chat.isBot) return true
    if (includeGroups && chat.isGroup) return true
    if (includeChannels && chat.isChannel) return true
    if (includeBots && chat.isBot) return true
    if (includeUnreadOnly && chat.unreadCount > 0) return true

    return false
}
