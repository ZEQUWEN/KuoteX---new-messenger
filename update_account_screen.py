import re

with open("app/src/main/java/com/example/ui/AccountScreen.kt", "r") as f:
    content = f.read()

# We need to add the Modifier.shimmerEffect() at the bottom of the file
shimmer_extension = """
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.ui.composed
import androidx.compose.foundation.shape.CircleShape

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
"""

if "shimmerEffect" not in content:
    content = content.replace("import androidx.compose.animation.core.tween", "import androidx.compose.animation.core.tween\n" + shimmer_extension)

# We need to replace the AccountScreen function completely.
# Let's use regex to extract everything from `@OptIn(ExperimentalMaterial3Api::class)` up to `fun SectionGroup`

account_screen_pattern = re.compile(r"@OptIn\(ExperimentalMaterial3Api::class\)\s*@Composable\s*fun AccountScreen.*?if \(showUsernameChange\) \{.*?\}", re.DOTALL)

new_account_screen = """@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountScreen(
    onBack: () -> Unit,
    viewModel: AccountViewModel = viewModel()
) {
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Аккаунт") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Назад")
                    }
                },
                actions = {
                    if (!state.isInitialLoading) {
                        IconButton(onClick = { 
                            viewModel.updateProfile(firstName, lastName, bio)
                            viewModel.updateSocialLinks(mapOf("telegram" to socialTelegram, "github" to socialGithub))
                            onBack()
                        }) {
                            Icon(Icons.Default.Check, "Сохранить")
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
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Box(
                            modifier = Modifier
                                .size(100.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer)
                                .clickable { viewModel.updateAvatar("mock_avatar_url") },
                            contentAlignment = Alignment.Center
                        ) {
                            if (state.avatarUrl != null) {
                                Icon(Icons.Default.Person, contentDescription = "Avatar", modifier = Modifier.size(60.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                            } else {
                                Icon(Icons.Default.AddAPhoto, contentDescription = "Add Avatar", tint = MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                        }
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
                            title = "@${state.username}",
                            subtitle = "Имя пользователя",
                            onClick = { showUsernameChange = true }
                        )
                        HorizontalDivider(modifier = Modifier.padding(start = 56.dp, end = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                        InfoRow(
                            icon = Icons.Default.Cake,
                            iconTint = Color(0xFF2196F3),
                            title = state.birthDate,
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
}"""

content = re.sub(account_screen_pattern, new_account_screen, content)

with open("app/src/main/java/com/example/ui/AccountScreen.kt", "w") as f:
    f.write(content)
print("Updated successfully")
