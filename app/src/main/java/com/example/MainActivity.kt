package com.example

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.webkit.GeolocationPermissions
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
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

// Update: Sel 01/07/2026 22:00 - v3.0.0 | Sel 01/07/2026 [UPDATE] - v3.1.0
// v3.1.0: hapus auto mode UI, notif center in-app, deep link dari status bar → panel notif
class MainActivity : ComponentActivity() {

    private var mqttBridge: MqttBridge? = null
    private var webViewRef: WebView? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var pendingNotifType: String? = null

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
        // Izin lokasi (untuk jadwal salat GPS)
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ), 1002)
        }
        pendingNotifType = intent.getStringExtra("notif_type")

        // v3.4.0: minta pembebasan optimasi baterai sekali — biar service & alarm azan tak
        // dibunuh OEM saat app ditutup. Diam-diam gagal kalau ditolak (azan tetap coba jalan).
        requestBatteryOptExemption()

        // Mulai SanyoService (background MQTT + notifikasi)
        val svcIntent = Intent(this, SanyoService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(svcIntent)
        else startService(svcIntent)


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

                                webChromeClient = object : WebChromeClient() {
                    override fun onGeolocationPermissionsShowPrompt(
                        origin: String?, callback: GeolocationPermissions.Callback?
                    ) { callback?.invoke(origin, true, false) }
                }
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
                                        pendingNotifType?.let { type ->
                                            val safe = type.replace("'", "\\'")
                                            view.evaluateJavascript("if(window.openNotifPanel)window.openNotifPanel('$safe')", null)
                                            pendingNotifType = null
                                        }
                                    }
                                }

                                settings.apply {
                                    javaScriptEnabled = true
                                    domStorageEnabled = true
                                    allowFileAccess = true
                                    mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                                    setGeolocationEnabled(true)
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
    // Update: Sel 01/07/2026 14:00 - v2.3.0 | Rab 01/07/2026 - v3.3.0
    // v3.1.2: tambah shareApp() — salin APK ke cache & launch Android share chooser
    // v3.3.0: jadwal solat dari status ESP (prayerTimes) ikut update UI WebView yg terbuka
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
                        "if(d.prayerTimes!==undefined){prayerTimes=d.prayerTimes;try{localStorage.setItem('prayerTimes',JSON.stringify(d.prayerTimes));}catch(e){}if(typeof renderPrayerTimes==='function')renderPrayerTimes();}" +
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
        fun sendBuzzer(count: Int) { mqttBridge?.sendBuzzer(count) }

        @JavascriptInterface
        fun shareApp() {
            // Salin APK ke cacheDir (background thread), lalu launch chooser di main thread
            Thread {
                try {
                    val src = java.io.File(packageCodePath)
                    val dst = java.io.File(cacheDir, "SmartSanyoControl_v${BuildConfig.VERSION_NAME}.apk")
                    src.copyTo(dst, overwrite = true)
                    val uri = androidx.core.content.FileProvider.getUriForFile(
                        this@MainActivity, "${packageName}.provider", dst
                    )
                    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                        type = "application/vnd.android.package-archive"
                        putExtra(android.content.Intent.EXTRA_STREAM, uri)
                        putExtra(android.content.Intent.EXTRA_SUBJECT, "Smart Sanyo Control v${BuildConfig.VERSION_NAME}")
                        putExtra(android.content.Intent.EXTRA_TEXT,
                            "Smart Sanyo Control v${BuildConfig.VERSION_NAME}\nAplikasi kontrol pompa air cerdas via MQTT & IoT")
                        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    mainHandler.post {
                        startActivity(android.content.Intent.createChooser(intent, "Bagikan Aplikasi"))
                    }
                } catch (e: Exception) { Log.e("ShareApp", "Error: ${e.message}") }
            }.start()
        }

        @JavascriptInterface
        fun getStoredNotifications(): String = try {
            getSharedPreferences(SanyoService.PREF_NOTIF_HISTORY, MODE_PRIVATE)
                .getString(SanyoService.NOTIF_HISTORY_KEY, "[]") ?: "[]"
        } catch (_: Exception) { "[]" }

        @JavascriptInterface
        fun savePrayerTimes(json: String) {
            // Simpan ke SharedPreferences agar SanyoService bisa baca waktu salat saat background
            try {
                val prefs = getSharedPreferences(SanyoService.PREF_PRAYER, MODE_PRIVATE).edit()
                val obj = org.json.JSONObject(json)
                obj.keys().forEach { key -> prefs.putString(key, obj.getString(key)) }
                prefs.apply()
                mqttBridge?.sendPrayerTimes(json) // teruskan ke ESP untuk tampil di LCD
            } catch (_: Exception) {}
        }

        @JavascriptInterface
        fun disconnect() { mqttBridge?.disconnect() }

        @JavascriptInterface
        fun isConnected(): Boolean = mqttBridge != null
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val type = intent.getStringExtra("notif_type") ?: return
        val safe = type.replace("'", "\\'")
        mainHandler.post {
            webViewRef?.evaluateJavascript("if(window.openNotifPanel)window.openNotifPanel('$safe')", null)
        }
    }

    @SuppressLint("BatteryLife")
    private fun requestBatteryOptExemption() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        try {
            val pm = getSystemService(android.content.Context.POWER_SERVICE) as android.os.PowerManager
            if (pm.isIgnoringBatteryOptimizations(packageName)) return
            startActivity(Intent(
                android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                android.net.Uri.parse("package:$packageName")
            ))
        } catch (_: Exception) { /* OEM tanpa dialog ini — abaikan, azan tetap coba jalan */ }
    }

    override fun onDestroy() {
        mqttBridge?.disconnect()
        super.onDestroy()
    }
}