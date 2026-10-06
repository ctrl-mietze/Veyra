package ctrl.mietze.veyraroot.ui.theme

import android.app.Activity
import android.content.Context
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Typography
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec
import com.materialkolor.rememberDynamicColorScheme
import ctrl.mietze.veyraroot.AccentColor
import ctrl.mietze.veyraroot.AppThemeMode
import ctrl.mietze.veyraroot.VerdictTint

private val AppTypography = Typography(
    displaySmall = TextStyle(fontSize = 38.sp, lineHeight = 44.sp, fontWeight = FontWeight.Light),
    headlineLarge = TextStyle(fontSize = 32.sp, lineHeight = 38.sp, fontWeight = FontWeight.Normal),
    headlineSmall = TextStyle(fontSize = 25.sp, lineHeight = 31.sp, fontWeight = FontWeight.Normal),
    titleLarge = TextStyle(fontSize = 21.sp, lineHeight = 27.sp, fontWeight = FontWeight.Medium),
    titleMedium = TextStyle(fontSize = 17.sp, lineHeight = 23.sp, fontWeight = FontWeight.Medium),
    titleSmall = TextStyle(fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Normal),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 21.sp, fontWeight = FontWeight.Normal),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 18.sp, fontWeight = FontWeight.Normal),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 17.sp, fontWeight = FontWeight.Medium),
)

private fun accentSeed(context: Context, accentColor: AccentColor): Color = when (accentColor) {
    AccentColor.Dynamic -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        Color(context.getColor(android.R.color.system_accent1_500))
    } else {
        Color(0xFFA855F7)
    }
    AccentColor.Blue -> Color(0xFF415F91)
    AccentColor.Violet -> Color(0xFF6750A4)
    AccentColor.Green -> Color(0xFF356A35)
    AccentColor.Orange -> Color(0xFF8B4F23)
}

