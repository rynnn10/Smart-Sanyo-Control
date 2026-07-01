package com.example

// Update: Sel 01/07/2026 22:00 - v3.0.0 | Rab 01/07/2026 - v3.4.0
// ForegroundService: MQTT monitoring latar belakang + notifikasi air + notifikasi + buzzer azan
// v3.1.0: icon notif → water droplet, simpan histori notif ke SharedPrefs, deep link ke notif center
// v3.2.0: suara azan (MediaPlayer) diputar saat waktu solat — jalan walau app tertutup
// v3.3.0: sinkron lintas device via ESP (status MQTT "prayerTimes"+"notifLog") — ESP jadi
//   sumber bersama, ganti deteksi level air lokal (debounce) yg dulu bisa beda antar device
// v3.4.0: azan tahan app ditutup total — AlarmManager exact per waktu solat (AzanScheduler)
//   + BootReceiver restart setelah HP nyala; volume azan pakai stream MEDIA (bisa diatur tombol)
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
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
        const val CHANNEL_ONGOING    = "sanyo_ongoing"
        const val CHANNEL_ALERT      = "sanyo_alert"
        const val NOTIF_ONGOING      = 1
        const val NOTIF_WATER        = 2
        const val NOTIF_PRAYER       = 3
        const val PREF_PRAYER        = "prayer_times"
        const val PREF_NOTIF_HISTORY = "notif_history"
        const val NOTIF_HISTORY_KEY  = "notifs_json"
        const val ACTION_PLAY_AZAN   = "com.example.PLAY_AZAN"
        const val EXTRA_PRAYER_NAME  = "prayer_name"
        private const val TAG             = "SanyoService"
        private const val BROKER          = "tcp://broker.emqx.io:1883"
        private const val TOPIC_STATUS    = "smartsanyo/riyan123/status"
        private const val TOPIC_CONTROL   = "smartsanyo/riyan123/control"
        private const val PRAYER_INTERVAL = 60_000L // cek tiap 1 menit
    }

    private var mqttClient: MqttClient? = null
    private var adzanPlayer: MediaPlayer? = null
    private val handler = Handler(Looper.getMainLooper())
    private var lastPrayerFired = ""
    private val prayerNames = listOf("Subuh", "Dzuhur", "Ashar", "Maghrib", "Isya")
    // Dedupe notifLog dari ESP vs notifikasi lokal (mis. checkPrayer() sendiri) —
    // kunci "type_menit", in-memory saja (volatile kalau service restart, sama seperti lastPrayerFired).
    private val notifSeen = LinkedHashSet<String>()
    private fun seenKey(type: String, epochSec: Long) = "${type}_${epochSec / 60}"
    private fun markSeen(key: String) {
        notifSeen.add(key)
        if (notifSeen.size > 40) notifSeen.remove(notifSeen.first())
    }

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
        AzanScheduler.scheduleAll(this) // pasang alarm exact tiap waktu solat (tahan app ditutup)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Dipicu AlarmManager saat waktu solat tiba walau service sempat mati/di-swipe.
        if (intent?.action == ACTION_PLAY_AZAN) {
            val name = intent.getStringExtra(EXTRA_PRAYER_NAME) ?: "Solat"
            firePrayer(name)
            AzanScheduler.scheduleAll(this) // jadwalkan ulang utk besok
        }
        return START_STICKY
    }
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        handler.removeCallbacks(prayerRunnable)
        try { mqttClient?.disconnect(); mqttClient?.close() } catch (_: Exception) {}
        try { adzanPlayer?.release() } catch (_: Exception) {}
        super.onDestroy()
    }

    // ─── MQTT ────────────────────────────────────────────────────────────────

    private val mqttCallback = object : MqttCallback {
        override fun connectionLost(c: Throwable?) {
            Log.w(TAG, "MQTT lost, retry in 10s")
            Thread.sleep(10_000)
            connectMqtt()
        }
        override fun messageArrived(t: String?, m: MqttMessage?) {
            onStatus(m?.toString() ?: return)
        }
        override fun deliveryComplete(t: IMqttDeliveryToken?) {}
    }

    private fun connectMqtt() {
        Thread {
            try {
                val id = "SanyoSvc_" + (100000..999999).random()
                val opts = MqttConnectOptions().apply {
                    isCleanSession = true; connectionTimeout = 10; keepAliveInterval = 30
                }
                mqttClient = MqttClient(BROKER, id, MemoryPersistence()).also {
                    it.setCallback(mqttCallback)
                    it.connect(opts)
                    it.subscribe(TOPIC_STATUS, 0)
                    Log.d(TAG, "MQTT connected")
                }
            } catch (e: Exception) {
                Log.e(TAG, "MQTT connect error: ${e.message}")
            }
        }.start()
    }

    // ESP adalah sumber bersama: status MQTT membawa "prayerTimes" (jadwal solat tersimpan)
    // dan "notifLog" (event air kritis/penuh/solat terakhir) — semua device (App & Web)
    // baca dari sini agar otomatis sinkron, tanpa server sendiri.
    private fun onStatus(payload: String) {
        try {
            val json = org.json.JSONObject(payload)
            json.optJSONObject("prayerTimes")?.let { pt ->
                val prefs = getSharedPreferences(PREF_PRAYER, Context.MODE_PRIVATE).edit()
                pt.keys().forEach { key -> prefs.putString(key, pt.getString(key)) }
                prefs.apply()
                AzanScheduler.scheduleAll(this) // jadwal solat berubah → pasang ulang alarm exact
            }
            json.optJSONArray("notifLog")?.let { mergeNotifLog(it) }
        } catch (_: Exception) {}
    }

    private fun mergeNotifLog(arr: org.json.JSONArray) {
        for (i in 0 until arr.length()) {
            val e = arr.optJSONObject(i) ?: continue
            val epoch = e.optLong("t", 0L)
            if (epoch <= 0L) continue // ESP belum sync NTP saat event ini terjadi — lewati
            val type = e.optString("type")
            val key = seenKey(type, epoch)
            if (notifSeen.contains(key)) continue
            markSeen(key)
            val text = e.optString("text")
            val title = when (type) {
                "water_low"  -> "⚠️ Air Hampir Habis!"
                "water_high" -> "💧 Tangki Hampir Penuh"
                "prayer"     -> "🕌 Waktu Solat"
                else -> "Notifikasi"
            }
            val id = if (type == "prayer") NOTIF_PRAYER else NOTIF_WATER
            notify(id, title, text, type)
        }
    }

    private fun publish(payload: String) = Thread {
        try {
            mqttClient?.publish(TOPIC_CONTROL, MqttMessage(payload.toByteArray()).apply { qos = 0 })
        } catch (_: Exception) {}
    }.start()

    // ─── Prayer Time ─────────────────────────────────────────────────────────

    // Cek per-menit selagi service hidup (cadangan bila alarm exact tak terpasang/terlewat).
    private fun checkPrayer() {
        val prefs = getSharedPreferences(PREF_PRAYER, Context.MODE_PRIVATE)
        val cal = java.util.Calendar.getInstance()
        val now = "%02d:%02d".format(
            cal.get(java.util.Calendar.HOUR_OF_DAY),
            cal.get(java.util.Calendar.MINUTE)
        )
        prayerNames.forEach { name ->
            val t = prefs.getString(name, "") ?: return@forEach
            if (t == now) firePrayer(name)
        }
    }

    // Satu pintu bunyi azan — dipakai checkPrayer() (handler) & alarm exact (onStartCommand).
    // Guard lastPrayerFired per (nama, menit) cegah dobel walau kedua jalur menembak barengan.
    private fun firePrayer(name: String) {
        val cal = java.util.Calendar.getInstance()
        val now = "%02d:%02d".format(
            cal.get(java.util.Calendar.HOUR_OF_DAY),
            cal.get(java.util.Calendar.MINUTE)
        )
        if (lastPrayerFired == "${name}_$now") return
        lastPrayerFired = "${name}_$now"
        notify(NOTIF_PRAYER, "🕌 Waktu $name", "Waktu $name telah tiba — $now WIB", "prayer")
        markSeen(seenKey("prayer", System.currentTimeMillis() / 1000)) // cegah dobel saat notifLog ESP tiba
        publish("BUZZER_5")
        playAdzan()
        Log.d(TAG, "Prayer: $name at $now — buzzer + azan")
    }

    /** Putar suara azan dari res/raw/adzan.mp3 via stream MEDIA (musik).
     *  v3.4.0: USAGE_MEDIA (bukan ALARM) supaya volume azan bisa diatur tombol volume HP.
     *  ponytail: konsekuensi — azan tidak lagi menembus mode senyap; itu trade-off yang
     *  diminta user (kontrol volume > tembus senyap). Balikkan ke USAGE_ALARM bila mau sebaliknya.
     *  User menaruh file di app/src/main/res/raw/adzan.mp3 — jika belum ada, dilewati
     *  (getIdentifier → 0), buzzer ESP tetap jalan. */
    private fun playAdzan() {
        val resId = resources.getIdentifier("adzan", "raw", packageName)
        if (resId == 0) { Log.w(TAG, "res/raw/adzan.mp3 belum ada — lewati audio azan"); return }
        try {
            adzanPlayer?.release()
            val afd = resources.openRawResourceFd(resId) ?: return
            adzanPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                afd.close()
                setOnCompletionListener { it.release(); adzanPlayer = null }
                prepare()
                start()
            }
        } catch (e: Exception) { Log.e(TAG, "azan play error: ${e.message}") }
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
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true).setContentIntent(pi).build()
    }

    private fun notify(id: Int, title: String, text: String, type: String = "") {
        saveNotifHistory(title, text, type)
        val i = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            if (type.isNotEmpty()) putExtra("notif_type", type)
        }
        val pi = PendingIntent.getActivity(
            this, id, i,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = NotificationCompat.Builder(this, CHANNEL_ALERT)
            .setContentTitle(title).setContentText(text)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setAutoCancel(true).setContentIntent(pi).build()
        getSystemService(NotificationManager::class.java).notify(id, n)
    }

    private fun saveNotifHistory(title: String, text: String, type: String) {
        try {
            val prefs = getSharedPreferences(PREF_NOTIF_HISTORY, Context.MODE_PRIVATE)
            val arr = try { org.json.JSONArray(prefs.getString(NOTIF_HISTORY_KEY, "[]")) } catch (_: Exception) { org.json.JSONArray() }
            arr.put(org.json.JSONObject().apply {
                put("title", title); put("text", text)
                put("type", type); put("time", System.currentTimeMillis())
            })
            val trimmed = if (arr.length() > 30) {
                org.json.JSONArray().also { t -> for (i in arr.length() - 30 until arr.length()) t.put(arr.get(i)) }
            } else arr
            prefs.edit().putString(NOTIF_HISTORY_KEY, trimmed.toString()).apply()
        } catch (_: Exception) {}
    }
}
