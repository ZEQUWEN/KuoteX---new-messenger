sed -i '/val isBatterySaver by viewModel.batterySaverEnabled.collectAsState()/c\
    val isBatterySaverSetting by viewModel.batterySaverEnabled.collectAsState()\
    val context = androidx.compose.ui.platform.LocalContext.current\
    var isCharging by remember { mutableStateOf(false) }\
    androidx.compose.runtime.DisposableEffect(context) {\
        val filter = android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED)\
        val receiver = object : android.content.BroadcastReceiver() {\
            override fun onReceive(ctx: android.content.Context, intent: android.content.Intent) {\
                val status: Int = intent.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1)\
                isCharging = status == android.os.BatteryManager.BATTERY_STATUS_CHARGING || status == android.os.BatteryManager.BATTERY_STATUS_FULL\
            }\
        }\
        context.registerReceiver(receiver, filter)\
        onDispose {\
            context.unregisterReceiver(receiver)\
        }\
    }\
    val isBatterySaver = isBatterySaverSetting && !isCharging' app/src/main/java/com/example/ui/MainScreen.kt
