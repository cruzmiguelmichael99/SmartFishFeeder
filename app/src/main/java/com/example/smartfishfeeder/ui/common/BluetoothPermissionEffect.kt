package com.example.smartfishfeeder.ui.common

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/**
 * Requests BLUETOOTH_CONNECT, only needed on Android 12 (API 31) and up —
 * on older versions Bluetooth was a normal install-time permission, so
 * this just reports granted = true right away, same pattern as
 * NotificationPermissionEffect's pre-Tiramisu handling.
 */
@Composable
fun BluetoothPermissionEffect(onResult: (granted: Boolean) -> Unit) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
        LaunchedEffect(Unit) { onResult(true) }
        return
    }

    val context = LocalContext.current

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted -> onResult(granted) }

    LaunchedEffect(Unit) {
        val alreadyGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.BLUETOOTH_CONNECT
        ) == PackageManager.PERMISSION_GRANTED

        if (alreadyGranted) {
            onResult(true)
        } else {
            launcher.launch(Manifest.permission.BLUETOOTH_CONNECT)
        }
    }
}