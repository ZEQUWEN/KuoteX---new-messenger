package com.example.ui

import androidx.compose.ui.graphics.Color

enum class NetworkType {
    ALL, MOBILE, WIFI, ROAMING
}

data class NetworkCategoryStats(
    val categoryName: String,
    val sizeBytes: Long,
    val color: Color
)

data class NetworkStatsModel(
    val networkType: NetworkType,
    val sentBytes: Long,
    val receivedBytes: Long,
    val categories: List<NetworkCategoryStats>
)

data class StorageCategoryStats(
    val categoryName: String,
    val sizeBytes: Long,
    val color: Color,
    val isSelected: Boolean = true,
    val subCategories: List<StorageCategoryStats>? = null,
    val isExpanded: Boolean = false
)

data class StorageStatsModel(
    val maxCacheSizeBytes: Long = -1L, // -1 for infinity
    val categories: List<StorageCategoryStats>
)
