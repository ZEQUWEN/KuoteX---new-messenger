package com.example.ui

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class PresenceWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val db = FirebaseFirestore.getInstance()
            val userId = inputData.getString("USER_ID") ?: return Result.failure()
            val isOnline = inputData.getBoolean("IS_ONLINE", true)
            
            val statusMap = hashMapOf(
                "isOnline" to isOnline,
                "lastActive" to System.currentTimeMillis()
            )
            
            db.collection("users").document(userId).collection("presence").document("status")
                .set(statusMap)
                .await()
                
            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            // Even if offline, Firestore caches locally, but if it truly fails we retry
            Result.retry()
        }
    }
}
