package com.example.smartfishfeeder.ui.dashboard

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.DeviceThermostat
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.smartfishfeeder.R
import com.example.smartfishfeeder.data.location.LocationHelper
import com.example.smartfishfeeder.data.model.AppNotification
import com.example.smartfishfeeder.ui.common.rememberIsOnline
import com.example.smartfishfeeder.util.nextFeedingTime
import com.example.smartfishfeeder.viewmodel.AuthViewModel
import com.example.smartfishfeeder.viewmodel.DashboardViewModel
import kotlinx.coroutines.launch


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Dashboard(
    viewModel: DashboardViewModel,
    authViewModel: AuthViewModel,
    onFeedNowClick: () -> Unit,
    modifier: Modifier = Modifier
) {

    val deviceStatus = viewModel.deviceStatus
    val weatherData = viewModel.weatherData
    val feedingSchedules = viewModel.feedingSchedules
    val aiRecommendation = viewModel.aiRecommendation
    val deviceInfo = viewModel.deviceInfo

    // Real, live internet connectivity — updates immediately on WiFi/data
    // connect or disconnect, independent of deviceStatus (which is still
    // mock data for the feeder hardware itself, not the phone's connection).
    val isOnline by rememberIsOnline()

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isRefreshing by remember { mutableStateOf(false) }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = {
            coroutineScope.launch {
                isRefreshing = true
                // Respects the manual pond location (Settings > Pond Location)
                // when one is set, same as MainActivity's initial fetch —
                // this used to always use phone GPS regardless of that
                // setting, silently overwriting a custom location's weather
                // the moment you pulled to refresh.
                if (viewModel.useCustomLocation) {
                    viewModel.refreshWeather(viewModel.customLatitude, viewModel.customLongitude)
                } else {
                    val locationHelper = LocationHelper(context)
                    if (locationHelper.hasLocationPermission()) {
                        val coords = locationHelper.getCurrentLocation()
                        if (coords != null) {
                            viewModel.refreshWeather(coords.first, coords.second)
                        }
                    }
                }
                isRefreshing = false
            }
        },
        modifier = modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {

            Spacer(Modifier.height(12.dp))

            DashboardHeader(
                authViewModel = authViewModel,
                notifications = viewModel.notifications,
                viewModel = viewModel
            )

            Spacer(Modifier.height(16.dp))

            DeviceStatusBanner(isOnline = isOnline)

            Spacer(Modifier.height(16.dp))

            val stormyConditions = listOf("Thunderstorm", "Rain")
            if (stormyConditions.any { weatherData.weatherCondition.equals(it, ignoreCase = true) }) {
                StormWarningCard()
                Spacer(Modifier.height(12.dp))
            }

            if (deviceInfo.feedLevelLow) {
                FeedLevelWarningCard()
                Spacer(Modifier.height(12.dp))
            }

            AIRecommendationCard(recommendation = aiRecommendation.recommendation)

            Spacer(Modifier.height(16.dp))

            PondImagePlaceholder()

            Spacer(Modifier.height(20.dp))

            Text(
                text = "Live Environmental Data",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Filled.Thermostat,
                    label = "Water Temperature",
                    value = deviceInfo.waterTemperature?.let { "$it°C" } ?: "No data",
                    source = when {
                        !deviceInfo.isOnline -> "Device not connected"
                        !deviceInfo.waterSensorOk -> "Sensor issue, showing last reading"
                        else -> "From Pond Sensor"
                    }
                )
                StatCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Filled.DeviceThermostat,
                    label = "Air Temperature",
                    value = "${weatherData.temperature}°C",
                    source = "From OpenWeather"
                )
            }

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Filled.WaterDrop,
                    label = "Humidity",
                    value = "${weatherData.humidity}%",
                    source = "From OpenWeather"
                )
                StatCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Filled.Cloud,
                    label = "Weather Condition",
                    value = weatherData.weatherCondition,
                    source = "Updated just now"
                )
            }

            Spacer(Modifier.height(20.dp))

            FeederStatusCard(
                nextFeedingTime = feedingSchedules.nextFeedingTime() ?: "No schedule",
                isBusy = false,
                onFeedNow = {
                    onFeedNowClick()
                }
            )

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun DashboardHeader(
    authViewModel: AuthViewModel,
    notifications: List<AppNotification>,
    viewModel: DashboardViewModel
) {
    var showMenu by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showNotifications by remember { mutableStateOf(false) }

    // Tracks how many notifications the user has already seen (opened the
    // dropdown for). The bell shows a red dot whenever there are more
    // notifications than that, i.e. new ones arrived since the last open.
    val hasUnread =
        notifications.firstOrNull()?.id != null &&
                notifications.first().id != viewModel.lastSeenNotificationId

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box {
            IconButton(onClick = { showMenu = true }) {
                Icon(
                    imageVector = Icons.Filled.Menu,
                    contentDescription = "Menu"
                )
            }
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false }
            ) {
                DropdownMenuItem(
                    text = { Text("About") },
                    onClick = {
                        showMenu = false
                        showAboutDialog = true
                    }
                )
                DropdownMenuItem(
                    text = { Text("Logout") },
                    onClick = {
                        showMenu = false
                        authViewModel.signOut()
                    }
                )
            }
        }
        Spacer(Modifier.width(4.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = "Smart Fish Feeder",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Fish Pond Monitor",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Box {
            IconButton(
                onClick = {
                    showNotifications = true
                    viewModel.markNotificationsAsSeen()
                }
            ) {
                Icon(
                    imageVector = Icons.Filled.Notifications,
                    contentDescription = "Notifications",
                    modifier = Modifier.size(24.dp)
                )
            }
            if (hasUnread) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .align(Alignment.TopEnd)
                        .background(Color(0xFFD93025), CircleShape)
                )
            }
            DropdownMenu(
                expanded = showNotifications,
                onDismissRequest = { showNotifications = false }
            ) {
                if (notifications.isEmpty()) {
                    DropdownMenuItem(
                        text = { Text("No notifications yet") },
                        onClick = { }
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .heightIn(max = 260.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        notifications.forEach { notification ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(
                                            text = notification.message,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                        Text(
                                            text = notification.timestamp,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                },
                                onClick = { }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            confirmButton = {
                TextButton(onClick = { showAboutDialog = false }) {
                    Text("OK")
                }
            },
            title = { Text("About") },
            text = {
                Text(
                    "Smart Fish Feeder monitors your pond and automates feeding " +
                            "using live sensor and weather data."
                )
            }
        )
    }
}

@Composable
private fun DeviceStatusBanner(isOnline: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = if (isOnline) Color(0xFFE7F7EC) else Color(0xFFFBEAEA),
                shape = RoundedCornerShape(12.dp)
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(
                    color = if (isOnline) Color(0xFF34A853) else Color(0xFFD93025),
                    shape = CircleShape
                )
        )
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = if (isOnline) "Online" else "Offline",
                fontWeight = FontWeight.SemiBold,
                color = if (isOnline) Color(0xFF1E8E3E) else Color(0xFFB3261E)
            )
            Text(
                text = if (isOnline) "Connected to the internet" else "No internet connection",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            imageVector = Icons.Filled.Wifi,
            contentDescription = "Wifi",
            tint = if (isOnline) Color(0xFF1E8E3E) else Color(0xFFB3261E)
        )
    }
}

