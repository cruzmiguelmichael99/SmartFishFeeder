package com.example.smartfishfeeder

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.example.smartfishfeeder.ui.schedule.ScheduleScreen
import com.example.smartfishfeeder.ui.history.HistoryScreen
import com.example.smartfishfeeder.viewmodel.DashboardViewModel
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import com.example.smartfishfeeder.ui.dashboard.Dashboard
import com.example.smartfishfeeder.ui.ai.AIScreen
import com.example.smartfishfeeder.ui.settings.SettingsScreen
import com.example.smartfishfeeder.viewmodel.AuthViewModel
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import com.example.smartfishfeeder.ui.common.rememberIsOnline
import androidx.compose.runtime.setValue

@Composable
fun MainScreen(
    viewModel: DashboardViewModel,
    authViewModel: AuthViewModel
) {

    var selectedItem = remember { mutableIntStateOf(0) }

    var scheduleInitialTab by remember { mutableIntStateOf(0) }

    val isOnline by rememberIsOnline()

    val snackbarHostState = remember { SnackbarHostState() }

    var previousOnlineStatus by remember {
        mutableStateOf<Boolean?>(null)
    }

    LaunchedEffect(isOnline) {
        if (previousOnlineStatus != null &&
            previousOnlineStatus != isOnline
        ) {
            if (isOnline) {
                snackbarHostState.showSnackbar(
                    message = "Connection restored. Internet is available again."
                )
            } else {
                snackbarHostState.showSnackbar(
                    message = "Connection lost. Bluetooth can be used as an alternative connection."
                )
            }
        }

        previousOnlineStatus = isOnline
    }

    val items = listOf(
        "Dashboard",
        "Schedule",
        "History",
        "AI",
        "Settings"
    )

    val icons = listOf(
        Icons.Filled.Home,
        Icons.Filled.DateRange,
        Icons.Filled.History,
        Icons.Filled.SmartToy,
        Icons.Filled.Settings
    )

    Scaffold(
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState
            )
        },
        bottomBar = {

            NavigationBar {

                items.forEachIndexed { index, item ->

                    NavigationBarItem(
                        selected = selectedItem.intValue == index,
                        onClick = {
                            selectedItem.intValue = index
                        },
                        icon = {
                            Icon(
                                imageVector = icons[index],
                                contentDescription = item
                            )
                        },
                        label = {
                            Text(item)
                        }
                    )
                }
            }
        }
    ) { paddingValues ->

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (selectedItem.intValue) {

                0 -> {
                    Dashboard(
                        viewModel = viewModel,
                        authViewModel = authViewModel,
                        onFeedNowClick = {
                            selectedItem.intValue = 1
                        }
                    )
                }

                1 -> {
                    ScheduleScreen(viewModel = viewModel)
                }

                2 -> {
                    HistoryScreen(viewModel = viewModel)
                }

                3 -> {
                    AIScreen(viewModel = viewModel)
                }

                4 -> {
                    SettingsScreen(
                        viewModel = viewModel,
                        authViewModel = authViewModel
                    )
                }
            }
        }
    }
}