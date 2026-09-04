package com.example.smartfishfeeder.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

object NotificationHelper {

    private const val CHANNEL_ID = "storm_alerts"
    private const val CHANNEL_NAME = "Storm Alerts"
    private const val NOTIFICATION_ID = 1001

    private const val PREFS_NAME = "notification_prefs"
    private const val KEY_LAST_NOTIFIED_CONDITION = "last_notified_condition"

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts when a storm is detected or forecast near your pond"
            }
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun hasNotificationPermission(context: Context): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
    }

    /** Reactive alert: the storm is happening right now. */
    fun showStormAlert(context: Context, weatherCondition: String) {
        if (!hasNotificationPermission(context)) return

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("Weather Alert")
            .setContentText("$weatherCondition detected near your pond. Consider pausing feeding.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    /** Predictive alert: a storm is forecast to arrive soon, from StormCheckWorker. */
    fun showIncomingStormAlert(context: Context, weatherCondition: String, etaLabel: String) {
        if (!hasNotificationPermission(context)) return

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("Storm Approaching")
            .setContentText("$weatherCondition expected $etaLabel near your pond. Consider pausing feeding.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    /**
     * The weather condition ("Thunderstorm", "Rain", etc.) we last actually
     * sent a notification for, persisted to disk so it survives an app
     * relaunch. Shared between the reactive and predictive alerts, so
     * they don't double-notify for the same storm. Returns null if
     * nothing has been notified yet (or it was reset because the storm
     * cleared).
     */
    fun getLastNotifiedCondition(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_LAST_NOTIFIED_CONDITION, null)
    }

    /** Pass null to reset, e.g. once the storm has cleared, so the next storm notifies fresh. */
    fun setLastNotifiedCondition(context: Context, condition: String?) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_LAST_NOTIFIED_CONDITION, condition).apply()
    }
}