package com.example.data.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE)
class FirestoreBackgroundSyncWorkerTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun testSchedulePeriodicSync_executesCleanly() {
        assertNotNull(context)
        FirestoreBackgroundSyncWorker.schedulePeriodicSync(context, intervalMinutes = 15)
        FirestoreBackgroundSyncWorker.syncImmediately(context)
        FirestoreBackgroundSyncWorker.cancelSync(context)
    }
}

