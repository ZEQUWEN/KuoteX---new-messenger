import os
import re

# Update AccountViewModel.kt
with open("app/src/main/java/com/example/ui/AccountViewModel.kt", "r") as f:
    vm_content = f.read()

# We need to add the optimistic update logic and hasUnsavedChanges
vm_new_methods = """
    private var lastSavedState: AccountState? = null

    init {
        loadProfile()
    }

    fun loadProfile() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isInitialLoading = true)
            delay(1500) // Mocking network load from Supabase
            val profile = SupabaseMock.Database.getProfile()
            lastSavedState = profile
            _uiState.value = profile
        }
    }

    fun hasUnsavedChanges(firstName: String, lastName: String, bio: String, socialLinks: Map<String, String>): Boolean {
        val currentSaved = lastSavedState ?: _uiState.value
        return firstName != currentSaved.firstName ||
               lastName != currentSaved.lastName ||
               bio != currentSaved.bio ||
               socialLinks != currentSaved.socialLinks
    }

    fun updateProfileData(firstName: String, lastName: String, bio: String, socialLinks: Map<String, String>) {
        val previousState = _uiState.value
        val newState = previousState.copy(
            firstName = firstName, 
            lastName = lastName, 
            bio = bio,
            socialLinks = socialLinks,
            isSuccess = false,
            error = null
        )
        
        // Optimistic UI update
        _uiState.value = newState

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            
            val profileResult = SupabaseMock.Database.updateProfile(
                firstName = firstName,
                lastName = lastName,
                bio = bio,
                birthDate = newState.birthDate,
                username = newState.username,
                phone = newState.phone
            )
            
            val socialResult = SupabaseMock.Database.updateSocialLinks(socialLinks)
            
            if (profileResult.isSuccess && socialResult.isSuccess) {
                val finalState = newState.copy(isLoading = false, isSuccess = true)
                lastSavedState = finalState
                _uiState.value = finalState
            } else {
                // Rollback on failure
                _uiState.value = previousState.copy(
                    isLoading = false,
                    error = profileResult.exceptionOrNull()?.message ?: socialResult.exceptionOrNull()?.message ?: "Ошибка сохранения"
                )
            }
        }
    }
    
    fun resetSuccessFlag() {
        _uiState.value = _uiState.value.copy(isSuccess = false)
    }
"""

# Replace init and loadProfile up to updateProfile
vm_pattern = re.compile(r"    init \{.*?fun updateBirthDate", re.DOTALL)
vm_content = re.sub(vm_pattern, vm_new_methods.strip() + "\n\n    fun updateBirthDate", vm_content)

# We also need to remove the old updateProfile, updateSocialLinks, saveProfile
vm_content = re.sub(r"    fun updateProfile\(.*?\).*?saveProfile\(\)\n    \}", "", vm_content, flags=re.DOTALL)
vm_content = re.sub(r"    fun updateSocialLinks\(.*?\).*?\}\n    \}", "", vm_content, flags=re.DOTALL)
vm_content = re.sub(r"    private fun saveProfile\(\).*?\}\n    \}", "", vm_content, flags=re.DOTALL)

with open("app/src/main/java/com/example/ui/AccountViewModel.kt", "w") as f:
    f.write(vm_content)

print("Updated ViewModel")
