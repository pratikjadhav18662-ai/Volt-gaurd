package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.model.AlarmSettings
import com.example.model.AlarmSoundType
import com.example.model.DefaultVehicles
import com.example.model.PersistedSession
import com.example.model.ThemePreference
import com.example.model.VehicleProfile
import com.example.model.VehicleType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.voltGuardDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "voltguard_preferences"
)

class VoltGuardPreferencesRepository(private val context: Context) {

    private object Keys {
        val ALARM_ENABLED = booleanPreferencesKey("alarm_enabled")
        val TARGET_PERCENT = intPreferencesKey("target_percent")
        val SOUND_TYPE = stringPreferencesKey("sound_type")
        val CUSTOM_SOUND_URI = stringPreferencesKey("custom_sound_uri")
        val CUSTOM_SOUND_TITLE = stringPreferencesKey("custom_sound_title")
        val VOICE_ENABLED = booleanPreferencesKey("voice_enabled")
        val VIBRATION_ENABLED = booleanPreferencesKey("vibration_enabled")
        val ALARM_VOLUME = floatPreferencesKey("alarm_volume")
        val THEME_PREFERENCE = stringPreferencesKey("theme_preference")
        val USE_DYNAMIC_COLOR = booleanPreferencesKey("use_dynamic_color")
        val SELECTED_VEHICLE_ID = stringPreferencesKey("selected_vehicle_id")
        val CUSTOM_VEHICLES = stringSetPreferencesKey("custom_vehicles")

        val SESSION_ACTIVE = booleanPreferencesKey("session_active")
        val SESSION_PAUSED = booleanPreferencesKey("session_paused")
        val SESSION_START_PERCENT = intPreferencesKey("session_start_percent")
        val SESSION_TARGET_PERCENT = intPreferencesKey("session_target_percent")
        val SESSION_START_MS = longPreferencesKey("session_start_ms")
        val SESSION_END_MS = longPreferencesKey("session_end_ms")
        val SESSION_TOTAL_MS = longPreferencesKey("session_total_ms")
        val SESSION_PAUSED_REMAINING_MS = longPreferencesKey("session_paused_remaining_ms")
        val SESSION_VEHICLE_NAME = stringPreferencesKey("session_vehicle_name")
        val SESSION_DEMO_MODE = booleanPreferencesKey("session_demo_mode")
    }

    val alarmSettingsFlow: Flow<AlarmSettings> = context.voltGuardDataStore.data.map { prefs ->
        val soundName = prefs[Keys.SOUND_TYPE] ?: AlarmSoundType.VOLT_CHIME.name
        val soundType = runCatching { AlarmSoundType.valueOf(soundName) }
            .getOrDefault(AlarmSoundType.VOLT_CHIME)
        AlarmSettings(
            alarmEnabled = prefs[Keys.ALARM_ENABLED] ?: true,
            targetPercent = (prefs[Keys.TARGET_PERCENT] ?: 100).coerceIn(50, 100),
            soundType = soundType,
            customSoundUri = prefs[Keys.CUSTOM_SOUND_URI]?.takeIf { it.isNotBlank() },
            customSoundTitle = prefs[Keys.CUSTOM_SOUND_TITLE]?.takeIf { it.isNotBlank() },
            voiceAnnouncementEnabled = prefs[Keys.VOICE_ENABLED] ?: true,
            vibrationEnabled = prefs[Keys.VIBRATION_ENABLED] ?: true,
            alarmVolume = (prefs[Keys.ALARM_VOLUME] ?: 0.92f).coerceIn(0.2f, 1.0f)
        )
    }

    val themePreferenceFlow: Flow<ThemePreference> = context.voltGuardDataStore.data.map { prefs ->
        val saved = prefs[Keys.THEME_PREFERENCE] ?: ThemePreference.SYSTEM.name
        runCatching { ThemePreference.valueOf(saved) }.getOrDefault(ThemePreference.SYSTEM)
    }

    val useDynamicColorFlow: Flow<Boolean> = context.voltGuardDataStore.data.map { prefs ->
        prefs[Keys.USE_DYNAMIC_COLOR] ?: true
    }

    val selectedVehicleIdFlow: Flow<String> = context.voltGuardDataStore.data.map { prefs ->
        prefs[Keys.SELECTED_VEHICLE_ID] ?: DefaultVehicles.presets.first().id
    }

    val customVehiclesFlow: Flow<List<VehicleProfile>> = context.voltGuardDataStore.data.map { prefs ->
        val rawSet = prefs[Keys.CUSTOM_VEHICLES].orEmpty()
        rawSet.mapNotNull { deserializeVehicle(it) }.sortedBy { it.name.lowercase() }
    }

    val persistedSessionFlow: Flow<PersistedSession> = context.voltGuardDataStore.data.map { prefs ->
        PersistedSession(
            isActive = prefs[Keys.SESSION_ACTIVE] ?: false,
            isPaused = prefs[Keys.SESSION_PAUSED] ?: false,
            startPercent = prefs[Keys.SESSION_START_PERCENT] ?: 35,
            targetPercent = prefs[Keys.SESSION_TARGET_PERCENT] ?: 100,
            startEpochMs = prefs[Keys.SESSION_START_MS] ?: 0L,
            endEpochMs = prefs[Keys.SESSION_END_MS] ?: 0L,
            totalDurationMs = prefs[Keys.SESSION_TOTAL_MS] ?: 0L,
            pausedRemainingMs = prefs[Keys.SESSION_PAUSED_REMAINING_MS] ?: 0L,
            vehicleName = prefs[Keys.SESSION_VEHICLE_NAME] ?: "Electric Vehicle",
            isDemoMode = prefs[Keys.SESSION_DEMO_MODE] ?: false
        )
    }

