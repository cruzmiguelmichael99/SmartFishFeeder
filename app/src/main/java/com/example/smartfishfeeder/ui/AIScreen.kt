package com.example.smartfishfeeder.ui.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.smartfishfeeder.data.location.LocationHelper
import com.example.smartfishfeeder.data.model.FishSpecies
import com.example.smartfishfeeder.util.nextFeedingTime
import com.example.smartfishfeeder.viewmodel.DashboardViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AIScreen(viewModel: DashboardViewModel) {

    val recommendation = viewModel.aiRecommendation
    val weatherData = viewModel.weatherData
    val deviceInfo = viewModel.deviceInfo
    val feedingSchedules = viewModel.feedingSchedules

    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    var isRefreshing by remember { mutableStateOf(false) }

    fun refresh() {
        coroutineScope.launch {
            isRefreshing = true

            // Re-fetch actual weather first, so Environmental Summary shows
            // fresh numbers too — this used to only re-run the AI
            // recommendation against whatever weather was already cached,
            // leaving the summary stale on refresh. Respects the manual
            // pond location (Settings > Pond Location) the same way
            // Dashboard's refresh and MainActivity's initial fetch do.
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

            viewModel.refreshAIRecommendation()
            isRefreshing = false
        }
    }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = { refresh() },
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "AI Feeding Assistant",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.height(20.dp))

            Text(
                text = "Species",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(Modifier.height(10.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FishSpecies.values().forEach { species ->
                    FilterChip(
                        selected = species == viewModel.selectedSpecies,
                        onClick = { viewModel.selectSpecies(species) },
                        label = { Text(species.displayName) }
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            CurrentRecommendationCard(
                recommendation = recommendation.recommendation,
                confidence = recommendation.confidence,
                timestamp = recommendation.timestamp
            )

            Spacer(Modifier.height(20.dp))

            Text(
                text = "Environmental Summary",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(Modifier.height(10.dp))

            Card(shape = RoundedCornerShape(14.dp)) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    SummaryRow(
                        "Water Temperature",
                        deviceInfo.waterTemperature?.let {
                            if (deviceInfo.waterSensorOk) "$it°C" else "$it°C (stale)"
                        } ?: "No data"
                    )
                    SummaryRow("Air Temperature", "${weatherData.temperature}°C")
                    SummaryRow("Humidity", "${weatherData.humidity}%")
                    SummaryRow("Weather Condition", weatherData.weatherCondition)
                    SummaryRow(
                        label = "Feeding Time",
                        value = feedingSchedules.nextFeedingTime() ?: "No schedule",
                        showDivider = false
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            Text(
                text = "About this recommendation",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(Modifier.height(10.dp))

            Card(shape = RoundedCornerShape(14.dp)) {
                Text(
                    text = recommendation.reason,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp)
                )
            }

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFEAF1FE), RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                Text(
                    text = "This is an AI-generated suggestion only. You are still in control.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(20.dp))

            Button(
                onClick = { refresh() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text("Refresh Recommendation", fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun CurrentRecommendationCard(
    recommendation: String,
    confidence: String,
    timestamp: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFE7F7EC))
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = "Current Recommendation",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = recommendation,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1E8E3E)
            )
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Confidence: $confidence",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = timestamp,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SummaryRow(
    label: String,
    value: String,
    showDivider: Boolean = true
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
    if (showDivider) {
        HorizontalDivider()
    }
}