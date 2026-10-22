package br.com.jeaxlabs.os

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import org.json.JSONArray
import org.json.JSONObject

class JeaxNotificationListener : NotificationListenerService() {
    override fun onListenerConnected() {
        super.onListenerConnected()
        saveActiveNotifications()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        saveActiveNotifications()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        saveActiveNotifications()
    }

    private fun saveActiveNotifications() {
        try {
            val active = activeNotifications ?: emptyArray()
            val result = JSONArray()
            active.sortedByDescending { it.postTime }.take(40).forEach { sbn ->
                val notification = sbn.notification ?: return@forEach
                val extras = notification.extras
                val title = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
                val text = (
                    extras?.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
                        ?: extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString()
                        ?: ""
                    ).trim()
                if (title.isBlank() && text.isBlank()) return@forEach
                val appName = try {
                    val info = packageManager.getApplicationInfo(sbn.packageName, 0)
                    packageManager.getApplicationLabel(info).toString()
                } catch (_: Exception) {
                    sbn.packageName
                }
                result.put(JSONObject().apply {
                    put("key", sbn.key)
                    put("package", sbn.packageName)
                    put("app", appName)
                    put("title", title)
                    put("text", text)
                    put("posted", sbn.postTime)
                })
            }
            getSharedPreferences("jeax_notifications", MODE_PRIVATE)
                .edit()
                .putString("active", result.toString())
                .apply()
        } catch (_: Exception) {
        }
    }
}