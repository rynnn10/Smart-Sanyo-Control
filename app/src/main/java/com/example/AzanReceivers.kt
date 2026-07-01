package com.example

// Update: Rab 01/07/2026 - v3.4.0
// Azan tahan app ditutup total: AlarmManager exact per waktu solat + restart setelah boot.
// - AzanScheduler.scheduleAll(): pasang alarm persis (setExactAndAllowWhileIdle) tiap solat.
// - AzanAlarmReceiver: dipicu alarm → start SanyoService (ACTION_PLAY_AZAN) walau app mati.
// - BootReceiver: HP nyala → start SanyoService (yg lalu pasang ulang alarm).
// ponytail: force-stop manual oleh user tetap tak bisa dilawan (batasan Android) — itu wajar.
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import java.util.Calendar

object AzanScheduler {
    private const val TAG = "AzanScheduler"
    private const val REQ_BASE = 4200
    val PRAYERS = listOf("Subuh", "Dzuhur", "Ashar", "Maghrib", "Isya")

    private fun pendingIntent(ctx: Context, index: Int, name: String): PendingIntent {
        val i = Intent(ctx, AzanAlarmReceiver::class.java).apply {
            action = SanyoService.ACTION_PLAY_AZAN
            putExtra(SanyoService.EXTRA_PRAYER_NAME, name)
        }
        var flags = PendingIntent.FLAG_UPDATE_CURRENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flags = flags or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getBroadcast(ctx, REQ_BASE + index, i, flags)
    }

    /** Pasang alarm exact untuk tiap waktu solat tersimpan pada kemunculan berikutnya. */
    fun scheduleAll(ctx: Context) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val prefs = ctx.getSharedPreferences(SanyoService.PREF_PRAYER, Context.MODE_PRIVATE)
        val exactOk = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
        PRAYERS.forEachIndexed { idx, name ->
            val hhmm = prefs.getString(name, null) ?: return@forEachIndexed
            val parts = hhmm.split(":")
            val hh = parts.getOrNull(0)?.toIntOrNull() ?: return@forEachIndexed
            val mm = parts.getOrNull(1)?.toIntOrNull() ?: return@forEachIndexed
            val cal = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hh); set(Calendar.MINUTE, mm)
                set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_MONTH, 1)
            }
            val pi = pendingIntent(ctx, idx, name)
            try {
                // ponytail: exact bila diizinkan; kalau tidak, allow-while-idle (inexact) — tetap bangun di Doze
                if (exactOk) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi)
                else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi)
            } catch (e: SecurityException) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi)
            }
        }
        Log.d(TAG, "Alarm azan terpasang (exact=$exactOk)")
    }
}

class AzanAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val name = intent.getStringExtra(SanyoService.EXTRA_PRAYER_NAME) ?: "Solat"
        val svc = Intent(context, SanyoService::class.java).apply {
            action = SanyoService.ACTION_PLAY_AZAN
            putExtra(SanyoService.EXTRA_PRAYER_NAME, name)
        }
        ContextCompat.startForegroundService(context, svc)
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // HP baru nyala → hidupkan service (onCreate-nya memasang ulang alarm azan).
        ContextCompat.startForegroundService(context, Intent(context, SanyoService::class.java))
    }
}
