package com.yesmin.ai.automation

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.yesmin.ai.MainActivity

/** Persistent notification with a tap-to-call shortcut (user enabled in Settings). */
object QuickAccess {
    private const val ID = 77

    fun show(ctx: Context) {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(NotificationChannel("quick", "Quick access", NotificationManager.IMPORTANCE_LOW))
        }
        fun pi(code: Int, route: String) = PendingIntent.getActivity(
            ctx, code, Intent(ctx, MainActivity::class.java).putExtra("route", route),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = NotificationCompat.Builder(ctx, "quick")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("Yesmin AI")
            .setContentText("Tap to talk to Yesmin")
            .setContentIntent(pi(1, "call"))
            .addAction(0, "Chat", pi(2, "chat"))
            .setOngoing(true)
            .build()
        try { nm.notify(ID, n) } catch (e: SecurityException) { }
    }

    fun hide(ctx: Context) {
        (ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(ID)
    }
}
