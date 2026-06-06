package ntou.project.grouping.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary          = DarkPrimary,
    onPrimary        = DarkOnPrimary,
    secondary        = Color(0xFFBDBDBD), // Light Gray for dark mode secondary
    onSecondary      = Color.Black,
    tertiary         = Color(0xFF757575), // Medium Gray
    onTertiary       = Color.White,
    background       = Color(0xFF000000), // Pure Black background
    onBackground     = Color(0xFFFFFFFF),
    surface          = Color(0xFF121212), // Near Black surface
    onSurface        = Color(0xFFFFFFFF),
    surfaceVariant   = Color(0xFF212121),
    onSurfaceVariant = Color(0xFFBDBDBD),
)

private val LightColorScheme = lightColorScheme(
    primary = PrimaryColor,
    secondary = SecondaryColor,
    tertiary = AccentColor,
    background = BackgroundColor,
    surface = SurfaceColor,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = TextPrimary,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    surfaceVariant = Color(0xFFF5F5F5), // Light Gray surface variant
    onSurfaceVariant = Color(0xFF757575),
)

@Composable
fun GroupingTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Set default to false to prioritize the B&W look
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme)
                dynamicDarkColorScheme(context).copy(primary = DarkPrimary, onPrimary = DarkOnPrimary)
            else
                dynamicLightColorScheme(context).copy(primary = LightPrimary, onPrimary = LightOnPrimary)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}