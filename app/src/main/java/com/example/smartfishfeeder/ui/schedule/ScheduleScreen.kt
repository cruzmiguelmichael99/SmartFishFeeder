package com.example.smartfishfeeder.ui.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.smartfishfeeder.data.model.FeedingSchedule
import com.example.smartfishfeeder.ui.common.rememberIsOnline
import com.example.smartfishfeeder.viewmodel.DashboardViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen(viewModel: DashboardViewModel) {

    val schedules = viewModel.feedingSchedules
    val context = LocalContext.current
    val isOnline by rememberIsOnline()
    var selectedTab by remember { mutableIntStateOf(0) }
    var showAddDialog by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()
    var isRefreshing by remember { mutableStateOf(false) }

    // No real backend to pull schedule data from yet, so this is a cosmetic
    // spinner only, it won't discard your local edits or change anything.
    // Wire this to a real fetch once schedules live somewhere other than
    // in-memory state on the ViewModel.
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = {
            coroutineScope.launch {
                isRefreshing = true
                delay(600)
                isRefreshing = false
            }
        },
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Feeding Schedule",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = { showAddDialog = true }) {
                    Icon(Icons.Filled.Add, contentDescription = "Add schedule")
                }
            }

            Spacer(Modifier.height(8.dp))

            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Schedule") }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Manual") }
                )
            }

            Spacer(Modifier.height(16.dp))

            if (selectedTab == 0) {
                ScheduleTabContent(
                    scheduleEnabled = viewModel.scheduleEnabled,
                    onScheduleEnabledChange = { enabled ->
                        viewModel.setScheduleFeedingEnabled(enabled)
                    },
                    schedules = schedules,
                    onToggleSchedule = { index, checked ->
                        viewModel.updateScheduleEnabled(index, checked)
                    },
                    onTimeChangeSchedule = { index, newTime ->
                        viewModel.updateScheduleTime(index, newTime)
                    },
                    onFeedingTypeChange = { index, feedingType ->
                        viewModel.updateScheduleFeedingType(index, feedingType)
                    }
                )
            } else {
                ManualFeedContent(viewModel = viewModel, context = context, isOnline = isOnline)
            }
        }
    }

    if (showAddDialog) {
        EditTimeDialog(
            initialTime = "06:00 AM",
            onDismiss = { showAddDialog = false },
            onConfirm = { newTime ->
                viewModel.addFeedingSchedule(newTime)
                showAddDialog = false
            }
        )
    }
}

