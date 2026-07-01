package com.example

// Update: Sel 01/07/2026 22:00 - v3.0.0
// ForegroundService: MQTT monitoring latar belakang + notifikasi air + notifikasi + buzzer azan
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import org.eclipse.paho.client.mqttv3.*
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence

class SanyoService : Service() {

    companion object {
        const val CHANNEL_ONGOING = "sanyo_ongoing"
        const val CHANNEL_ALERT   = "sanyo_alert"
        const val NOTIF_ONGOING   = 1
        const val NOTIF_WATER     = 2
        const val NOTIF_PRAYER    = 3
        const val PREF_PRAYER     = "prayer_times"
        private const val TAG             = "SanyoService"
        private const val BROKER          = "tcp://broker.emqx.io:1883"
        private const val TOPIC_STATUS    = "smartsanyo/riyan123/status"
        private const val TOPIC_CONTROL   = "smartsanyo/riyan123/control"
        private const val PRAYER_INTERVAL = 60_000L // cek tiap 1 menit
    }

    private var mqttClient: MqttClient? = null
    private val handler = Handler(Looper.getMainLooper())
    private var lastWaterLevel = -1
    private var lastWaterNotif = 0L
    private var lastPrayerFired = ""
    private val prayerNames = listOf("Subuh", "Dzuhur", "Ashar", "Maghrib", "Isya")

    private val prayerRunnable = object : Runnable {
        override fun run() {
            checkPrayer()
            handler.postDelayed(this, PRAYER_INTERVAL)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createChannels()
        startForeground(NOTIF_ONGOING, buildOngoing())
        connectMqtt()
        handler.post(prayerRunnable)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        handler.removeCallbacks(prayerRunnable)
        try { mqttClient?.disconnect(); mqttClient?.close() } catch (_: Exception) {}
        super.onDestroy()
    }

    // ─── MQTT ────────────────────────────────────────────────────────────────

    private fun connectMqtt() {
        Thread {
            try {
                val id = "SanyoSvc_" + (100000..999999).random()
                val opts = MqttConnectOptions().apply {
                    isCleanSession = true; connectionTimeout = 10; keepAliveInterval = 30
                }
                val cb = object : MqttCallback {
                    override fun connectionLost(c: Throwable) {
                        Log.w(TAG, "MQTT lost, retry in 10s")
                        Thread.sleep(10_000)
                        connectMqtt()
                    }
                    override fun messageArrived(t: String?, m: MqttMessage?) {
                        onStatus(m?.toString() ?: return)
                    }
                    override fun deliveryComplete(t: IMqttDeliveryToken?) {}
                }
                mqttClient = MqttClient(BROKER, id, MemoryPersistence()).also {
                    it.setCallback(cb)
                    it.connect(opts)
                    it.subscribe(TOPIC_STATUS, 0)
                    Log.d(TAG, "MQTT connected")
                }
            } catch (e: Exception) {
                Log.e(TAG, "MQTT connect error: ${e.message}")
            }
        }.start()
    }

    private fun onStatus(payload: String) {
        try {
            val json = org.json.JSONObject(payload)
            val level = json.optInt("waterLevel", -1).takeIf { it >= 0 } ?: return
            val now = System.currentTimeMillis()
            if (now - lastWaterNotif < 300_000) return // debounce 5 menit
            if (level <= 10 && lastWaterLevel > 10) {
                notify(NOTIF_WATER, "⚠️ Air Hampir Habis!", "Level air ${level}% — segera hidupkan pompa")
                lastWaterNotif = now
            } else if (level >= 90 && (lastWaterLevel < 90 || lastWaterLevel < 0)) {
                notify(NOTIF_WATER, "💧 Tangki Hampir Penuh", "Level air ${level}% — pompa akan mati otomatis")
                lastWaterNotif = now
            }
            lastWaterLevel = level
        } catch (_: Exception) {}
    }

    private fun publish(payload: String) = Thread {
        try {
            mqttClient?.publish(TOPIC_CONTROL, MqttMessage(payload.toByteArray()).apply { qos = 0 })
        } catch (_: Exception) {}
    }.start()

    // ─── Prayer Time ─────────────────────────────────────────────────────────

    private fun checkPrayer() {
        val prefs = getSharedPreferences(PREF_PRAYER, Context.MODE_PRIVATE)
        val cal = java.util.Calendar.getInstance()
        val now = "%02d:%02d".format(
            cal.get(java.util.Calendar.HOUR_OF_DAY),
            cal.get(java.util.Calendar.MINUTE)
        )
        prayerNames.forEach { name ->
            val t = prefs.getString(name, "") ?: return@forEach
            if (t == now && lastPrayerFired != "${name}_$now") {
                lastPrayerFired = "${name}_$now"
                notify(NOTIF_PRAYER, "🕌 Waktu $name", "Waktu $name telah tiba — $now WIB")
                publish("BUZZER_5")
                Log.d(TAG, "Prayer: $name at $now — buzzer sent")
            }
        }
    }

    // ─── Notifications ───────────────────────────────────────────────────────

    private fun createChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(
                CHANNEL_ONGOING, "Sanyo Aktif", NotificationManager.IMPORTANCE_LOW
            ))
            nm.createNotificationChannel(NotificationChannel(
                CHANNEL_ALERT, "Notifikasi Sanyo", NotificationManager.IMPORTANCE_HIGH
            ).apply { enableVibration(true) })
        }
    }

    private fun buildOngoing(): Notification {
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ONGOING)
            .setContentTitle("Smart Sanyo Control")
            .setContentText("Monitoring air & jadwal salat aktif")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true).setContentIntent(pi).build()
    }

    private fun notify(id: Int, title: String, text: String) {
        val pi = PendingIntent.getActivity(
            this, id, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = NotificationCompat.Builder(this, CHANNEL_ALERT)
            .setContentTitle(title).setContentText(text)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setAutoCancel(true).setContentIntent(pi).build()
        getSystemService(NotificationManager::class.java).notify(id, n)
    }
}
