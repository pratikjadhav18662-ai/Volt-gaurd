package com.example.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.data.VoltGuardPreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class ChargeAlarmService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var tonePlayer: AlarmTonePlayer
    private lateinit var preferencesRepository: VoltGuardPreferencesRepository

    override fun onCreate() {
        super.onCreate()
        tonePlayer = AlarmTonePlayer(applicationContext)
        preferencesRepository = VoltGuardPreferencesRepository(applicationContext)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP_ALARM -> {
                stopAlarmAndSelf()
                return START_NOT_STICKY
            }
            ACTION_START_ALARM, null -> {
                val vehicleName = intent?.getStringExtra(EXTRA_VEHICLE_NAME) ?: "Electric Vehicle"
                val targetPercent = intent?.getIntExtra(EXTRA_TARGET_PERCENT, 100) ?: 100

                ChargeAlarmStateHolder.setRinging(vehicleName, targetPercent)
                val notification = buildAlarmNotification(vehicleName, targetPercent)

                runCatching {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        ServiceCompat.startForeground(
                            this,
                            NOTIFICATION_ID,
                            notification,
                            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                        )
                    } else {
                        startForeground(NOTIFICATION_ID, notification)
                    }
                }

                serviceScope.launch {
                    val settings = preferencesRepository.getAlarmSettingsSnapshot()
                    preferencesRepository.clearSession()
                    tonePlayer.startAlarm(
                        soundType = settings.soundType,
                        customSoundUri = settings.customSoundUri,
                        volume = settings.alarmVolume,
                        voiceEnabled = settings.voiceAnnouncementEnabled,
                        vibrationEnabled = settings.vibrationEnabled,
                        vehicleName = vehicleName,
                        targetPercent = targetPercent
                    )
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun stopAlarmAndSelf() {
        tonePlayer.stopAll()
        ChargeAlarmStateHolder.clear()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        tonePlayer.stopAll()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notif_channel_alarm_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = getString(R.string.notif_channel_alarm_desc)
                enableVibration(true)
                setSound(null, null)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildAlarmNotification(vehicleName: String, targetPercent: Int): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_FROM_ALARM, true)
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this,
            2001,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, ChargeAlarmReceiver::class.java).apply {
            action = ChargeAlarmReceiver.ACTION_STOP_ALARM
        }
        val stopPendingIntent = PendingIntent.getBroadcast(
            this,
            2002,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = getString(R.string.alarm_title_full, targetPercent)
        val contentText = "$vehicleName reached $targetPercent%. Unplug your charger now."

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(contentText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(openAppPendingIntent)
            .addAction(
                android.R.drawable.ic_media_pause,
                getString(R.string.notif_action_stop_alarm),
                stopPendingIntent
            )
            .build()
    }

    companion object {
        const val ACTION_START_ALARM = "com.example.voltguard.ACTION_START_ALARM"
        const val ACTION_STOP_ALARM = "com.example.voltguard.ACTION_STOP_ALARM"
        const val EXTRA_VEHICLE_NAME = "extra_vehicle_name"
        const val EXTRA_TARGET_PERCENT = "extra_target_percent"
        const val EXTRA_FROM_ALARM = "extra_from_alarm"

        private const val CHANNEL_ID = "voltguard_full_charge_alarm_channel"
        private const val NOTIFICATION_ID = 9001

        fun startAlarm(context: Context, vehicleName: String, targetPercent: Int) {
            val intent = Intent(context, ChargeAlarmService::class.java).apply {
                action = ACTION_START_ALARM
                putExtra(EXTRA_VEHICLE_NAME, vehicleName)
                putExtra(EXTRA_TARGET_PERCENT, targetPercent)
            }
            runCatching {
                ContextCompat.startForegroundService(context, intent)
            }
        }

        fun stopAlarm(context: Context) {
            ChargeAlarmStateHolder.clear()
            val intent = Intent(context, ChargeAlarmService::class.java).apply {
                action = ACTION_STOP_ALARM
            }
            runCatching {
                context.stopService(intent)
            }
        }
    }
}
