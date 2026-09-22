package com.example.ui

import android.content.Context
import androidx.work.*
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.util.concurrent.TimeUnit

object PresenceManager {
    fun updatePresence(context: Context, userId: String, isOnline: Boolean) {
        val inputData = Data.Builder()
            .putString("USER_ID", userId)
            .putBoolean("IS_ONLINE", isOnline)
            .build()
            
        val workRequest = OneTimeWorkRequestBuilder<PresenceWorker>()
            .setInputData(inputData)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
            .build()
            
        WorkManager.getInstance(context).enqueueUniqueWork(
            "presence_update_$userId",
            ExistingWorkPolicy.REPLACE,
            workRequest
        )
    }

    fun observePresence(userId: String): Flow<UserPresence?> = callbackFlow {
        val db = FirebaseFirestore.getInstance()
        val listener = db.collection("users").document(userId).collection("presence").document("status")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                
                if (snapshot != null && snapshot.exists()) {
                    val isOnline = snapshot.getBoolean("isOnline") ?: false
                    val lastActive = snapshot.getLong("lastActive") ?: 0L
                    trySend(UserPresence(userId, isOnline, lastActive))
                } else {
                    trySend(null)
                }
            }
            
        awaitClose { listener.remove() }
    }
}
