package com.example.smartfishfeeder.viewmodel

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.smartfishfeeder.data.bluetooth.BluetoothHelper
import com.example.smartfishfeeder.data.model.AIRecommendation
import com.example.smartfishfeeder.data.model.AppNotification
import com.example.smartfishfeeder.data.model.DeviceInfo
import com.example.smartfishfeeder.data.model.FeedingEvent
import com.example.smartfishfeeder.data.model.FeedingSchedule
import com.example.smartfishfeeder.data.model.FishSpecies
import com.example.smartfishfeeder.data.model.WeatherData
import com.example.smartfishfeeder.data.repository.AppStateRepository
import com.example.smartfishfeeder.data.repository.DeviceRepository
import com.example.smartfishfeeder.data.repository.FeederRepository
import com.example.smartfishfeeder.data.repository.HistoryRepository
import com.example.smartfishfeeder.data.repository.NotificationRepository
import com.example.smartfishfeeder.data.repository.RecommendationRepository
import com.example.smartfishfeeder.data.repository.ScheduleRepository
import com.example.smartfishfeeder.data.repository.WeatherRepository
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DashboardViewModel : ViewModel() {

    private val feederRepository = FeederRepository()
    private val weatherRepository = WeatherRepository()
    private val scheduleRepository = ScheduleRepository()
    private val historyRepository = HistoryRepository()
    private val recommendationRepository = RecommendationRepository()
    private val notificationRepository = NotificationRepository()
    private val appStateRepository = AppStateRepository()
    private val deviceRepository = DeviceRepository()

    private val auth = FirebaseAuth.getInstance()

    val deviceStatus = feederRepository.getDeviceStatus()

    val temperatureReading = feederRepository.getTemperatureReading()

    var weatherData: WeatherData by mutableStateOf(
        weatherRepository.getWeatherData()
    )
        private set

    val feedingSchedules = mutableStateListOf<FeedingSchedule>()

    val feedingHistory = mutableStateListOf<FeedingEvent>()

    var aiRecommendation: AIRecommendation by mutableStateOf(
        recommendationRepository.getAIRecommendation()
    )
        private set

    val notifications = mutableStateListOf<AppNotification>()
    var lastSeenNotificationId: String? by mutableStateOf(null)
        private set

    var scheduleEnabled: Boolean by mutableStateOf(true)
        private set

    // Which species the AI recommendation is calculated for. Defaults to
    // Tilapia. Not persisted yet, resets to the default on relaunch, same
    // as scheduleEnabled was before AppStateRepository, say the word if you
    // want this saved the same way.
    var selectedSpecies: FishSpecies by mutableStateOf(FishSpecies.TILAPIA)
        private set

    // Live ESP32 status, written by the device itself (once its firmware
    // exists) and observed here in real time via a Firestore listener —
    // no polling or manual refresh needed. Starts as DeviceInfo() (offline,
    // empty) until the device has ever connected.
    var deviceInfo: DeviceInfo by mutableStateOf(DeviceInfo())
        private set

    // Manual pond location override — when enabled, weather fetches (and
    // StormCheckWorker's background checks, via cacheLocation reusing the
    // same coordinates) use this instead of the phone's live GPS. Useful
    // when the pond isn't wherever the phone happens to be.
    var useCustomLocation: Boolean by mutableStateOf(false)
        private set
    var customLatitude: Double by mutableStateOf(0.0)
        private set
    var customLongitude: Double by mutableStateOf(0.0)
        private set
    var customLocationName: String by mutableStateOf("")
        private set

    init {
        auth.addAuthStateListener { firebaseAuth ->

            if (firebaseAuth.currentUser != null) {
                loadSchedules()
                loadHistory()
                loadNotifications()
                loadLastKnownState()
            } else {
                feedingSchedules.clear()
                feedingHistory.clear()
                notifications.clear()
            }
        }

        viewModelScope.launch {
            deviceRepository.observeDeviceStatus().collect { info ->
                deviceInfo = info
            }
        }
    }

    private fun loadSchedules() {
        viewModelScope.launch {
            try {
                val schedules = scheduleRepository.getFeedingSchedules()
                feedingSchedules.clear()
                feedingSchedules.addAll(schedules)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun loadHistory() {
        viewModelScope.launch {
            try {
                val history = historyRepository.getFeedingHistory()
                feedingHistory.clear()
                feedingHistory.addAll(history)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun loadNotifications() {
        viewModelScope.launch {
            try {
                val saved = notificationRepository.getNotifications()
                notifications.clear()
                notifications.addAll(saved.sortedByDescending { it.id })
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun loadLastKnownState() {
        viewModelScope.launch {
            try {
                appStateRepository.getLastWeather()?.let { weatherData = it }
                appStateRepository.getLastAIRecommendation()?.let { aiRecommendation = it }
                appStateRepository.getScheduleEnabled()?.let { scheduleEnabled = it }
                lastSeenNotificationId = appStateRepository.getLastSeenNotificationId()
                appStateRepository.getCustomLocation()?.let { saved ->
                    useCustomLocation = saved.enabled
                    customLatitude = saved.latitude
                    customLongitude = saved.longitude
                    customLocationName = saved.displayName
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /** Called from Settings when the user sets or clears a manual pond location. */
    fun setCustomLocation(enabled: Boolean, latitude: Double, longitude: Double, displayName: String) {
        useCustomLocation = enabled
        customLatitude = latitude
        customLongitude = longitude
        customLocationName = displayName
        viewModelScope.launch {
            try {
                appStateRepository.saveCustomLocation(enabled, latitude, longitude, displayName)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * Converts a typed address into coordinates and saves it as the custom
     * pond location, all in one step. Returns true on success — the caller
     * (SettingsScreen) uses this to show a success or error message.
     */
    suspend fun geocodeAndSaveLocation(address: String): Boolean {
        val result = weatherRepository.geocodeAddress(address) ?: return false
        setCustomLocation(true, result.latitude, result.longitude, result.displayName)
        return true
    }

    /** Called when the user picks a different species on the AI screen. */
    fun selectSpecies(species: FishSpecies) {
        selectedSpecies = species
    }

    fun updateScheduleEnabled(index: Int, enabled: Boolean) {
        if (index !in feedingSchedules.indices) return
        val updatedSchedule = feedingSchedules[index].copy(enabled = enabled)
        feedingSchedules[index] = updatedSchedule
        viewModelScope.launch {
            try {
                scheduleRepository.saveFeedingSchedule(updatedSchedule)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun updateScheduleTime(index: Int, newTime: String) {
        if (index !in feedingSchedules.indices) return
        val updatedSchedule = feedingSchedules[index].copy(feedingTime = newTime)
        feedingSchedules[index] = updatedSchedule
        viewModelScope.launch {
            try {
                scheduleRepository.saveFeedingSchedule(updatedSchedule)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun updateScheduleFeedingType(index: Int, feedingType: String) {
        if (index !in feedingSchedules.indices) return

        val updatedSchedule = feedingSchedules[index].copy(
            feedingType = feedingType
        )

        feedingSchedules[index] = updatedSchedule

        viewModelScope.launch {
            try {
                scheduleRepository.saveFeedingSchedule(updatedSchedule)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun deleteSchedule(index: Int) {
        if (index !in feedingSchedules.indices) return
        val scheduleToDelete = feedingSchedules[index]
        feedingSchedules.removeAt(index)
        viewModelScope.launch {
            try {
                scheduleRepository.deleteFeedingSchedule(scheduleToDelete.id)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun setScheduleFeedingEnabled(enabled: Boolean) {
        scheduleEnabled = enabled
        viewModelScope.launch {
            try {
                appStateRepository.saveScheduleEnabled(enabled)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    suspend fun refreshWeather(latitude: Double, longitude: Double) {
        val result = weatherRepository.fetchWeather(latitude, longitude)
        if (result != null) {
            weatherData = result
            try {
                appStateRepository.saveLastWeather(result)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * Sends current sensor + weather readings, plus the selected species,
     * to Gemini. Passes the real (possibly null) water temperature from
     * the ESP32 straight through — no longer silently substitutes the old
     * mock value when the device hasn't connected, since that made Gemini's
     * recommendation look like it was based on real data when it wasn't.
     * Also passes waterSensorOk, so RecommendationRepository can tell
     * Gemini whether that temperature is a live reading or a preserved
     * stale one from before the sensor stopped responding.
     */
    suspend fun refreshAIRecommendation() {
        val result = recommendationRepository.fetchAIRecommendation(
            waterTemperature = deviceInfo.waterTemperature,
            waterSensorOk = deviceInfo.waterSensorOk,
            airTemperature = weatherData.temperature,
            humidity = weatherData.humidity,
            weatherCondition = weatherData.weatherCondition,
            species = selectedSpecies
        )
        if (result != null) {
            aiRecommendation = result
            try {
                appStateRepository.saveLastAIRecommendation(result)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * Caches the current GPS location so StormCheckWorker can check
     * forecasts in the background using this saved location, instead of
     * needing ACCESS_BACKGROUND_LOCATION to fetch a fresh one itself.
     */
    fun cacheLocation(latitude: Double, longitude: Double) {
        viewModelScope.launch {
            try {
                appStateRepository.saveLastLocation(latitude, longitude)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun addFeedingSchedule(time: String) {
        val newSchedule = FeedingSchedule(
            id = "SCHEDULE${System.currentTimeMillis()}",
            feedingTime = time,
            enabled = true,
            feedingType = "Normal"
        )
        feedingSchedules.add(newSchedule)
        viewModelScope.launch {
            try {
                scheduleRepository.saveFeedingSchedule(newSchedule)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun recordFeedingEvent(automatic: Boolean) {
        val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
        val newEvent = FeedingEvent(
            id = "EVENT${System.currentTimeMillis()}",
            dateTime = timeFormat.format(Date()),
            automatic = automatic,
            status = "Command Sent"
        )
        feedingHistory.add(0, newEvent)
        viewModelScope.launch {
            try {
                historyRepository.saveFeedingEvent(newEvent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * Sends the "feed now" command to the ESP32. When online, this goes
     * through Firestore (DeviceRepository.sendFeedNowCommand) as before.
     * When offline, it falls back to a direct classic-Bluetooth connection
     * (BluetoothHelper) to the paired ESP32, sending the same "FEED"
     * string directly instead of queuing a command the device can't see.
     *
     * Call this alongside recordFeedingEvent() when the user taps Feed Now
     * — recordFeedingEvent() logs it in the app's history immediately
     * either way; this actually tells the device to do it.
     */
    fun sendFeedNowCommand(context: Context, isOnline: Boolean) {
        viewModelScope.launch {
            if (isOnline) {
                try {
                    deviceRepository.sendFeedNowCommand()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            } else {
                val success = BluetoothHelper(context).sendFeedCommand()
                if (!success) {
                    addNotification(
                        "Couldn't reach the feeder over Bluetooth — check it's paired and in range."
                    )
                }
            }
        }
    }

    /** The UID to hardcode into the ESP32 firmware so it knows which Firestore path to use. */
    fun getPairingId(): String? = deviceRepository.getPairingId()

    override fun onCleared() {
        super.onCleared()
    }
    fun markNotificationsAsSeen() {
        val newestNotificationId = notifications.firstOrNull()?.id ?: return

        lastSeenNotificationId = newestNotificationId

        viewModelScope.launch {
            try {
                appStateRepository.saveLastSeenNotificationId(
                    newestNotificationId
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun addNotification(message: String) {
        val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
        val newNotification = AppNotification(
            id = "NOTIF${System.currentTimeMillis()}",
            message = message,
            timestamp = timeFormat.format(Date())
        )
        notifications.add(0, newNotification)
        viewModelScope.launch {
            try {
                notificationRepository.saveNotification(newNotification)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}