@Composable
private fun StormWarningCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFBEAEA))
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.WarningAmber,
                contentDescription = null,
                tint = Color(0xFFB3261E)
            )
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    text = "Weather Alert",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Rain or storm conditions are near your pond. Consider pausing feeding.",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFB3261E)
                )
            }
        }
    }
}

@Composable
private fun FeedLevelWarningCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF4E5))
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.WarningAmber,
                contentDescription = null,
                tint = Color(0xFFB26A00)
            )
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    text = "Feed Level Low",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "The feeder hopper is running low. Consider refilling soon.",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFB26A00)
                )
            }
        }
    }
}

@Composable
private fun AIRecommendationCard(recommendation: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFE7F7EC))
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.AutoAwesome,
                contentDescription = null,
                tint = Color(0xFF1E8E3E)
            )
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    text = "AI Recommendation",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = recommendation,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1E8E3E)
                )
            }
        }
    }
}

@Composable
private fun PondImagePlaceholder() {
    Image(
        painter = painterResource(id = R.drawable.pond_image),
        contentDescription = "Your pond",
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp)
            .clip(RoundedCornerShape(16.dp))
    )
}

@Composable
private fun StatCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    label: String,
    value: String,
    source: String
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.padding(14.dp)) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = source,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun FeederStatusCard(
    nextFeedingTime: String,
    isBusy: Boolean,
    onFeedNow: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Next Feeding",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = nextFeedingTime,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Box(
                    modifier = Modifier
                        .background(Color(0xFFF1F1F1), shape = RoundedCornerShape(20.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = if (isBusy) "Feeding" else "Idle",
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onFeedNow,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Restaurant,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text("FEED NOW", fontWeight = FontWeight.Bold)
            }
        }
    }
}