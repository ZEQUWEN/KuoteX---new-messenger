package com.example.ui.channel

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * ChannelCustomizationManager
 * Central singleton managing channel and group appearance, Telegram boost voting,
 * animated emoji statuses, wallpaper preferences, polls, and admin logs.
 */
object ChannelCustomizationManager {

    private val customizations = MutableStateFlow<Map<String, ChannelCustomization>>(
        mapOf(
            "c1" to ChannelCustomization(
                chatId = "c1",
                emojiStatus = "⚡",
                profileColorId = 0,
                profileEmojiPattern = "⚡",
                chatWallpaperId = "cyber_grid",
                boostLevel = 4,
                boostCount = 28,
                boostsRequiredForNextLevel = 35,
                hasVotedBoost = true,
                subscriberCount = 12450,
                description = "Главный канал разработки KuoteX. Ежедневные инсайды и обновления."
            ),
            "c5" to ChannelCustomization(
                chatId = "c5",
                emojiStatus = "🔥",
                profileColorId = 1,
                profileEmojiPattern = "🔥",
                chatWallpaperId = "deep_space",
                boostLevel = 3,
                boostCount = 18,
                boostsRequiredForNextLevel = 25,
                hasVotedBoost = false,
                subscriberCount = 8900,
                description = "Android News & Tech Updates. Все самое свежее из мира мобильной разработки."
            ),
            "g1" to ChannelCustomization(
                chatId = "g1",
                emojiStatus = "👑",
                profileColorId = 2,
                profileEmojiPattern = "👑",
                chatWallpaperId = "telegram_doodles",
                boostLevel = 5,
                boostCount = 42,
                boostsRequiredForNextLevel = 50,
                hasVotedBoost = true,
                subscriberCount = 350,
                description = "Официальная группа разработчиков OpenAI & Kotlin Devs."
            )
        )
    )

    private val pollsState = MutableStateFlow<Map<String, List<PollData>>>(
        mapOf(
            "c1" to listOf(
                PollData(
                    id = "poll_neon_1",
                    chatId = "c1",
                    creatorId = "admin",
                    creatorName = "KuoteX Admin",
                    question = "Какое новое оформление каналов в KuoteX вам нравится больше всего? ✨",
                    options = listOf(
                        PollOption(0, "🔥 Анимированные эмодзи в шапке", 142, 0.44f),
                        PollOption(1, "🎨 Градиенты цвета профиля и цитат", 98, 0.31f),
                        PollOption(2, "🖼️ Кастомные обои для подписчиков", 52, 0.16f),
                        PollOption(3, "⭐ Система бустов и уровней", 28, 0.09f)
                    ),
                    isAnonymous = true,
                    isMultipleChoice = false,
                    isQuiz = false,
                    totalVoters = 320,
                    userSelectedOptionIds = setOf(0)
                ),
                PollData(
                    id = "poll_neon_quiz",
                    chatId = "c1",
                    creatorId = "admin",
                    creatorName = "KuoteX Quiz Master",
                    question = "Викторина: Какой максимальный уровень буста доступен для каналов в Telegram?",
                    options = listOf(
                        PollOption(0, "Уровень 5", 12, 0.10f),
                        PollOption(1, "Уровень 10", 85, 0.71f),
                        PollOption(2, "Уровень 20", 15, 0.12f),
                        PollOption(3, "Неограниченно", 8, 0.07f)
                    ),
                    isAnonymous = false,
                    isMultipleChoice = false,
                    isQuiz = true,
                    correctOptionIndex = 1,
                    explanation = "В Telegram каналы открывают все функции кастомизации на 10 уровне бустов!",
                    totalVoters = 120,
                    userSelectedOptionIds = setOf(1)
                )
            )
        )
    )

