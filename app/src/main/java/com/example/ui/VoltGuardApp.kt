package com.example.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.ElectricScooter
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.ElectricScooter
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.R
import com.example.model.ThemePreference
import com.example.ui.components.FullChargeAlarmOverlay
import com.example.ui.screens.AlarmSettingsScreen
import com.example.ui.screens.ChargeScreen
import com.example.ui.screens.VehiclesScreen
import com.example.viewmodel.ChargeViewModel

enum class VoltGuardTab(
    val routeId: String,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    CHARGE("charge_dashboard", R.string.nav_charge, Icons.Filled.Bolt, Icons.Outlined.Bolt),
    VEHICLES("vehicles_garage", R.string.nav_vehicles, Icons.Filled.ElectricScooter, Icons.Outlined.ElectricScooter),
    ALARM("alarm_settings", R.string.nav_alarm, Icons.Filled.NotificationsActive, Icons.Outlined.Notifications)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoltGuardApp(
    viewModel: ChargeViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var currentTab by rememberSaveable { mutableStateOf(VoltGuardTab.CHARGE) }
    val context = LocalContext.current

    var hasNotificationPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        )
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasNotificationPermission = granted
    }

    val requestNotificationPermission = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // Support system back gesture cleanly from secondary tabs back to Charge cockpit
    BackHandler(enabled = currentTab != VoltGuardTab.CHARGE && !uiState.alarmRinging) {
        currentTab = VoltGuardTab.CHARGE
    }

    Box(modifier = Modifier.fillMaxSize()) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val useNavigationRail = maxWidth >= 600.dp

            Scaffold(
                modifier = Modifier.fillMaxSize(),
                containerColor = MaterialTheme.colorScheme.background,
                contentWindowInsets = WindowInsets.safeDrawing,
                topBar = {
                    CenterAlignedTopAppBar(
                        title = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Filled.Bolt,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = stringResource(R.string.app_name),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        },
                        actions = {
                            val themeIcon = when (uiState.themePreference) {
                                ThemePreference.SYSTEM -> Icons.Filled.BrightnessAuto
                                ThemePreference.LIGHT -> Icons.Filled.LightMode
                                ThemePreference.DARK -> Icons.Filled.DarkMode
                            }
                            IconButton(
                                onClick = viewModel::cycleThemePreference,
                                modifier = Modifier.testTag("top_bar_theme_switcher")
                            ) {
                                Icon(
                                    imageVector = themeIcon,
                                    contentDescription = "Theme: ${uiState.themePreference.label}. Tap to switch.",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                            containerColor = MaterialTheme.colorScheme.background,
                            titleContentColor = MaterialTheme.colorScheme.onBackground
                        )
                    )
                },
                bottomBar = {
                    if (!useNavigationRail) {
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                            tonalElevation = 0.dp
                        ) {
                            VoltGuardTab.entries.forEach { tab ->
                                val selected = currentTab == tab
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { currentTab = tab },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                            contentDescription = stringResource(tab.labelRes)
                                        )
                                    },
                                    label = { Text(stringResource(tab.labelRes)) },
                                    modifier = Modifier.testTag("nav_tab_${tab.routeId}"),
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                        selectedTextColor = MaterialTheme.colorScheme.primary,
                                        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                )
                            }
                        }
                    }
                }
            ) { innerPadding ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    if (useNavigationRail) {
                        NavigationRail(
                            modifier = Modifier.fillMaxHeight(),
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                        ) {
                            VoltGuardTab.entries.forEach { tab ->
                                val selected = currentTab == tab
                                NavigationRailItem(
                                    selected = selected,
                                    onClick = { currentTab = tab },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                            contentDescription = stringResource(tab.labelRes)
                                        )
                                    },
                                    label = { Text(stringResource(tab.labelRes)) },
                                    modifier = Modifier.testTag("rail_tab_${tab.routeId}"),
                                    colors = NavigationRailItemDefaults.colors(
                                        selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                        selectedTextColor = MaterialTheme.colorScheme.primary,
                                        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                )
                            }
                        }
                    }

                    Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        when (currentTab) {
                            VoltGuardTab.CHARGE -> ChargeScreen(
                                uiState = uiState,
                                needsNotificationPermission = !hasNotificationPermission,
                                onRequestNotificationPermission = requestNotificationPermission,
                                onToggleAlarmEnabled = viewModel::toggleAlarmEnabled,
                                onSelectInputMode = viewModel::setInputMode,
                                onStartBatteryChange = viewModel::setStartBatteryPercent,
                                onDirectTimerChange = viewModel::setDirectTimer,
                                onTargetPercentChange = viewModel::setTargetPercent,
                                onStartCharging = {
                                    if (!hasNotificationPermission) {
                                        requestNotificationPermission()
                                    }
                                    viewModel.startChargingSession()
                                },
                                onPauseCharging = viewModel::pauseCharging,
                                onResumeCharging = viewModel::resumeCharging,
                                onStopCharging = viewModel::stopChargingSession,
                                onAdjustBattery = viewModel::adjustLiveBatteryBy,
                                onStartQuickDemo = viewModel::startQuickTenSecondDemo,
                                onTestAlarmNow = viewModel::triggerFullChargeAlarmNow,
                                onOpenVehiclesTab = { currentTab = VoltGuardTab.VEHICLES }
                            )

                            VoltGuardTab.VEHICLES -> VehiclesScreen(
                                vehicles = uiState.vehicles,
                                selectedVehicleId = uiState.selectedVehicle.id,
                                onSelectVehicle = { vehicleId ->
                                    viewModel.selectVehicle(vehicleId)
                                    currentTab = VoltGuardTab.CHARGE
                                },
                                onAddCustomVehicle = viewModel::addCustomVehicle,
                                onDeleteCustomVehicle = viewModel::deleteCustomVehicle
                            )

                            VoltGuardTab.ALARM -> AlarmSettingsScreen(
                                settings = uiState.alarmSettings,
                                themePreference = uiState.themePreference,
                                useDynamicColor = uiState.useDynamicColor,
                                activeVehicleName = uiState.selectedVehicle.name,
                                needsNotificationPermission = !hasNotificationPermission,
                                onRequestNotificationPermission = requestNotificationPermission,
                                onSelectThemePreference = viewModel::setThemePreference,
                                onToggleDynamicColor = viewModel::toggleDynamicColor,
                                onTargetPercentChange = viewModel::setTargetPercent,
                                onSelectSoundType = viewModel::updateSoundType,
                                onCustomSoundSelected = viewModel::selectCustomSystemSound,
                                onPreviewSound = viewModel::previewAlarmSound,
                                onToggleVoice = viewModel::toggleVoiceAnnouncement,
                                onPreviewVoice = viewModel::previewVoiceMessage,
                                onToggleVibration = viewModel::toggleVibration,
                                onVolumeChange = viewModel::updateAlarmVolume,
                                onTriggerFullTestAlarm = viewModel::triggerFullChargeAlarmNow
                            )
                        }
                    }
                }
            }
        }

        // Full-Screen 100% Charged Alarm Modal Overlay
        AnimatedVisibility(
            visible = uiState.alarmRinging,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            FullChargeAlarmOverlay(
                vehicleName = uiState.alarmVehicleName,
                targetPercent = uiState.alarmTargetPercent,
                onDismiss = viewModel::dismissAlarm,
                onSnoozeFiveMin = viewModel::snoozeAlarmFiveMinutes
            )
        }
    }
}
