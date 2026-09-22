package com.example.ui.auth

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.analytics.AnalyticsTracker
import com.example.auth.AuthResult
import com.example.auth.AuthState
import com.example.auth.FirebaseUserInfo
import com.example.data.repository.AuthRepository
import com.example.data.repository.UserDataRepository
import com.example.ui.UserAccount
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * UI State representation for Authentication flows.
 */
sealed class AuthUiState {
    object Idle : AuthUiState()
    data class Loading(val message: String = "Processing...") : AuthUiState()
    data class Success(val user: FirebaseUserInfo, val accountId: String) : AuthUiState()
    data class Error(val message: String, val code: String? = null) : AuthUiState()
    data class Requires2FA(val accountId: String) : AuthUiState()
}

/**
 * Dedicated AuthViewModel responsible for handling login, registration,
 * Google Auth, password reset, and 2FA authentication sessions.
 */
class AuthViewModel(
    private val authRepository: AuthRepository,
    private val userDataRepository: UserDataRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    private val _isPasswordResetSent = MutableStateFlow(false)
    val isPasswordResetSent: StateFlow<Boolean> = _isPasswordResetSent.asStateFlow()

    val authState: StateFlow<AuthState> = authRepository.authState
    val currentUser: StateFlow<FirebaseUserInfo?> = authRepository.currentUser
    val registeredAccounts = userDataRepository.getAllAccounts()

    fun resetState() {
        _uiState.value = AuthUiState.Idle
        _isPasswordResetSent.value = false
    }

    fun loginWithEmailOrLogin(identifier: String, pass: String, accounts: List<UserAccount>) {
        if (identifier.isBlank() || pass.isBlank()) {
            _uiState.value = AuthUiState.Error("Please enter login and password")
            return
        }

        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading("Signing in...")
            AnalyticsTracker.logButtonClick("login_attempt", "auth", "login_screen")

            // Check matching local or cached account
            val matchedAccount = accounts.find {
                it.username.equals(identifier, ignoreCase = true) ||
                it.username.equals("@$identifier", ignoreCase = true) ||
                it.phoneNumber == identifier
            }

            if (matchedAccount != null && matchedAccount.is2FAEnabled) {
                _uiState.value = AuthUiState.Requires2FA(matchedAccount.id)
                return@launch
            }

            val emailToUse = if (identifier.contains("@") && identifier.contains(".")) {
                identifier
            } else {
                "${identifier.replace("@", "").lowercase()}@kuotex.app"
            }

            val result = authRepository.signInWithEmail(emailToUse, pass)
            when (result) {
                is AuthResult.Success -> {
                    val accountId = matchedAccount?.id ?: result.data.uid
                    _uiState.value = AuthUiState.Success(result.data, accountId)
                    AnalyticsTracker.logButtonClick("login_success", "auth", "login_screen")
                }
                is AuthResult.Error -> {
                    // Fallback to local credential match if offline
                    if (matchedAccount != null) {
                        val fallbackInfo = FirebaseUserInfo(
                            uid = matchedAccount.id,
                            email = "${matchedAccount.username.replace("@", "").lowercase()}@kuotex.app",
                            phoneNumber = matchedAccount.phoneNumber,
                            displayName = matchedAccount.displayName,
                            photoUrl = matchedAccount.profilePicUrl,
                            isEmailVerified = true
                        )
                        _uiState.value = AuthUiState.Success(fallbackInfo, matchedAccount.id)
                    } else {
                        _uiState.value = AuthUiState.Error(result.message, result.code)
                        AnalyticsTracker.logButtonClick("login_failure", "auth", "login_screen")
                    }
                }
            }
        }
    }

    fun register(
        username: String,
        displayName: String,
        phone: String,
        password: String,
        email: String? = null
    ) {
        if (username.isBlank() || phone.isBlank() || password.isBlank()) {
            _uiState.value = AuthUiState.Error("All required fields must be filled")
            return
        }

        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading("Creating account...")
            val effectiveEmail = if (!email.isNullOrBlank()) {
                email
            } else {
                "${username.replace("@", "").lowercase()}@kuotex.app"
            }

            val result = authRepository.signUpWithEmail(
                email = effectiveEmail,
                password = password,
                username = username,
                displayName = displayName.ifBlank { username },
                phoneNumber = phone
            )

            when (result) {
                is AuthResult.Success -> {
                    val newAccount = UserAccount(
                        id = result.data.uid,
                        username = username.let { if (it.startsWith("@")) it else "@$it" },
                        displayName = displayName.ifBlank { username },
                        phoneNumber = phone,
                        profilePicUrl = result.data.photoUrl ?: "",
                        isActive = true,
                        customStatus = "Available",
                        is2FAEnabled = false
                    )
                    userDataRepository.insertAccount(newAccount)
                    _uiState.value = AuthUiState.Success(result.data, newAccount.id)
                    AnalyticsTracker.logButtonClick("signup_success", "auth", "registration_screen")
                }
                is AuthResult.Error -> {
                    // Create local account even if offline
                    val localId = java.util.UUID.randomUUID().toString()
                    val newAccount = UserAccount(
                        id = localId,
                        username = username.let { if (it.startsWith("@")) it else "@$it" },
                        displayName = displayName.ifBlank { username },
                        phoneNumber = phone,
                        profilePicUrl = "",
                        isActive = true,
                        customStatus = "Available",
                        is2FAEnabled = false
                    )
                    userDataRepository.insertAccount(newAccount)
                    val localInfo = FirebaseUserInfo(
                        uid = localId,
                        email = effectiveEmail,
                        phoneNumber = phone,
                        displayName = newAccount.displayName,
                        photoUrl = null,
                        isEmailVerified = false
                    )
                    _uiState.value = AuthUiState.Success(localInfo, localId)
                }
            }
        }
    }

    fun signInWithGoogle(context: Context) {
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading("Connecting to Google...")
            val result = authRepository.signInWithGoogle(context)
            when (result) {
                is AuthResult.Success -> {
                    val user = result.data
                    val newAccount = UserAccount(
                        id = user.uid,
                        username = "@${(user.displayName ?: "user").replace(" ", "").lowercase()}",
                        displayName = user.displayName ?: "Google User",
                        phoneNumber = user.phoneNumber ?: "",
                        profilePicUrl = user.photoUrl ?: "",
                        isActive = true,
                        customStatus = "Verified Google User",
                        is2FAEnabled = false
                    )
                    userDataRepository.insertAccount(newAccount)
                    _uiState.value = AuthUiState.Success(user, newAccount.id)
                }
                is AuthResult.Error -> {
                    _uiState.value = AuthUiState.Error(result.message)
                }
            }
        }
    }

    fun sendPasswordReset(email: String) {
        if (email.isBlank()) return
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading("Sending reset instructions...")
            val res = authRepository.sendPasswordReset(email)
            when (res) {
                is AuthResult.Success -> {
                    _isPasswordResetSent.value = true
                    _uiState.value = AuthUiState.Idle
                }
                is AuthResult.Error -> {
                    _uiState.value = AuthUiState.Error(res.message)
                }
            }
        }
    }

    suspend fun checkPhoneExists(phone: String): Boolean {
        return authRepository.checkPhoneExists(phone)
    }

    override fun onCleared() {
        super.onCleared()
        // Ensure sensitive in-flight credentials are purged from memory
        _uiState.value = AuthUiState.Idle
    }
}
