package com.alhafidz.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import java.time.LocalTime

enum class Mode { LIGHT, SENJA, DARK }

class Pal(val mode: Mode, val ok: Color, val warn: Color, val bad: Color, val track: Color, val g1: Color, val g2: Color)

val LocalPal = staticCompositionLocalOf {
    Pal(Mode.LIGHT, Color(0xFF2EB872), Color(0xFFF5A623), Color(0xFFE5484D), Color(0xFFE3E9F0), Color(0xFF22A6F0), Color(0xFF0A5FA8))
}

/** Pagi-siang (05-15) terang, sore (16-18) senja, malam (19-04) gelap. */
fun currentMode(): Mode = when (Prefs.themeMode) {
    1 -> Mode.LIGHT
    2 -> Mode.SENJA
    3 -> Mode.DARK
    else -> {
        val h = LocalTime.now().hour
        if (h in 5..15) Mode.LIGHT else if (h in 16..18) Mode.SENJA else Mode.DARK
    }
}

fun Lv.color(p: Pal): Color = when (this) { Lv.OK -> p.ok; Lv.WARN -> p.warn; Lv.BAD -> p.bad; Lv.NONE -> p.track }

@Composable
fun GarasiTheme(mode: Mode, content: @Composable () -> Unit) {
    val cs = when (mode) {
        Mode.LIGHT -> lightColorScheme(
            primary = Color(0xFF0B79C2), onPrimary = Color.White,
            primaryContainer = Color(0xFFD6ECFA), onPrimaryContainer = Color(0xFF063F66),
            secondary = Color(0xFFE07B1A), background = Color(0xFFF3F6FA), onBackground = Color(0xFF14202B),
            surface = Color.White, onSurface = Color(0xFF14202B),
            surfaceVariant = Color(0xFFE8EEF5), onSurfaceVariant = Color(0xFF55606B),
            surfaceContainer = Color(0xFFEAF1F8), outline = Color(0xFFB7C3CF)
        )
        Mode.SENJA -> darkColorScheme(
            primary = Color(0xFFFFA45C), onPrimary = Color(0xFF3A1A00),
            primaryContainer = Color(0xFF5E3347), onPrimaryContainer = Color(0xFFFFD9C2),
            secondary = Color(0xFFFF7A8A), background = Color(0xFF241527), onBackground = Color(0xFFFFE8D6),
            surface = Color(0xFF35203A), onSurface = Color(0xFFFFE8D6),
            surfaceVariant = Color(0xFF4A2D4D), onSurfaceVariant = Color(0xFFE8C9C0),
            surfaceContainer = Color(0xFF3B2441), outline = Color(0xFF8D6B7A)
        )
        Mode.DARK -> darkColorScheme(
            primary = Color(0xFF52B5F5), onPrimary = Color(0xFF00243D),
            primaryContainer = Color(0xFF123A57), onPrimaryContainer = Color(0xFFCDE8FB),
            secondary = Color(0xFFFFA24C), background = Color(0xFF0D1620), onBackground = Color(0xFFEAF2F8),
            surface = Color(0xFF16222F), onSurface = Color(0xFFEAF2F8),
            surfaceVariant = Color(0xFF223344), onSurfaceVariant = Color(0xFF9FB1C0),
            surfaceContainer = Color(0xFF1B2A39), outline = Color(0xFF50657A)
        )
    }
    val pal = when (mode) {
        Mode.LIGHT -> Pal(mode, Color(0xFF2EB872), Color(0xFFF5A623), Color(0xFFE5484D), Color(0xFFE3E9F0), Color(0xFF22A6F0), Color(0xFF0A5FA8))
        Mode.SENJA -> Pal(mode, Color(0xFF5FD39A), Color(0xFFFFC857), Color(0xFFFF6B6B), Color(0xFF5A3C5E), Color(0xFFFF9A5C), Color(0xFFB5446E))
        Mode.DARK -> Pal(mode, Color(0xFF3DDC97), Color(0xFFFFB84D), Color(0xFFFF6B6B), Color(0xFF2A3C50), Color(0xFF1B6FA8), Color(0xFF0D3A5C))
    }
    CompositionLocalProvider(LocalPal provides pal) {
        MaterialTheme(colorScheme = cs, content = content)
    }
}
