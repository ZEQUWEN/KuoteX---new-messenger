package com.example.ui

import android.annotation.SuppressLint
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebAppScreen(url: String, initData: String) {
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.cacheMode = WebSettings.LOAD_DEFAULT
                
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        // Подмешиваем JS-мост для Telegram/KuoteX WebApp API
                        val jsBridge = """
                            window.KuoteX = {
                                WebApp: {
                                    initData: '$initData',
                                    close: function() { Android.closeWebApp(); },
                                    openLink: function(url) { Android.openLink(url); },
                                    openInvoice: function(url) { Android.openInvoice(url); },
                                    themeParams: {
                                        bg_color: '#ffffff',
                                        text_color: '#000000',
                                        hint_color: '#a8a8a8',
                                        link_color: '#2481cc',
                                        button_color: '#2481cc',
                                        button_text_color: '#ffffff'
                                    }
                                }
                            };
                            // Для совместимости с ботами, ожидающими Telegram.WebApp
                            window.Telegram = window.KuoteX; 
                        """.trimIndent()
                        evaluateJavascript(jsBridge, null)
                    }
                }
                loadUrl(url)
            }
        },
        update = { webView ->
            webView.loadUrl(url)
        }
    )
}
