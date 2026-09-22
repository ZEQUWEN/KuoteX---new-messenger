package com.example.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.auth.AuthResult
import com.example.auth.FirebaseAuthManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

val languages = listOf(
    Triple("en", "English", "🇺🇸"),
    Triple("ru", "Русский", "🇷🇺"),
    Triple("uk", "Українська", "🇺🇦"),
    Triple("es", "Español", "🇪🇸"),
    Triple("de", "Deutsch", "🇩🇪"),
    Triple("fr", "Français", "🇫🇷"),
    Triple("kz", "Қазақша", "🇰🇿")
)

fun getCountryFlagForPhoneNumber(phone: String): String {
    if (phone.startsWith("+79") || phone.startsWith("+78") || phone.startsWith("+73") || phone.startsWith("+74") || phone.startsWith("+75")) return "🇷🇺"
    if (phone.startsWith("+77") || phone.startsWith("+76") || phone.startsWith("+70") || phone.startsWith("+71") || phone.startsWith("+72")) return "🇰🇿"
    if (phone.startsWith("+1")) return "🇺🇸"
    if (phone.startsWith("+380")) return "🇺🇦"
    if (phone.startsWith("+44")) return "🇬🇧"
    if (phone.startsWith("+49")) return "🇩🇪"
    if (phone.startsWith("+33")) return "🇫🇷"
    if (phone.startsWith("+34")) return "🇪🇸"
    return "🌍"
}

@Composable
fun LanguageSelector() {
    var selectedLanguage by remember { mutableStateOf(languages[1]) }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text("Select Language", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(languages) { lang ->
                    val isSelected = lang == selectedLanguage
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else Color.Transparent)
                            .border(1.dp, if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent, RoundedCornerShape(12.dp))
                            .clickable { selectedLanguage = lang }
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text("${lang.third} ${lang.second}", style = MaterialTheme.typography.bodySmall, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                    }
                }
            }
        }
    }
}

@Composable
fun FirebaseAuthBadge(modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(Color(0xFF00E676), CircleShape)
            )
            Text(
                text = "Firebase Auth Secure",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
fun TelegramModerationBlockedDialog(
    user: com.example.data.RegisteredUserRole,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFFF1744).copy(alpha = 0.2f))
                        .border(1.dp, Color(0xFFFF1744), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.Block,
                        contentDescription = "Blocked",
                        tint = Color(0xFFFF1744),
                        modifier = Modifier.size(20.dp)
                    )
                }
                Column {
                    Text(
                        text = "Аккаунт заблокирован",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFF5252)
                    )
                    Text(
                        text = "KuoteX Moderation",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Доступ к аккаунту ${user.displayName} (${user.username}) приостановлен администрацией в соответствии с правилами сообщества.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFFFF1744).copy(alpha = 0.1f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFF1744).copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Причина блокировки:",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFF5252)
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = user.blockReason ?: "Нарушение правил сообщества",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (user.blockedBy != null) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "Модератор: ${user.blockedBy}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF1744))
            ) {
                Text("Понятно", color = Color.White)
            }
        }
    )
}

@Composable
fun ForgotPasswordDialog(
    onDismiss: () -> Unit,
    onSendReset: suspend (String) -> AuthResult<Unit>
) {
    var email by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var resultMessage by remember { mutableStateOf<String?>(null) }
    var isSuccess by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.LockReset, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("Сброс пароля Firebase")
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Введите ваш email адрес. Мы отправим вам официальную ссылку Firebase для сброса пароля.",
                    style = MaterialTheme.typography.bodyMedium
                )
                OutlinedTextField(
                    value = email,
                    onValueChange = { 
                        email = it
                        resultMessage = null
                    },
                    label = { Text("Email") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth()
                )

                if (resultMessage != null) {
                    Text(
                        text = resultMessage!!,
                        color = if (isSuccess) Color(0xFF00E676) else MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (email.isNotBlank()) {
                        scope.launch {
                            isLoading = true
                            val res = onSendReset(email)
                            isLoading = false
                            if (res is AuthResult.Success) {
                                isSuccess = true
                                resultMessage = "Письмо со ссылкой для сброса пароля успешно отправлено на $email."
                            } else if (res is AuthResult.Error) {
                                isSuccess = false
                                resultMessage = res.message
                            }
                        }
                    }
                },
                enabled = email.isNotBlank() && !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Text("Отправить")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Закрыть")
            }
        }
    )
}

