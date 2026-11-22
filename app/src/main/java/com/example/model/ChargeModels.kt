package com.example.model

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToLong

enum class VehicleType(val label: String) {
    E_SCOOTER("E-Scooter"),
    E_BIKE("E-Bike"),
    E_MOTORCYCLE("E-Moto"),
    EV_CAR("EV Car")
}

data class VehicleProfile(
    val id: String,
    val name: String,
    val type: VehicleType,
    val batteryKwh: Double,
    val chargerKw: Double,
    val isCustom: Boolean = false
)

enum class AlarmSoundType(val title: String, val subtitle: String) {
    VOLT_CHIME("Volt Chime", "Crisp high-visibility harmonic EV chime"),
    PULSE_SIREN("Pulse Siren", "Urgent dual-tone full-charge alert"),
    SYSTEM_ALARM("Device Alarm", "Uses your Android default alarm ringtone"),
    CUSTOM_SYSTEM("Custom System Sound", "Pick any notification sound or melody from your device")
}

enum class ChargeInputMode(val label: String) {
    SMART_ESTIMATE("EV Smart Calc"),
    DIRECT_TIMER("Set Timer")
}

enum class ChargeStatus {
    IDLE,
    CHARGING,
    PAUSED,
    ALARM_RINGING,
    COMPLETED
}

enum class ThemePreference(val label: String) {
    SYSTEM("System"),
    LIGHT("Light"),
    DARK("Dark");

    fun next(): ThemePreference = when (this) {
        SYSTEM -> LIGHT
        LIGHT -> DARK
        DARK -> SYSTEM
    }
}

data class AlarmSettings(
    val alarmEnabled: Boolean = true,
    val targetPercent: Int = 100,
    val soundType: AlarmSoundType = AlarmSoundType.VOLT_CHIME,
    val customSoundUri: String? = null,
    val customSoundTitle: String? = null,
    val voiceAnnouncementEnabled: Boolean = true,
    val vibrationEnabled: Boolean = true,
    val alarmVolume: Float = 0.92f
) {
    val activeSoundDisplayName: String
        get() = if (soundType == AlarmSoundType.CUSTOM_SYSTEM && !customSoundTitle.isNullOrBlank()) {
            customSoundTitle
        } else {
            soundType.title
        }
}

data class PersistedSession(
    val isActive: Boolean = false,
    val isPaused: Boolean = false,
    val startPercent: Int = 35,
    val targetPercent: Int = 100,
    val startEpochMs: Long = 0L,
    val endEpochMs: Long = 0L,
    val totalDurationMs: Long = 0L,
    val pausedRemainingMs: Long = 0L,
    val vehicleName: String = "Electric Vehicle",
    val isDemoMode: Boolean = false
)

object DefaultVehicles {
    val presets: List<VehicleProfile> = listOf(
        VehicleProfile(
            id = "preset_escooter",
            name = "City E-Scooter",
            type = VehicleType.E_SCOOTER,
            batteryKwh = 3.2,
            chargerKw = 0.75
        ),
        VehicleProfile(
            id = "preset_ebike",
            name = "Commuter E-Bike",
            type = VehicleType.E_BIKE,
            batteryKwh = 0.72,
            chargerKw = 0.20
        ),
        VehicleProfile(
            id = "preset_emoto",
            name = "Sport E-Motorcycle",
            type = VehicleType.E_MOTORCYCLE,
            batteryKwh = 14.4,
            chargerKw = 3.3
        ),
        VehicleProfile(
            id = "preset_ev_compact",
            name = "Compact EV Hatchback",
            type = VehicleType.EV_CAR,
            batteryKwh = 40.0,
            chargerKw = 7.2
        ),
        VehicleProfile(
            id = "preset_ev_suv",
            name = "Long-Range EV SUV",
            type = VehicleType.EV_CAR,
            batteryKwh = 75.0,
            chargerKw = 11.0
        )
    )
}

object ChargingCalculator {
    private const val CHARGING_EFFICIENCY = 0.90

    fun estimateDurationMs(
        batteryKwh: Double,
        chargerKw: Double,
        startPercent: Int,
        targetPercent: Int
    ): Long {
        val safeTarget = targetPercent.coerceIn(1, 100)
        val safeStart = startPercent.coerceIn(0, safeTarget - 1)
        val safePower = max(0.05, chargerKw)
        val safeBattery = max(0.1, batteryKwh)

        val deltaPercent = (safeTarget - safeStart).coerceAtLeast(1)
        val effectiveKw = safePower * CHARGING_EFFICIENCY

        val taperRange = (safeTarget - max(safeStart, 85)).coerceAtLeast(0)
        val normalRange = deltaPercent - taperRange

        val normalHours = (safeBattery * (normalRange / 100.0)) / effectiveKw
        val taperHours = (safeBattery * (taperRange / 100.0)) / (effectiveKw * 0.72)

        val totalHours = normalHours + taperHours
        return max(60_000L, (totalHours * 3_600_000.0).roundToLong())
    }

    fun calculateEnergyNeededKwh(
        batteryKwh: Double,
        startPercent: Int,
        targetPercent: Int
    ): Double {
        val delta = (targetPercent - startPercent).coerceAtLeast(0)
        return batteryKwh * (delta / 100.0)
    }

    fun formatDurationHms(durationMs: Long): String {
        val totalSeconds = max(0L, durationMs / 1000L)
        val hours = totalSeconds / 3600L
        val minutes = (totalSeconds % 3600L) / 60L
        val seconds = totalSeconds % 60L
        return if (hours > 0L) {
            String.format(Locale.US, "%02dh %02dm %02ds", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%02dm %02ds", minutes, seconds)
        }
    }

    fun formatDurationShort(durationMs: Long): String {
        val totalMinutes = max(1L, (durationMs + 59_999L) / 60_000L)
        val hours = totalMinutes / 60L
        val minutes = totalMinutes % 60L
        return when {
            hours > 0 && minutes > 0 -> "${hours}h ${minutes}m"
            hours > 0 -> "${hours}h"
            else -> "${minutes}m"
        }
    }

    fun formatClockTime(epochMs: Long): String {
        val formatter = SimpleDateFormat("h:mm a", Locale.getDefault())
        return formatter.format(Date(epochMs))
    }
}
