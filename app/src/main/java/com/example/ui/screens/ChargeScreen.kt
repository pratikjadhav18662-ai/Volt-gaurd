package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.ElectricScooter
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.TwoWheeler
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.ChargeInputMode
import com.example.model.ChargeStatus
import com.example.model.ChargingCalculator
import com.example.model.VehicleType
import com.example.ui.components.EnergyGauge
import com.example.viewmodel.VoltGuardUiState
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun ChargeScreen(
    uiState: VoltGuardUiState,
    needsNotificationPermission: Boolean,
    onRequestNotificationPermission: () -> Unit,
    onToggleAlarmEnabled: (Boolean) -> Unit,
    onSelectInputMode: (ChargeInputMode) -> Unit,
    onStartBatteryChange: (Int) -> Unit,
    onDirectTimerChange: (Int, Int) -> Unit,
    onTargetPercentChange: (Int) -> Unit,
    onStartCharging: () -> Unit,
    onPauseCharging: () -> Unit,
    onResumeCharging: () -> Unit,
    onStopCharging: () -> Unit,
    onAdjustBattery: (Int) -> Unit,
    onStartQuickDemo: () -> Unit,
    onTestAlarmNow: () -> Unit,
    onOpenVehiclesTab: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val isActiveSession = uiState.status == ChargeStatus.CHARGING || uiState.status == ChargeStatus.PAUSED
    val alarmEnabled = uiState.alarmSettings.alarmEnabled
    val targetPercent = uiState.alarmSettings.targetPercent

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 600.dp)
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Compact Sleek Active Vehicle Header Pill
            CompactVehicleStrip(
                vehicleName = uiState.selectedVehicle.name,
                vehicleType = uiState.selectedVehicle.type,
                batteryKwh = uiState.selectedVehicle.batteryKwh,
                chargerKw = uiState.selectedVehicle.chargerKw,
                onClick = onOpenVehiclesTab
            )

            // Runtime notification permission prompt (Android 13+)
            AnimatedVisibility(
                visible = needsNotificationPermission,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                NotificationPermissionCard(onRequestPermission = onRequestNotificationPermission)
            }

            // HERO: Large Circular Battery Progress Gauge
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                EnergyGauge(
                    batteryPercent = uiState.liveBatteryPercent,
                    targetPercent = targetPercent,
                    alarmEnabled = alarmEnabled,
                    status = uiState.status,
                    remainingMs = uiState.remainingMs,
                    estimatedDurationMs = uiState.estimatedDurationMs
                )
            }

            // PROMINENT SIMPLE TOGGLE SWITCH: Enable / Disable 100% Charge Alarm
            ChargeAlarmToggleCard(
                alarmEnabled = alarmEnabled,
                targetPercent = targetPercent,
                activeSoundName = uiState.alarmSettings.activeSoundDisplayName,
                onToggle = onToggleAlarmEnabled
            )

            // Clean Telemetry Strip
            val etaDisplay = if (isActiveSession) {
                ChargingCalculator.formatClockTime(uiState.etaEpochMs)
            } else {
                ChargingCalculator.formatClockTime(
                    System.currentTimeMillis() + uiState.estimatedDurationMs
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                TelemetryStatTile(
                    icon = Icons.Filled.Schedule,
                    label = "FULL AT",
                    value = etaDisplay,
                    modifier = Modifier.weight(1f)
                )
                TelemetryStatTile(
                    icon = Icons.Filled.Bolt,
                    label = "CHARGER",
                    value = String.format(Locale.US, "%.2f kW", uiState.selectedVehicle.chargerKw),
                    modifier = Modifier.weight(1f)
                )
                TelemetryStatTile(
                    icon = Icons.Filled.Speed,
                    label = "NEEDED",
                    value = String.format(Locale.US, "%.1f kWh", uiState.estimatedEnergyKwh),
                    modifier = Modifier.weight(1f)
                )
            }

            // Setup Controls (Idle/Completed) or Live Active Session Controls
            if (!isActiveSession) {
                ChargeSetupCard(
                    uiState = uiState,
                    onSelectInputMode = onSelectInputMode,
                    onStartBatteryChange = onStartBatteryChange,
                    onDirectTimerChange = onDirectTimerChange,
                    onTargetPercentChange = onTargetPercentChange
                )

                Button(
                    onClick = onStartCharging,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(58.dp)
                        .testTag("start_charge_monitor_button"),
                    shape = MaterialTheme.shapes.large,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Filled.Bolt,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.btn_start_charging),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Quick verification actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    FilledTonalButton(
                        onClick = onStartQuickDemo,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("quick_demo_button"),
                        shape = MaterialTheme.shapes.medium,
                        contentPadding = PaddingValues(horizontal = 12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Timer,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.btn_quick_demo),
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    OutlinedButton(
                        onClick = onTestAlarmNow,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("test_alarm_now_button"),
                        shape = MaterialTheme.shapes.medium,
                        contentPadding = PaddingValues(horizontal = 12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.NotificationsActive,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.btn_test_alarm),
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            } else {
                ActiveSessionControlCard(
                    uiState = uiState,
                    onPauseCharging = onPauseCharging,
                    onResumeCharging = onResumeCharging,
                    onStopCharging = onStopCharging,
                    onAdjustBattery = onAdjustBattery
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun CompactVehicleStrip(
    vehicleName: String,
    vehicleType: VehicleType,
    batteryKwh: Double,
    chargerKw: Double,
    onClick: () -> Unit
) {
    val overlayStart = MaterialTheme.colorScheme.surfaceContainerLow
    val overlayEnd = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.45f)

    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .testTag("active_vehicle_hero_card"),
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Image(
                painter = painterResource(id = R.drawable.img_ev_charging_hero_1795325364410),
                contentDescription = "Electric vehicle charging banner",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            0.0f to overlayStart.copy(alpha = 0.96f),
                            0.65f to overlayStart.copy(alpha = 0.80f),
                            1.0f to overlayEnd
                        )
                    )
            )

            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    modifier = Modifier.size(42.dp),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = vehicleTypeIcon(vehicleType),
                            contentDescription = vehicleType.label,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = vehicleName,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = String.format(
                            Locale.US,
                            "%.2f kWh  •  %.2f kW Charger",
                            batteryKwh,
                            chargerKw
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Icon(
                    imageVector = Icons.Filled.ChevronRight,
                    contentDescription = "Switch vehicle",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ChargeAlarmToggleCard(
    alarmEnabled: Boolean,
    targetPercent: Int,
    activeSoundName: String,
    onToggle: (Boolean) -> Unit
) {
    val containerColor = if (alarmEnabled) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f)
    } else {
        MaterialTheme.colorScheme.surfaceContainerLow
    }
    val borderColor = if (alarmEnabled) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle(!alarmEnabled) }
            .testTag("alarm_toggle_card"),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(1.5.dp, borderColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(
                modifier = Modifier.size(48.dp),
                shape = MaterialTheme.shapes.medium,
                color = if (alarmEnabled) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (alarmEnabled) {
                            Icons.Filled.NotificationsActive
                        } else {
                            Icons.Filled.NotificationsOff
                        },
                        contentDescription = if (alarmEnabled) "Alarm enabled" else "Alarm disabled",
                        tint = if (alarmEnabled) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = "$targetPercent% Charge Alarm",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (alarmEnabled) {
                        "Armed • Sound: $activeSoundName"
                    } else {
                        "Alarm is turned off"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (alarmEnabled) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Switch(
                checked = alarmEnabled,
                onCheckedChange = onToggle,
                modifier = Modifier.testTag("alarm_enable_switch"),
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                    uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHigh
                )
            )
        }
    }
}

@Composable
private fun NotificationPermissionCard(
    onRequestPermission: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.Notifications,
                contentDescription = "Notification alert permission",
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(24.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.perm_notif_banner_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(R.string.perm_notif_banner_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
            Button(
                onClick = onRequestPermission,
                modifier = Modifier.testTag("grant_notification_permission_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.tertiary,
                    contentColor = MaterialTheme.colorScheme.onTertiary
                ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(
                    text = stringResource(R.string.perm_notif_button),
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}

@Composable
private fun TelemetryStatTile(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(13.dp)
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = value,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ChargeSetupCard(
    uiState: VoltGuardUiState,
    onSelectInputMode: (ChargeInputMode) -> Unit,
    onStartBatteryChange: (Int) -> Unit,
    onDirectTimerChange: (Int, Int) -> Unit,
    onTargetPercentChange: (Int) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ChargeInputMode.entries.forEach { mode ->
                    val selected = uiState.inputMode == mode
                    FilterChip(
                        selected = selected,
                        onClick = { onSelectInputMode(mode) },
                        label = {
                            Text(
                                text = mode.label,
                                style = MaterialTheme.typography.labelLarge
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("mode_chip_${mode.name.lowercase()}"),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                }
            }

            if (uiState.inputMode == ChargeInputMode.SMART_ESTIMATE) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Current Vehicle Battery",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${uiState.startBatteryPercent}%",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    val maxSlider = (uiState.alarmSettings.targetPercent - 1).coerceAtLeast(1).toFloat()
                    Slider(
                        value = uiState.startBatteryPercent.toFloat().coerceIn(0f, maxSlider),
                        onValueChange = { onStartBatteryChange(it.roundToInt()) },
                        valueRange = 0f..maxSlider,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("current_battery_slider"),
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        )
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Charger Dashboard Time Remaining",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        TimeStepperBox(
                            label = "HOURS",
                            value = uiState.directHours,
                            onDecrement = { onDirectTimerChange(uiState.directHours - 1, uiState.directMinutes) },
                            onIncrement = { onDirectTimerChange(uiState.directHours + 1, uiState.directMinutes) },
                            tagPrefix = "timer_hours",
                            modifier = Modifier.weight(1f)
                        )
                        TimeStepperBox(
                            label = "MINUTES",
                            value = uiState.directMinutes,
                            onDecrement = { onDirectTimerChange(uiState.directHours, uiState.directMinutes - 5) },
                            onIncrement = { onDirectTimerChange(uiState.directHours, uiState.directMinutes + 5) },
                            tagPrefix = "timer_minutes",
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Target Charge Level",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(80, 90, 100).forEach { target ->
                        val selected = uiState.alarmSettings.targetPercent == target
                        FilterChip(
                            selected = selected,
                            onClick = { onTargetPercentChange(target) },
                            label = {
                                Text(
                                    text = if (target == 100) "100% Full" else "$target%",
                                    style = MaterialTheme.typography.labelLarge
                                )
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("target_chip_$target"),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TimeStepperBox(
    label: String,
    value: Int,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    tagPrefix: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                FilledTonalIconButton(
                    onClick = onDecrement,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("${tagPrefix}_minus")
                ) {
                    Icon(Icons.Filled.Remove, contentDescription = "Decrease $label")
                }
                Text(
                    text = String.format(Locale.US, "%02d", value),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                FilledTonalIconButton(
                    onClick = onIncrement,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("${tagPrefix}_plus")
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Increase $label")
                }
            }
        }
    }
}

@Composable
private fun ActiveSessionControlCard(
    uiState: VoltGuardUiState,
    onPauseCharging: () -> Unit,
    onResumeCharging: () -> Unit,
    onStopCharging: () -> Unit,
    onAdjustBattery: (Int) -> Unit
) {
    val isPaused = uiState.status == ChargeStatus.PAUSED
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = if (uiState.isDemoMode) {
                        "10s Live Demo Running"
                    } else if (uiState.alarmSettings.alarmEnabled) {
                        "Alarm Armed for ${uiState.alarmSettings.targetPercent}%"
                    } else {
                        "Monitoring Silently (Alarm Off)"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = if (uiState.alarmSettings.alarmEnabled) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.secondary
                    },
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (uiState.alarmSettings.alarmEnabled) {
                        "Sound + voice alert will trigger automatically at ${uiState.alarmSettings.targetPercent}%."
                    } else {
                        "Turn on the switch above to sound an alarm at ${uiState.alarmSettings.targetPercent}%."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Sync Vehicle %:",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                OutlinedButton(
                    onClick = { onAdjustBattery(-5) },
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("calibrate_minus_5_button")
                ) {
                    Text("-5%")
                }
                OutlinedButton(
                    onClick = { onAdjustBattery(5) },
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("calibrate_plus_5_button")
                ) {
                    Text("+5%")
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                FilledTonalButton(
                    onClick = if (isPaused) onResumeCharging else onPauseCharging,
                    modifier = Modifier
                        .weight(1f)
                        .height(54.dp)
                        .testTag("pause_resume_charge_button"),
                    shape = MaterialTheme.shapes.large
                ) {
                    Icon(
                        imageVector = if (isPaused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(
                            if (isPaused) R.string.btn_resume_charging else R.string.btn_pause_charging
                        ),
                        style = MaterialTheme.typography.titleSmall
                    )
                }

                Button(
                    onClick = onStopCharging,
                    modifier = Modifier
                        .weight(1f)
                        .height(54.dp)
                        .testTag("stop_charge_button"),
                    shape = MaterialTheme.shapes.large,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    )
                ) {
                    Icon(
                        imageVector = Icons.Filled.Stop,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.btn_stop_charging),
                        style = MaterialTheme.typography.titleSmall
                    )
                }
            }
        }
    }
}

fun vehicleTypeIcon(type: VehicleType): ImageVector = when (type) {
    VehicleType.E_SCOOTER -> Icons.Filled.ElectricScooter
    VehicleType.E_BIKE -> Icons.Filled.DirectionsBike
    VehicleType.E_MOTORCYCLE -> Icons.Filled.TwoWheeler
    VehicleType.EV_CAR -> Icons.Filled.DirectionsCar
}
