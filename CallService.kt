package com.yesmin.ai.voice

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.yesmin.ai.MainActivity
import com.yesmin.ai.Yesmin

/** Foreground service (microphone type) so a call keeps working if you switch to another app. */
class CallService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_END) {
            Yesmin.call.stop()
            stopSelf()
            return START_NOT_STICKY
        }
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(NotificationChannel("call", "Yesmin call", NotificationManager.IMPORTANCE_LOW))
        }
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).putExtra("route", "call"),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val end = PendingIntent.getService(
            this, 1, Intent(this, CallService::class.java).setAction(ACTION_END),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n: Notification = NotificationCompat.Builder(this, "call")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("Yesmin call in progress")
            .setContentText("Microphone is active")
            .setContentIntent(open)
            .addAction(0, "End call", end)
            .setOngoing(true)
            .build()
        try {
            val type = if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0
            ServiceCompat.startForeground(this, 41, n, type)
        } catch (e: Exception) {
            Yesmin.call.stop()
            stopSelf()
        }
        return START_NOT_STICKY
    }

    companion object {
        const val ACTION_END = "com.yesmin.ai.END_CALL"
    }
}
