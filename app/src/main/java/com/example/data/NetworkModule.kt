package com.example.data

import android.content.Context
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

object NetworkModule {
    
    fun provideOkHttpClient(context: Context, onDataUsage: (com.example.ui.NetworkType, Long, Long, String) -> Unit): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        
        val statsInterceptor = NetworkStatsInterceptor(context, onDataUsage)

        return OkHttpClient.Builder()
            .addInterceptor(statsInterceptor)
            .addInterceptor(loggingInterceptor)
            .build()
    }

    fun provideRetrofit(okHttpClient: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl("https://api.example.com/") // Replace with actual base URL
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create())
            .build()
    }
}
