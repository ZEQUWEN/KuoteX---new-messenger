package com.example

import android.app.Application
import android.util.Log
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.example.analytics.FirebaseAnalyticsHelper
import com.example.di.appModule
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level

/**
 * KuoteX Application entry point.
 * Initializes Koin dependency injection and application-wide subsystems.
 */
class KuoteXApplication : Application(), ImageLoaderFactory {

    override fun onCreate() {
        super.onCreate()

        // 0. Initialize Security & Native Encryption Subsystems early
        try {
            com.example.data.CryptoManager.init(this)
            net.sqlcipher.database.SQLiteDatabase.loadLibs(this)
        } catch (e: Throwable) {
            Log.w("KuoteXApplication", "Security subsystems init: ${e.message}")
        }

        // 1. Initialize Koin Dependency Injection
        try {
            startKoin {
                androidLogger(Level.ERROR)
                androidContext(this@KuoteXApplication)
                modules(appModule)
            }
            Log.i("KuoteXApplication", "Koin initialized with AppModule successfully")
        } catch (e: Exception) {
            Log.w("KuoteXApplication", "Koin start: ${e.message}")
        }

        // 2. Initialize Firebase & Cloud infrastructure
        initFirebase()

        // 3. Initialize Core Legacy/Bridge Containers & Remote Config
        try {
            com.example.di.Injector.init(com.example.di.DefaultAppContainer(this))
        } catch (e: Exception) {
            Log.w("KuoteXApplication", "Injector init: ${e.message}")
        }
        try {
            com.example.config.FirebaseRemoteConfigManager.init(this)
        } catch (e: Exception) {
            Log.w("KuoteXApplication", "FirebaseRemoteConfigManager init: ${e.message}")
        }
        try {
            FirebaseAnalyticsHelper.init(this)
        } catch (e: Exception) {
            Log.w("KuoteXApplication", "FirebaseAnalyticsHelper init: ${e.message}")
        }
        try {
            com.example.data.FirestoreUserRoleManager.init(this)
        } catch (e: Exception) {
            Log.w("KuoteXApplication", "FirestoreUserRoleManager init: ${e.message}")
        }

        // 4. Initialize Background Silent Firestore WorkManager Worker
        try {
            com.example.data.sync.FirestoreBackgroundSyncWorker.schedulePeriodicSync(this)
        } catch (e: Exception) {
            Log.w("KuoteXApplication", "FirestoreBackgroundSyncWorker schedule: ${e.message}")
        }
    }

    private fun initFirebase() {
        try {
            if (FirebaseApp.getApps(this).isEmpty()) {
                val app = try {
                    FirebaseApp.initializeApp(this)
                } catch (e: Exception) {
                    null
                }

                if (app == null) {
                    val options = try {
                        FirebaseOptions.fromResource(this)
                    } catch (e: Exception) {
                        FirebaseOptions.Builder()
                            .setApplicationId("1:1086187753295:android:b4f79bfa9c8e463f50bf3a")
                            .setApiKey("AIzaSyCNigsYj2MRPd9E2SL1Zo49PxMTfuMQYAs")
                            .setProjectId("kuotex-96819")
                            .setGcmSenderId("1086187753295")
                            .setStorageBucket("kuotex-96819.firebasestorage.app")
                            .build()
                    }
                    if (options != null) {
                        FirebaseApp.initializeApp(this, options)
                    }
                }
                Log.i("KuoteXApplication", "Firebase initialized successfully")
                
                try {
                    val appCheck = com.google.firebase.appcheck.FirebaseAppCheck.getInstance()
                    appCheck.installAppCheckProviderFactory(
                        PlayIntegrityAppCheckProviderFactory.getInstance()
                    )
                    Log.i("KuoteXApplication", "Firebase App Check initialized with Play Integrity")
                } catch (e: Exception) {
                    Log.d("KuoteXApplication", "AppCheck init: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.w("KuoteXApplication", "FirebaseApp init error: ${e.message}")
        }
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .crossfade(true)
            .allowHardware(false)
            .build()
    }
}
