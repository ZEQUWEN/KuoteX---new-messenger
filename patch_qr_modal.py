import re

with open("app/src/main/java/com/example/ui/BotProfileScreen.kt", "r") as f:
    content = f.read()

target = """    if (showQrDialog) {
        ModalBottomSheet(
            onDismissRequest = { showQrDialog = false },
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "QR-код",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp)
                )
                
                Card(
                    modifier = Modifier.size(250.dp).padding(16.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        // In a real app, generate a QR code bitmap here
                        Icon(
                            imageVector = Icons.Filled.QrCodeScanner,
                            contentDescription = "QR Placeholder",
                            modifier = Modifier.size(100.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        // Mocking an actual QR image
                        coil.compose.AsyncImage(
                            model = "https://api.qrserver.com/v1/create-qr-code/?size=200x200&data=t.me/${botUsername.removePrefix("@")}",
                            contentDescription = "Real QR Code",
                            modifier = Modifier.size(150.dp).clip(RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.Fit
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Button(
                    onClick = { showQrDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("Поделиться")
                }
            }
        }
    }"""

replacement = """    if (showQrDialog) {
        var selectedThemeIndex by remember { mutableStateOf(0) }
        val themes = listOf(
            listOf(Color(0xFF8E2DE2), Color(0xFF4A00E0)),
            listOf(Color(0xFFFF9933), Color(0xFF66B3FF)),
            listOf(Color(0xFF00C9FF), Color(0xFF92FE9D)),
            listOf(Color(0xFFfc4a1a), Color(0xFFf7b733)),
            listOf(Color(0xFF1D976C), Color(0xFF93F9B9))
        )
        
        ModalBottomSheet(
            onDismissRequest = { showQrDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxHeight(0.9f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // QR Container with Theme
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 32.dp, vertical = 16.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(androidx.compose.ui.graphics.Brush.linearGradient(themes[selectedThemeIndex])),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        modifier = Modifier.size(240.dp),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                coil.compose.AsyncImage(
                                    model = "https://api.qrserver.com/v1/create-qr-code/?size=250x250&data=t.me/${botUsername.removePrefix("@")}",
                                    contentDescription = "Real QR Code",
                                    modifier = Modifier.size(160.dp),
                                    contentScale = ContentScale.Fit
                                )
                                coil.compose.AsyncImage(
                                    model = botPic,
                                    contentDescription = "Avatar inside QR",
                                    modifier = Modifier
                                        .size(40.dp)
                                        .background(Color.White, CircleShape)
                                        .padding(4.dp)
                                        .clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = botUsername.uppercase(),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.Black
                            )
                        }
                    }
                }
                
                // Themes selector
                androidx.compose.foundation.lazy.LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(horizontal = 32.dp)
                ) {
                    items(themes.size) { index ->
                        Box(
                            modifier = Modifier
                                .size(60.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(androidx.compose.ui.graphics.Brush.linearGradient(themes[index]))
                                .clickable { selectedThemeIndex = index }
                                .then(
                                    if (selectedThemeIndex == index) {
                                        Modifier.border(3.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
                                    } else Modifier
                                )
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Button(
                        onClick = { showQrDialog = false },
                        modifier = Modifier.weight(1f).height(50.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Поделиться")
                    }
                    
                    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
                    Button(
                        onClick = { 
                            clipboardManager.setText(androidx.compose.ui.text.AnnotatedString("https://t.me/${botUsername.removePrefix("@")}"))
                            Toast.makeText(context, "Ссылка скопирована", Toast.LENGTH_SHORT).show()
                            showQrDialog = false 
                        },
                        modifier = Modifier.weight(1f).height(50.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer)
                    ) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Скопировать")
                    }
                }
            }
        }
    }"""

content = content.replace(target, replacement)

# Auto delete options fix
target_autodel = """val options = listOf("Нет", "1 день", "1 неделя", "1 месяц", "1 год", "Настроить")"""
replacement_autodel = """val options = listOf("Выключить", "24 часа", "7 дней", "1 месяц", "1 год", "Настроить")"""
content = content.replace(target_autodel, replacement_autodel)

# Fix DropdownMenu position by wrapping in Box
target_dropdown = """                actions = {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "More")
                    }
                    DropdownMenu("""
replacement_dropdown = """                actions = {
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "More")
                        }
                        DropdownMenu("""

target_dropdown_end = """                        )
                    }
                }
            )"""
replacement_dropdown_end = """                        )
                        }
                    }
                }
            )"""

if target_dropdown in content:
    content = content.replace(target_dropdown, replacement_dropdown)
    # The end bracket of DropdownMenu is at line 114
    # let's just find the end of the top app bar
    # 
    
with open("app/src/main/java/com/example/ui/BotProfileScreen.kt", "w") as f:
    f.write(content)