@Composable
fun RootMyGalaxyTheme(
    accentColor: AccentColor,
    themeMode: AppThemeMode,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val systemDarkTheme = isSystemInDarkTheme()
    val darkTheme = when (themeMode) {
        AppThemeMode.System -> systemDarkTheme
        AppThemeMode.Light -> false
        AppThemeMode.Dark -> true
        AppThemeMode.WhiteDark -> false
        AppThemeMode.WhitePurple -> false
        AppThemeMode.DarkPurple,
        AppThemeMode.Oled,
        AppThemeMode.OledPurple,
        AppThemeMode.Green,
        AppThemeMode.Violet,
        AppThemeMode.Orange,
        AppThemeMode.Aurora,
        AppThemeMode.Graphite,
        AppThemeMode.Midnight -> true
    }
    val colors = when (themeMode) {
        AppThemeMode.System -> if (systemDarkTheme) {
            val oledPurple = rememberDynamicColorScheme(
                seedColor = Color(0xFFA855F7),
                isDark = true,
                style = PaletteStyle.TonalSpot,
                specVersion = ColorSpec.SpecVersion.SPEC_2025,
            )
            oledPurple.copy(
                background = Color(0xFF000000),
                surface = Color(0xFF000000),
                surfaceDim = Color(0xFF000000),
                surfaceBright = Color(0xFF111111),
                surfaceVariant = Color(0xFF0E0E0E),
                surfaceContainerLowest = Color(0xFF000000),
                surfaceContainerLow = Color(0xFF050505),
                surfaceContainer = Color(0xFF080808),
                surfaceContainerHigh = Color(0xFF0C0C0C),
                surfaceContainerHighest = Color(0xFF111111),
            )
        } else {
            lightColorScheme(
                primary = Color(0xFF7651B5),
                onPrimary = Color.White,
                primaryContainer = Color(0xFFEBDDFF),
                onPrimaryContainer = Color(0xFF2B1453),
                secondary = Color(0xFF695978),
                background = Color(0xFFFFF7FF),
                onBackground = Color(0xFF211A22),
                surface = Color(0xFFFFF7FF),
                onSurface = Color(0xFF211A22),
                surfaceContainer = Color(0xFFF8F0F9),
                surfaceContainerHigh = Color(0xFFF2EAF3),
                surfaceContainerHighest = Color(0xFFECE4ED),
                onSurfaceVariant = Color(0xFF554D58),
            )
        }
        AppThemeMode.WhiteDark -> lightColorScheme(
            primary = Color(0xFF34323A),
            onPrimary = Color.White,
            primaryContainer = Color(0xFFE7E3EA),
            onPrimaryContainer = Color(0xFF242127),
            secondary = Color(0xFF5F5964),
            background = Color(0xFFF9F6FA),
            onBackground = Color(0xFF1D1B20),
            surface = Color(0xFFF9F6FA),
            onSurface = Color(0xFF1D1B20),
            surfaceContainer = Color(0xFFF2EEF3),
            surfaceContainerHigh = Color(0xFFECE7ED),
            surfaceContainerHighest = Color(0xFFE5E0E6),
            onSurfaceVariant = Color(0xFF514B54),
        )
        AppThemeMode.WhitePurple -> lightColorScheme(
            primary = Color(0xFF7651B5),
            onPrimary = Color.White,
            primaryContainer = Color(0xFFEBDDFF),
            onPrimaryContainer = Color(0xFF2B1453),
            secondary = Color(0xFF695978),
            background = Color(0xFFFFF7FF),
            onBackground = Color(0xFF211A22),
            surface = Color(0xFFFFF7FF),
            onSurface = Color(0xFF211A22),
            surfaceContainer = Color(0xFFF8F0F9),
            surfaceContainerHigh = Color(0xFFF2EAF3),
            surfaceContainerHighest = Color(0xFFECE4ED),
            onSurfaceVariant = Color(0xFF554D58),
        )
        AppThemeMode.DarkPurple -> rememberDynamicColorScheme(
            seedColor = Color(0xFFA855F7),
            isDark = true,
            style = PaletteStyle.TonalSpot,
            specVersion = ColorSpec.SpecVersion.SPEC_2025,
        )
        AppThemeMode.Green -> rememberDynamicColorScheme(
            seedColor = Color(0xFF4CAF50),
            isDark = true,
            style = PaletteStyle.TonalSpot,
            specVersion = ColorSpec.SpecVersion.SPEC_2025,
        )
        AppThemeMode.Violet -> rememberDynamicColorScheme(
            seedColor = Color(0xFF6750A4),
            isDark = true,
            style = PaletteStyle.TonalSpot,
            specVersion = ColorSpec.SpecVersion.SPEC_2025,
        )
        AppThemeMode.Orange -> rememberDynamicColorScheme(
            seedColor = Color(0xFFFF8A50),
            isDark = true,
            style = PaletteStyle.TonalSpot,
            specVersion = ColorSpec.SpecVersion.SPEC_2025,
        )
        AppThemeMode.Aurora -> rememberDynamicColorScheme(
            seedColor = Color(0xFF7068FF),
            isDark = true,
            style = PaletteStyle.TonalSpot,
            specVersion = ColorSpec.SpecVersion.SPEC_2025,
        ).copy(
            primary = Color(0xFFB8ADFF),
            secondary = Color(0xFF72DDD5),
            tertiary = Color(0xFF78C8FF),
            background = Color(0xFF080A12),
            surface = Color(0xFF080A12),
            surfaceContainer = Color(0xFF101522),
            surfaceContainerHigh = Color(0xFF151B2B),
            surfaceContainerHighest = Color(0xFF1B2335),
        )
        AppThemeMode.Graphite -> rememberDynamicColorScheme(
            seedColor = Color(0xFF8D94A0),
            isDark = true,
            style = PaletteStyle.Neutral,
            specVersion = ColorSpec.SpecVersion.SPEC_2025,
        ).copy(
            primary = Color(0xFFC8CAD0),
            secondary = Color(0xFFAEB4BE),
            background = Color(0xFF0C0D0F),
            surface = Color(0xFF0C0D0F),
            surfaceContainer = Color(0xFF15171A),
            surfaceContainerHigh = Color(0xFF1B1D21),
            surfaceContainerHighest = Color(0xFF22252A),
        )
        AppThemeMode.Midnight -> rememberDynamicColorScheme(
            seedColor = Color(0xFF536DFE),
            isDark = true,
            style = PaletteStyle.TonalSpot,
            specVersion = ColorSpec.SpecVersion.SPEC_2025,
        ).copy(
            primary = Color(0xFFB9C3FF),
            secondary = Color(0xFFC7AFFF),
            tertiary = Color(0xFF75D5FF),
            background = Color(0xFF050713),
            surface = Color(0xFF050713),
            surfaceContainer = Color(0xFF0D1122),
            surfaceContainerHigh = Color(0xFF12182D),
            surfaceContainerHighest = Color(0xFF192038),
        )
        AppThemeMode.Oled,
        AppThemeMode.OledPurple -> {
            val oledBase = rememberDynamicColorScheme(
                seedColor = if (themeMode == AppThemeMode.OledPurple) {
                    Color(0xFFA855F7)
                } else {
                    accentSeed(context, accentColor)
                },
                isDark = true,
                style = PaletteStyle.TonalSpot,
                specVersion = ColorSpec.SpecVersion.SPEC_2025,
            )
            oledBase.copy(
                // OLED means literal black for the page itself. Cards deliberately stay a few
                // levels above black so their boundaries remain visible without sacrificing the
                // fully-black canvas around them.
                background = Color(0xFF000000),
                surface = Color(0xFF000000),
                surfaceDim = Color(0xFF000000),
                surfaceBright = Color(0xFF111111),
                surfaceVariant = Color(0xFF0E0E0E),
                surfaceContainerLowest = Color(0xFF000000),
                surfaceContainerLow = Color(0xFF050505),
                surfaceContainer = Color(0xFF080808),
                surfaceContainerHigh = Color(0xFF0C0C0C),
                surfaceContainerHighest = Color(0xFF111111),
            )
        }
        else -> if (
            accentColor == AccentColor.Dynamic &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        ) {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        } else {
            val generatedColors = rememberDynamicColorScheme(
                seedColor = accentSeed(context, accentColor),
                isDark = darkTheme,
                style = PaletteStyle.TonalSpot,
                specVersion = ColorSpec.SpecVersion.SPEC_2025,
            )
            if (darkTheme) {
                generatedColors
            } else {
                generatedColors.copy(
                    onSurfaceVariant = lerp(generatedColors.surface, generatedColors.onSurface, 0.8f),
                )
            }
        }
    }

    SideEffect {
        val window = (context as Activity).window
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = !darkTheme
            isAppearanceLightNavigationBars = !darkTheme
        }
        // Keep the system-edge canvas identical to the app canvas. On OLED themes this is
        // deliberately #000000 rather than the near-black Material surface used by cards.
        window.decorView.setBackgroundColor(colors.background.toArgb())
        @Suppress("DEPRECATION")
        run {
            window.statusBarColor = colors.background.toArgb()
            window.navigationBarColor = colors.background.toArgb()
        }
        // The notification is drawn by the system and cannot read any of this, so the verdict colours are
        // handed over here - the one place in the app that has them as plain values.
        VerdictTint.update(colors)
    }

    MaterialExpressiveTheme(
        colorScheme = colors,
        typography = AppTypography,
        motionScheme = MotionScheme.expressive(),
        content = content,
    )
}
