package com.example.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class ChargeAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            ACTION_TRIGGER_ALARM -> {
                val vehicleName = intent.getStringExtra(ChargeAlarmService.EXTRA_VEHICLE_NAME)
                    ?: "Electric Vehicle"
                val targetPercent = intent.getIntExtra(ChargeAlarmService.EXTRA_TARGET_PERCENT, 100)
                ChargeAlarmService.startAlarm(context, vehicleName, targetPercent)
            }
            ACTION_STOP_ALARM -> {
                ChargeAlarmService.stopAlarm(context)
            }
        }
    }

    companion object {
        const val ACTION_TRIGGER_ALARM = "com.example.voltguard.TRIGGER_FULL_CHARGE_ALARM"
        const val ACTION_STOP_ALARM = "com.example.voltguard.STOP_FULL_CHARGE_ALARM"
    }
}
