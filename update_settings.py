import re
with open("app/src/main/java/com/example/ui/SettingsScreens.kt", "r") as f:
    text = f.read()

# Replace the "Global Dark Theme" and "Auto Theme Switcher" with a nice toggle.
# And maybe just have Day/Night mode selector.

replacement = """
            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()
            
            Text("Настройки темы", modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.setAutoThemeEnabled(!isAutoThemeEnabled) }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Автоматическая тема", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                    Switch(
                        checked = isAutoThemeEnabled,
                        onCheckedChange = { viewModel.setAutoThemeEnabled(it) },
                        colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colorScheme.primary, checkedTrackColor = MaterialTheme.colorScheme.primaryContainer)
                    )
                }
                if (!isAutoThemeEnabled) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha=0.2f), modifier = Modifier.padding(horizontal = 16.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.setDarkThemeEnabled(!isDarkThemeEnabled) }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Тёмная тема", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                        Switch(
                            checked = isDarkThemeEnabled,
                            onCheckedChange = { viewModel.setDarkThemeEnabled(it) },
                            colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colorScheme.primary, checkedTrackColor = MaterialTheme.colorScheme.primaryContainer)
                        )
                    }
                }
            }
"""

text = re.sub(
    r'Spacer\(modifier = Modifier.height\(16.dp\)\)\s*HorizontalDivider\(\)\s*Text\("Настройки", modifier = Modifier.padding\(16.dp\), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary\)\s*Row\(\s*modifier = Modifier.fillMaxWidth\(\).padding\(horizontal = 16.dp, vertical = 8.dp\),\s*horizontalArrangement = Arrangement.SpaceBetween,\s*verticalAlignment = Alignment.CenterVertically\s*\)\ {\s*Text\("Auto Theme Switcher", style = MaterialTheme.typography.bodyLarge\)\s*Switch\(\s*checked = isAutoThemeEnabled,\s*onCheckedChange = \{ viewModel.setAutoThemeEnabled\(it\) \}\s*\)\s*\}\s*Row\(\s*modifier = Modifier.fillMaxWidth\(\).padding\(horizontal = 16.dp, vertical = 8.dp\),\s*horizontalArrangement = Arrangement.SpaceBetween,\s*verticalAlignment = Alignment.CenterVertically\s*\)\ {\s*Text\("Global Dark Theme", style = MaterialTheme.typography.bodyLarge\)\s*Switch\(\s*checked = isDarkThemeEnabled,\s*onCheckedChange = \{ viewModel.setDarkThemeEnabled\(it\) \}\s*\)\s*\}',
    replacement,
    text,
    flags=re.DOTALL
)

with open("app/src/main/java/com/example/ui/SettingsScreens.kt", "w") as f:
    f.write(text)
