sed -i '/fun removeGroupMember/i\
    fun addGroupMember(chatId: String, userId: String, userName: String, isAdmin: Boolean) {\
        viewModelScope.launch {\
            repository.insertGroupMember(com.example.data.GroupMember(chatId, userId, userName, isAdmin))\
        }\
    }\
' app/src/main/java/com/example/ui/AppViewModel.kt