    private val boostersState = MutableStateFlow<Map<String, List<BoosterUser>>>(
        mapOf(
            "c1" to listOf(
                BoosterUser("u1", "Pavel Durov", "https://picsum.photos/seed/durov/100", 10, 4, System.currentTimeMillis() - 3600000),
                BoosterUser("u2", "Alex KuoteX", "https://picsum.photos/seed/alex/100", 5, 3, System.currentTimeMillis() - 7200000),
                BoosterUser("u3", "Elena Star", "https://picsum.photos/seed/elena/100", 3, 2, System.currentTimeMillis() - 14400000),
                BoosterUser("u4", "KuoteX VIP", "https://picsum.photos/seed/vip/100", 2, 1, System.currentTimeMillis() - 28800000)
            )
        )
    )

    private val recentActionsState = MutableStateFlow<Map<String, List<AdminActionLog>>>(
        mapOf(
            "c1" to listOf(
                AdminActionLog(
                    adminName = "Pavel (Owner)",
                    adminAvatar = "https://picsum.photos/seed/durov/100",
                    actionTitle = "Изменен эмодзи-статус канала",
                    details = "Установлен анимированный эмодзи: ⚡ (Молния)",
                    timestamp = System.currentTimeMillis() - 1200000
                ),
                AdminActionLog(
                    adminName = "Alex KuoteX",
                    adminAvatar = "https://picsum.photos/seed/alex/100",
                    actionTitle = "Обновлен цвет профиля",
                    details = "Выбран градиент 'Синий Неон' и стиль цитат",
                    timestamp = System.currentTimeMillis() - 3600000
                ),
                AdminActionLog(
                    adminName = "Elena Star",
                    adminAvatar = "https://picsum.photos/seed/elena/100",
                    actionTitle = "Закреплено сообщение",
                    details = "Опрос: 'Какое новое оформление каналов вам нравится?'",
                    timestamp = System.currentTimeMillis() - 86400000
                )
            )
        )
    )

    fun getCustomizationFlow(chatId: String): StateFlow<Map<String, ChannelCustomization>> = customizations.asStateFlow()

    fun getCustomization(chatId: String): ChannelCustomization {
        return customizations.value[chatId] ?: ChannelCustomization(chatId = chatId).also { defaultItem ->
            customizations.update { it + (chatId to defaultItem) }
        }
    }

    fun updateAvatar(chatId: String, avatarUrl: String) {
        customizations.update { map ->
            val current = map[chatId] ?: ChannelCustomization(chatId = chatId)
            val updated = current.copy(avatarUrl = avatarUrl)
            map + (chatId to updated)
        }
        logAdminAction(
            chatId = chatId,
            title = "Обновлен аватар",
            details = "Загружена новая фотография профиля"
        )
    }

    fun updateEmojiStatus(chatId: String, emoji: String?, isAnimated: Boolean = true) {
        customizations.update { map ->
            val current = map[chatId] ?: ChannelCustomization(chatId = chatId)
            val updated = current.copy(emojiStatus = emoji, emojiStatusAnimated = isAnimated)
            map + (chatId to updated)
        }
        logAdminAction(
            chatId = chatId,
            title = if (emoji != null) "Изменен эмодзи-статус" else "Удален эмодзи-статус",
            details = if (emoji != null) "Установлен статус: $emoji" else "Эмодзи-статус сброшен"
        )
    }

    fun updateProfileColor(chatId: String, colorId: Int, emojiPattern: String? = null) {
        customizations.update { map ->
            val current = map[chatId] ?: ChannelCustomization(chatId = chatId)
            val updated = current.copy(
                profileColorId = colorId,
                profileEmojiPattern = emojiPattern ?: current.profileEmojiPattern
            )
            map + (chatId to updated)
        }
        val paletteName = TelegramProfilePalettes.getPalette(colorId).name
        logAdminAction(
            chatId = chatId,
            title = "Изменен цвет профиля",
            details = "Выбран стиль '$paletteName'"
        )
    }

