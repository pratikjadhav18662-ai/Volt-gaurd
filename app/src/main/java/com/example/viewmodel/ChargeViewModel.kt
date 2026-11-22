package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.alarm.AlarmTonePlayer
import com.example.alarm.ChargeAlarmScheduler
import com.example.alarm.ChargeAlarmService
import com.example.alarm.ChargeAlarmStateHolder
import com.example.data.VoltGuardPreferencesRepository
import com.example.model.AlarmSettings
import com.example.model.AlarmSoundType
import com.example.model.ChargeInputMode
import com.example.model.ChargeStatus
import com.example.model.ChargingCalculator
import com.example.model.DefaultVehicles
import com.example.model.PersistedSession
import com.example.model.ThemePreference
import com.example.model.VehicleProfile
import com.example.model.VehicleType
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.roundToLong

data class VoltGuardUiState(
    val vehicles: List<VehicleProfile> = DefaultVehicles.presets,
    val selectedVehicle: VehicleProfile = DefaultVehicles.presets.first(),
    val alarmSettings: AlarmSettings = AlarmSettings(),
    val inputMode: ChargeInputMode = ChargeInputMode.SMART_ESTIMATE,
    val startBatteryPercent: Int = 35,
    val directHours: Int = 1,
    val directMinutes: Int = 30,
    val status: ChargeStatus = ChargeStatus.IDLE,
    val liveBatteryPercent: Float = 35f,
    val remainingMs: Long = 0L,
    val totalDurationMs: Long = 0L,
    val etaEpochMs: Long = 0L,
    val isDemoMode: Boolean = false,
    val alarmRinging: Boolean = false,
    val alarmVehicleName: String = DefaultVehicles.presets.first().name,
    val alarmTargetPercent: Int = 100,
    val themePreference: ThemePreference = ThemePreference.SYSTEM,
    val useDynamicColor: Boolean = true
) {
    val estimatedDurationMs: Long
        get() = when (inputMode) {
            ChargeInputMode.SMART_ESTIMATE -> ChargingCalculator.estimateDurationMs(
                batteryKwh = selectedVehicle.batteryKwh,
                chargerKw = selectedVehicle.chargerKw,
                startPercent = startBatteryPercent,
                targetPercent = alarmSettings.targetPercent
            )
            ChargeInputMode.DIRECT_TIMER -> {
                val mins = (directHours * 60L + directMinutes).coerceAtLeast(1L)
                mins * 60_000L
            }
        }

    val estimatedEnergyKwh: Double
        get() = ChargingCalculator.calculateEnergyNeededKwh(
            batteryKwh = selectedVehicle.batteryKwh,
            startPercent = startBatteryPercent,
            targetPercent = alarmSettings.targetPercent
        )
}

class ChargeViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext = application.applicationContext
    private val repository = VoltGuardPreferencesRepository(appContext)
    private val previewPlayer = AlarmTonePlayer(appContext)

    private val _uiState = MutableStateFlow(VoltGuardUiState())
    val uiState: StateFlow<VoltGuardUiState> = _uiState.asStateFlow()

    private var activeSession: PersistedSession = PersistedSession()

    init {
        observePersistedData()
        observeAlarmServiceState()
        startLiveTicker()
    }

    private fun observePersistedData() {
        viewModelScope.launch {
            combine(
                repository.alarmSettingsFlow,
                repository.selectedVehicleIdFlow,
                repository.customVehiclesFlow,
                repository.persistedSessionFlow
            ) { settings, selectedId, customVehicles, session ->
                Quad(settings, selectedId, customVehicles, session)
            }.collect { (settings, selectedId, customVehicles, session) ->
                activeSession = session
                val allVehicles = DefaultVehicles.presets + customVehicles
                val selected = allVehicles.firstOrNull { it.id == selectedId } ?: allVehicles.first()

                _uiState.update { current ->
                    val clampedStart = current.startBatteryPercent.coerceIn(0, (settings.targetPercent - 1).coerceAtLeast(0))
                    val derivedStatus = when {
                        current.alarmRinging -> ChargeStatus.ALARM_RINGING
                        session.isActive && session.isPaused -> ChargeStatus.PAUSED
                        session.isActive -> ChargeStatus.CHARGING
                        current.status == ChargeStatus.COMPLETED -> ChargeStatus.COMPLETED
                        else -> ChargeStatus.IDLE
                    }
                    current.copy(
                        vehicles = allVehicles,
                        selectedVehicle = selected,
                        alarmSettings = settings,
                        startBatteryPercent = clampedStart,
                        status = derivedStatus,
                        isDemoMode = session.isDemoMode,
                        liveBatteryPercent = if (!session.isActive && derivedStatus == ChargeStatus.IDLE) {
                            clampedStart.toFloat()
                        } else {
                            current.liveBatteryPercent
                        }
                    )
                }
            }
        }
        viewModelScope.launch {
            combine(
                repository.themePreferenceFlow,
                repository.useDynamicColorFlow
            ) { themePref, dynamicColor ->
                themePref to dynamicColor
            }.collect { (themePref, dynamicColor) ->
                _uiState.update { current ->
                    current.copy(
                        themePreference = themePref,
                        useDynamicColor = dynamicColor
                    )
                }
            }
        }
    }

    private fun observeAlarmServiceState() {
        viewModelScope.launch {
            ChargeAlarmStateHolder.alarmState.collect { snapshot ->
                _uiState.update { current ->
                    if (snapshot.isRinging) {
                        current.copy(
                            alarmRinging = true,
                            alarmVehicleName = snapshot.vehicleName,
                            alarmTargetPercent = snapshot.targetPercent,
                            status = ChargeStatus.ALARM_RINGING,
                            liveBatteryPercent = snapshot.targetPercent.toFloat(),
                            remainingMs = 0L
                        )
                    } else if (current.alarmRinging) {
                        current.copy(
                            alarmRinging = false,
                            status = ChargeStatus.COMPLETED,
                            liveBatteryPercent = current.alarmTargetPercent.toFloat(),
                            remainingMs = 0L
                        )
                    } else {
                        current
                    }
                }
            }
        }
    }

    private fun startLiveTicker() {
        viewModelScope.launch {
            while (isActive) {
                val session = activeSession
                val now = System.currentTimeMillis()
                if (session.isActive && !ChargeAlarmStateHolder.alarmState.value.isRinging) {
                    if (session.isPaused) {
                        val total = max(1L, session.totalDurationMs)
                        val rem = session.pausedRemainingMs.coerceIn(0L, total)
                        val progress = ((total - rem).toFloat() / total.toFloat()).coerceIn(0f, 1f)
                        val percent = session.startPercent + (session.targetPercent - session.startPercent) * progress
                        _uiState.update {
                            it.copy(
                                status = ChargeStatus.PAUSED,
                                liveBatteryPercent = percent,
                                remainingMs = rem,
                                totalDurationMs = total,
                                etaEpochMs = now + rem,
                                isDemoMode = session.isDemoMode
                            )
                        }
                    } else {
                        val rem = (session.endEpochMs - now).coerceAtLeast(0L)
                        val total = max(1L, session.totalDurationMs)
                        if (rem <= 0L) {
                            if (_uiState.value.alarmSettings.alarmEnabled) {
                                triggerFullChargeAlarm(session.vehicleName, session.targetPercent)
                            } else {
                                completeSessionSilently(session.targetPercent)
                            }
                        } else {
                            val progress = ((total - rem).toFloat() / total.toFloat()).coerceIn(0f, 1f)
                            val percent = session.startPercent + (session.targetPercent - session.startPercent) * progress
                            _uiState.update {
                                it.copy(
                                    status = ChargeStatus.CHARGING,
                                    liveBatteryPercent = percent,
                                    remainingMs = rem,
                                    totalDurationMs = total,
                                    etaEpochMs = session.endEpochMs,
                                    isDemoMode = session.isDemoMode
                                )
                            }
                        }
                    }
                }
                delay(250L)
            }
        }
    }

    fun setInputMode(mode: ChargeInputMode) {
        _uiState.update { it.copy(inputMode = mode) }
    }

    fun setStartBatteryPercent(percent: Int) {
        _uiState.update { state ->
            val maxStart = (state.alarmSettings.targetPercent - 1).coerceAtLeast(0)
            val clamped = percent.coerceIn(0, maxStart)
            state.copy(
                startBatteryPercent = clamped,
                liveBatteryPercent = if (state.status == ChargeStatus.IDLE || state.status == ChargeStatus.COMPLETED) {
                    clamped.toFloat()
                } else {
                    state.liveBatteryPercent
                },
                status = if (state.status == ChargeStatus.COMPLETED) ChargeStatus.IDLE else state.status
            )
        }
    }

    fun setDirectTimer(hours: Int, minutes: Int) {
        val safeH = hours.coerceIn(0, 24)
        val safeM = if (safeH == 0) minutes.coerceIn(1, 59) else minutes.coerceIn(0, 59)
        _uiState.update { it.copy(directHours = safeH, directMinutes = safeM) }
    }

    fun toggleAlarmEnabled(enabled: Boolean) {
        val updated = _uiState.value.alarmSettings.copy(alarmEnabled = enabled)
        viewModelScope.launch { repository.updateAlarmSettings(updated) }

        val session = activeSession
        if (session.isActive && !session.isPaused) {
            if (enabled) {
                val now = System.currentTimeMillis()
                if (session.endEpochMs > now) {
                    ChargeAlarmScheduler.scheduleAlarm(
                        context = appContext,
                        triggerAtEpochMs = session.endEpochMs,
                        vehicleName = session.vehicleName,
                        targetPercent = session.targetPercent
                    )
                }
            } else {
                ChargeAlarmScheduler.cancelAlarm(appContext)
            }
        }
        if (!enabled && _uiState.value.alarmRinging) {
            dismissAlarm()
        }
    }

    fun setTargetPercent(target: Int) {
        val safeTarget = target.coerceIn(50, 100)
        val updated = _uiState.value.alarmSettings.copy(targetPercent = safeTarget)
        viewModelScope.launch { repository.updateAlarmSettings(updated) }
    }

    fun setThemePreference(preference: ThemePreference) {
        viewModelScope.launch { repository.updateThemePreference(preference) }
    }

    fun cycleThemePreference() {
        val next = _uiState.value.themePreference.next()
        viewModelScope.launch { repository.updateThemePreference(next) }
    }

    fun toggleDynamicColor(enabled: Boolean) {
        viewModelScope.launch { repository.updateUseDynamicColor(enabled) }
    }

    fun updateSoundType(soundType: AlarmSoundType) {
        val updated = _uiState.value.alarmSettings.copy(soundType = soundType)
        viewModelScope.launch { repository.updateAlarmSettings(updated) }
    }

    fun selectCustomSystemSound(uri: String, title: String) {
        val cleanTitle = title.trim().ifEmpty { "Custom System Sound" }
        val updated = _uiState.value.alarmSettings.copy(
            soundType = AlarmSoundType.CUSTOM_SYSTEM,
            customSoundUri = uri,
            customSoundTitle = cleanTitle
        )
        viewModelScope.launch { repository.updateAlarmSettings(updated) }
        previewPlayer.previewSound(
            soundType = AlarmSoundType.CUSTOM_SYSTEM,
            customSoundUri = uri,
            volume = updated.alarmVolume,
            vibrationEnabled = updated.vibrationEnabled
        )
    }

    fun toggleVoiceAnnouncement(enabled: Boolean) {
        val updated = _uiState.value.alarmSettings.copy(voiceAnnouncementEnabled = enabled)
        viewModelScope.launch { repository.updateAlarmSettings(updated) }
    }

    fun toggleVibration(enabled: Boolean) {
        val updated = _uiState.value.alarmSettings.copy(vibrationEnabled = enabled)
        viewModelScope.launch { repository.updateAlarmSettings(updated) }
    }

    fun updateAlarmVolume(volume: Float) {
        val updated = _uiState.value.alarmSettings.copy(alarmVolume = volume.coerceIn(0.2f, 1.0f))
        viewModelScope.launch { repository.updateAlarmSettings(updated) }
    }

    fun previewAlarmSound(soundType: AlarmSoundType) {
        val settings = _uiState.value.alarmSettings
        previewPlayer.previewSound(
            soundType = soundType,
            customSoundUri = settings.customSoundUri,
            volume = settings.alarmVolume,
            vibrationEnabled = settings.vibrationEnabled
        )
    }

    fun previewVoiceMessage() {
        val state = _uiState.value
        previewPlayer.previewVoice(
            vehicleName = state.selectedVehicle.name,
            targetPercent = state.alarmSettings.targetPercent
        )
    }

    fun selectVehicle(vehicleId: String) {
        viewModelScope.launch {
            repository.selectVehicle(vehicleId)
        }
    }

    fun addCustomVehicle(name: String, type: VehicleType, batteryKwh: Double, chargerKw: Double) {
        val cleanName = name.trim().ifEmpty { "My ${type.label}" }
        val profile = VehicleProfile(
            id = "custom_${UUID.randomUUID().toString().take(8)}",
            name = cleanName,
            type = type,
            batteryKwh = batteryKwh.coerceIn(0.2, 250.0),
            chargerKw = chargerKw.coerceIn(0.1, 350.0),
            isCustom = true
        )
        viewModelScope.launch {
            repository.addCustomVehicle(profile)
        }
    }

    fun deleteCustomVehicle(vehicleId: String) {
        viewModelScope.launch {
            repository.deleteCustomVehicle(vehicleId)
        }
    }

    fun startChargingSession() {
        previewPlayer.stopAll()
        val state = _uiState.value
        val now = System.currentTimeMillis()
        val durationMs = state.estimatedDurationMs
        val endMs = now + durationMs
        val target = state.alarmSettings.targetPercent
        val start = state.startBatteryPercent.coerceIn(0, (target - 1).coerceAtLeast(0))

        val session = PersistedSession(
            isActive = true,
            isPaused = false,
            startPercent = start,
            targetPercent = target,
            startEpochMs = now,
            endEpochMs = endMs,
            totalDurationMs = durationMs,
            pausedRemainingMs = 0L,
            vehicleName = state.selectedVehicle.name,
            isDemoMode = false
        )
        activeSession = session
        viewModelScope.launch {
            repository.saveSession(session)
        }
        if (state.alarmSettings.alarmEnabled) {
            ChargeAlarmScheduler.scheduleAlarm(
                context = appContext,
                triggerAtEpochMs = endMs,
                vehicleName = state.selectedVehicle.name,
                targetPercent = target
            )
        } else {
            ChargeAlarmScheduler.cancelAlarm(appContext)
        }
        _uiState.update {
            it.copy(
                status = ChargeStatus.CHARGING,
                liveBatteryPercent = start.toFloat(),
                remainingMs = durationMs,
                totalDurationMs = durationMs,
                etaEpochMs = endMs,
                isDemoMode = false
            )
        }
    }

    /**
     * Launches a 10-second live demonstration charging from 96% to target (100%)
     * and fires the real 100% sound + voice alarm so the user can test end-to-end immediately.
     */
    fun startQuickTenSecondDemo() {
        previewPlayer.stopAll()
        val state = _uiState.value
        val now = System.currentTimeMillis()
        val durationMs = 10_000L
        val endMs = now + durationMs
        val target = state.alarmSettings.targetPercent
        val start = (target - 4).coerceAtLeast(50)

        val session = PersistedSession(
            isActive = true,
            isPaused = false,
            startPercent = start,
            targetPercent = target,
            startEpochMs = now,
            endEpochMs = endMs,
            totalDurationMs = durationMs,
            pausedRemainingMs = 0L,
            vehicleName = state.selectedVehicle.name,
            isDemoMode = true
        )
        activeSession = session
        viewModelScope.launch {
            repository.saveSession(session)
        }
        if (!state.alarmSettings.alarmEnabled) {
            // Auto-arm alarm for quick demo so user can verify the 100% alert
            viewModelScope.launch {
                repository.updateAlarmSettings(state.alarmSettings.copy(alarmEnabled = true))
            }
        }
        ChargeAlarmScheduler.scheduleAlarm(
            context = appContext,
            triggerAtEpochMs = endMs,
            vehicleName = state.selectedVehicle.name,
            targetPercent = target
        )
        _uiState.update {
            it.copy(
                status = ChargeStatus.CHARGING,
                liveBatteryPercent = start.toFloat(),
                remainingMs = durationMs,
                totalDurationMs = durationMs,
                etaEpochMs = endMs,
                isDemoMode = true
            )
        }
    }

    fun pauseCharging() {
        val session = activeSession
        if (!session.isActive || session.isPaused) return
        val rem = (session.endEpochMs - System.currentTimeMillis()).coerceAtLeast(1000L)
        ChargeAlarmScheduler.cancelAlarm(appContext)
        val pausedSession = session.copy(isPaused = true, pausedRemainingMs = rem)
        activeSession = pausedSession
        viewModelScope.launch { repository.saveSession(pausedSession) }
        _uiState.update { it.copy(status = ChargeStatus.PAUSED, remainingMs = rem) }
    }

    fun resumeCharging() {
        val session = activeSession
        if (!session.isActive || !session.isPaused) return
        val now = System.currentTimeMillis()
        val rem = session.pausedRemainingMs.coerceAtLeast(1000L)
        val newEnd = now + rem
        val resumed = session.copy(isPaused = false, endEpochMs = newEnd, pausedRemainingMs = 0L)
        activeSession = resumed
        viewModelScope.launch { repository.saveSession(resumed) }
        if (_uiState.value.alarmSettings.alarmEnabled) {
            ChargeAlarmScheduler.scheduleAlarm(
                context = appContext,
                triggerAtEpochMs = newEnd,
                vehicleName = resumed.vehicleName,
                targetPercent = resumed.targetPercent
            )
        }
        _uiState.update { it.copy(status = ChargeStatus.CHARGING, etaEpochMs = newEnd) }
    }

    fun adjustLiveBatteryBy(deltaPercent: Int) {
        val session = activeSession
        if (!session.isActive) return
        val state = _uiState.value
        val currentRounded = state.liveBatteryPercent.roundToInt()
        val newPercent = (currentRounded + deltaPercent).coerceIn(0, session.targetPercent)

        if (newPercent >= session.targetPercent) {
            triggerFullChargeAlarm(session.vehicleName, session.targetPercent)
            return
        }

        val now = System.currentTimeMillis()
        val newDuration = if (session.isDemoMode) {
            val remainingRatio = (session.targetPercent - newPercent).toDouble() /
                max(1, session.targetPercent - session.startPercent).toDouble()
            max(2000L, (session.totalDurationMs * remainingRatio).roundToLong())
        } else {
            ChargingCalculator.estimateDurationMs(
                batteryKwh = state.selectedVehicle.batteryKwh,
                chargerKw = state.selectedVehicle.chargerKw,
                startPercent = newPercent,
                targetPercent = session.targetPercent
            )
        }

        val newEnd = now + newDuration
        val updated = session.copy(
            startPercent = newPercent,
            startEpochMs = now,
            endEpochMs = newEnd,
            totalDurationMs = newDuration,
            pausedRemainingMs = if (session.isPaused) newDuration else 0L
        )
        activeSession = updated
        viewModelScope.launch { repository.saveSession(updated) }
        if (!updated.isPaused) {
            ChargeAlarmScheduler.scheduleAlarm(
                context = appContext,
                triggerAtEpochMs = newEnd,
                vehicleName = updated.vehicleName,
                targetPercent = updated.targetPercent
            )
        }
        _uiState.update {
            it.copy(
                liveBatteryPercent = newPercent.toFloat(),
                remainingMs = newDuration,
                totalDurationMs = newDuration,
                etaEpochMs = newEnd
            )
        }
    }

    fun stopChargingSession() {
        ChargeAlarmScheduler.cancelAlarm(appContext)
        ChargeAlarmService.stopAlarm(appContext)
        previewPlayer.stopAll()
        activeSession = PersistedSession()
        viewModelScope.launch { repository.clearSession() }
        _uiState.update { state ->
            state.copy(
                status = ChargeStatus.IDLE,
                liveBatteryPercent = state.startBatteryPercent.toFloat(),
                remainingMs = 0L,
                isDemoMode = false,
                alarmRinging = false
            )
        }
    }

    fun triggerFullChargeAlarmNow() {
        val state = _uiState.value
        triggerFullChargeAlarm(state.selectedVehicle.name, state.alarmSettings.targetPercent)
    }

    private fun completeSessionSilently(targetPercent: Int) {
        ChargeAlarmScheduler.cancelAlarm(appContext)
        activeSession = PersistedSession()
        viewModelScope.launch { repository.clearSession() }
        _uiState.update {
            it.copy(
                status = ChargeStatus.COMPLETED,
                liveBatteryPercent = targetPercent.toFloat(),
                remainingMs = 0L,
                isDemoMode = false
            )
        }
    }

    private fun triggerFullChargeAlarm(vehicleName: String, targetPercent: Int) {
        ChargeAlarmScheduler.cancelAlarm(appContext)
        previewPlayer.stopAll()
        activeSession = PersistedSession()
        viewModelScope.launch { repository.clearSession() }
        ChargeAlarmService.startAlarm(appContext, vehicleName, targetPercent)
    }

    fun dismissAlarm() {
        ChargeAlarmScheduler.cancelAlarm(appContext)
        ChargeAlarmService.stopAlarm(appContext)
        previewPlayer.stopAll()
        _uiState.update { state ->
            state.copy(
                alarmRinging = false,
                status = ChargeStatus.COMPLETED,
                remainingMs = 0L
            )
        }
    }

    fun snoozeAlarmFiveMinutes() {
        val state = _uiState.value
        ChargeAlarmService.stopAlarm(appContext)
        val now = System.currentTimeMillis()
        val snoozeMs = 5 * 60_000L
        val endMs = now + snoozeMs
        val session = PersistedSession(
            isActive = true,
            isPaused = false,
            startPercent = (state.alarmTargetPercent - 1).coerceAtLeast(95),
            targetPercent = state.alarmTargetPercent,
            startEpochMs = now,
            endEpochMs = endMs,
            totalDurationMs = snoozeMs,
            pausedRemainingMs = 0L,
            vehicleName = state.alarmVehicleName,
            isDemoMode = false
        )
        activeSession = session
        viewModelScope.launch { repository.saveSession(session) }
        ChargeAlarmScheduler.scheduleAlarm(
            context = appContext,
            triggerAtEpochMs = endMs,
            vehicleName = state.alarmVehicleName,
            targetPercent = state.alarmTargetPercent
        )
        _uiState.update {
            it.copy(
                alarmRinging = false,
                status = ChargeStatus.CHARGING,
                remainingMs = snoozeMs,
                totalDurationMs = snoozeMs,
                etaEpochMs = endMs
            )
        }
    }

    override fun onCleared() {
        previewPlayer.stopAll()
        super.onCleared()
    }

    private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
}
