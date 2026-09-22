package com.example.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

data class SettingsItem(
    val id: String,
    val title: String,
    val keywords: List<String>,
    val icon: ImageVector,
    val color: Color,
    val categoryPath: String,
    val routeAction: String
)

object SettingsRegistry {
    val items = listOf(
        SettingsItem("profile_phone", "Номер телефона", listOf("телефон", "изменить", "phone", "аккаунт"), Icons.Filled.Phone, Color(0xFF3B82F6), "Аккаунт > Номер телефона", "settings/profile"),
        SettingsItem("profile_username", "Имя пользователя", listOf("юзернейм", "username", "ник", "аккаунт"), Icons.Filled.AlternateEmail, Color(0xFF3B82F6), "Аккаунт > Имя пользователя", "settings/profile"),
        SettingsItem("profile_bio", "О себе", listOf("био", "bio", "описание", "аккаунт"), Icons.Filled.Info, Color(0xFF3B82F6), "Аккаунт > О себе", "settings/profile"),
        SettingsItem("theme_color", "Цветовая тема", listOf("цвет", "тема", "оформление", "дизайн"), Icons.Filled.Palette, Color(0xFFF59E0B), "Настройки чатов > Цветовая тема", "settings/themes"),
        SettingsItem("theme_wallpaper", "Обои для чатов", listOf("фон", "обои", "чат", "картинка"), Icons.Filled.Wallpaper, Color(0xFFF59E0B), "Настройки чатов > Обои для чатов", "settings/themes"),
        SettingsItem("security_passcode", "Код-пароль", listOf("пароль", "пин", "защита", "passcode"), Icons.Filled.Lock, Color(0xFF10B981), "Конфиденциальность > Код-пароль", "settings/security"),
        SettingsItem("security_2fa", "Двухэтапная аутентификация", listOf("2fa", "пароль", "защита", "двойная"), Icons.Filled.Security, Color(0xFF10B981), "Конфиденциальность > Двухэтапная аутентификация", "settings/security"),
        SettingsItem("general_sounds", "Уведомления и звуки", listOf("звук", "уведомления", "сигнал", "звонок"), Icons.Filled.Notifications, Color(0xFFEF4444), "Уведомления > Уведомления и звуки", "settings/general"),
        SettingsItem("storage_network", "Использование сети", listOf("трафик", "сеть", "интернет", "данные"), Icons.Filled.DataUsage, Color(0xFF3B82F6), "Данные и память > Использование сети", "settings/storage"),
        SettingsItem("folders_manage", "Папки с чатами", listOf("папки", "папка", "сортировка", "фильтр", "folders"), Icons.Filled.Folder, Color(0xFF2196F3), "Настройки > Папки с чатами", "settings/folders"),
        SettingsItem("devices_active", "Активные сеансы", listOf("устройства", "сеансы", "завершить", "телефон"), Icons.Filled.Devices, Color(0xFF00BCD4), "Устройства > Активные сеансы", "settings/devices"),
        SettingsItem("battery_saver", "Режим энергосбережения", listOf("батарея", "энергия", "заряд", "экономия"), Icons.Filled.BatterySaver, Color(0xFFFF9800), "Энергосбережение > Режим", "settings/battery"),
        SettingsItem("language_app", "Язык приложения", listOf("язык", "language", "перевод", "русский"), Icons.Filled.Language, Color(0xFF9C27B0), "Язык > Язык приложения", "settings/language")
    )
}
