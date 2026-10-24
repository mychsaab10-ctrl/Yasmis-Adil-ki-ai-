package com.yesmin.ai.automation

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.flow.MutableStateFlow

data class NotifItem(val pkg: String, val title: String, val text: String, val time: Long)

/** Works only after the user grants Notification Access. Kept in memory only (never saved to disk). */
class YesminNotificationListener : NotificationListenerService() {

    override fun onListenerConnected() {
        connected = true
        try {
            val now = activeNotifications?.mapNotNull { convert(it) }.orEmpty()
            recent.value = now.sortedByDescending { it.time }.take(30)
        } catch (e: Exception) { }
    }

    override fun onListenerDisconnected() {
        connected = false
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val item = sbn?.let { convert(it) } ?: return
        recent.value = (listOf(item) + recent.value).take(30)
    }

    private fun convert(sbn: StatusBarNotification): NotifItem? {
        if (sbn.packageName == packageName || sbn.isOngoing) return null
        val e = sbn.notification.extras ?: return null
        val title = e.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = e.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        if (title.isBlank() && text.isBlank()) return null
        return NotifItem(sbn.packageName, title, text, sbn.postTime)
    }

    companion object {
        val recent = MutableStateFlow<List<NotifItem>>(emptyList())
        @Volatile var connected = false
    }
}
