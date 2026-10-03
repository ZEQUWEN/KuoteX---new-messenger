package com.example.ui.qr

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.ui.AppViewModel
import com.example.ui.Contact
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import java.util.concurrent.Executors

data class ScannedContactInfo(
    val rawValue: String,
    val displayName: String,
    val username: String,
    val phoneNumber: String?
)

fun parseQrCodeToContact(raw: String): ScannedContactInfo {
    val trimmed = raw.trim()
    return when {
        trimmed.startsWith("https://t.me/") || trimmed.startsWith("http://t.me/") -> {
            val username = trimmed.substringAfter("t.me/").substringBefore("/").substringBefore("?").removePrefix("@")
            ScannedContactInfo(
                rawValue = trimmed,
                displayName = "@$username",
                username = username,
                phoneNumber = null
            )
        }
        trimmed.startsWith("tg://resolve?domain=") -> {
            val username = trimmed.substringAfter("domain=").substringBefore("&").removePrefix("@")
            ScannedContactInfo(
                rawValue = trimmed,
                displayName = "@$username",
                username = username,
                phoneNumber = null
            )
        }
        trimmed.startsWith("tel:") -> {
            val phone = trimmed.removePrefix("tel:")
            ScannedContactInfo(
                rawValue = trimmed,
                displayName = phone,
                username = phone.replace("+", ""),
                phoneNumber = phone
            )
        }
        trimmed.startsWith("+") && trimmed.length in 7..16 -> {
            ScannedContactInfo(
                rawValue = trimmed,
                displayName = trimmed,
                username = trimmed.replace("+", ""),
                phoneNumber = trimmed
            )
        }
        trimmed.startsWith("@") -> {
            val username = trimmed.removePrefix("@")
            ScannedContactInfo(
                rawValue = trimmed,
                displayName = "@$username",
                username = username,
                phoneNumber = null
            )
        }
        else -> {
            val clean = trimmed.take(30)
            ScannedContactInfo(
                rawValue = trimmed,
                displayName = if (clean.startsWith("@")) clean else "@${clean.removePrefix("@")}",
                username = clean.removePrefix("@"),
                phoneNumber = null
            )
        }
    }
}

/**
 * QR Code Scanner View with CameraX, Real-Time ZXing Image Analysis,
 * Flashlight control, Gallery picker fallback, and instant Contact Addition.
 */
