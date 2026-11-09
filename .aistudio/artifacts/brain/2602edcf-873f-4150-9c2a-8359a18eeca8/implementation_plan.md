# Implementation Plan: Pinning Chats to the Top

## Status: Completed

1. **Data Model & Persistence Layer**:
   - Added `isPinned: Boolean = false` and `pinnedTimestamp: Long = 0L` to `Chat` model.
   - Added `updateChatPinStatus(chatId, isPinned, pinnedTimestamp)` to `ChatDao`, `MessengerRepository`, and `ChatDataRepository` (`FirestoreRepositoryImpl`).
   - Implemented `togglePinChat(chatId, onLimitReached)` in `AppViewModel` enforcing a maximum limit of 5 pinned chats.

2. **UI & Sorting in Conversation List**:
   - Updated `pageFilteredChats` sorting: pinned chats appear first sorted by `pinnedTimestamp DESC`, unpinned chats sorted by `lastMessageTimestamp DESC`.
   - Enhanced `ChatListItem` with:
     - Blue-tinted card background highlight and subtle border for pinned chats (`Color(0xFF2AABEE).copy(alpha = 0.10f)`).
     - Telegram-styled pushpin icon (`Icons.Filled.PushPin` rotated 45 degrees in circle badge) next to timestamp.
     - Telegram-blue colored timestamp for pinned chats.

3. **Gestures & Actions**:
   - **Swipe Action**: Start-to-end (left-to-right) swipe on any chat snaps back and toggles Pin/Unpin.
   - **Long-Press Menu**: Long-pressing a chat opens `ChatActionBottomSheet` with "Закрепить" / "Открепить", mute, archive, and delete actions.
   - **Limit Feedback**: When attempting to pin more than 5 chats, displays notification: *"Закреплено максимум 5 чатов. Открепите один, чтобы закрепить новый."*
