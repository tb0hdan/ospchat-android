package com.ospchat.android.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColors =
    darkColorScheme(
        primary = Indigo80,
        secondary = IndigoGrey80,
        tertiary = Teal80,
    )

private val LightColors =
    lightColorScheme(
        primary = Indigo40,
        secondary = IndigoGrey40,
        tertiary = Teal40,
    )

@Composable
fun OspChatTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = OspChatTypography,
        content = content,
    )
}
