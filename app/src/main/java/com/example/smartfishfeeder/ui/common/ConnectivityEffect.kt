package com.example.smartfishfeeder.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.example.smartfishfeeder.data.connectivity.NetworkConnectivityObserver

/**
 * Live device internet connectivity as Compose State, updating immediately
 * when WiFi/mobile data connects or disconnects — no polling or refresh
 * needed, the UI just recomposes on change.
 */
@Composable
fun rememberIsOnline(): State<Boolean> {
    val context = LocalContext.current
    val observer = remember { NetworkConnectivityObserver(context) }
    return observer.observe().collectAsState(initial = true)
}