    fun updateWallpaper(
        chatId: String,
        wallpaperId: String,
        customUri: String? = null,
        blur: Float = 0f,
        dim: Float = 0.2f,
        patternOpacity: Float = 0.35f
    ) {
        customizations.update { map ->
            val current = map[chatId] ?: ChannelCustomization(chatId = chatId)
            val updated = current.copy(
                chatWallpaperId = wallpaperId,
                wallpaperCustomUri = customUri,
                wallpaperBlur = blur,
                wallpaperDim = dim,
                wallpaperPatternOpacity = patternOpacity
            )
            map + (chatId to updated)
        }
        val wallpaperName = TelegramWallpapers.getPreset(wallpaperId).name
        logAdminAction(
            chatId = chatId,
            title = "Установлены обои чата",
            details = "Выбраны обои: $wallpaperName (Затемнение: ${(dim * 100).toInt()}%)"
        )
    }

    fun updateAutoDelete(chatId: String, period: String?) {
        customizations.update { map ->
            val current = map[chatId] ?: ChannelCustomization(chatId = chatId)
            val updated = current.copy(autoDeletePeriod = period)
            map + (chatId to updated)
        }
        logAdminAction(
            chatId = chatId,
            title = if (period != null) "Включено автоудаление" else "Отключено автоудаление",
            details = if (period != null) "Период: $period" else "Автоудаление сообщений выключено"
        )
    }

    fun toggleBoost(chatId: String): Boolean {
        var newVotedState = false
        customizations.update { map ->
            val current = map[chatId] ?: ChannelCustomization(chatId = chatId)
            newVotedState = !current.hasVotedBoost
            val newCount = if (newVotedState) current.boostCount + 1 else (current.boostCount - 1).coerceAtLeast(0)
            val newLevel = com.example.data.ecosystem.KuoteXBoostProgression.calculateLevel(newCount)
            val reqNext = com.example.data.ecosystem.KuoteXBoostProgression.nextLevelRequirement(newCount)
            val updated = current.copy(
                hasVotedBoost = newVotedState,
                boostCount = newCount,
                boostLevel = newLevel,
                boostsRequiredForNextLevel = reqNext
            )
            map + (chatId to updated)
        }
        if (newVotedState) {
            boostersState.update { map ->
                val list = map[chatId]?.toMutableList() ?: mutableListOf()
                list.add(
                    0,
                    BoosterUser(
                        userId = "me",
                        userName = "Вы",
                        avatarUrl = "https://picsum.photos/seed/my_avatar/100",
                        boostsCount = 1,
                        levelGranted = 1,
                        timestamp = System.currentTimeMillis()
                    )
                )
                map + (chatId to list)
            }
        }
        return newVotedState
    }

    private fun calculateLevel(boosts: Int): Int {
        return when {
            boosts >= 50 -> 6
            boosts >= 35 -> 5
            boosts >= 25 -> 4
            boosts >= 15 -> 3
            boosts >= 8 -> 2
            boosts >= 3 -> 1
            else -> 0
        }
    }

    fun updateSignatures(chatId: String, enabled: Boolean) {
        customizations.update { map ->
            val current = map[chatId] ?: ChannelCustomization(chatId = chatId)
            map + (chatId to current.copy(isSignaturesEnabled = enabled))
        }
        logAdminAction(
            chatId = chatId,
            title = "Подписи сообщений",
            details = if (enabled) "Подписи авторов включены" else "Подписи авторов отключены"
        )
    }

    fun updateSlowMode(chatId: String, seconds: Int) {
        customizations.update { map ->
            val current = map[chatId] ?: ChannelCustomization(chatId = chatId)
            map + (chatId to current.copy(slowModeSeconds = seconds))
        }
        logAdminAction(
            chatId = chatId,
            title = "Медленный режим",
            details = if (seconds > 0) "Интервал: $seconds сек" else "Медленный режим выключен"
        )
    }

    fun updatePermissions(chatId: String, restricted: Set<String>) {
        customizations.update { map ->
            val current = map[chatId] ?: ChannelCustomization(chatId = chatId)
            map + (chatId to current.copy(restrictedPermissions = restricted))
        }
        logAdminAction(
            chatId = chatId,
            title = "Изменены разрешения участников",
            details = "Ограничено действий: ${restricted.size}"
        )
    }

