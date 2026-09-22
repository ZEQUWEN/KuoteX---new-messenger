package com.example.ui

fun calculatePasswordStrength(password: String): Int {
    if (password.isEmpty()) return 0
    var strength = 0
    if (password.length >= 8) strength += 1
    if (password.length >= 12) strength += 1
    if (password.any { it.isUpperCase() }) strength += 1
    if (password.any { it.isDigit() }) strength += 1
    if (password.any { !it.isLetterOrDigit() }) strength += 1
    return strength
}
