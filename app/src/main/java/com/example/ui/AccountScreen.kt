package com.example.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*

import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.CircularProgressIndicator

import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.koin.androidx.compose.koinViewModel
import androidx.lifecycle.viewmodel.compose.viewModel

import coil.compose.AsyncImage
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import androidx.compose.ui.layout.ContentScale
import android.net.Uri




fun Modifier.shimmerEffect(): Modifier = composed {
    val transition = rememberInfiniteTransition(label = "")
    val alpha by transition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = ""
    )
    background(color = Color.Gray.copy(alpha = alpha))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountScreen(
    onBack: () -> Unit,
    appViewModel: com.example.ui.AppViewModel,
    viewModel: AccountViewModel = koinViewModel()
) {
    LaunchedEffect(appViewModel.repository) {
        viewModel.setRepository(appViewModel.repository)
    }
    val state by viewModel.uiState.collectAsState()
    
    var firstName by remember(state.firstName) { mutableStateOf(state.firstName) }
    var lastName by remember(state.lastName) { mutableStateOf(state.lastName) }
    var bio by remember(state.bio) { mutableStateOf(state.bio) }
    var socialTelegram by remember(state.socialLinks) { mutableStateOf(state.socialLinks["telegram"] ?: "") }
    var socialGithub by remember(state.socialLinks) { mutableStateOf(state.socialLinks["github"] ?: "") }
    
    var showDatePicker by remember { mutableStateOf(false) }
    var showPhoneChange by remember { mutableStateOf(false) }
    var showUsernameChange by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    
    val activeAccount = com.example.ui.LocalActiveAccount.current

    LaunchedEffect(activeAccount) {
        if (state.isInitialLoading) {
            viewModel.initialize(activeAccount)
        }
    }

    LaunchedEffect(state.isSuccess) {
        if (state.isSuccess) {
            // Sync with global AppViewModel profile FIRST so it doesn't get cancelled by snackbar suspend
            if (activeAccount != null) {
                appViewModel.updateProfile(
                    id = activeAccount.id,
                    username = "@" + state.username.removePrefix("@"),
                    displayName = state.firstName + if (state.lastName.isNotBlank()) " ${state.lastName}" else "",
                    bio = state.bio,
                    profilePicUrl = state.avatarUrl ?: activeAccount.profilePicUrl
                )
            }
            viewModel.resetSuccessFlag()
            snackbarHostState.showSnackbar("Изменения успешно сохранены")
        }
    }
    
    LaunchedEffect(state.error) {
        if (state.error != null) {
            snackbarHostState.showSnackbar(state.error ?: "Ошибка")
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Аккаунт") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Назад")
                    }
                },
                actions = {
                    val hasChanges = viewModel.hasUnsavedChanges(
                        firstName, lastName, bio,
                        mapOf("telegram" to socialTelegram, "github" to socialGithub)
                    )
                    
                    if (!state.isInitialLoading) {
                        IconButton(
                            onClick = { 
                                viewModel.updateProfileData(
                                    firstName, lastName, bio,
                                    mapOf("telegram" to socialTelegram, "github" to socialGithub)
                                )
                            },
                            enabled = hasChanges && !state.isLoading
                        ) {
                            if (state.isLoading) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.Check, "Сохранить", tint = if (hasChanges) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f))
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        if (state.isInitialLoading) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding)
            ) {
                item { Spacer(Modifier.height(24.dp)) }
                item { Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { Box(modifier = Modifier.size(100.dp).clip(CircleShape).shimmerEffect()) } }
                item { Spacer(Modifier.height(32.dp)) }
                items(3) {
                    Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).height(120.dp).clip(RoundedCornerShape(16.dp)).shimmerEffect())
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                item { Spacer(Modifier.height(16.dp)) }
                
                // Avatar section
                item {
                    var showCropDialog by remember { mutableStateOf<Uri?>(null) }
                    val photoPickerLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.PickVisualMedia(),
                        onResult = { uri ->
                            if (uri != null) {
                                showCropDialog = uri
                            }
                        }
                    )
                    
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Box(
                            modifier = Modifier
                                .size(100.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer)
                                .clickable { 
                                    photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (state.avatarUrl != null) {
                                AsyncImage(
                                    model = state.avatarUrl,
                                    contentDescription = "Avatar",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Icon(Icons.Default.AddAPhoto, contentDescription = "Add Avatar", tint = MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                        }
                    }
                    
                    if (showCropDialog != null) {
                        AlertDialog(
                            onDismissRequest = { showCropDialog = null },
                            title = { Text("Обрезка фото") },
                            text = { 
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("Настройте область видимости фото профиля.", style = MaterialTheme.typography.bodyMedium)
                                    Spacer(Modifier.height(16.dp))
                                    Box(modifier = Modifier.size(200.dp).clip(CircleShape).background(Color.Black)) {
                                        AsyncImage(
                                            model = showCropDialog,
                                            contentDescription = "Crop",
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                        // Mock crop overlay
                                        Box(modifier = Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.2f)))
                                    }
                                }
                            },
                            confirmButton = {
                                Button(onClick = {
                                    viewModel.updateAvatar(showCropDialog.toString())
                                    showCropDialog = null
                                }) {
                                    Text("Готово")
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { showCropDialog = null }) {
                                    Text("Отмена")
                                }
                            }
                        )
                    }
                }
                
                item { Spacer(Modifier.height(24.dp)) }

                // Name section
                item {
                    SectionGroup(title = "Ваше имя") {
                        CustomTextField(
                            value = firstName,
                            onValueChange = { firstName = it },
                            placeholder = "Имя"
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                        CustomTextField(
                            value = lastName,
                            onValueChange = { lastName = it },
                            placeholder = "Фамилия"
                        )
                    }
                }
                
                item { Spacer(Modifier.height(16.dp)) }

                // Bio section
                item {
                    SectionGroup(
                        title = "О себе", 
                        footer = "Напишите немного о себе."
                    ) {
                        Box(modifier = Modifier.fillMaxWidth()) {
                            CustomTextField(
                                value = bio,
                                onValueChange = { if (it.length <= 70) bio = it },
                                placeholder = "О себе"
                            )
                            Text(
                                text = "${70 - bio.length}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .align(Alignment.CenterEnd)
                                    .padding(end = 16.dp)
                            )
                        }
                    }
                }
                
                item { Spacer(Modifier.height(16.dp)) }

                // Social Links
                item {
                    SectionGroup(title = "Социальные сети", footer = "Ссылки на ваши профили.") {
                        CustomTextField(
                            value = socialTelegram,
                            onValueChange = { socialTelegram = it },
                            placeholder = "Telegram (https://t.me/...)"
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                        CustomTextField(
                            value = socialGithub,
                            onValueChange = { socialGithub = it },
                            placeholder = "GitHub (https://github.com/...)"
                        )
                    }
                }

                item { Spacer(Modifier.height(16.dp)) }

                // Info section
                item {
                    SectionGroup(title = "Информация о Вас") {
                        InfoRow(
                            icon = Icons.Default.Phone,
                            iconTint = Color(0xFF4CAF50),
                            title = state.phone,
                            subtitle = "Нажмите, чтобы изменить номер телефона",
                            onClick = { showPhoneChange = true }
                        )
                        HorizontalDivider(modifier = Modifier.padding(start = 56.dp, end = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                        InfoRow(
                            icon = Icons.Default.AlternateEmail,
                            iconTint = Color(0xFFFF9800),
                            title = if (state.username.isNotBlank()) "@${state.username.removePrefix("@")}" else "Не задано",
                            subtitle = "Имя пользователя",
                            onClick = { showUsernameChange = true }
                        )
                        HorizontalDivider(modifier = Modifier.padding(start = 56.dp, end = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                        InfoRow(
                            icon = Icons.Default.Cake,
                            iconTint = Color(0xFF2196F3),
                            title = state.birthDate.takeIf { it.isNotBlank() } ?: "Укажите дату рождения",
                            subtitle = "День рождения",
                            onClick = { showDatePicker = !showDatePicker }
                        )
                    }
                }
                
                // Date Picker Animated Visibility
                item {
                    AnimatedVisibility(
                        visible = showDatePicker,
                        enter = expandVertically(animationSpec = tween(300)) + fadeIn(animationSpec = tween(300)),
                        exit = shrinkVertically(animationSpec = tween(300)) + fadeOut(animationSpec = tween(300))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(16.dp)
                        ) {
                            Text("Укажите свой день рождения", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                            Spacer(Modifier.height(16.dp))
                            
                            var selectedDate by remember { mutableStateOf(state.birthDate) }
                            
                            WheelDatePicker(
                                initialDate = state.birthDate,
                                onDateSelected = { selectedDate = it }
                            )
                            
                            Spacer(Modifier.height(16.dp))
                            Button(
                                onClick = { 
                                    viewModel.updateBirthDate(selectedDate)
                                    showDatePicker = false 
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Сохранить")
                            }
                        }
                    }
                }
                
                item { Spacer(Modifier.height(32.dp)) }
                
                // Delete Account
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .clickable { showDeleteConfirm = true },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                    ) {
                        Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                            Text("Удалить учетную запись", color = MaterialTheme.colorScheme.onErrorContainer, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                
                item { Spacer(Modifier.height(32.dp)) }
            }
        }
    }

    if (showPhoneChange) {
        ChangePhoneDialog(
            viewModel = viewModel,
            onDismiss = { showPhoneChange = false }
        )
    }
    
    if (showUsernameChange) {
        ChangeUsernameDialog(
            viewModel = viewModel,
            currentUsername = state.username,
            onDismiss = { showUsernameChange = false }
        )
    }
    
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Удаление аккаунта") },
            text = { Text("Вы уверены, что хотите окончательно удалить свою учетную запись и все связанные с ней данные профиля? Это действие нельзя отменить.") },
            confirmButton = {
                Button(
                    onClick = { 
                        viewModel.deleteAccount(
                            onSuccess = { onBack() },
                            onError = { }
                        )
                        showDeleteConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Удалить")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Отмена")
                }
            }
        )
    }
}

@Composable
fun SectionGroup(
    title: String? = null,
    footer: String? = null,
    content: @Composable () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        if (title != null) {
            Text(
                text = title,
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp)
            )
        }
        
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                content()
            }
        }
        
        if (footer != null) {
            Text(
                text = footer,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp)
            )
        }
    }
}