@Composable
private fun ScheduleTabContent(
    scheduleEnabled: Boolean,
    onScheduleEnabledChange: (Boolean) -> Unit,
    schedules: List<FeedingSchedule>,
    onToggleSchedule: (Int, Boolean) -> Unit,
    onTimeChangeSchedule: (Int, String) -> Unit,
    onFeedingTypeChange: (Int, String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Enable Schedule", fontWeight = FontWeight.SemiBold)
                    Text(
                        text = "Automatic feeding based on time",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = scheduleEnabled, onCheckedChange = onScheduleEnabledChange)
            }
        }

        Spacer(Modifier.height(16.dp))

        schedules.forEachIndexed { index, schedule ->
            ScheduleItemCard(
                schedule = schedule,
                onToggle = { checked ->
                    onToggleSchedule(index, checked)
                },
                onTimeChange = { newTime ->
                    onTimeChangeSchedule(index, newTime)
                },
                onFeedingTypeChange = { feedingType ->
                    onFeedingTypeChange(index, feedingType)
                }
            )
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun ScheduleItemCard(
    schedule: FeedingSchedule,
    onToggle: (Boolean) -> Unit,
    onTimeChange: (String) -> Unit,
    onFeedingTypeChange: (String) -> Unit
) {
    var showTimeDialog by remember { mutableStateOf(false) }
    var feedingTypeExpanded by remember { mutableStateOf(false) }

    val currentFeedingType =
        if (schedule.feedingType.isBlank()) "Normal"
        else schedule.feedingType

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(Color(0xFFEAF1FE), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.AccessTime,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    text = feedingPeriodLabel(schedule.feedingTime),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = schedule.feedingTime,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Everyday",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))

                Box {
                    OutlinedButton(
                        onClick = {
                            feedingTypeExpanded = true
                        }
                    ) {
                        Text(currentFeedingType)
                    }

                    DropdownMenu(
                        expanded = feedingTypeExpanded,
                        onDismissRequest = {
                            feedingTypeExpanded = false
                        }
                    ) {
                        DropdownMenuItem(
                            text = {
                                Text("Normal")
                            },
                            onClick = {
                                onFeedingTypeChange("Normal")
                                feedingTypeExpanded = false
                            }
                        )

                        DropdownMenuItem(
                            text = {
                                Text("Reduced")
                            },
                            onClick = {
                                onFeedingTypeChange("Reduced")
                                feedingTypeExpanded = false
                            }
                        )
                    }
                }
            }

            IconButton(onClick = { showTimeDialog = true }) {
                Icon(
                    imageVector = Icons.Filled.Edit,
                    contentDescription = "Edit time",
                    modifier = Modifier.size(18.dp)
                )
            }

            Switch(checked = schedule.enabled, onCheckedChange = onToggle)
        }
    }

    if (showTimeDialog) {
        EditTimeDialog(
            initialTime = schedule.feedingTime,
            onDismiss = { showTimeDialog = false },
            onConfirm = { newTime ->
                onTimeChange(newTime)
                showTimeDialog = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditTimeDialog(
    initialTime: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    val (initialHour, initialMinute) = remember { parseFeedingTime(initialTime) }
    val timePickerState = rememberTimePickerState(
        initialHour = initialHour,
        initialMinute = initialMinute,
        is24Hour = false
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(formatFeedingTime(timePickerState.hour, timePickerState.minute))
                }
            ) {
                Text("OK")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        text = {
            TimePicker(state = timePickerState)
        }
    )
}

@Composable
private fun ManualFeedContent(
    viewModel: DashboardViewModel,
    context: android.content.Context,
    isOnline: Boolean
) {

    var isFeeding by remember { mutableStateOf(false) }

    LaunchedEffect(isFeeding) {
        if (isFeeding) {
            delay(3000)
            isFeeding = false
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Spacer(Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .background(
                    color = Color(0xFFEAF1FE),
                    shape = RoundedCornerShape(16.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.AccessTime,
                contentDescription = "Manual feed",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(56.dp)
            )
        }

        Spacer(Modifier.height(24.dp))

        Text(
            text = "Dispenser Status",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(4.dp))

        Text(
            text = if (isFeeding) "Feeding in progress" else "Ready to feed",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = if (isFeeding) Color(0xFF1A73E8) else Color(0xFF1E8E3E)
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = "The feeder will dispense food to the pond.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(48.dp))

        Button(
            onClick = {
                isFeeding = true
                viewModel.recordFeedingEvent(automatic = false)
                viewModel.sendFeedNowCommand(context, isOnline)
            },
            enabled = !isFeeding,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("FEED NOW", fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.height(12.dp))

        OutlinedButton(
            onClick = { isFeeding = false },
            enabled = isFeeding,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("CANCEL")
        }

        Spacer(Modifier.height(8.dp))
    }
}

/**
 * Derives a display label ("Morning Feeding", etc.) from a time string
 * like "06:00 AM" or "06:00 PM". This is a placeholder until FeedingSchedule
 * has its own label/days fields.
 */
private fun feedingPeriodLabel(feedingTime: String): String {
    val isPM = feedingTime.contains("PM", ignoreCase = true)
    val hourPart = feedingTime.trim().split(":").firstOrNull()?.toIntOrNull() ?: 0
    val hour24 = when {
        isPM && hourPart != 12 -> hourPart + 12
        !isPM && hourPart == 12 -> 0
        else -> hourPart
    }
    return when (hour24) {
        in 5..11 -> "Morning Feeding"
        in 12..16 -> "Afternoon Feeding"
        in 17..20 -> "Evening Feeding"
        else -> "Night Feeding"
    }
}

/** Parses "06:00 AM" into a (hour24, minute) pair for TimePickerState. */
private fun parseFeedingTime(feedingTime: String): Pair<Int, Int> {
    val isPM = feedingTime.contains("PM", ignoreCase = true)
    val digitsOnly = feedingTime
        .replace("AM", "", ignoreCase = true)
        .replace("PM", "", ignoreCase = true)
        .trim()
    val parts = digitsOnly.split(":")
    val hourPart = parts.getOrNull(0)?.trim()?.toIntOrNull() ?: 6
    val minutePart = parts.getOrNull(1)?.trim()?.toIntOrNull() ?: 0
    val hour24 = when {
        isPM && hourPart != 12 -> hourPart + 12
        !isPM && hourPart == 12 -> 0
        else -> hourPart
    }
    return hour24 to minutePart
}

/** Formats a 24-hour (hour, minute) pair back into "06:00 AM" style. */
private fun formatFeedingTime(hour24: Int, minute: Int): String {
    val period = if (hour24 < 12) "AM" else "PM"
    val hour12 = when {
        hour24 == 0 -> 12
        hour24 > 12 -> hour24 - 12
        else -> hour24
    }
    return "%02d:%02d %s".format(hour12, minute, period)
}