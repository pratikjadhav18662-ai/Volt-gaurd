package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.model.ThemePreference

private val VoltDarkColorScheme = darkColorScheme(
    primary = VoltLime,
    onPrimary = VoltOnLime,
    primaryContainer = VoltLimeContainer,
    onPrimaryContainer = VoltOnLimeContainer,
    secondary = ElectricCyan,
    onSecondary = ElectricOnCyan,
    secondaryContainer = ElectricCyanContainer,
    onSecondaryContainer = ElectricOnCyanContainer,
    tertiary = ChargeAmber,
    onTertiary = ChargeOnAmber,
    tertiaryContainer = ChargeAmberContainer,
    onTertiaryContainer = ChargeOnAmberContainer,
    error = AlarmCrimson,
    onError = AlarmOnCrimson,
    errorContainer = AlarmCrimsonContainer,
    onErrorContainer = AlarmOnCrimsonContainer,
    background = VoltObsidian,
    onBackground = TextPrimaryDark,
    surface = VoltSurfaceDark,
    onSurface = TextPrimaryDark,
    surfaceVariant = VoltSurfaceVariantDark,
    onSurfaceVariant = TextSecondaryDark,
    surfaceContainerLowest = VoltObsidian,
    surfaceContainerLow = VoltSurfaceContainerLow,
    surfaceContainer = VoltSurfaceContainer,
    surfaceContainerHigh = VoltSurfaceContainerHigh,
    outline = OutlineDark,
    outlineVariant = OutlineVariantDark
)

private val VoltLightColorScheme = lightColorScheme(
    primary = VoltPrimaryLight,
    onPrimary = VoltOnPrimaryLight,
    primaryContainer = VoltPrimaryContainerLight,
    onPrimaryContainer = VoltOnPrimaryContainerLight,
    secondary = VoltSecondaryLight,
    onSecondary = VoltOnSecondaryLight,
    secondaryContainer = VoltSecondaryContainerLight,
    onSecondaryContainer = VoltOnSecondaryContainerLight,
    tertiary = ChargeAmberLight,
    onTertiary = ChargeOnAmberLight,
    tertiaryContainer = ChargeAmberContainerLight,
    onTertiaryContainer = ChargeOnAmberContainerLight,
    error = AlarmCrimsonLight,
    onError = AlarmOnCrimsonLight,
    errorContainer = AlarmCrimsonContainerLight,
    onErrorContainer = AlarmOnCrimsonContainerLight,
    background = VoltLightBg,
    onBackground = TextPrimaryLight,
    surface = VoltLightSurface,
    onSurface = TextPrimaryLight,
    surfaceVariant = VoltLightSurfaceVariant,
    onSurfaceVariant = TextSecondaryLight,
    surfaceContainerLowest = VoltLightSurfaceContainerLowest,
    surfaceContainerLow = VoltLightSurfaceContainerLow,
    surfaceContainer = VoltLightSurfaceContainer,
    surfaceContainerHigh = VoltLightSurfaceContainerHigh,
    outline = OutlineLight,
    outlineVariant = OutlineVariantLight
)

val VoltShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun resolveDarkTheme(preference: ThemePreference): Boolean = when (preference) {
    ThemePreference.SYSTEM -> isSystemInDarkTheme()
    ThemePreference.DARK -> true
    ThemePreference.LIGHT -> false
}

@Composable
fun VoltGuardTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> VoltDarkColorScheme
        else -> VoltLightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = VoltShapes,
        content = content
    )
}