@Composable
fun QrCodeScannerView(
    viewModel: AppViewModel,
    modifier: Modifier = Modifier,
    onContactAdded: ((Contact) -> Unit)? = null,
    onCloseScanner: () -> Unit
) {
    val context = LocalContext.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
        if (!isGranted) {
            Toast.makeText(context, "Доступ к камере необходим для сканирования QR-кода", Toast.LENGTH_SHORT).show()
        }
    }

    var scannedResult by remember { mutableStateOf<ScannedContactInfo?>(null) }
    var isTorchEnabled by remember { mutableStateOf(false) }
    var cameraControlRef by remember { mutableStateOf<Camera?>(null) }
    var isAddedSuccessfully by remember { mutableStateOf(false) }

    // Haptic feedback trigger
    fun vibrateOnSuccess() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator?.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                vibrator?.vibrate(100)
            }
        } catch (_: Exception) {}
    }

    // Gallery Picker contract to scan QR from screenshot
    val pickImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                inputStream?.close()
                if (bitmap != null) {
                    val intArray = IntArray(bitmap.width * bitmap.height)
                    bitmap.getPixels(intArray, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                    val source = RGBLuminanceSource(bitmap.width, bitmap.height, intArray)
                    val binaryBitmap = BinaryBitmap(HybridBinarizer(source))
                    val reader = MultiFormatReader().apply {
                        setHints(mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE)))
                    }
                    val result = reader.decode(binaryBitmap)
                    if (result != null && result.text.isNotBlank()) {
                        vibrateOnSuccess()
                        scannedResult = parseQrCodeToContact(result.text)
                    } else {
                        Toast.makeText(context, "QR-код на изображении не найден", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Не удалось распознать QR-код из галереи", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A))
    ) {
        if (!hasCameraPermission) {
            // Permission request UI
            CameraPermissionRationaleCard(
                onRequestPermission = {
                    permissionLauncher.launch(Manifest.permission.CAMERA)
                },
                onPickFromGallery = {
                    pickImageLauncher.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
            )
        } else {
            // Live Camera Preview
            val lifecycleOwner = LocalLifecycleOwner.current
            val executor = remember { Executors.newSingleThreadExecutor() }
            var cameraProviderRef by remember { mutableStateOf<ProcessCameraProvider?>(null) }

            DisposableEffect(lifecycleOwner) {
                onDispose {
                    try {
                        cameraControlRef?.cameraControl?.enableTorch(false)
                        cameraProviderRef?.unbindAll()
                    } catch (_: Exception) {}
                    try {
                        executor.shutdown()
                    } catch (_: Exception) {}
                }
            }

            AndroidView(
                factory = { ctx ->
                    val previewView = PreviewView(ctx).apply {
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                    }
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)

                    cameraProviderFuture.addListener({
                        try {
                            val cameraProvider = cameraProviderFuture.get()
                            cameraProviderRef = cameraProvider
                            val preview = Preview.Builder().build().also {
                                it.setSurfaceProvider(previewView.surfaceProvider)
                            }

                            val imageAnalysis = ImageAnalysis.Builder()
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .build()

                            imageAnalysis.setAnalyzer(executor) { imageProxy ->
                                if (scannedResult == null) {
                                    val decodedText = decodeQrFromImageProxy(imageProxy)
                                    if (!decodedText.isNullOrBlank()) {
                                        previewView.post {
                                            if (scannedResult == null) {
                                                vibrateOnSuccess()
                                                scannedResult = parseQrCodeToContact(decodedText)
                                            }
                                        }
                                    }
                                }
                                imageProxy.close()
                            }

                            cameraProvider.unbindAll()
                            val camera = cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                CameraSelector.DEFAULT_BACK_CAMERA,
                                preview,
                                imageAnalysis
                            )
                            cameraControlRef = camera
                        } catch (e: Exception) {
                            previewView.post {
                                Toast.makeText(ctx, "Ошибка запуска камеры: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }, ContextCompat.getMainExecutor(ctx))

                    previewView
                },
                onRelease = {
                    try {
                        cameraProviderRef?.unbindAll()
                    } catch (_: Exception) {}
                },
                modifier = Modifier.fillMaxSize()
            )

            // Viewfinder Overlay (Dimmed background + Transparent center + Glowing corners & Laser)
            QrViewfinderOverlay(modifier = Modifier.fillMaxSize())

            // Top Bar Controls (Torch & Gallery)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Flashlight Button
                IconButton(
                    onClick = {
                        isTorchEnabled = !isTorchEnabled
                        cameraControlRef?.cameraControl?.enableTorch(isTorchEnabled)
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                ) {
                    Icon(
                        imageVector = if (isTorchEnabled) Icons.Filled.FlashOn else Icons.Filled.FlashOff,
                        contentDescription = "Фонарик",
                        tint = if (isTorchEnabled) Color(0xFFFFD54F) else Color.White
                    )
                }

                Text(
                    text = "Сканирование QR-кода",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                // Pick from Gallery
                IconButton(
                    onClick = {
                        pickImageLauncher.launch(
                            androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Image,
                        contentDescription = "Выбрать из галереи",
                        tint = Color.White
                    )
                }
            }

            // Bottom Instructions
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 32.dp, start = 24.dp, end = 24.dp)
                    .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(16.dp))
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Text(
                    text = "Наведите камеру на QR-код профиля пользователя для быстрого добавления в контакты",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
            }
        }

        // Contact Found Bottom Dialog
        scannedResult?.let { contactInfo ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.75f)),
                contentAlignment = Alignment.BottomCenter
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(16.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1B2E)),
                    border = BorderStroke(1.dp, Color(0xFF8B5CF6).copy(alpha = 0.4f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Success Badge
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .background(
                                    Brush.radialGradient(
                                        listOf(Color(0xFF34D399).copy(alpha = 0.3f), Color.Transparent)
                                    ),
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(Color(0xFF10B981), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isAddedSuccessfully) Icons.Filled.Check else Icons.Filled.PersonAdd,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = if (isAddedSuccessfully) "Контакт добавлен!" else "Пользователь найден!",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = contactInfo.displayName,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF38BDF8)
                        )

                        if (!contactInfo.phoneNumber.isNullOrBlank()) {
                            Text(
                                text = contactInfo.phoneNumber,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.LightGray
                            )
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // Action Buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Rescan Button
                            OutlinedButton(
                                onClick = {
                                    scannedResult = null
                                    isAddedSuccessfully = false
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp),
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                            ) {
                                Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Повтор", fontSize = 13.sp)
                            }

                            // Add to Contacts Button
                            Button(
                                onClick = {
                                    viewModel.addContact(
                                        name = contactInfo.displayName.removePrefix("@"),
                                        phoneNumberOrUsername = contactInfo.phoneNumber ?: contactInfo.username
                                    ) { newContact ->
                                        isAddedSuccessfully = true
                                        onContactAdded?.invoke(newContact)
                                        Toast.makeText(context, "Контакт ${contactInfo.displayName} добавлен в телефонную книгу!", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier
                                    .weight(1.5f)
                                    .height(48.dp),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isAddedSuccessfully) Color(0xFF10B981) else Color(0xFF8B5CF6)
                                )
                            ) {
                                Icon(
                                    imageVector = if (isAddedSuccessfully) Icons.Filled.Check else Icons.Filled.PersonAdd,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (isAddedSuccessfully) "Добавлен ✓" else "В контакты",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Done / Close Button
                        Button(
                            onClick = {
                                scannedResult = null
                                isAddedSuccessfully = false
                                onCloseScanner()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2D2A40))
                        ) {
                            Text("Закрыть", color = Color.White, fontSize = 14.sp)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Camera Permission Rationale Card when CAMERA permission is not yet granted
 */
@Composable
private fun CameraPermissionRationaleCard(
    onRequestPermission: () -> Unit,
    onPickFromGallery: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1B2E)),
            border = BorderStroke(1.dp, Color(0xFF8B5CF6).copy(alpha = 0.35f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .background(
                            Brush.radialGradient(
                                listOf(Color(0xFF8B5CF6).copy(alpha = 0.35f), Color.Transparent)
                            ),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .background(Color(0xFF8B5CF6), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CameraAlt,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Требуется доступ к камере",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Чтобы сканировать QR-коды профилей и мгновенно добавлять пользователей в контакты, разрешите приложению использовать камеру устройства.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF94A3B8),
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = onRequestPermission,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B5CF6))
                ) {
                    Icon(Icons.Filled.QrCodeScanner, contentDescription = null, tint = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Разрешить доступ к камере", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedButton(
                    onClick = onPickFromGallery,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
                ) {
                    Icon(Icons.Filled.Image, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Выбрать фото из галереи", color = Color.White, fontSize = 13.sp)
                }
            }
        }
    }
}

/**
 * Animated Viewfinder Overlay with Darkened Cutout, Glowing Corner Brackets,
 * and Moving Laser Line.
 */
@Composable
private fun QrViewfinderOverlay(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "laser_transition")
    val laserProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "laser_progress"
    )

    Canvas(modifier = modifier) {
        val canvasWidth = size.width
        val canvasHeight = size.height

        val scanBoxSize = 250.dp.toPx()
        val left = (canvasWidth - scanBoxSize) / 2f
        val top = (canvasHeight - scanBoxSize) / 2.3f
        val right = left + scanBoxSize
        val bottom = top + scanBoxSize

        // Dim outer area
        val overlayPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.argb(160, 0, 0, 0)
        }
        drawContext.canvas.nativeCanvas.drawRect(0f, 0f, canvasWidth, canvasHeight, overlayPaint)

        // Clear scanning box
        val clearPaint = android.graphics.Paint().apply {
            xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.CLEAR)
        }
        drawContext.canvas.nativeCanvas.drawRoundRect(
            left, top, right, bottom,
            24.dp.toPx(), 24.dp.toPx(),
            clearPaint
        )

        // Corner Brackets
        val cornerLength = 28.dp.toPx()
        val cornerStroke = 4.dp.toPx()
        val cornerColor = androidx.compose.ui.graphics.Color(0xFF8B5CF6)

        // Top Left
        drawLine(cornerColor, Offset(left, top + cornerLength), Offset(left, top), cornerStroke)
        drawLine(cornerColor, Offset(left, top), Offset(left + cornerLength, top), cornerStroke)

        // Top Right
        drawLine(cornerColor, Offset(right - cornerLength, top), Offset(right, top), cornerStroke)
        drawLine(cornerColor, Offset(right, top), Offset(right, top + cornerLength), cornerStroke)

        // Bottom Left
        drawLine(cornerColor, Offset(left, bottom - cornerLength), Offset(left, bottom), cornerStroke)
        drawLine(cornerColor, Offset(left, bottom), Offset(left + cornerLength, bottom), cornerStroke)

        // Bottom Right
        drawLine(cornerColor, Offset(right - cornerLength, bottom), Offset(right, bottom), cornerStroke)
        drawLine(cornerColor, Offset(right, bottom), Offset(right, bottom - cornerLength), cornerStroke)

        // Animated Laser Line
        val laserY = top + scanBoxSize * laserProgress
        drawLine(
            brush = Brush.horizontalGradient(
                colors = listOf(
                    Color.Transparent,
                    Color(0xFF38BDF8),
                    Color(0xFF8B5CF6),
                    Color(0xFF38BDF8),
                    Color.Transparent
                )
            ),
            start = Offset(left + 8.dp.toPx(), laserY),
            end = Offset(right - 8.dp.toPx(), laserY),
            strokeWidth = 3.dp.toPx()
        )
    }
}

/**
 * Decodes QR code from CameraX ImageProxy using ZXing PlanarYUVLuminanceSource
 */
private fun decodeQrFromImageProxy(imageProxy: ImageProxy): String? {
    val plane = imageProxy.planes[0]
    val buffer = plane.buffer
    val rowStride = plane.rowStride
    val width = imageProxy.width
    val height = imageProxy.height

    val bytes = if (rowStride == width) {
        val data = ByteArray(buffer.remaining())
        buffer.get(data)
        data
    } else {
        val data = ByteArray(width * height)
        var offset = 0
        for (row in 0 until height) {
            buffer.position(row * rowStride)
            buffer.get(data, offset, width)
            offset += width
        }
        data
    }

    val source = PlanarYUVLuminanceSource(
        bytes,
        width,
        height,
        0,
        0,
        width,
        height,
        false
    )
    val binaryBitmap = BinaryBitmap(HybridBinarizer(source))
    val reader = MultiFormatReader().apply {
        setHints(mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE)))
    }

    return try {
        reader.decode(binaryBitmap)?.text
    } catch (_: Exception) {
        null
    }
}
