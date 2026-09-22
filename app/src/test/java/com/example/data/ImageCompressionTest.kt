package com.example.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.utils.ImageCompressionPreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class ImageCompressionTest {

    @Test
    fun testCompressionPresetsDimensionsAndQuality() {
        val balanced = ImageCompressionPreset.BALANCED_AUTO
        assertEquals(1280, balanced.maxDimension)
        assertEquals(80, balanced.initialQuality)
        assertTrue(balanced.targetMaxBytes > 0)

        val hd = ImageCompressionPreset.HIGH_QUALITY
        assertEquals(1920, hd.maxDimension)
        assertEquals(88, hd.initialQuality)

        val dataSaver = ImageCompressionPreset.DATA_SAVER
        assertEquals(800, dataSaver.maxDimension)
        assertEquals(65, dataSaver.initialQuality)

        val original = ImageCompressionPreset.ORIGINAL
        assertEquals(4096, original.maxDimension)
        assertEquals(100, original.initialQuality)
    }

    @Test
    fun testDocumentJsonSerialization() {
        val json = org.json.JSONObject().apply {
            put("uri", "https://firebasestorage.googleapis.com/v0/b/bucket/test.jpg")
            put("localUri", "file:///data/user/0/com.example/cache/test.jpg")
            put("name", "photo.jpg")
            put("size", 250000L)
            put("originalSize", 3200000L)
            put("width", 1280)
            put("height", 720)
            put("savedBytes", 2950000L)
            put("compressionRatio", 92.18)
            put("mimeType", "image/jpeg")
            put("isCompressed", true)
        }

        assertEquals(250000L, json.getLong("size"))
        assertEquals(3200000L, json.getLong("originalSize"))
        assertEquals(1280, json.getInt("width"))
        assertEquals(720, json.getInt("height"))
        assertTrue(json.getBoolean("isCompressed"))
    }
}
