import re

with open("app/src/main/java/com/example/ui/StorageSettingsScreen.kt", "r") as f:
    content = f.read()

start_marker = "            Card(\n                modifier = Modifier\n                    .fillMaxWidth()\n                    .padding(16.dp),\n                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)\n            ) {"
end_marker = "            Spacer(modifier = Modifier.height(16.dp))\n            \n            Text(\n                text = \"Автозагрузка медиа\","

start_idx = content.find(start_marker)
end_idx = content.find(end_marker)

if start_idx != -1 and end_idx != -1:
    replacement = """            val gradientColors = listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.secondaryContainer)
            val brush = androidx.compose.ui.graphics.Brush.verticalGradient(colors = gradientColors)
            
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Box(modifier = Modifier.background(brush)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Оптимизация хранилища",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Рекомендуем: удаление старых медиасообщений или сжатие сохраненных изображений для освобождения места.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    coroutineScope.launch {
                                        isScanningForDuplicates = true
                                        delay(1500) // Simulate scanning
                                        duplicateSizeFound = (Math.random() * 200 * 1024 * 1024).toLong() + 50 * 1024 * 1024 // 50MB to 250MB
                                        isScanningForDuplicates = false
                                        showOptimizeDialog = true
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                if (isScanningForDuplicates) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Сканирование...")
                                } else {
                                    Text("Оптимизировать")
                                }
                            }
                            OutlinedButton(
                                onClick = { 
                                    coroutineScope.launch {
                                        com.example.utils.ExportUtils.exportCacheToZip(context)
                                        snackbarHostState.showSnackbar("Файлы успешно сохранены в zip архив")
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Экспорт")
                            }
                        }
                        
                        if (largestCategories.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(16.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            Text(
                                text = "Персональные рекомендации",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            largestCategories.forEach { category ->
                                val actionText = when (category.categoryName) {
                                    "Видео" -> "Удалить старые видеосообщения"
                                    "Фото" -> "Удалить дубликаты изображений"
                                    "Файлы" -> "Удалить дубликаты файлов"
                                    else -> "Очистить ${category.categoryName.lowercase()}"
                                }
                                Text(
                                    text = "• $actionText (${formatBytes(category.sizeBytes)})",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                    }
                }
            }
"""
    content = content[:start_idx] + replacement + "\n" + content[end_idx:]
    
    with open("app/src/main/java/com/example/ui/StorageSettingsScreen.kt", "w") as f:
        f.write(content)
