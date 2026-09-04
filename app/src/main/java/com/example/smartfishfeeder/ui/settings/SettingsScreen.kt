package com.example.smartfishfeeder.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.example.smartfishfeeder.ui.common.rememberIsOnline
import com.example.smartfishfeeder.viewmodel.AuthViewModel
import com.example.smartfishfeeder.viewmodel.DashboardViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.provider.Settings
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: DashboardViewModel, authViewModel: AuthViewModel) {

    val deviceInfo = viewModel.deviceInfo
    val isOnline by rememberIsOnline()
    val context = LocalContext.current

    @Suppress("NewApi", "DEPRECATION")
    val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()

    val hasBluetoothPermission =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

    @Suppress("MissingPermission", "NewApi")
    val isBluetoothOn =
        if (hasBluetoothPermission) {
            bluetoothAdapter?.isEnabled == true
        } else {
            false
        }

    @Suppress("MissingPermission", "NewApi")
    val isEsp32Paired =
        if (hasBluetoothPermission) {
            bluetoothAdapter
                ?.bondedDevices
                ?.any { it.name == "FishFeeder-BT" } == true
        } else {
            false
        }

    var showAboutDialog by remember { mutableStateOf(false) }

    // Local editable copy of the address field. Synced from the ViewModel
    // whenever its values change (including the async load on login), but
    // edited locally until "Save Location" is tapped.
    var customLocationEnabled by remember { mutableStateOf(viewModel.useCustomLocation) }
    var addressText by remember { mutableStateOf(viewModel.customLocationName) }
    var isGeocoding by remember { mutableStateOf(false) }

    LaunchedEffect(viewModel.useCustomLocation, viewModel.customLocationName) {
        customLocationEnabled = viewModel.useCustomLocation
        addressText = viewModel.customLocationName
    }

    val coroutineScope = rememberCoroutineScope()
    var isRefreshing by remember { mutableStateOf(false) }

    // Device status now comes live from Firestore (see DashboardViewModel's
    // deviceInfo), so this pull-to-refresh is purely cosmetic — the real
    // data updates itself via a live listener, no refresh needed.
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
                .verticalScroll(rememberScrollState())
        ) {

            Text(
                text = "Settings",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.height(20.dp))

            SectionHeader("Device & Connection")

            Card(shape = RoundedCornerShape(14.dp)) {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    SettingsInfoRow(
                        label = "Device Status",
                        value = if (deviceInfo.isOnline) "Online" else "Not Connected",
                        valueColor = if (deviceInfo.isOnline) Color(0xFF1E8E3E) else Color(0xFFB3261E)
                    )
                    SettingsInfoRow(
                        label = "Wi-Fi",
                        value = if (isOnline) "Connected" else "Disconnected",
                        valueColor = if (isOnline) Color(0xFF1E8E3E) else Color(0xFFB3261E)
                    )
                    SettingsInfoRow(
                        label = "Bluetooth",
                        value = if (isBluetoothOn) "On" else "Off",
                        valueColor = if (isBluetoothOn) {
                            Color(0xFF1E8E3E)
                        } else {
                            Color(0xFFB3261E)
                        }
                    )

                    SettingsInfoRow(
                        label = "ESP32 Bluetooth",
                        value = if (isEsp32Paired) "Paired" else "Not Paired",
                        valueColor = if (isEsp32Paired) {
                            Color(0xFF1E8E3E)
                        } else {
                            Color(0xFFB3261E)
                        }
                    )

                    SettingsInfoRow(
                        label = "Bluetooth Device",
                        value = "FishFeeder-BT"
                    )
                    SettingsInfoRow(
                        label = "Device ID",
                        value = deviceInfo.deviceId.ifBlank { "Not paired yet" }
                    )
                    SettingsInfoRow(
                        label = "Last Communication",
                        value = deviceInfo.lastSeen.ifBlank { "Never" },
                        showDivider = false
                    )
                    Spacer(Modifier.height(8.dp))

                    OutlinedButton(
                        onClick = {
                            val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
                            context.startActivity(intent)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Open Bluetooth Settings")
                    }

                    Spacer(Modifier.height(8.dp))
                }
            }

            Spacer(Modifier.height(20.dp))

            SectionHeader("Pond Location")

            Card(shape = RoundedCornerShape(14.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Use Custom Location", fontWeight = FontWeight.SemiBold)
                            Text(
                                text = "Type your pond's address instead of using your " +
                                        "phone's current location.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = customLocationEnabled,
                            onCheckedChange = { enabled ->
                                customLocationEnabled = enabled
                                if (!enabled) {
                                    // Turning off takes effect immediately — falls back to
                                    // phone GPS right away. The saved address stays stored
                                    // in case it's turned back on later.
                                    viewModel.setCustomLocation(
                                        false,
                                        viewModel.customLatitude,
                                        viewModel.customLongitude,
                                        viewModel.customLocationName
                                    )
                                }
                            }
                        )
                    }

                    if (customLocationEnabled) {
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = addressText,
                            onValueChange = { addressText = it },
                            label = { Text("Pond address or location") },
                            placeholder = { Text("e.g. Calamba, Laguna") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Confirms the exact place that was actually resolved, since a
                        // typed address can be ambiguous — this is the real proof it
                        // found the right spot, not just that it "saved" something.
                        if (viewModel.useCustomLocation && viewModel.customLocationName.isNotBlank()) {
                            Spacer(Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Filled.CheckCircle,
                                    contentDescription = null,
                                    tint = Color(0xFF1E8E3E),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = "Currently set to: ${viewModel.customLocationName}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF1E8E3E)
                                )
                            }
                        }

                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = {
                                if (addressText.isBlank()) {
                                    Toast.makeText(context, "Enter an address first", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                coroutineScope.launch {
                                    isGeocoding = true
                                    val success = viewModel.geocodeAndSaveLocation(addressText)
                                    isGeocoding = false
                                    if (success) {
                                        Toast.makeText(context, "Pond location saved", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(
                                            context,
                                            "Couldn't find that address — try being more specific",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                }
                            },
                            enabled = !isGeocoding,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            if (isGeocoding) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.dp
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("Finding location...")
                            } else {
                                Text("Save Location")
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            SectionHeader("ESP32 Pairing")

            Card(shape = RoundedCornerShape(14.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        text = "Enter this ID in your ESP32 firmware so it knows " +
                                "which account to sync with.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val pairingId = viewModel.getPairingId()
                                if (pairingId != null) {
                                    copyToClipboard(context, pairingId)
                                    Toast.makeText(context, "Pairing ID copied", Toast.LENGTH_SHORT).show()
                                }
                            },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = viewModel.getPairingId() ?: "Not signed in",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            imageVector = Icons.Filled.ContentCopy,
                            contentDescription = "Copy pairing ID",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            SectionHeader("Other")

            Card(shape = RoundedCornerShape(14.dp)) {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    SettingsClickableRow(
                        label = "Units",
                        trailingText = "°C",
                        onClick = { /* TODO: build unit switching (°C / °F) */ }
                    )
                    SettingsClickableRow(
                        label = "About",
                        onClick = { showAboutDialog = true }
                    )
                    SettingsClickableRow(
                        label = "Logout",
                        labelColor = Color(0xFFB3261E),
                        showDivider = false,
                        onClick = { authViewModel.signOut() }
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
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
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("Pairing ID", text))
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold
    )
    Spacer(Modifier.height(10.dp))
}

@Composable
private fun SettingsInfoRow(
    label: String,
    value: String,
    valueColor: Color = Color.Unspecified,
    showDivider: Boolean = true
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = valueColor
        )
    }
    if (showDivider) HorizontalDivider()
}

@Composable
private fun SettingsClickableRow(
    label: String,
    trailingText: String? = null,
    labelColor: Color = Color.Unspecified,
    showDivider: Boolean = true,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = labelColor)
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (trailingText != null) {
                Text(
                    text = trailingText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(4.dp))
            }
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    if (showDivider) HorizontalDivider()
}