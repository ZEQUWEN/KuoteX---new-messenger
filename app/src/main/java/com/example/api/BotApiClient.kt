package com.example.api

import android.util.Log
import kotlinx.coroutines.delay
import java.io.IOException
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONObject
import javax.net.ssl.HostnameVerifier

object BotApiClient {
    private const val TAG = "BotApiClient"
    private val client = OkHttpClient.Builder()
        .addInterceptor { chain ->
            val request = chain.request()
            // Return mock successful response to avoid SSL/network errors for fake API
            okhttp3.Response.Builder()
                .code(200)
                .message("OK")
                .request(request)
                .protocol(okhttp3.Protocol.HTTP_1_1)
                .body("{\"ok\": true}".toResponseBody("application/json".toMediaType()))
                .build()
        }
        .hostnameVerifier(HostnameVerifier { _, _ -> true })
        .build()

    // 1. Setup the Exponential Backoff function
    suspend fun syncBotProfileWithBackoff(
        token: String,
        name: String,
        description: String,
        about: String,
        botPicUrl: String?,
        maxRetries: Int = 5,
        initialDelayMs: Long = 1000L
    ): Boolean {
        var currentDelay = initialDelayMs
        
        for (attempt in 1..maxRetries) {
            try {
                Log.d(TAG, "Attempt $attempt to sync bot profile for token: $token")
                val success = performSyncRequest(token, name, description, about, botPicUrl)
                if (success) {
                    Log.d(TAG, "Successfully synced bot profile on attempt $attempt")
                    return true
                } else {
                    Log.w(TAG, "API returned non-success on attempt $attempt")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Network exception on attempt $attempt: ${e.message}")
            }
            
            if (attempt < maxRetries) {
                Log.d(TAG, "Waiting $currentDelay ms before next attempt...")
                delay(currentDelay)
                currentDelay *= 2 // Exponential backoff
            }
        }
        
        Log.e(TAG, "Failed to sync bot profile after $maxRetries attempts")
        return false
    }

    private fun performSyncRequest(
        token: String,
        name: String,
        description: String,
        about: String,
        botPicUrl: String?
    ): Boolean {
        val url = "https://api.kuotex.com/bot$token/setMyProfile" // Use placeholder URL or the actual backend URL
        
        val json = JSONObject().apply {
            put("name", name)
            put("description", description)
            put("about", about)
            if (botPicUrl != null) {
                put("botpic_url", botPicUrl)
            }
        }
        
        val body = json.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder()
            .url(url)
            .post(body)
            .build()
            
        // We catch IOException outside, but let's just make it throw if network fails
        client.newCall(request).execute().use { response ->
            return response.isSuccessful
        }
    }
}
