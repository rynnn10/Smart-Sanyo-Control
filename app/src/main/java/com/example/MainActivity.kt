package com.example

import android.annotation.SuppressLint
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import android.util.Log
import androidx.core.app.ActivityCompat
import android.content.pm.PackageManager
import android.Manifest

// Update: Sel 01/07/2026 14:00 - v2.3.0
// Tambah sendAutoOnEnabled (sebelumnya hilang dari bridge), handle hasSchedule dari ESP
class MainActivity : ComponentActivity() {

    private var mqttBridge: MqttBridge? = null
    private var webViewRef: WebView? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Izin notifikasi Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001)
            }
        }


        setContent {
            MyApplicationTheme {
                var showExitDialog by remember { mutableStateOf(false) }

                BackHandler(enabled = !showExitDialog) { showExitDialog = true }

                if (showExitDialog) {
                    AlertDialog(
                        onDismissRequest = { showExitDialog = false },
                        icon = {
                            androidx.compose.material3.Icon(
                                imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                                contentDescription = "Exit Icon",
                                modifier = Modifier.size(36.dp),
                                tint = androidx.compose.material3.MaterialTheme.colorScheme.primary
                            )
                        },
                        title = {
                            Text("Keluar Aplikasi", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        },
                        text = {
                            Text("Apakah Anda yakin ingin keluar dari aplikasi Smart Sanyo Control?",
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        },
                        confirmButton = {
                            androidx.compose.foundation.layout.Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center
                            ) {
                                androidx.compose.material3.OutlinedButton(onClick = { showExitDialog = false }) { Text("Kembali") }
                                androidx.compose.foundation.layout.Spacer(Modifier.size(12.dp))
                                androidx.compose.material3.Button(onClick = { showExitDialog = false; this@MainActivity.finish() }) { Text("Ya, Keluar") }
                            }
                        },
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
                        containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerHigh
                    )
                }

                Scaffold(modifier = Modifier.fillMaxSize().imePadding()) { innerPadding ->
                    AndroidView(
                        modifier = Modifier.fillMaxSize().padding(innerPadding),
                        factory = {
                            WebView(it).apply {
                                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                                setBackgroundColor(android.graphics.Color.parseColor("#05070f"))

                                webViewClient = object : WebViewClient() {
                                    override fun onPageFinished(view: WebView, url: String) {
                                        super.onPageFinished(view, url)
                                        val versionName = BuildConfig.VERSION_NAME
                                        val ts = BuildConfig.BUILD_TIMESTAMP
                                        val js = """
                                            javascript:(function(){
                                                var vn = document.getElementById('appVersion');
                                                if(vn) vn.innerHTML = '$versionName';
                                                var bt = document.getElementById('buildTimestamp');
                                                if(bt){
                                                    var d = new Date(parseInt('$ts'));
                                                    var days=['Minggu','Senin','Selasa','Rabu','Kamis','Jumat','Sabtu'];
                                                    var months=['Januari','Februari','Maret','April','Mei','Juni','Juli','Agustus','September','Oktober','November','Desember'];
                                                    bt.innerHTML = days[d.getDay()]+', '+d.getDate()+' '+months[d.getMonth()]+' '+d.getFullYear()+' | '+String(d.getHours()).padStart(2,'0')+':'+String(d.getMinutes()).padStart(2,'0')+':'+String(d.getSeconds()).padStart(2,'0');
                                                }
                                            })()
                                        """.trimIndent().replace("\n", " ")
                                        view.evaluateJavascript(js, null)
                                    }
                                }

                                settings.apply {
                                    javaScriptEnabled = true
                                    domStorageEnabled = true
                                    allowFileAccess = true
                                    mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                                }

                                 webViewRef = this
                                 addJavascriptInterface(MQTTBridge(), "MqttAndroid")
                                loadUrl("file:///android_asset/index.html")
                            }
                        },
                        update = {}
                    )
                }
            }
        }
    }

    // ============================================================
    // MQTT Bridge — native Java (Paho library), all comm via MQTT
    // Update: Sel 01/07/2026 14:00 - v2.3.0
    // Tambah sendAutoOnEnabled + hasSchedule/scheduleCount ke JS
    // ============================================================
    inner class MQTTBridge {
        @JavascriptInterface
        fun connect() {
            mqttBridge?.disconnect()
            mqttBridge = MqttBridge { payload ->
                val escaped = payload.replace("'", "\\'").replace("\n", " ")
                mainHandler.post {
                    webViewRef?.evaluateJavascript(
                        "javascript:(function(){ try {" +
                        "var d=JSON.parse('$escaped');" +
                        "if(d.waterLevel!==undefined) currentWaterLevel=parseInt(d.waterLevel);" +
                        "if(d.pumpStatus!==undefined) pumpStatus=d.pumpStatus;" +
                        "if(d.autoOffEnabled!==undefined) autoOffEnabled=d.autoOffEnabled;" +
                        "if(d.autoOffLevel!==undefined) autoOffLevel=d.autoOffLevel;" +
                        "if(d.autoOnLevel!==undefined) autoOnLevel=d.autoOnLevel;" +
                        "if(d.ssid!==undefined) espSSIDValue=d.ssid;" +
                        "if(d.rssi!==undefined){espRssiValue=d.rssi;espQualValue=rssiToQuality(d.rssi);}" +
                        "if(d.hasSchedule!==undefined) espHasSchedule=d.hasSchedule;" +
                        "if(typeof markOnline==='function'){markOnline();}else{isOnline=true;}" +
                        "updateUI();" +
                        "}catch(e){console.error('MQTT:',e)} })()",
                        null
                    )
                }
            }
            mqttBridge?.connect()
        }

        @JavascriptInterface
        fun send(pumpOn: Boolean) { mqttBridge?.sendCommand(pumpOn) }

        @JavascriptInterface
        fun sendAutoOff(enabled: Boolean) { mqttBridge?.sendAutoOffCommand(enabled) }

        @JavascriptInterface
        fun sendAutoOffLevel(level: Int) { mqttBridge?.sendAutoOffLevel(level) }

        @JavascriptInterface
        fun sendAutoOnLevel(level: Int) { mqttBridge?.sendAutoOnLevel(level) }

        @JavascriptInterface
        fun sendSchedule(json: String) { mqttBridge?.sendSchedule(json) }

        @JavascriptInterface
        fun sendAutoOnEnabled(enabled: Boolean) { mqttBridge?.sendAutoOnEnabled(enabled) }

        @JavascriptInterface
        fun disconnect() { mqttBridge?.disconnect() }

        @JavascriptInterface
        fun isConnected(): Boolean = mqttBridge != null
    }

    override fun onDestroy() {
        mqttBridge?.disconnect()
        super.onDestroy()
    }
}