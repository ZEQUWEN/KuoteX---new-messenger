package com.example.di

import androidx.compose.ui.graphics.Color
import com.example.ui.AppTheme

/**
 * Interface defining dynamic Theme and Appearance engine.
 * Allows switching theme palettes, neon shaders, and particle settings without file replacements.
 */
interface ThemeEngine {
    fun getPrimaryColor(theme: AppTheme, customColor: Long?): Color?
    fun getSecondaryColor(theme: AppTheme, customColor: Long?): Color?
    fun isParticlesEnabled(theme: AppTheme): Boolean
}

class DefaultThemeEngine : ThemeEngine {
    override fun getPrimaryColor(theme: AppTheme, customColor: Long?): Color? {
        if (customColor != null && customColor != 0L) {
            return Color(customColor.toULong())
        }
        return when (theme) {
            AppTheme.NEON_SNOWFLAKES -> Color(0xFF00E5FF)
            AppTheme.NEON_CHERRY_BLOSSOM -> Color(0xFFFF4081)
            AppTheme.NEON_CONFETTI -> Color(0xFFFFD600)
            AppTheme.NEON_MOON -> Color(0xFF7C4DFF)
            AppTheme.NEON_ROOM_FOG -> Color(0xFF64FFDA)
            AppTheme.DEFAULT -> null
        }
    }

    override fun getSecondaryColor(theme: AppTheme, customColor: Long?): Color? {
        if (customColor != null && customColor != 0L) {
            return Color(customColor.toULong())
        }
        return when (theme) {
            AppTheme.NEON_SNOWFLAKES -> Color(0xFF80D8FF)
            AppTheme.NEON_CHERRY_BLOSSOM -> Color(0xFFFF80AB)
            AppTheme.NEON_CONFETTI -> Color(0xFFFFAB40)
            AppTheme.NEON_MOON -> Color(0xFFB388FF)
            AppTheme.NEON_ROOM_FOG -> Color(0xFFA7FFEB)
            AppTheme.DEFAULT -> null
        }
    }

    override fun isParticlesEnabled(theme: AppTheme): Boolean {
        return theme != AppTheme.DEFAULT
    }
}
