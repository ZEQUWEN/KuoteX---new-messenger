package com.example.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.telephony.TelephonyManager
import com.example.ui.NetworkType
import okhttp3.Interceptor
import okhttp3.Response

class NetworkStatsInterceptor(
    private val context: Context,
    private val onDataUsage: (NetworkType, Long, Long, String) -> Unit
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        
        // Estimate sent bytes (headers + body)
        var sentBytes = 0L
        sentBytes += request.url.toString().toByteArray().size
        sentBytes += request.method.toByteArray().size
        request.headers.forEach { (name, value) ->
            sentBytes += name.toByteArray().size + value.toByteArray().size
        }
        
        request.body?.let { body ->
            val contentLength = body.contentLength()
            if (contentLength != -1L) {
                sentBytes += contentLength
            }
        }

        val response = chain.proceed(request)
        
        // Estimate received bytes (headers + body)
        var receivedBytes = 0L
        receivedBytes += response.code.toString().toByteArray().size
        response.headers.forEach { (name, value) ->
            receivedBytes += name.toByteArray().size + value.toByteArray().size
        }
        
        val responseBody = response.peekBody(Long.MAX_VALUE)
        receivedBytes += responseBody.contentLength().takeIf { it != -1L } ?: 0L

        val currentNetworkType = getCurrentNetworkType(context)
        
        // Categorize based on content type or URL
        val categoryName = categorizeTraffic(request.url.toString(), response.header("Content-Type"))
        
        onDataUsage(currentNetworkType, sentBytes, receivedBytes, categoryName)
        
        return response
    }

    private fun getCurrentNetworkType(context: Context): NetworkType {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = connectivityManager.activeNetwork ?: return NetworkType.WIFI
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return NetworkType.WIFI

        return when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkType.WIFI
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> {
                val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
                if (telephonyManager.isNetworkRoaming) NetworkType.ROAMING else NetworkType.MOBILE
            }
            else -> NetworkType.WIFI
        }
    }

    private fun categorizeTraffic(url: String, contentType: String?): String {
        return when {
            contentType?.startsWith("video/") == true || url.contains(".mp4") -> "Видео"
            contentType?.startsWith("image/") == true || url.contains(".jpg") || url.contains(".png") -> "Фото"
            url.contains("/api/messages") -> "Сообщения"
            else -> "Файлы"
        }
    }
}
