package com.example.utils

import android.content.Context
import android.util.Log

object CrashReporter {
    private const val TAG = "CrashReporter"
    
    fun logLifecycleEvent(event: String) {
        Log.d(TAG, "Lifecycle Event: \$event")
    }

    fun init(context: Context) {
        val prefs = context.getSharedPreferences("crash_prefs", Context.MODE_PRIVATE)
        val defaultUncaughtExceptionHandler = Thread.getDefaultUncaughtExceptionHandler()
        
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            val stackTrace = throwable.stackTraceToString()
            val isNavTransitionException = throwable is IllegalStateException && 
                (throwable.message?.contains("Cannot transition entry that is not in the back stack") == true ||
                 stackTrace.contains("prepareForTransition") ||
                 stackTrace.contains("PredictiveBackHandler"))

            if (isNavTransitionException) {
                Log.w(TAG, "Suppressed non-fatal navigation predictive back transition error: ${throwable.message}")
                return@setDefaultUncaughtExceptionHandler
            }

            prefs.edit().putString("last_crash", stackTrace).commit()
            Log.e(TAG, "Exception caught", throwable)
            defaultUncaughtExceptionHandler?.uncaughtException(thread, throwable)
        }
    }
    
    fun getLastCrash(context: Context): String? {
        val prefs = context.getSharedPreferences("crash_prefs", Context.MODE_PRIVATE)
        val crash = prefs.getString("last_crash", null)
        prefs.edit().remove("last_crash").commit()
        return crash
    }
}
