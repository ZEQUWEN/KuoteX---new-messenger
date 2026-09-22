package com.example.utils

import java.util.regex.Pattern

object MessageSanitizer {
    // Matches <script> ... </script> blocks
    private val SCRIPT_PATTERN = Pattern.compile(
        "<script\\b[^<]*(?:(?!</script>)<[^<]*)*</script>",
        Pattern.CASE_INSENSITIVE
    )

    // Matches generic HTML tags that might be used for injection (e.g., <img>, <iframe>, <a>)
    // We remove them to prevent XSS if the text is ever rendered in a WebView or HtmlCompat context.
    private val HTML_TAG_PATTERN = Pattern.compile(
        "<(/?(?:img|iframe|a|b|i|u|strong|em|div|span|p|br|hr|table|tr|td|th|tbody|thead|tfoot|ul|ol|li|h[1-6])[^>]*)>",
        Pattern.CASE_INSENSITIVE
    )

    /**
     * Sanitizes incoming text to prevent potential XSS-like injections
     * and ensures clean formatting for UI rendering.
     */
    fun sanitize(input: String): String {
        if (input.isBlank()) return ""
        
        var sanitized = input
        
        // 1. Remove <script> blocks entirely
        sanitized = SCRIPT_PATTERN.matcher(sanitized).replaceAll("")
        
        // 2. Remove risky HTML tags
        sanitized = HTML_TAG_PATTERN.matcher(sanitized).replaceAll("")
        
        // 3. Remove zero-width spaces, null bytes, and unprintable control characters
        // (excluding standard whitespace like \n, \t, \r)
        val controlChars = Regex("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\u200B]")
        sanitized = sanitized.replace(controlChars, "")
        
        // 4. Normalize excessive newlines (max 3 consecutive newlines allowed)
        sanitized = sanitized.replace(Regex("\\n{4,}"), "\n\n\n")
        
        // 5. Trim leading/trailing whitespace
        return sanitized.trim()
    }
}
