package com.example

import android.os.Bundle
import com.example.data.dataStore
import com.example.data.UserPreferencesRepository
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.data.AppDatabase
import com.example.data.MessengerRepository
import com.example.ui.AppViewModel
import com.example.ui.MainAppNavigation
import com.example.ui.theme.KuoteXTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import android.Manifest
import android.os.Build
import androidx.activity.result.contract.ActivityResultContracts
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import android.util.Log
import android.widget.Toast
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.example.ui.ConnectionStatus
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.animation.doOnEnd
import android.animation.ObjectAnimator
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {
    private val viewModel: AppViewModel by inject()
    private var isAppReady = false

    // Ask for POST_NOTIFICATIONS permission
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            Log.d("FCM", "Notification permission granted")
            fetchFCMToken()
        } else {
            Log.d("FCM", "Notification permission denied")
        }
    }

    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (androidx.core.content.ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                fetchFCMToken()
            } else {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            fetchFCMToken()
        }
    }

    private fun fetchFCMToken() {
        try {
            val messaging = FirebaseMessaging.getInstance()
            messaging.isAutoInitEnabled = false
            messaging.token.addOnCompleteListener { task ->
                if (!task.isSuccessful) {
                    val ex = task.exception
                    val errorMsg = ex?.message ?: "Unknown error"
                    if (errorMsg.contains("TOO_MANY_REGISTRATIONS", ignoreCase = true)) {
                        Log.w("FCM", "FCM: Too many app registrations on device/emulator, skipping push token sync: $errorMsg")
                    } else if (errorMsg.contains("SERVICE_NOT_AVAILABLE", ignoreCase = true)) {
                        Log.w("FCM", "FCM: Google Play Services unavailable for push tokens: $errorMsg")
                    } else {
                        Log.w("FCM", "FCM token retrieval notice: $errorMsg")
                    }
                    return@addOnCompleteListener
                }
                val token = task.result
                Log.d("FCM", "FCM Token acquired successfully: $token")
            }
        } catch (e: Throwable) {
            Log.w("FCM", "Safe FCM token retrieval catch: ${e.message}")
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        splashScreen.setKeepOnScreenCondition { !isAppReady }
        
        splashScreen.setOnExitAnimationListener { splashScreenViewProvider ->
            val fadeOut = ObjectAnimator.ofFloat(
                splashScreenViewProvider.view,
                android.view.View.ALPHA,
                1f,
                0f
            ).apply {
                interpolator = AccelerateDecelerateInterpolator()
                duration = 300L
                doOnEnd { splashScreenViewProvider.remove() }
            }
            fadeOut.start()
        }

        super.onCreate(savedInstanceState)

        // Asynchronously initialize Firebase services for a smooth launch experience
        lifecycleScope.launch(Dispatchers.Default) {
            try {
                if (FirebaseApp.getApps(applicationContext).isEmpty()) {
                    FirebaseApp.initializeApp(applicationContext)
                }
                com.example.analytics.AnalyticsTracker.init(applicationContext)
                com.example.config.FirebaseRemoteConfigManager.init(applicationContext)
            } catch (e: Exception) {
                Log.w("Firebase", "Async Firebase init: ${e.message}")
            } finally {
                withContext(Dispatchers.Main) {
                    isAppReady = true
                }
            }
        }

        com.example.utils.CrashReporter.init(this)
        com.example.utils.CrashReporter.logLifecycleEvent("onCreate")

        val lastCrash = com.example.utils.CrashReporter.getLastCrash(this)
        if (lastCrash != null) {
            Log.e("MainActivity", "Previous crash was detected:\n$lastCrash")
        }

        try {
            com.example.data.CryptoManager.init(applicationContext)
            net.sqlcipher.database.SQLiteDatabase.loadLibs(applicationContext)
            
            // Ensure DI Injector is initialized
            try {
                com.example.di.Injector.init(com.example.di.DefaultAppContainer(applicationContext))
            } catch (e: Exception) {
                // Already initialized in Application or prior step
            }

            val appContainer = com.example.di.Injector.get()
            val db = appContainer.database
            val sharedPrefs = appContainer.sharedPreferences
            val repository = appContainer.messengerRepository
            val userPrefs = appContainer.userPreferencesRepository

            com.example.ui.botapi.BotRegistry.init(db.botDao())
            
            // Setup WorkManager for edge cases (background sync)
            val workRequest = androidx.work.PeriodicWorkRequestBuilder<com.example.data.MessageSyncWorker>(
                15, java.util.concurrent.TimeUnit.MINUTES
            ).build()
            androidx.work.WorkManager.getInstance(applicationContext).enqueueUniquePeriodicWork(
                "MessageSync",
                androidx.work.ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            )
            
            val cacheCleanupRequest = androidx.work.PeriodicWorkRequestBuilder<com.example.data.CacheCleanupWorker>(
                7, java.util.concurrent.TimeUnit.DAYS
            ).build()
            androidx.work.WorkManager.getInstance(applicationContext).enqueueUniquePeriodicWork(
                "CacheCleanup",
                androidx.work.ExistingPeriodicWorkPolicy.KEEP,
                cacheCleanupRequest
            )
            
            // Start presence background worker
            try {
                com.example.ui.PresenceManager.updatePresence(applicationContext, "current_user_id", true)
            } catch(e: Exception) { e.printStackTrace() }
            
            viewModel.checkAutoTheme()

            // Initialize Notification Channels
            com.example.notifications.NotificationHelper.initNotificationChannels(applicationContext)
            askNotificationPermission()

            intent?.getStringExtra("OPEN_CHAT_ID")?.let { chatId ->
                viewModel.setPendingOpenChatId(chatId)
            }

            intent?.getStringExtra("OPEN_STREAM_HOST_ID")?.let { hostId ->
                viewModel.setPendingOpenStreamHostId(hostId)
            }

            val connectivityManager = getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            
            // Check initial network state
            val activeNet = connectivityManager.activeNetwork
            val caps = connectivityManager.getNetworkCapabilities(activeNet)
            val isInitiallyOnline = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
            viewModel.setConnectionStatus(if (isInitiallyOnline) ConnectionStatus.ONLINE else ConnectionStatus.OFFLINE)

            val networkRequest = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()

            val networkCallback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    viewModel.setConnectionStatus(ConnectionStatus.CONNECTING)
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        viewModel.setConnectionStatus(ConnectionStatus.ONLINE)
                    }, 600)
                }
                override fun onLost(network: Network) {
                    viewModel.setConnectionStatus(ConnectionStatus.OFFLINE)
                }
                override fun onUnavailable() {
                    viewModel.setConnectionStatus(ConnectionStatus.OFFLINE)
                }
            }

            try {
                connectivityManager.registerNetworkCallback(networkRequest, networkCallback)
            } catch (e: Exception) {
                e.printStackTrace()
            }

            enableEdgeToEdge()
            setContent {
                val primaryColorLong by viewModel.customPrimaryColor.collectAsState()
                val secondaryColorLong by viewModel.customSecondaryColor.collectAsState()
                
                val themeOpacity by viewModel.themeOpacity.collectAsState()
                val primary = if (primaryColorLong != null && primaryColorLong != 0L) Color(primaryColorLong!!.toULong()) else null
                val secondary = if (secondaryColorLong != null && secondaryColorLong != 0L) Color(secondaryColorLong!!.toULong()) else null
                
val isDarkThemeEnabled by viewModel.isDarkThemeEnabled.collectAsState()
                val isAutoThemeEnabled by viewModel.isAutoThemeEnabled.collectAsState()
                val systemDarkTheme = androidx.compose.foundation.isSystemInDarkTheme()
                val batterySaverEnabled by viewModel.batterySaverEnabled.collectAsState()
                
                var batteryLevel by androidx.compose.runtime.remember { mutableStateOf(100f) }
                LaunchedEffect(Unit) {
                    val batteryStatus: Intent? = IntentFilter(Intent.ACTION_BATTERY_CHANGED).let { ifilter ->
                        applicationContext.registerReceiver(null, ifilter)
                    }
                    val level: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
                    val scale: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
                    if (level != -1 && scale != -1) {
                        batteryLevel = level * 100f / scale.toFloat()
                    }
                }
                
                val disableNeon = batterySaverEnabled && batteryLevel < 20f
                val finalCustomPrimary = if (disableNeon) null else primary
                val finalCustomSecondary = if (disableNeon) null else secondary

                KuoteXTheme(darkTheme = if (isAutoThemeEnabled) systemDarkTheme else isDarkThemeEnabled, customPrimary = finalCustomPrimary, customSecondary = finalCustomSecondary, themeOpacity = themeOpacity) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = androidx.compose.material3.MaterialTheme.colorScheme.background
                    ) {
                        MainAppNavigation(viewModel)
                    }
                }
            }
        } catch (e: Exception) {
            setContent {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Text(text = "CRASH: \${e.stackTraceToString()}", modifier = Modifier.padding(16.dp).verticalScroll(rememberScrollState()))
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        com.example.utils.CrashReporter.logLifecycleEvent("onStart")
        com.example.notifications.InAppNotificationManager.setAppForeground(true)
    }

    override fun onResume() {
        super.onResume()
        com.example.utils.CrashReporter.logLifecycleEvent("onResume")
        com.example.notifications.InAppNotificationManager.setAppForeground(true)
    }

    override fun onPause() {
        super.onPause()
        com.example.utils.CrashReporter.logLifecycleEvent("onPause")
    }

    override fun onStop() {
        super.onStop()
        com.example.utils.CrashReporter.logLifecycleEvent("onStop")
        com.example.notifications.InAppNotificationManager.setAppForeground(false)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.getStringExtra("OPEN_CHAT_ID")?.let { chatId ->
            try {
                viewModel.setPendingOpenChatId(chatId)
            } catch (e: Exception) {
                // Handled safely
            }
        }
        intent.getStringExtra("OPEN_STREAM_HOST_ID")?.let { hostId ->
            try {
                viewModel.setPendingOpenStreamHostId(hostId)
            } catch (e: Exception) {
                // Handled safely
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        com.example.utils.CrashReporter.logLifecycleEvent("onDestroy")
    }
}
