package org.nxy.hasstools.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

@Immutable
data class ColorFamily(
    val color: Color,
    val onColor: Color,
    val colorContainer: Color,
    val onColorContainer: Color
)

val unspecified_scheme = ColorFamily(
    Color.Unspecified, Color.Unspecified, Color.Unspecified, Color.Unspecified
)

val extendedLight = ExtendedColorScheme(
    success = ColorFamily(
        successLight,
        onSuccessLight,
        successContainerLight,
        onSuccessContainerLight,
    ),
)

val extendedDark = ExtendedColorScheme(
    success = ColorFamily(
        successDark,
        onSuccessDark,
        successContainerDark,
        onSuccessContainerDark,
    ),
)

val extendedLightMediumContrast = ExtendedColorScheme(
    success = ColorFamily(
        successLightMediumContrast,
        onSuccessLightMediumContrast,
        successContainerLightMediumContrast,
        onSuccessContainerLightMediumContrast,
    ),
)

val extendedLightHighContrast = ExtendedColorScheme(
    success = ColorFamily(
        successLightHighContrast,
        onSuccessLightHighContrast,
        successContainerLightHighContrast,
        onSuccessContainerLightHighContrast,
    ),
)

val extendedDarkMediumContrast = ExtendedColorScheme(
    success = ColorFamily(
        successDarkMediumContrast,
        onSuccessDarkMediumContrast,
        successContainerDarkMediumContrast,
        onSuccessContainerDarkMediumContrast,
    ),
)

val extendedDarkHighContrast = ExtendedColorScheme(
    success = ColorFamily(
        successDarkHighContrast,
        onSuccessDarkHighContrast,
        successContainerDarkHighContrast,
        onSuccessContainerDarkHighContrast,
    ),
)

val LocalExtendedColorScheme = staticCompositionLocalOf {
    ExtendedColorScheme(
        success = unspecified_scheme
    )
}

@Composable
fun AppTheme(
    lightScheme: ColorScheme = blueLightScheme,
    darkScheme: ColorScheme = blueDarkScheme,
    extendedScheme: ExtendedColorScheme = if (isSystemInDarkTheme()) extendedDark else extendedLight,
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = false,
    content: @Composable() () -> Unit
) {
    val colorScheme = when {
        dynamicColor -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> darkScheme
        else -> lightScheme
    }

    CompositionLocalProvider(LocalExtendedColorScheme provides extendedScheme) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AppTypography,
            content = content
        )
    }
}
