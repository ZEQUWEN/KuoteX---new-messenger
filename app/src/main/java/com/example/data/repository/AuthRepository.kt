package com.example.data.repository

import android.content.Context
import com.example.auth.AuthResult
import com.example.auth.AuthState
import com.example.auth.FirebaseAuthManager
import com.example.auth.FirebaseUserInfo
import com.example.data.RegisteredUserRole
import com.example.data.UserDao
import com.example.ui.UserAccount
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Clean repository abstraction for Authentication lifecycle and Firebase Auth operations.
 */
interface AuthRepository {
    val authState: StateFlow<AuthState>
    val currentUser: StateFlow<FirebaseUserInfo?>

    suspend fun signInWithEmail(emailOrLogin: String, password: String): AuthResult<FirebaseUserInfo>
    suspend fun signUpWithEmail(
        email: String,
        password: String,
        username: String,
        displayName: String,
        phoneNumber: String
    ): AuthResult<FirebaseUserInfo>
    suspend fun signInWithGoogle(context: Context): AuthResult<FirebaseUserInfo>
    suspend fun sendPasswordReset(email: String): AuthResult<Unit>
    suspend fun signOut()
    suspend fun checkPhoneExists(phone: String): Boolean
}

class AuthRepositoryImpl(
    private val userDao: UserDao,
    private val authManager: FirebaseAuthManager = FirebaseAuthManager
) : AuthRepository {

    override val authState: StateFlow<AuthState> = authManager.authState
    override val currentUser: StateFlow<FirebaseUserInfo?> = authManager.currentUser

    override suspend fun signInWithEmail(emailOrLogin: String, password: String): AuthResult<FirebaseUserInfo> {
        return authManager.signInWithEmail(emailOrLogin, password)
    }

    override suspend fun signUpWithEmail(
        email: String,
        password: String,
        username: String,
        displayName: String,
        phoneNumber: String
    ): AuthResult<FirebaseUserInfo> {
        return authManager.signUpWithEmail(
            email = email,
            pass = password,
            displayName = displayName,
            photoUrl = null
        )
    }

    override suspend fun signInWithGoogle(context: Context): AuthResult<FirebaseUserInfo> {
        return authManager.signInWithGoogle(context)
    }

    override suspend fun sendPasswordReset(email: String): AuthResult<Unit> {
        return authManager.sendPasswordResetEmail(email)
    }

    override suspend fun signOut() {
        authManager.signOut()
    }

    override suspend fun checkPhoneExists(phone: String): Boolean {
        return userDao.checkPhoneNumberExists(phone)
    }
}
