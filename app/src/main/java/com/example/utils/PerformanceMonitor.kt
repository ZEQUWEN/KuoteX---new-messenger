package com.example.utils

import androidx.compose.runtime.mutableStateOf

object PerformanceMonitor {
    // Animation intensity factor, from 0.1f (lowest) to 1.0f (highest)
    val animationIntensity = mutableStateOf(1.0f)
    
    private var slowFrameCount = 0
    private var frameCount = 0
    private var lastCheckTimeMs = 0L

    fun trackFrame(dtMs: Float, currentTimeMs: Long) {
        if (dtMs > 32f) { // Dropping frames (less than ~30 FPS)
            slowFrameCount++
        }
        frameCount++
        
        if (lastCheckTimeMs == 0L) {
            lastCheckTimeMs = currentTimeMs
        } else if ((currentTimeMs - lastCheckTimeMs) > 1000L) { // Every second
            if (slowFrameCount > 3) {
                // Stuttering detected, reduce animation intensity
                if (animationIntensity.value > 0.2f) {
                    animationIntensity.value -= 0.15f
                }
            } else if (slowFrameCount == 0 && frameCount > 50) {
                // Smooth rendering, gently increase intensity
                if (animationIntensity.value < 1.0f) {
                    animationIntensity.value += 0.05f
                }
            }
            
            // Clamp value
            animationIntensity.value = animationIntensity.value.coerceIn(0.1f, 1.0f)
                
            // Reset counters
            slowFrameCount = 0
            frameCount = 0
            lastCheckTimeMs = currentTimeMs
        }
    }
}
