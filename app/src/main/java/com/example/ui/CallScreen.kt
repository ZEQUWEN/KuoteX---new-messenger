package com.example.ui
import androidx.compose.material.icons.filled.ArrowBack

import android.Manifest
import android.content.Context
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.example.di.Injector
import com.example.webrtc.CallConnectionState
import com.example.webrtc.WebRtcCallSession
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.MultiplePermissionsState
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import kotlinx.coroutines.delay
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.SignalCellularAlt
import kotlin.random.Random

enum class CallState {
    CONNECTING, RINGING, CONNECTED, ENDED
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun CallScreen(viewModel: AppViewModel, chatId: String, isVideo: Boolean, navController: NavController) {
    val chats by viewModel.chats.collectAsState()
    val chat = chats.find { it.id == chatId }

    // Scoped / Factory session instance created on entry and disposed immediately on exit
    val callSession = remember(chatId, isVideo) {
        Injector.get().createCallSession(chatId, isVideo)
    }

    DisposableEffect(callSession) {
        callSession.initializeCall()
        onDispose {
            // Instant release of WebRTC memory, camera channels, and audio buffers
            callSession.close()
        }
    }
    
    val permissionsToRequest = mutableListOf(Manifest.permission.RECORD_AUDIO)
    if (isVideo) {
        permissionsToRequest.add(Manifest.permission.CAMERA)
    }

    val permissionsState = rememberMultiplePermissionsState(permissionsToRequest)

    LaunchedEffect(Unit) {
        if (!permissionsState.allPermissionsGranted) {
            permissionsState.launchMultiplePermissionRequest()
        }
    }

    if (permissionsState.allPermissionsGranted) {
        CallContent(
            chatTitle = chat?.title ?: "Unknown",
            isVideo = isVideo,
            session = callSession,
            onEndCall = { navController.popBackStack() }
        )
    } else {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Permissions required for call.")
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = { permissionsState.launchMultiplePermissionRequest() }) {
                    Text("Grant Permissions")
                }
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = { navController.popBackStack() }) {
                    Text("Cancel")
                }
            }
        }
    }
}

@Composable
fun CallContent(
    chatTitle: String,
    isVideo: Boolean,
    session: WebRtcCallSession? = null,
    onEndCall: () -> Unit
) {
    var callState by remember { mutableStateOf(CallState.CONNECTING) }
    var callDuration by remember { mutableStateOf(0) }
    var isMuted by remember { mutableStateOf(false) }
    var isVideoEnabled by remember { mutableStateOf(isVideo) }
    var isSpeakerphone by remember { mutableStateOf(isVideo) }
    var networkQuality by remember { mutableStateOf("Excellent") }
    var networkColor by remember { mutableStateOf(Color.Green) }

    LaunchedEffect(Unit) {
        // Simulate signaling process
        delay(1500)
        callState = CallState.RINGING
        delay(3000)
        callState = CallState.CONNECTED
    }

    LaunchedEffect(callState) {
        if (callState == CallState.CONNECTED) {
            while (true) {
                delay(1000)
                callDuration++
                // Simulate network conditions
                if (Random.nextFloat() > 0.85f) {
                    val states = listOf("Excellent" to Color.Green, "Good" to Color.Green, "Fair" to Color.Yellow, "Poor" to Color.Red)
                    val pick = states.random()
                    networkQuality = pick.first
                    networkColor = pick.second
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Video Preview
        if (isVideoEnabled) {
            CameraPreview(modifier = Modifier.fillMaxSize())
        }

        // Overlay Content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(48.dp))
            
            // E2E Encrypted indicator
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .background(Color.DarkGray.copy(alpha = 0.6f), CircleShape)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Icon(Icons.Filled.Lock, contentDescription = "E2E Encrypted", tint = Color.Green, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("End-to-End Encrypted", color = Color.White, style = MaterialTheme.typography.labelSmall)
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = chatTitle,
                color = Color.White,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            val statusText = when (callState) {
                CallState.CONNECTING -> "Connecting..."
                CallState.RINGING -> "Ringing..."
                CallState.CONNECTED -> formatDuration(callDuration)
                CallState.ENDED -> "Call Ended"
            }
            Text(
                text = statusText,
                color = Color.LightGray,
                style = MaterialTheme.typography.titleMedium
            )

            if (callState == CallState.CONNECTED) {
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .background(Color.DarkGray.copy(alpha = 0.6f), CircleShape)
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Filled.SignalCellularAlt, contentDescription = "Network", tint = networkColor, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Connection: $networkQuality", color = Color.White, style = MaterialTheme.typography.labelSmall)
                }
            }

            Spacer(modifier = Modifier.weight(1f))
            
            // Call Controls
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                FloatingActionButton(
                    onClick = { isSpeakerphone = !isSpeakerphone },
                    containerColor = if (isSpeakerphone) Color.White else Color.DarkGray,
                    contentColor = if (isSpeakerphone) Color.Black else Color.White
                ) {
                    Icon(Icons.Filled.VolumeUp, contentDescription = "Speaker")
                }

                FloatingActionButton(
                    onClick = { isMuted = !isMuted },
                    containerColor = if (isMuted) Color.White else Color.DarkGray,
                    contentColor = if (isMuted) Color.Black else Color.White
                ) {
                    Icon(if (isMuted) Icons.Filled.MicOff else Icons.Filled.Mic, contentDescription = "Mute")
                }
                
                if (isVideo) {
                    FloatingActionButton(
                        onClick = { isVideoEnabled = !isVideoEnabled },
                        containerColor = if (!isVideoEnabled) Color.White else Color.DarkGray,
                        contentColor = if (!isVideoEnabled) Color.Black else Color.White
                    ) {
                        Icon(if (!isVideoEnabled) Icons.Filled.VideocamOff else Icons.Filled.Videocam, contentDescription = "Video")
                    }
                    FloatingActionButton(
                        onClick = { /* Switch camera */ },
                        containerColor = Color.DarkGray,
                        contentColor = Color.White
                    ) {
                        Icon(Icons.Filled.Cameraswitch, contentDescription = "Switch Camera")
                    }
                }
                
                FloatingActionButton(
                    onClick = { 
                        callState = CallState.ENDED
                        onEndCall()
                    },
                    containerColor = Color.Red,
                    contentColor = Color.White
                ) {
                    Icon(Icons.Filled.CallEnd, contentDescription = "End Call")
                }
            }
        }
    }
}

fun formatDuration(seconds: Int): String {
    val mins = seconds / 60
    val secs = seconds % 60
    return String.format("%02d:%02d", mins, secs)
}

@Composable
fun CameraPreview(modifier: Modifier = Modifier) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            val previewView = PreviewView(ctx).apply {
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            }
            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
            
            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
                
                val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
                
                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview
                    )
                } catch (exc: Exception) {
                    // Handle exception
                }
            }, ContextCompat.getMainExecutor(ctx))
            
            previewView
        }
    )
}


@androidx.compose.material3.ExperimentalMaterial3Api
@Composable
fun CallsListScreen(navController: NavController) {
    androidx.compose.material3.Scaffold(
        topBar = {
            androidx.compose.material3.TopAppBar(
                title = { androidx.compose.material3.Text("Calls") },
                navigationIcon = {
                    androidx.compose.material3.IconButton(onClick = { navController.popBackStack() }) {
                        androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        androidx.compose.foundation.layout.Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = androidx.compose.ui.Alignment.Center) {
            androidx.compose.material3.Text("No recent calls", style = androidx.compose.material3.MaterialTheme.typography.bodyLarge, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
