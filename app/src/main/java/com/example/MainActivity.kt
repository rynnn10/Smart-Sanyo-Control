package com.example

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                val context = androidx.compose.ui.platform.LocalContext.current
                var showExitDialog by remember { mutableStateOf(false) }

                BackHandler(enabled = !showExitDialog) {
                    showExitDialog = true
                }

                if (showExitDialog) {
                    AlertDialog(
                        onDismissRequest = { showExitDialog = false },
                        icon = { 
                            androidx.compose.material3.Icon(
                                imageVector = androidx.compose.material.icons.Icons.AutoMirrored.Filled.ExitToApp,
                                contentDescription = "Exit Icon",
                                modifier = Modifier.size(36.dp),
                                tint = androidx.compose.material3.MaterialTheme.colorScheme.primary
                            )
                        },
                        title = { 
                            Text(
                                "Keluar Aplikasi",
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                style = androidx.compose.material3.MaterialTheme.typography.titleLarge
                            ) 
                        },
                        text = { 
                            Text(
                                "Apakah Anda yakin ingin keluar dari aplikasi Smart Sanyo Control?",
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium
                            ) 
                        },
                        confirmButton = {
                            androidx.compose.material3.Button(
                                onClick = { 
                                    showExitDialog = false
                                    this@MainActivity.finish() 
                                },
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                            ) {
                                Text("Ya, Keluar")
                            }
                        },
                        dismissButton = {
                            androidx.compose.material3.OutlinedButton(
                                onClick = { showExitDialog = false },
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                            ) {
                                Text("Kembali")
                            }
                        },
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
                        containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerHigh
                    )
                }

                Scaffold(
                    modifier = Modifier
                        .fillMaxSize()
                        .imePadding()
                ) { innerPadding ->
                    AndroidView(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                        factory = { context ->
                            WebView(context).apply {
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                                // Matches the HTML background to prevent visual flashing during load
                                setBackgroundColor(android.graphics.Color.parseColor("#05070f"))
                                
                                webViewClient = WebViewClient()
                                settings.apply {
                                    javaScriptEnabled = true
                                    domStorageEnabled = true
                                    allowFileAccess = true
                                    mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                                }
                                loadUrl("file:///android_asset/index.html")
                            }
                        },
                        update = {}
                    )
                }
            }
        }
    }
}
