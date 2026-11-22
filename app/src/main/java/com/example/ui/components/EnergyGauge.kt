package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.ChargeStatus
import com.example.model.ChargingCalculator
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun EnergyGauge(
    batteryPercent: Float,
    targetPercent: Int,
    alarmEnabled: Boolean,
    status: ChargeStatus,
    remainingMs: Long,
    estimatedDurationMs: Long,
    modifier: Modifier = Modifier
) {
    val animatedPercent by animateFloatAsState(
        targetValue = batteryPercent.coerceIn(0f, 100f),
        animationSpec = spring(dampingRatio = 0.82f, stiffness = 220f),
        label = "battery_percent_anim"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "charge_pulse_transition")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.30f,
        targetValue = 0.88f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1250, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )
    val orbitAngle by infiniteTransition.animateFloat(
        initialValue = -90f,
        targetValue = 270f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "orbit_angle"
    )

    val isDarkPalette = MaterialTheme.colorScheme.background.luminance() < 0.5f

    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor by animateColorAsState(
        targetValue = MaterialTheme.colorScheme.secondary,
        animationSpec = tween(durationMillis = 350),
        label = "gauge_secondary_color"
    )
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val errorColor = MaterialTheme.colorScheme.error
    val mutedColor = MaterialTheme.colorScheme.onSurfaceVariant
    val trackColor by animateColorAsState(
        targetValue = MaterialTheme.colorScheme.surfaceContainerHigh,
        animationSpec = tween(durationMillis = 350),
        label = "gauge_track_color"
    )
    val tickInactive by animateColorAsState(
        targetValue = MaterialTheme.colorScheme.outlineVariant,
        animationSpec = tween(durationMillis = 350),
        label = "gauge_tick_inactive"
    )
    val surfaceBg by animateColorAsState(
        targetValue = MaterialTheme.colorScheme.surfaceContainerLow,
        animationSpec = tween(durationMillis = 350),
        label = "gauge_surface_bg"
    )

    val targetAccent = when {
        status == ChargeStatus.ALARM_RINGING -> errorColor
        status == ChargeStatus.PAUSED -> tertiaryColor
        !alarmEnabled && status == ChargeStatus.IDLE -> secondaryColor
        else -> primaryColor
    }
    val activeAccent by animateColorAsState(
        targetValue = targetAccent,
        animationSpec = tween(durationMillis = 350),
        label = "gauge_active_accent"
    )

    Box(
        modifier = modifier
            .widthIn(max = 296.dp)
            .aspectRatio(1f)
            .testTag("energy_charge_gauge"),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val diameter = min(size.width, size.height)
            val center = Offset(size.width / 2f, size.height / 2f)
            val strokeWidth = diameter * 0.072f
            val arcPadding = strokeWidth * 1.4f
            val arcSize = Size(diameter - arcPadding * 2f, diameter - arcPadding * 2f)
            val arcTopLeft = Offset(
                (size.width - arcSize.width) / 2f,
                (size.height - arcSize.height) / 2f
            )

            // Full 360° circular progress starting cleanly at 12 o'clock (-90°)
            val startAngle = -90f
            val totalSweep = 360f

            // Atmospheric inner radial glow adapted for dark vs light background contrast
            val innerGlowAlpha = when {
                status == ChargeStatus.CHARGING -> (if (isDarkPalette) 0.18f else 0.12f) * pulseAlpha
                else -> if (isDarkPalette) 0.08f else 0.06f
            }
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        activeAccent.copy(alpha = innerGlowAlpha),
                        surfaceBg.copy(alpha = if (isDarkPalette) 0.65f else 0.85f),
                        surfaceBg.copy(alpha = 0.0f)
                    ),
                    center = center,
                    radius = diameter * 0.43f
                ),
                radius = diameter * 0.43f,
                center = center
            )

            // Subtle outer 48-tick dial ring
            val tickCount = 48
            val outerR = diameter * 0.488f
            val innerR = diameter * 0.460f
            val filledTicks = ((animatedPercent / 100f) * tickCount).roundToInt()

            for (i in 0 until tickCount) {
                val angleDeg = startAngle + (totalSweep * i / tickCount)
                val angleRad = angleDeg * (PI / 180.0)
                val isMajor = i % 12 == 0
                val rStart = if (isMajor) innerR - diameter * 0.012f else innerR
                val p1 = Offset(
                    x = center.x + (rStart * cos(angleRad)).toFloat(),
                    y = center.y + (rStart * sin(angleRad)).toFloat()
                )
                val p2 = Offset(
                    x = center.x + (outerR * cos(angleRad)).toFloat(),
                    y = center.y + (outerR * sin(angleRad)).toFloat()
                )
                drawLine(
                    color = if (i < filledTicks) activeAccent.copy(alpha = if (isMajor) 0.90f else 0.45f)
                    else tickInactive.copy(alpha = if (isMajor) 0.65f else 0.28f),
                    start = p1,
                    end = p2,
                    strokeWidth = if (isMajor) 3.2f else 1.8f,
                    cap = StrokeCap.Round
                )
            }

            // Circular track ring
            drawArc(
                color = trackColor,
                startAngle = startAngle,
                sweepAngle = totalSweep,
                useCenter = false,
                topLeft = arcTopLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            val progressSweep = (totalSweep * (animatedPercent / 100f)).coerceIn(3f, totalSweep)

            // Soft outer glow halo when charging or complete
            if (status == ChargeStatus.CHARGING || status == ChargeStatus.ALARM_RINGING || status == ChargeStatus.COMPLETED) {
                drawArc(
                    color = activeAccent.copy(alpha = 0.24f * pulseAlpha),
                    startAngle = startAngle,
                    sweepAngle = progressSweep,
                    useCenter = false,
                    topLeft = arcTopLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth * 1.6f, cap = StrokeCap.Round)
                )
            }

            // Crisp progress circle stroke
            drawArc(
                brush = Brush.sweepGradient(
                    0.0f to activeAccent,
                    0.5f to secondaryColor,
                    1.0f to activeAccent
                ),
                startAngle = startAngle,
                sweepAngle = progressSweep,
                useCenter = false,
                topLeft = arcTopLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            // Active endpoint indicator dot
            val arcRadius = arcSize.width / 2f
            val tipAngleRad = (startAngle + progressSweep) * (PI / 180.0)
            val tipOffset = Offset(
                x = center.x + (arcRadius * cos(tipAngleRad)).toFloat(),
                y = center.y + (arcRadius * sin(tipAngleRad)).toFloat()
            )
            drawCircle(
                color = activeAccent,
                radius = strokeWidth * 0.40f,
                center = tipOffset
            )

            // Subtle orbiting pulse dot during active charge
            if (status == ChargeStatus.CHARGING) {
                val sparkRad = orbitAngle * (PI / 180.0)
                val innerOrbit = arcRadius - strokeWidth * 1.15f
                val sparkPos = Offset(
                    x = center.x + (innerOrbit * cos(sparkRad)).toFloat(),
                    y = center.y + (innerOrbit * sin(sparkRad)).toFloat()
                )
                drawCircle(
                    color = secondaryColor.copy(alpha = 0.7f),
                    radius = 5f,
                    center = sparkPos
                )
            }
        }

        // Clean Center Battery Percentage Readout
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(28.dp)
        ) {
            val statusIcon = when {
                status == ChargeStatus.CHARGING -> Icons.Filled.Bolt
                status == ChargeStatus.PAUSED -> Icons.Filled.PauseCircle
                status == ChargeStatus.ALARM_RINGING -> Icons.Filled.NotificationsActive
                status == ChargeStatus.COMPLETED -> Icons.Filled.CheckCircle
                alarmEnabled -> Icons.Filled.NotificationsActive
                else -> Icons.Filled.NotificationsOff
            }
            val statusLabel = when {
                status == ChargeStatus.CHARGING -> "CHARGING"
                status == ChargeStatus.PAUSED -> "PAUSED"
                status == ChargeStatus.ALARM_RINGING -> "ALARM!"
                status == ChargeStatus.COMPLETED -> "FULL CHARGE"
                alarmEnabled -> "ALARM ARMED"
                else -> "ALARM OFF"
            }
            val pillColor = if (!alarmEnabled && status == ChargeStatus.IDLE) mutedColor else activeAccent

            Surface(
                color = pillColor.copy(alpha = 0.14f),
                shape = MaterialTheme.shapes.small
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = statusIcon,
                        contentDescription = statusLabel,
                        tint = pillColor,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = statusLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = pillColor,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = "${animatedPercent.roundToInt()}",
                    style = MaterialTheme.typography.displayLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "%",
                    style = MaterialTheme.typography.headlineMedium,
                    color = activeAccent,
                    modifier = Modifier.padding(bottom = 8.dp, start = 2.dp)
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            val timeSubtitle = when (status) {
                ChargeStatus.CHARGING, ChargeStatus.PAUSED ->
                    ChargingCalculator.formatDurationHms(remainingMs)
                ChargeStatus.ALARM_RINGING, ChargeStatus.COMPLETED ->
                    "$targetPercent% Target Reached"
                ChargeStatus.IDLE ->
                    "Est. ${ChargingCalculator.formatDurationShort(estimatedDurationMs)} to $targetPercent%"
            }

            Text(
                text = timeSubtitle,
                style = MaterialTheme.typography.labelMedium,
                color = if (status == ChargeStatus.CHARGING) {
                    MaterialTheme.colorScheme.secondary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
    }
}
