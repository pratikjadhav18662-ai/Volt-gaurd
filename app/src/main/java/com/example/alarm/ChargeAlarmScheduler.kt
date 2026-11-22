package com.example.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

object ChargeAlarmScheduler {
    private const val TAG = "ChargeAlarmScheduler"
    private const val REQUEST_CODE_ALARM = 1001

    fun scheduleAlarm(
        context: Context,
        triggerAtEpochMs: Long,
        vehicleName: String,
        targetPercent: Int
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val pendingIntent = buildAlarmPendingIntent(context, vehicleName, targetPercent)

        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAtEpochMs,
                        pendingIntent
                    )
                } else {
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAtEpochMs,
                        pendingIntent
                    )
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtEpochMs,
                    pendingIntent
                )
            }
        }.onFailure { error ->
            Log.w(TAG, "Failed to schedule alarm via AlarmManager", error)
        }
    }

    fun cancelAlarm(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val pendingIntent = buildAlarmPendingIntent(context, "", 100)
        runCatching {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }

    private fun buildAlarmPendingIntent(
        context: Context,
        vehicleName: String,
        targetPercent: Int
    ): PendingIntent {
        val intent = Intent(context, ChargeAlarmReceiver::class.java).apply {
            action = ChargeAlarmReceiver.ACTION_TRIGGER_ALARM
            putExtra(ChargeAlarmService.EXTRA_VEHICLE_NAME, vehicleName)
            putExtra(ChargeAlarmService.EXTRA_TARGET_PERCENT, targetPercent)
        }
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_ALARM,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