    fun updateChannelInfo(chatId: String, description: String, inviteLink: String) {
        customizations.update { map ->
            val current = map[chatId] ?: ChannelCustomization(chatId = chatId)
            map + (chatId to current.copy(description = description, inviteLink = inviteLink))
        }
        logAdminAction(
            chatId = chatId,
            title = "Обновлена информация о канале",
            details = "Изменено описание и ссылка"
        )
    }

    fun updateDirectMessages(chatId: String, enabled: Boolean, starPrice: Int) {
        customizations.update { map ->
            val current = map[chatId] ?: ChannelCustomization(chatId = chatId)
            map + (chatId to current.copy(directMessagesEnabled = enabled, directMessageStarPrice = starPrice))
        }
        logAdminAction(
            chatId = chatId,
            title = "Сообщения каналу",
            details = if (enabled) "Платные сообщения активны (⭐ $starPrice)" else "Сообщения каналу выключены"
        )
    }

    fun updateAutoTranslate(chatId: String, enabled: Boolean) {
        customizations.update { map ->
            val current = map[chatId] ?: ChannelCustomization(chatId = chatId)
            map + (chatId to current.copy(autoTranslateEnabled = enabled))
        }
        logAdminAction(
            chatId = chatId,
            title = "Автоперевод сообщений",
            details = if (enabled) "Автоперевод включен" else "Автоперевод выключен"
        )
    }

    fun updateReactionsConfig(
        chatId: String,
        enabled: Boolean,
        available: List<String>,
        maxPerPost: Int,
        paidStars: Boolean
    ) {
        customizations.update { map ->
            val current = map[chatId] ?: ChannelCustomization(chatId = chatId)
            map + (chatId to current.copy(
                reactionsEnabled = enabled,
                availableReactions = available,
                maxReactionsPerPost = maxPerPost,
                paidStarReactionsEnabled = paidStars
            ))
        }
        logAdminAction(
            chatId = chatId,
            title = "Настройка реакций",
            details = "Доступно реакций: ${available.size}, макс на пост: $maxPerPost"
        )
    }

    fun updateAutoGreeting(chatId: String, enabled: Boolean, greetingText: String) {
        customizations.update { map ->
            val current = map[chatId] ?: ChannelCustomization(chatId = chatId)
            map + (chatId to current.copy(
                autoGreetingEnabled = enabled,
                autoGreetingText = greetingText
            ))
        }
        logAdminAction(
            chatId = chatId,
            title = "Приветственное сообщение",
            details = if (enabled) "Автоприветствие включено" else "Автоприветствие отключено"
        )
    }

    fun updateAuthorProfiles(chatId: String, showProfiles: Boolean) {
        customizations.update { map ->
            val current = map[chatId] ?: ChannelCustomization(chatId = chatId)
            map + (chatId to current.copy(showAuthorProfiles = showProfiles))
        }
    }

    fun updateChannelType(chatId: String, isPublic: Boolean, inviteLink: String, restrictSaving: Boolean) {
        customizations.update { map ->
            val current = map[chatId] ?: ChannelCustomization(chatId = chatId)
            map + (chatId to current.copy(
                isPublic = isPublic,
                inviteLink = inviteLink,
                restrictSavingContent = restrictSaving
            ))
        }
        logAdminAction(
            chatId = chatId,
            title = "Тип канала",
            details = if (isPublic) "Публичный ($inviteLink)" else "Частный канал"
        )
    }

    fun updateDiscussionChat(chatId: String, discussionTitle: String) {
        customizations.update { map ->
            val current = map[chatId] ?: ChannelCustomization(chatId = chatId)
            map + (chatId to current.copy(discussionChatTitle = discussionTitle))
        }
    }

    // --- Poll & Voting Operations ---

    fun getPollsFlow(chatId: String): StateFlow<Map<String, List<PollData>>> = pollsState.asStateFlow()

