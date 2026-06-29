package com.example

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class MyFirebaseMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        // Tangkap data dari Google Apps Script
        val title = remoteMessage.notification?.title ?: "Peringatan Sanyo"
        val body = remoteMessage.notification?.body ?: "Cek status air Anda."

        showNotification(title, body)
    }

    private fun showNotification(title: String, body: String) {
        val channelId = "sanyo_notif_channel"
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )

val notificationBuilder = NotificationCompat.Builder(this, channelId)
    .setSmallIcon(R.drawable.ic_icon_sanyo)
    .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Notifikasi Sanyo", NotificationManager.IMPORTANCE_HIGH)
            notificationManager.createNotificationChannel(channel)
        }

        notificationManager.notify(0, notificationBuilder.build())
    }

    override fun onNewToken(token: String) {
        // Cetak token ini di Logcat Android Studio/VS Code. 
        // Token ini yang akan kita masukkan ke Google Apps Script nanti!
        println("FCM TOKEN BARU: $token") 
    }
}