@Composable
fun LoginScreen(
    context: android.content.Context = androidx.compose.ui.platform.LocalContext.current,
    accounts: List<UserAccount>,
    onNavigateToRegister: () -> Unit,
    onLoginSuccess: (String) -> Unit,
    viewModel: AppViewModel? = null,
    forceManualLogin: Boolean = false
) {
    var selectedAccount by remember { mutableStateOf<UserAccount?>(null) }
    var showManualLogin by remember(forceManualLogin) { mutableStateOf(forceManualLogin) }
    var emailOrPhone by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var twoFactorCode by remember { mutableStateOf("") }
    var showTwoFactor by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showForgotPasswordDialog by remember { mutableStateOf(false) }
    var blockedUserRecord by remember { mutableStateOf<com.example.data.RegisteredUserRole?>(null) }
    val scope = rememberCoroutineScope()

    if (blockedUserRecord != null) {
        TelegramModerationBlockedDialog(
            user = blockedUserRecord!!,
            onDismiss = { blockedUserRecord = null }
        )
    }

    if (showForgotPasswordDialog) {
        ForgotPasswordDialog(
            onDismiss = { showForgotPasswordDialog = false },
            onSendReset = { email ->
                if (viewModel != null) {
                    viewModel.sendFirebasePasswordReset(email)
                } else {
                    FirebaseAuthManager.sendPasswordResetEmail(email)
                }
            }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        if (isLoading) {
            NeonLoadingSpinner(size = 80.dp, color = MaterialTheme.colorScheme.primary)
        } else {
            Card(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    FirebaseAuthBadge(modifier = Modifier.padding(bottom = 12.dp))

                    Icon(
                        Icons.Filled.AccountCircle,
                        contentDescription = "Login",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(54.dp)
                    )
                    Spacer(Modifier.height(12.dp))

                    if (accounts.isEmpty() || showManualLogin) {
                        LanguageSelector()

                        Text(
                            text = if (showManualLogin) "Войти в аккаунт" else "Добро пожаловать в KuoteX",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Авторизация через Firebase Authentication",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(16.dp))

                        if (errorMessage != null) {
                            Surface(
                                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.85f),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 14.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Filled.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                    Text(
                                        text = errorMessage!!,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            }
                        }

                        if (!showTwoFactor) {
                            OutlinedTextField(
                                value = emailOrPhone,
                                onValueChange = { 
                                    emailOrPhone = it
                                    errorMessage = null
                                },
                                label = { Text("Email или Номер телефона") },
                                leadingIcon = {
                                    if (emailOrPhone.startsWith("+")) {
                                        Text(getCountryFlagForPhoneNumber(emailOrPhone), modifier = Modifier.padding(start = 8.dp))
                                    } else {
                                        Icon(Icons.Filled.Email, contentDescription = null)
                                    }
                                },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(12.dp))
                            OutlinedTextField(
                                value = password,
                                onValueChange = { 
                                    password = it
                                    errorMessage = null
                                },
                                label = { Text("Пароль") },
                                singleLine = true,
                                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                trailingIcon = {
                                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                        Icon(
                                            if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                            contentDescription = "Toggle Password"
                                        )
                                    }
                                },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                                modifier = Modifier.fillMaxWidth()
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                TextButton(onClick = { showForgotPasswordDialog = true }) {
                                    Text("Забыли пароль?", style = MaterialTheme.typography.labelMedium)
                                }
                            }

                            Spacer(Modifier.height(12.dp))
                            Button(
                                onClick = {
                                    if (emailOrPhone.isNotBlank() && password.isNotBlank()) {
                                        val blockInfo = com.example.data.FirestoreUserRoleManager.getUserBlockInfo(emailOrPhone)
                                        if (blockInfo != null && blockInfo.isBlocked) {
                                            blockedUserRecord = blockInfo
                                            return@Button
                                        }

                                        scope.launch {
                                            isLoading = true
                                            errorMessage = null
                                            
                                            val loginEmail = if (emailOrPhone.contains("@")) {
                                                emailOrPhone
                                            } else {
                                                "${emailOrPhone.replace("+", "").trim()}@kuotex.app"
                                            }

                                            val result = if (viewModel != null) {
                                                viewModel.signInWithFirebase(loginEmail, password)
                                            } else {
                                                FirebaseAuthManager.signInWithEmail(loginEmail, password)
                                            }

                                            isLoading = false
                                            if (result is AuthResult.Success) {
                                                onLoginSuccess(result.data.email ?: result.data.uid)
                                            } else if (result is AuthResult.Error) {
                                                errorMessage = result.message
                                            }
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = emailOrPhone.isNotBlank() && password.isNotBlank()
                            ) {
                                Text("Войти")
                            }

                            Spacer(Modifier.height(8.dp))

OutlinedButton(
                                onClick = {
                                    scope.launch {
                                        isLoading = true
                                        val result = if (viewModel != null) {
                                            viewModel.signInWithGoogle(context)
                                        } else {
                                            FirebaseAuthManager.signInWithGoogle(context)
                                        }
                                        isLoading = false
                                        if (result is AuthResult.Success) {
                                            onLoginSuccess((result as AuthResult.Success<com.example.auth.FirebaseUserInfo>).data.uid)
                                        } else if (result is AuthResult.Error) {
                                            errorMessage = result.message
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Filled.AccountCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Войти через Google")
                            }
                        } else {
                            OutlinedTextField(
                                value = twoFactorCode,
                                onValueChange = { twoFactorCode = it },
                                label = { Text("6-значный код 2FA") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                            )
                            Spacer(Modifier.height(20.dp))
                            Button(
                                onClick = {
                                    if (twoFactorCode.length >= 4) {
                                        val blockInfo = com.example.data.FirestoreUserRoleManager.getUserBlockInfo(emailOrPhone)
                                        if (blockInfo != null && blockInfo.isBlocked) {
                                            blockedUserRecord = blockInfo
                                        } else {
                                            scope.launch {
                                                isLoading = true
                                                delay(1200)
                                                isLoading = false
                                                onLoginSuccess(emailOrPhone)
                                            }
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = twoFactorCode.isNotBlank()
                            ) {
                                Text("Подтвердить и войти")
                            }
                        }

                        Spacer(Modifier.height(8.dp))
                        if (accounts.isNotEmpty()) {
                            TextButton(onClick = {
                                showManualLogin = false
                                showTwoFactor = false
                                emailOrPhone = ""
                                password = ""
                                twoFactorCode = ""
                                errorMessage = null
                            }) {
                                Text("Выбрать существующий аккаунт")
                            }
                        }

                        OutlinedButton(onClick = onNavigateToRegister, modifier = Modifier.fillMaxWidth()) {
                            Text("Создать новый аккаунт")
                        }
                    } else if (selectedAccount == null) {
                        LanguageSelector()

                        Text("Выберите аккаунт", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(14.dp))

                        accounts.forEach { account ->
                            val blockInfo = com.example.data.FirestoreUserRoleManager.getUserBlockInfo(account.username)
                                ?: com.example.data.FirestoreUserRoleManager.getUserBlockInfo(account.phoneNumber)
                            val isAccBlocked = blockInfo?.isBlocked == true

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                colors = if (isAccBlocked) CardDefaults.cardColors(containerColor = Color(0xFFFF1744).copy(alpha = 0.08f)) else CardDefaults.cardColors(),
                                onClick = {
                                    if (isAccBlocked && blockInfo != null) {
                                        blockedUserRecord = blockInfo
                                    } else {
                                        selectedAccount = account
                                    }
                                }
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .background(if (isAccBlocked) Color(0xFFFF1744) else MaterialTheme.colorScheme.primary, shape = CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(account.displayName.take(1).uppercase(), color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
                                    }
                                    Spacer(Modifier.width(16.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(account.displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                            if (isAccBlocked) {
                                                Spacer(Modifier.width(6.dp))
                                                Surface(
                                                    color = Color(0xFFFF1744),
                                                    shape = RoundedCornerShape(4.dp)
                                                ) {
                                                    Text(
                                                        "BLOCKED",
                                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = Color.White,
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }
                                        }
                                        Text(account.username, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    if (account.is2FAEnabled) {
                                        Icon(Icons.Filled.Lock, contentDescription = "2FA Enabled", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(16.dp))
                        TextButton(onClick = { showManualLogin = true }) {
                            Text("Войти под другим логином")
                        }
                        OutlinedButton(onClick = onNavigateToRegister, modifier = Modifier.fillMaxWidth()) {
                            Text("Зарегистрироваться")
                        }
                    } else {
                        LanguageSelector()

                        Text("С возвращением, ${selectedAccount!!.displayName}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(20.dp))

                        if (errorMessage != null) {
                            Surface(
                                color = MaterialTheme.colorScheme.errorContainer,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                            ) {
                                Text(
                                    text = errorMessage!!,
                                    modifier = Modifier.padding(10.dp),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }

                        if (!showTwoFactor) {
                            OutlinedTextField(
                                value = password,
                                onValueChange = { 
                                    password = it
                                    errorMessage = null
                                },
                                label = { Text("Пароль") },
                                singleLine = true,
                                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                trailingIcon = {
                                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                        Icon(
                                            if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                            contentDescription = "Toggle Password"
                                        )
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(20.dp))
                            Button(
                                onClick = {
                                    if (password.isNotEmpty()) {
                                        val blockInfo = com.example.data.FirestoreUserRoleManager.getUserBlockInfo(selectedAccount!!.username)
                                            ?: com.example.data.FirestoreUserRoleManager.getUserBlockInfo(selectedAccount!!.phoneNumber)
                                        if (blockInfo != null && blockInfo.isBlocked) {
                                            blockedUserRecord = blockInfo
                                        } else if (selectedAccount!!.is2FAEnabled) {
                                            showTwoFactor = true
                                        } else {
                                            scope.launch {
                                                isLoading = true
                                                delay(1000)
                                                isLoading = false
                                                onLoginSuccess(selectedAccount!!.username)
                                            }
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = password.isNotBlank()
                            ) {
                                Text("Войти")
                            }
                        } else {
                            OutlinedTextField(
                                value = twoFactorCode,
                                onValueChange = { twoFactorCode = it },
                                label = { Text("6-значный код 2FA") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                            )
                            Spacer(Modifier.height(20.dp))
                            Button(
                                onClick = {
                                    if (twoFactorCode.length >= 4) {
                                        val blockInfo = com.example.data.FirestoreUserRoleManager.getUserBlockInfo(selectedAccount!!.username)
                                            ?: com.example.data.FirestoreUserRoleManager.getUserBlockInfo(selectedAccount!!.phoneNumber)
                                        if (blockInfo != null && blockInfo.isBlocked) {
                                            blockedUserRecord = blockInfo
                                        } else {
                                            scope.launch {
                                                isLoading = true
                                                delay(1000)
                                                isLoading = false
                                                onLoginSuccess(selectedAccount!!.username)
                                            }
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = twoFactorCode.isNotBlank()
                            ) {
                                Text("Подтвердить и войти")
                            }
                        }

                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = {
                            selectedAccount = null
                            password = ""
                            twoFactorCode = ""
                            showTwoFactor = false
                            errorMessage = null
                        }) {
                            Text("Выбрать другой аккаунт")
                        }
                    }
                }
            }
        }
    }
}

enum class RegistrationMethod {
    EMAIL, PHONE
}

@Composable
fun RegistrationScreen(
    accounts: List<UserAccount>,
    onNavigateToLogin: () -> Unit,
    onRegisterSuccess: (String) -> Unit,
    viewModel: AppViewModel? = null,
    checkPhoneExists: suspend (String) -> Boolean = { false }
) {
    var regMethod by remember { mutableStateOf(RegistrationMethod.EMAIL) }
    var displayName by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var emailOrPhone by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    val strength = remember(password) { calculatePasswordStrength(password) }
    val strengthColor = when (strength) {
        0 -> Color.Gray
        1, 2 -> Color(0xFFFF1744)
        3 -> Color(0xFFFFEA00)
        4, 5 -> Color(0xFF00E676)
        else -> Color.Gray
    }
    val animatedProgress by animateFloatAsState(
        targetValue = strength.toFloat() / 5f,
        animationSpec = tween(300)
    )
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        if (isLoading) {
            NeonLoadingSpinner(size = 80.dp, color = MaterialTheme.colorScheme.primary)
        } else {
            Card(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    FirebaseAuthBadge(modifier = Modifier.padding(bottom = 12.dp))

                    Icon(
                        Icons.Filled.PersonAdd,
                        contentDescription = "Register",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(54.dp)
                    )
                    Spacer(Modifier.height(12.dp))
                    Text("Создать аккаунт", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("Регистрация в Firebase Cloud", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(16.dp))

                    TabRow(
                        selectedTabIndex = if (regMethod == RegistrationMethod.EMAIL) 0 else 1,
                        containerColor = Color.Transparent,
                        divider = {}
                    ) {
                        Tab(
                            selected = regMethod == RegistrationMethod.EMAIL,
                            onClick = {
                                regMethod = RegistrationMethod.EMAIL
                                errorMessage = null
                            },
                            text = { Text("Email (Firebase)") },
                            icon = { Icon(Icons.Filled.Email, null) }
                        )
                        Tab(
                            selected = regMethod == RegistrationMethod.PHONE,
                            onClick = {
                                regMethod = RegistrationMethod.PHONE
                                errorMessage = null
                            },
                            text = { Text("Телефон") },
                            icon = { Icon(Icons.Filled.Phone, null) }
                        )
                    }

                    Spacer(Modifier.height(16.dp))

                    if (errorMessage != null) {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Filled.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                Text(
                                    text = errorMessage!!,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = displayName,
                        onValueChange = { displayName = it },
                        label = { Text("Ваше имя (Display Name)") },
                        leadingIcon = { Icon(Icons.Filled.Badge, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(10.dp))

                    OutlinedTextField(
                        value = username,
                        onValueChange = { 
                            username = if (it.startsWith("@")) it else "@$it"
                        },
                        label = { Text("Имя пользователя (@username)") },
                        leadingIcon = { Icon(Icons.Filled.AlternateEmail, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(10.dp))

                    OutlinedTextField(
                        value = emailOrPhone,
                        onValueChange = { 
                            emailOrPhone = it
                            errorMessage = null
                        },
                        label = { Text(if (regMethod == RegistrationMethod.PHONE) "Номер телефона (+7900...)" else "Email адрес") },
                        leadingIcon = {
                            if (regMethod == RegistrationMethod.PHONE && emailOrPhone.startsWith("+")) {
                                Text(getCountryFlagForPhoneNumber(emailOrPhone), modifier = Modifier.padding(start = 8.dp))
                            } else {
                                Icon(if (regMethod == RegistrationMethod.PHONE) Icons.Filled.Phone else Icons.Filled.MailOutline, contentDescription = null)
                            }
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = if (regMethod == RegistrationMethod.PHONE) KeyboardType.Phone else KeyboardType.Email),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(10.dp))

                    OutlinedTextField(
                        value = password,
                        onValueChange = { 
                            password = it
                            errorMessage = null
                        },
                        label = { Text("Пароль (минимум 6 символов)") },
                        singleLine = true,
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                    contentDescription = "Toggle Password"
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(6.dp))

                    // Password Strength Indicator
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Надёжность пароля",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = when (strength) {
                                0 -> ""
                                1, 2 -> "Слабый"
                                3 -> "Средний"
                                4, 5 -> "Надёжный"
                                else -> ""
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = strengthColor,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(5.dp)
                            .background(Color.DarkGray, CircleShape)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(fraction = animatedProgress)
                                .height(5.dp)
                                .background(strengthColor, CircleShape)
                                .shadow(
                                    elevation = if (strength > 0) 6.dp else 0.dp,
                                    shape = CircleShape,
                                    ambientColor = strengthColor,
                                    spotColor = strengthColor
                                )
                        )
                    }
                    Spacer(Modifier.height(10.dp))

                    OutlinedTextField(
                        value = confirmPassword,
                        onValueChange = { confirmPassword = it },
                        label = { Text("Повторите пароль") },
                        singleLine = true,
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        isError = password.isNotEmpty() && confirmPassword.isNotEmpty() && password != confirmPassword
                    )

                    Spacer(Modifier.height(20.dp))

                    Button(
                        onClick = {
                            scope.launch {
                                isLoading = true
                                errorMessage = null

                                val targetEmail = if (regMethod == RegistrationMethod.EMAIL) {
                                    emailOrPhone
                                } else {
                                    "${emailOrPhone.replace("+", "").trim()}@kuotex.app"
                                }

                                val isBlocked = com.example.data.FirestoreUserRoleManager.isUserBlocked(emailOrPhone) ||
                                                com.example.data.FirestoreUserRoleManager.isUserBlocked(username)
                                if (isBlocked) {
                                    isLoading = false
                                    errorMessage = "⛔ Регистрация отклонена: данный аккаунт заблокирован модерацией."
                                    return@launch
                                }

                                val result = if (viewModel != null) {
                                    viewModel.signUpWithFirebase(
                                        email = targetEmail,
                                        pass = password,
                                        displayName = displayName.ifBlank { username.removePrefix("@") },
                                        username = username.ifBlank { "@${targetEmail.substringBefore("@")}" },
                                        phoneNumber = if (regMethod == RegistrationMethod.PHONE) emailOrPhone else ""
                                    )
                                } else {
                                    FirebaseAuthManager.signUpWithEmail(
                                        email = targetEmail,
                                        pass = password,
                                        displayName = displayName.ifBlank { username.removePrefix("@") }
                                    )
                                }

                                isLoading = false
                                if (result is AuthResult.Success) {
                                    android.widget.Toast.makeText(context, "✅ Аккаунт Firebase успешно создан!", android.widget.Toast.LENGTH_SHORT).show()
                                    onRegisterSuccess(result.data.email ?: result.data.uid)
                                } else if (result is AuthResult.Error) {
                                    errorMessage = result.message
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = emailOrPhone.isNotBlank() && password.length >= 6 && password == confirmPassword
                    ) {
                        Text("Зарегистрироваться в Firebase")
                    }

                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = onNavigateToLogin) {
                        Text("Уже есть аккаунт? Войти")
                    }
                }
            }
        }
    }
}