@Composable
fun CustomTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(placeholder) },
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent
        ),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
fun InfoRow(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(28.dp)
        )
        Spacer(Modifier.width(16.dp))
        Column {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun ChangePhoneDialog(
    viewModel: AccountViewModel,
    onDismiss: () -> Unit
) {
    var step by remember { mutableStateOf(1) } // 1 = Phone input, 2 = OTP input
    var newPhone by remember { mutableStateOf("") }
    var otp by remember { mutableStateOf("") }
    var method by remember { mutableStateOf(OtpType.SMS) }
    var error by remember { mutableStateOf<String?>(null) }
    
    val state by viewModel.uiState.collectAsState()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (step == 1) "Новый номер" else "Код подтверждения") },
        text = {
            Column {
                if (step == 1) {
                    Text("На Ваш новый номер поступит звонок или SMS с кодом подтверждения.")
                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(
                        value = newPhone,
                        onValueChange = { newPhone = it },
                        label = { Text("Номер телефона") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = method == OtpType.SMS, onClick = { method = OtpType.SMS })
                        Text("SMS")
                        Spacer(Modifier.width(16.dp))
                        RadioButton(selected = method == OtpType.EMAIL, onClick = { method = OtpType.EMAIL })
                        Text("Email")
                    }
                } else {
                    Text("Введите 6-значный код.")
                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(
                        value = otp,
                        onValueChange = { if (it.length <= 6) otp = it },
                        label = { Text("Код") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                
                if (error != null) {
                    Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
                }
                if (state.isLoading) {
                    CircularProgressIndicator(modifier = Modifier.padding(top = 8.dp).align(Alignment.CenterHorizontally))
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    error = null
                    if (step == 1) {
                        if (method == OtpType.SMS) {
                            viewModel.requestPhoneChange(newPhone, onCodeSent = { step = 2 }, onError = { error = it })
                        } else {
                            viewModel.requestEmailChange(newPhone, onCodeSent = { step = 2 }, onError = { error = it })
                        }
                    } else {
                        viewModel.verifyOtp(otp, method, newPhone, onSuccess = { onDismiss() }, onError = { error = it })
                    }
                },
                enabled = !state.isLoading
            ) {
                Text("Далее")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !state.isLoading) {
                Text("Отмена")
            }
        }
    )
}

@Composable
fun ChangeUsernameDialog(
    viewModel: AccountViewModel,
    currentUsername: String,
    onDismiss: () -> Unit
) {
    var username by remember { mutableStateOf(currentUsername.removePrefix("@")) }
    var error by remember { mutableStateOf<String?>(null) }
    
    val available by viewModel.usernameAvailable.collectAsState()
    
    LaunchedEffect(username) {
        viewModel.checkUsername(username)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Имя пользователя") },
        text = {
            Column {
                Text("Вы можете выбрать публичное имя пользователя в Telegram. В этом случае другие люди смогут найти Вас по такому имени.")
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = username,
                    onValueChange = { 
                        username = it
                        if (it.contains("@")) {
                            error = "Символ @ не требуется."
                        } else {
                            error = null
                        }
                    },
                    label = { Text("t.me/") },
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(Modifier.height(8.dp))
                if (username.length < 5) {
                    Text("Минимальная длина — 5 символов.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                } else if (available == true) {
                    Text("Имя пользователя свободно.", color = Color(0xFF4CAF50), style = MaterialTheme.typography.bodySmall)
                } else if (available == false) {
                    Text("Это имя пользователя уже занято.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                if (error != null) {
                    Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    error = null
                    val cleanUsername = username.replace("@", "")
                    viewModel.saveUsername(cleanUsername, onSuccess = { onDismiss() }, onError = { error = it })
                },
                enabled = available == true && error == null
            ) {
                Text("Сохранить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        }
    )
}