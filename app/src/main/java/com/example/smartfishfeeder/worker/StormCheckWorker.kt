package com.example.smartfishfeeder.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.smartfishfeeder.data.model.AppNotification
import com.example.smartfishfeeder.data.repository.AppStateRepository
import com.example.smartfishfeeder.data.repository.NotificationRepository
import com.example.smartfishfeeder.data.repository.WeatherRepository
import com.example.smartfishfeeder.notification.NotificationHelper
import com.google.firebase.auth.FirebaseAuth
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val TAG = "StormCheckWorker"

/**
 * Runs periodically in the background (even with the app closed) to check
 * whether a storm is forecast for the next few hours near the last known
 * pond location, and notifies the user if so. Uses the last GPS location
 * saved during normal foreground app use (AppStateRepository) rather than
 * requesting a fresh location fix, so it doesn't need
 * ACCESS_BACKGROUND_LOCATION.
 */
class StormCheckWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val appStateRepository = AppStateRepository()
    private val weatherRepository = WeatherRepository()
    private val notificationRepository = NotificationRepository()

    override suspend fun doWork(): Result {
        if (FirebaseAuth.getInstance().currentUser == null) {
            Log.d(TAG, "Skipped: no user signed in")
            return Result.success()
        }

        val location = appStateRepository.getLastLocation()
        if (location == null) {
            Log.d(TAG, "Skipped: no cached location yet (open the app with location permission first)")
            return Result.success()
        }
        Log.d(TAG, "Checking forecast for lat=${location.first}, lon=${location.second}")

        val incoming = weatherRepository.fetchIncomingStorm(location.first, location.second)
        if (incoming == null) {
            Log.d(TAG, "No storm in the next ~6 hours (or the forecast fetch failed)")
            return Result.success()
        }

        val (condition, etaLabel) = incoming
        Log.d(TAG, "Storm found: $condition, $etaLabel")

        // Shared with the reactive (current-conditions) alert so the two
        // don't double-notify for the same ongoing storm.
        val lastNotified = NotificationHelper.getLastNotifiedCondition(applicationContext)
        if (lastNotified == condition) {
            Log.d(TAG, "Skipped: already notified for '$condition' (use Reset Storm Alert to force it)")
            return Result.success()
        }

        NotificationHelper.showIncomingStormAlert(applicationContext, condition, etaLabel)
        NotificationHelper.setLastNotifiedCondition(applicationContext, condition)
        Log.d(TAG, "Notification sent for $condition $etaLabel")

        val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
        val newNotification = AppNotification(
            id = "NOTIF${System.currentTimeMillis()}",
            message = "Weather Alert: $condition expected $etaLabel near your pond.",
            timestamp = timeFormat.format(Date())
        )
        try {
            notificationRepository.saveNotification(newNotification)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return Result.success()
    }
}