    fun createPoll(chatId: String, poll: PollData) {
        pollsState.update { map ->
            val currentList = map[chatId] ?: emptyList()
            map + (chatId to (listOf(poll) + currentList))
        }
        logAdminAction(
            chatId = chatId,
            title = "Создан опрос",
            details = "${if (poll.isQuiz) "Викторина" else "Опрос"}: '${poll.question.take(30)}...'"
        )
    }

    fun votePoll(chatId: String, pollId: String, optionIndex: Int, multiSelect: Boolean = false) {
        pollsState.update { map ->
            val list = map[chatId] ?: return@update map
            val updatedList = list.map { poll ->
                if (poll.id != pollId || poll.isClosed) return@map poll

                val currentSelected = poll.userSelectedOptionIds.toMutableSet()
                if (multiSelect) {
                    if (currentSelected.contains(optionIndex)) {
                        currentSelected.remove(optionIndex)
                    } else {
                        currentSelected.add(optionIndex)
                    }
                } else {
                    currentSelected.clear()
                    currentSelected.add(optionIndex)
                }

                // Recalculate counts
                val newOptions = poll.options.mapIndexed { idx, opt ->
                    val isSelected = currentSelected.contains(idx)
                    val baseCount = if (poll.userSelectedOptionIds.contains(idx)) opt.voteCount - 1 else opt.voteCount
                    val finalCount = if (isSelected) baseCount + 1 else baseCount
                    opt.copy(voteCount = finalCount.coerceAtLeast(0))
                }
                val total = newOptions.sumOf { it.voteCount }
                val formattedOptions = newOptions.map { opt ->
                    opt.copy(percentage = if (total > 0) opt.voteCount.toFloat() / total else 0f)
                }

                poll.copy(
                    options = formattedOptions,
                    totalVoters = total,
                    userSelectedOptionIds = currentSelected
                )
            }
            map + (chatId to updatedList)
        }
    }

    fun retractPollVote(chatId: String, pollId: String) {
        pollsState.update { map ->
            val list = map[chatId] ?: return@update map
            val updatedList = list.map { poll ->
                if (poll.id != pollId) return@map poll
                val newOptions = poll.options.mapIndexed { idx, opt ->
                    val wasSelected = poll.userSelectedOptionIds.contains(idx)
                    val finalCount = if (wasSelected) (opt.voteCount - 1).coerceAtLeast(0) else opt.voteCount
                    opt.copy(voteCount = finalCount)
                }
                val total = newOptions.sumOf { it.voteCount }
                val formattedOptions = newOptions.map { opt ->
                    opt.copy(percentage = if (total > 0) opt.voteCount.toFloat() / total else 0f)
                }
                poll.copy(
                    options = formattedOptions,
                    totalVoters = total,
                    userSelectedOptionIds = emptySet()
                )
            }
            map + (chatId to updatedList)
        }
    }

    fun closePoll(chatId: String, pollId: String) {
        pollsState.update { map ->
            val list = map[chatId] ?: return@update map
            val updatedList = list.map { poll ->
                if (poll.id == pollId) poll.copy(isClosed = true) else poll
            }
            map + (chatId to updatedList)
        }
    }

    // --- Boosters & Audit Log ---

    fun getBoostersFlow(chatId: String): StateFlow<Map<String, List<BoosterUser>>> = boostersState.asStateFlow()

    fun getRecentActionsFlow(chatId: String): StateFlow<Map<String, List<AdminActionLog>>> = recentActionsState.asStateFlow()

    private fun logAdminAction(chatId: String, title: String, details: String) {
        recentActionsState.update { map ->
            val currentList = map[chatId] ?: emptyList()
            val newLog = AdminActionLog(
                adminName = "Вы (Администратор)",
                adminAvatar = "https://picsum.photos/seed/my_avatar/100",
                actionTitle = title,
                details = details,
                timestamp = System.currentTimeMillis()
            )
            map + (chatId to (listOf(newLog) + currentList.take(20)))
        }
    }
}
