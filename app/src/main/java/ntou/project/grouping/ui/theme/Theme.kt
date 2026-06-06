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
    secondary        = SecondaryColor,
    onSecondary      = Color.White,
    tertiary         = AccentColor,
    onTertiary       = Color.Black,
    background       = Color(0xFF1C1B1F),
    onBackground     = Color(0xFFE6E1E5),
    surface          = Color(0xFF2B2930),
    onSurface        = Color(0xFFE6E1E5),
    surfaceVariant   = Color(0xFF49454F),
    onSurfaceVariant = Color(0xFFCAC4D0),
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
)

@Composable
fun GroupingTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = true,
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