    suspend fun getAlarmSettingsSnapshot(): AlarmSettings = alarmSettingsFlow.first()

    suspend fun updateAlarmSettings(settings: AlarmSettings) {
        context.voltGuardDataStore.edit { prefs ->
            prefs[Keys.ALARM_ENABLED] = settings.alarmEnabled
            prefs[Keys.TARGET_PERCENT] = settings.targetPercent.coerceIn(50, 100)
            prefs[Keys.SOUND_TYPE] = settings.soundType.name
            if (settings.customSoundUri.isNullOrBlank()) {
                prefs.remove(Keys.CUSTOM_SOUND_URI)
            } else {
                prefs[Keys.CUSTOM_SOUND_URI] = settings.customSoundUri
            }
            if (settings.customSoundTitle.isNullOrBlank()) {
                prefs.remove(Keys.CUSTOM_SOUND_TITLE)
            } else {
                prefs[Keys.CUSTOM_SOUND_TITLE] = settings.customSoundTitle
            }
            prefs[Keys.VOICE_ENABLED] = settings.voiceAnnouncementEnabled
            prefs[Keys.VIBRATION_ENABLED] = settings.vibrationEnabled
            prefs[Keys.ALARM_VOLUME] = settings.alarmVolume.coerceIn(0.2f, 1.0f)
        }
    }

    suspend fun updateThemePreference(preference: ThemePreference) {
        context.voltGuardDataStore.edit { prefs ->
            prefs[Keys.THEME_PREFERENCE] = preference.name
        }
    }

    suspend fun updateUseDynamicColor(enabled: Boolean) {
        context.voltGuardDataStore.edit { prefs ->
            prefs[Keys.USE_DYNAMIC_COLOR] = enabled
        }
    }

    suspend fun selectVehicle(vehicleId: String) {
        context.voltGuardDataStore.edit { prefs ->
            prefs[Keys.SELECTED_VEHICLE_ID] = vehicleId
        }
    }

    suspend fun addCustomVehicle(vehicle: VehicleProfile) {
        context.voltGuardDataStore.edit { prefs ->
            val current = prefs[Keys.CUSTOM_VEHICLES].orEmpty().toMutableSet()
            current.removeAll { it.startsWith("${vehicle.id}|") }
            current.add(serializeVehicle(vehicle))
            prefs[Keys.CUSTOM_VEHICLES] = current
            prefs[Keys.SELECTED_VEHICLE_ID] = vehicle.id
        }
    }

    suspend fun deleteCustomVehicle(vehicleId: String) {
        context.voltGuardDataStore.edit { prefs ->
            val current = prefs[Keys.CUSTOM_VEHICLES].orEmpty().toMutableSet()
            current.removeAll { it.startsWith("$vehicleId|") }
            prefs[Keys.CUSTOM_VEHICLES] = current
            if (prefs[Keys.SELECTED_VEHICLE_ID] == vehicleId) {
                prefs[Keys.SELECTED_VEHICLE_ID] = DefaultVehicles.presets.first().id
            }
        }
    }

    suspend fun saveSession(session: PersistedSession) {
        context.voltGuardDataStore.edit { prefs ->
            prefs[Keys.SESSION_ACTIVE] = session.isActive
            prefs[Keys.SESSION_PAUSED] = session.isPaused
            prefs[Keys.SESSION_START_PERCENT] = session.startPercent
            prefs[Keys.SESSION_TARGET_PERCENT] = session.targetPercent
            prefs[Keys.SESSION_START_MS] = session.startEpochMs
            prefs[Keys.SESSION_END_MS] = session.endEpochMs
            prefs[Keys.SESSION_TOTAL_MS] = session.totalDurationMs
            prefs[Keys.SESSION_PAUSED_REMAINING_MS] = session.pausedRemainingMs
            prefs[Keys.SESSION_VEHICLE_NAME] = session.vehicleName
            prefs[Keys.SESSION_DEMO_MODE] = session.isDemoMode
        }
    }

    suspend fun clearSession() {
        context.voltGuardDataStore.edit { prefs ->
            prefs[Keys.SESSION_ACTIVE] = false
            prefs[Keys.SESSION_PAUSED] = false
            prefs[Keys.SESSION_START_MS] = 0L
            prefs[Keys.SESSION_END_MS] = 0L
            prefs[Keys.SESSION_TOTAL_MS] = 0L
            prefs[Keys.SESSION_PAUSED_REMAINING_MS] = 0L
            prefs[Keys.SESSION_DEMO_MODE] = false
        }
    }

    private fun serializeVehicle(v: VehicleProfile): String {
        val safeName = v.name.replace("|", " ").trim()
        return "${v.id}|$safeName|${v.type.name}|${v.batteryKwh}|${v.chargerKw}"
    }

    private fun deserializeVehicle(raw: String): VehicleProfile? {
        val parts = raw.split("|")
        if (parts.size < 5) return null
        val type = runCatching { VehicleType.valueOf(parts[2]) }.getOrDefault(VehicleType.E_SCOOTER)
        val battery = parts[3].toDoubleOrNull() ?: return null
        val charger = parts[4].toDoubleOrNull() ?: return null
        return VehicleProfile(
            id = parts[0],
            name = parts[1],
            type = type,
            batteryKwh = battery,
            chargerKw = charger,
            isCustom = true
        )
    }
}
