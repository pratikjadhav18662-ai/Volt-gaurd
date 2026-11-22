package com.example.alarm

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ActiveAlarmSnapshot(
    val isRinging: Boolean = false,
    val vehicleName: String = "Electric Vehicle",
    val targetPercent: Int = 100
)

object ChargeAlarmStateHolder {
    private val _alarmState = MutableStateFlow(ActiveAlarmSnapshot())
    val alarmState: StateFlow<ActiveAlarmSnapshot> = _alarmState.asStateFlow()

    fun setRinging(vehicleName: String, targetPercent: Int) {
        _alarmState.value = ActiveAlarmSnapshot(
            isRinging = true,
            vehicleName = vehicleName,
            targetPercent = targetPercent
        )
    }

    fun clear() {
        _alarmState.value = ActiveAlarmSnapshot(isRinging = false)
    }
}
