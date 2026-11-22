package com.example.alarm

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import android.util.Log
import com.example.R
import com.example.model.AlarmSoundType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.PI
import kotlin.math.sin

class AlarmTonePlayer(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var mediaPlayer: MediaPlayer? = null
    private var audioTrack: AudioTrack? = null
    private var synthJob: Job? = null
    private var ttsJob: Job? = null
    private var textToSpeech: TextToSpeech? = null
    @Volatile
    private var ttsReady: Boolean = false

    private val alarmAudioAttributes: AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    fun startAlarm(
        soundType: AlarmSoundType,
        customSoundUri: String?,
        volume: Float,
        voiceEnabled: Boolean,
        vibrationEnabled: Boolean,
        vehicleName: String,
        targetPercent: Int
    ) {
        stopAll()
        val safeVolume = volume.coerceIn(0.2f, 1.0f)

        when (soundType) {
            AlarmSoundType.SYSTEM_ALARM -> startRingtoneUriLoop(uri = null, volume = safeVolume)
            AlarmSoundType.CUSTOM_SYSTEM -> {
                val parsedUri = customSoundUri?.takeIf { it.isNotBlank() }?.let { runCatching { Uri.parse(it) }.getOrNull() }
                startRingtoneUriLoop(uri = parsedUri, volume = safeVolume)
            }
            AlarmSoundType.VOLT_CHIME -> startSynthesizedLoop(isSiren = false, volume = safeVolume)
            AlarmSoundType.PULSE_SIREN -> startSynthesizedLoop(isSiren = true, volume = safeVolume)
        }

        if (vibrationEnabled) {
            startVibration(repeat = true)
        }

        if (voiceEnabled) {
            val spokenText = context.getString(R.string.alarm_tts_message, vehicleName, targetPercent)
            startVoiceAnnouncements(spokenText, repeat = true)
        }
    }

    fun previewSound(
        soundType: AlarmSoundType,
        customSoundUri: String?,
        volume: Float,
        vibrationEnabled: Boolean
    ) {
        stopAll()
        val safeVolume = volume.coerceIn(0.2f, 1.0f)

        if (vibrationEnabled) {
            startVibration(repeat = false)
        }

        synthJob = scope.launch {
            when (soundType) {
                AlarmSoundType.SYSTEM_ALARM -> {
                    startRingtoneUriLoop(uri = null, volume = safeVolume)
                    delay(2800L)
                    stopMediaPlayer()
                }
                AlarmSoundType.CUSTOM_SYSTEM -> {
                    val parsedUri = customSoundUri?.takeIf { it.isNotBlank() }?.let { runCatching { Uri.parse(it) }.getOrNull() }
                    startRingtoneUriLoop(uri = parsedUri, volume = safeVolume)
                    delay(3200L)
                    stopMediaPlayer()
                }
                AlarmSoundType.VOLT_CHIME -> {
                    playSynthesizedPatternOnce(isSiren = false, volume = safeVolume)
                }
                AlarmSoundType.PULSE_SIREN -> {
                    playSynthesizedPatternOnce(isSiren = true, volume = safeVolume)
                }
            }
        }
    }

    fun previewVoice(vehicleName: String, targetPercent: Int) {
        stopVoice()
        val spokenText = context.getString(R.string.alarm_tts_message, vehicleName, targetPercent)
        startVoiceAnnouncements(spokenText, repeat = false)
    }

    fun stopAll() {
        synthJob?.cancel()
        synthJob = null
        stopAudioTrack()
        stopMediaPlayer()
        stopVoice()
        stopVibration()
    }

    private fun startRingtoneUriLoop(uri: Uri?, volume: Float) {
        val resolvedUri = uri
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)

        if (resolvedUri == null) {
            startSynthesizedLoop(isSiren = false, volume = volume)
            return
        }

        val started = runCatching {
            val mp = MediaPlayer().apply {
                setAudioAttributes(alarmAudioAttributes)
                setDataSource(context, resolvedUri)
                isLooping = true
                setVolume(volume, volume)
                prepare()
                start()
            }
            mediaPlayer = mp
            true
        }.getOrElse { error ->
            Log.w(TAG, "Ringtone URI playback failed, falling back to Volt Chime", error)
            false
        }

        if (!started) {
            startSynthesizedLoop(isSiren = false, volume = volume)
        }
    }

    private fun startSynthesizedLoop(isSiren: Boolean, volume: Float) {
        synthJob = scope.launch {
            while (isActive) {
                playSynthesizedPatternOnce(isSiren = isSiren, volume = volume)
                delay(if (isSiren) 450L else 1200L)
            }
        }
    }

    private suspend fun playSynthesizedPatternOnce(isSiren: Boolean, volume: Float) {
        val sampleRate = 22_050
        val pcmSamples = if (isSiren) {
            buildPulseSirenPcm(sampleRate, volume)
        } else {
            buildVoltChimePcm(sampleRate, volume)
        }

        runCatching {
            val bufferBytes = pcmSamples.size * 2
            val minBuffer = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(bufferBytes)

            val track = AudioTrack.Builder()
                .setAudioAttributes(alarmAudioAttributes)
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(minBuffer)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()

            audioTrack = track
            track.write(pcmSamples, 0, pcmSamples.size)
            track.play()

            val durationMs = (pcmSamples.size * 1000L) / sampleRate
            delay(durationMs + 100L)
            stopAudioTrack()
        }.onFailure { error ->
            Log.w(TAG, "AudioTrack synthesis playback failed", error)
        }
    }

    private fun buildVoltChimePcm(sampleRate: Int, volume: Float): ShortArray {
        val notesHz = doubleArrayOf(523.25, 659.25, 783.99, 1046.50)
        val noteDurationSec = 0.22
        val gapSec = 0.04
        val samplesPerNote = (sampleRate * noteDurationSec).toInt()
        val samplesPerGap = (sampleRate * gapSec).toInt()
        val totalSamples = notesHz.size * (samplesPerNote + samplesPerGap)
        val output = ShortArray(totalSamples)
        val amplitude = (Short.MAX_VALUE * 0.72 * volume).toDouble()

        var offset = 0
        for (freq in notesHz) {
            for (i in 0 until samplesPerNote) {
                val t = i.toDouble() / sampleRate
                val progress = i.toDouble() / samplesPerNote
                val attack = (progress / 0.08).coerceAtMost(1.0)
                val decay = (1.0 - progress).coerceAtLeast(0.0)
                val envelope = attack * (0.25 + 0.75 * decay)
                val wave = 0.75 * sin(2.0 * PI * freq * t) + 0.25 * sin(2.0 * PI * (freq * 2.0) * t)
                output[offset + i] = (wave * amplitude * envelope).toInt()
                    .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                    .toShort()
            }
            offset += samplesPerNote + samplesPerGap
        }
        return output
    }

    private fun buildPulseSirenPcm(sampleRate: Int, volume: Float): ShortArray {
        val pulseCount = 3
        val pulseSamples = (sampleRate * 0.24).toInt()
        val pauseSamples = (sampleRate * 0.14).toInt()
        val totalSamples = pulseCount * (pulseSamples + pauseSamples)
        val output = ShortArray(totalSamples)
        val amplitude = (Short.MAX_VALUE * 0.80 * volume).toDouble()

        var offset = 0
        for (p in 0 until pulseCount) {
            for (i in 0 until pulseSamples) {
                val t = i.toDouble() / sampleRate
                val progress = i.toDouble() / pulseSamples
                val env = when {
                    progress < 0.06 -> progress / 0.06
                    progress > 0.90 -> (1.0 - progress) / 0.10
                    else -> 1.0
                }
                val wave = 0.65 * sin(2.0 * PI * 880.0 * t) + 0.35 * sin(2.0 * PI * 1320.0 * t)
                output[offset + i] = (wave * amplitude * env).toInt()
                    .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                    .toShort()
            }
            offset += pulseSamples + pauseSamples
        }
        return output
    }

    private fun startVoiceAnnouncements(message: String, repeat: Boolean) {
        stopVoice()
        ttsReady = false

        val tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                textToSpeech?.setAudioAttributes(alarmAudioAttributes)
                textToSpeech?.language = Locale.getDefault()
                ttsReady = true
            }
        }
        textToSpeech = tts

        ttsJob = scope.launch {
            var waited = 0L
            while (!ttsReady && waited < 3500L && isActive) {
                delay(150L)
                waited += 150L
            }
            if (!ttsReady || !isActive) return@launch

            val params = Bundle().apply {
                putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
            }
            if (repeat) {
                while (isActive) {
                    runCatching {
                        textToSpeech?.speak(message, TextToSpeech.QUEUE_FLUSH, params, "voltguard_full")
                    }
                    delay(6500L)
                }
            } else {
                runCatching {
                    textToSpeech?.speak(message, TextToSpeech.QUEUE_FLUSH, params, "voltguard_preview")
                }
            }
        }
    }

    private fun startVibration(repeat: Boolean) {
        runCatching {
            val vibrator = getVibrator() ?: return
            if (!vibrator.hasVibrator()) return
            val timings = longArrayOf(0L, 450L, 250L, 450L, 600L)
            val repeatIndex = if (repeat) 0 else -1
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(timings, repeatIndex))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(timings, repeatIndex)
            }
        }
    }

    private fun stopVibration() {
        runCatching {
            getVibrator()?.cancel()
        }
    }

    private fun getVibrator(): Vibrator? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            manager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    private fun stopAudioTrack() {
        runCatching {
            audioTrack?.let { track ->
                if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                    track.stop()
                }
                track.release()
            }
        }
        audioTrack = null
    }

    private fun stopMediaPlayer() {
        runCatching {
            mediaPlayer?.let { mp ->
                if (mp.isPlaying) mp.stop()
                mp.release()
            }
        }
        mediaPlayer = null
    }

    private fun stopVoice() {
        ttsJob?.cancel()
        ttsJob = null
        runCatching {
            textToSpeech?.stop()
            textToSpeech?.shutdown()
        }
        textToSpeech = null
        ttsReady = false
    }

    companion object {
        private const val TAG = "AlarmTonePlayer"
    }
}
