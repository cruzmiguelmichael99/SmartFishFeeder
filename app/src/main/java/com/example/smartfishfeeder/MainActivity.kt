package com.example.smartfishfeeder

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.smartfishfeeder.data.location.LocationHelper
import com.example.smartfishfeeder.notification.NotificationHelper
import com.example.smartfishfeeder.ui.auth.LoginScreen
import com.example.smartfishfeeder.ui.common.BluetoothPermissionEffect
import com.example.smartfishfeeder.ui.common.LocationPermissionEffect
import com.example.smartfishfeeder.ui.common.NotificationPermissionEffect
import com.example.smartfishfeeder.ui.theme.SmartFishFeederTheme
import com.example.smartfishfeeder.viewmodel.AuthViewModel
import com.example.smartfishfeeder.viewmodel.DashboardViewModel
import com.example.smartfishfeeder.worker.StormCheckWorker
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {

    private val viewModel: DashboardViewModel by viewModels()
    private val authViewModel: AuthViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Checks the forecast for an incoming storm every 30 minutes, even
        // with the app closed. KEEP means re-launching the app doesn't
        // reset or duplicate this — it just keeps running on its existing
        // schedule. No-ops internally if the user isn't logged in or has
        // no cached location yet (see StormCheckWorker).
        val stormCheckRequest = PeriodicWorkRequestBuilder<StormCheckWorker>(30, TimeUnit.MINUTES)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "storm_check",
            ExistingPeriodicWorkPolicy.KEEP,
            stormCheckRequest
        )

        setContent {
            SmartFishFeederTheme {
                val currentUser = authViewModel.currentUser

                if (currentUser == null) {

                    LoginScreen(
                        authViewModel = authViewModel
                    )

                } else {

                    val context = LocalContext.current

                    var locationGranted by remember {
                        mutableStateOf(false)
                    }

                    var notificationGranted by remember {
                        mutableStateOf(false)
                    }

                    // Requested up front (not just when Feed Now is tapped
                    // offline) so the permission dialog doesn't surprise
                    // the user mid-action the first time they're actually
                    // offline and need the Bluetooth fallback.
                    var bluetoothGranted by remember {
                        mutableStateOf(false)
                    }

                    LaunchedEffect(Unit) {
                        NotificationHelper.createNotificationChannel(context)
                    }

                    LocationPermissionEffect { granted ->
                        locationGranted = granted
                    }

                    NotificationPermissionEffect { granted ->
                        notificationGranted = granted
                    }

                    BluetoothPermissionEffect { granted ->
                        bluetoothGranted = granted
                    }

                    // Fetches weather, refreshes the AI recommendation, and caches
                    // the location for StormCheckWorker to reuse in the background.
                    // Uses the manually-set pond location (Settings > Pond Location)
                    // when one is set, since the pond isn't necessarily wherever
                    // the phone happens to be — falls back to live phone GPS
                    // otherwise. Runs once per successful location resolution, to
                    // avoid the earlier duplicate-Gemini-call rate-limit issue.
                    LaunchedEffect(locationGranted, viewModel.useCustomLocation) {
                        val coords = if (viewModel.useCustomLocation) {
                            viewModel.customLatitude to viewModel.customLongitude
                        } else if (locationGranted) {
                            LocationHelper(context).getCurrentLocation()
                        } else {
                            null
                        }

                        if (coords != null) {
                            viewModel.refreshWeather(coords.first, coords.second)
                            viewModel.refreshAIRecommendation()
                            viewModel.cacheLocation(coords.first, coords.second)

                            val currentWeather = viewModel.weatherData
                            val stormyConditions = listOf("Thunderstorm", "Rain")
                            val isStorm = stormyConditions.any {
                                currentWeather.weatherCondition.equals(it, ignoreCase = true)
                            }

                            val lastNotified = NotificationHelper.getLastNotifiedCondition(context)

                            if (isStorm && lastNotified != currentWeather.weatherCondition) {
                                viewModel.addNotification(
                                    "Weather Alert: ${currentWeather.weatherCondition} detected near your pond."
                                )
                                if (notificationGranted) {
                                    NotificationHelper.showStormAlert(
                                        context,
                                        currentWeather.weatherCondition
                                    )
                                }
                                NotificationHelper.setLastNotifiedCondition(
                                    context,
                                    currentWeather.weatherCondition
                                )
                            } else if (!isStorm && lastNotified != null) {
                                NotificationHelper.setLastNotifiedCondition(context, null)
                            }
                        }
                    }

                    MainScreen(
                        viewModel = viewModel,
                        authViewModel = authViewModel
                    )
                }
            }
        }
    }
}