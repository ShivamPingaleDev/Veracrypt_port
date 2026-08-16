package dev.shivampingale.vcport

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Shapes
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import kotlin.math.cos
import kotlin.math.sin
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Desktop VeraCrypt about-banner blue (10, 108, 206). */
val VcDesktopBlue = Color(0xFF0A6CCE)
private val WindowGray = Color(0xFFE8EEF4)
private val Panel = Color(0xFFFFFFFF)
private val Ink = Color(0xFF1A1A1A)
private val Mute = Color(0xFF3D4A57)
private val Panic = Color(0xFFC62828)

val LocalVcSkin = compositionLocalOf { VcSkin.Desktop }

enum class VcSkin(val picker: String, val tag: String) {
    Desktop("Desktop", "skin_desktop"),
    Cyberpunk("Cyberpunk", "skin_cyberpunk"),
    Matrix("Matrix", "skin_matrix"),
    Evangelion("Evangelion", "skin_eva"),
    Signal("Signal", "skin_signal")
}

/* OFL latin subsets: Rajdhani (CP2077 HUD), VT323 (CRT), Oswald (MAGI condensed), IBM Plex Sans. */
private val Rajdhani = FontFamily(Font(R.font.rajdhani_semibold, FontWeight.SemiBold))
private val Vt323 = FontFamily(Font(R.font.vt323_regular, FontWeight.Normal))
private val Oswald = FontFamily(Font(R.font.oswald_semibold, FontWeight.SemiBold))
private val IbmPlex = FontFamily(Font(R.font.ibmplexsans_medium, FontWeight.Medium))

private val DesktopScheme = lightColorScheme(
    primary = VcDesktopBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6E8F8),
    onPrimaryContainer = Color(0xFF062E57),
    secondary = Color(0xFF4A6A86),
    onSecondary = Color.White,
    background = WindowGray,
    onBackground = Ink,
    surface = Panel,
    onSurface = Ink,
    surfaceVariant = Color(0xFFD9E3EE),
    onSurfaceVariant = Mute,
    outline = Color(0xFF9AAEBE),
    error = Panic,
    onError = Color.White
)

/** OnePlus 8T Cyberpunk edition + delayed-yellow HUD (XDA / dark-future palettes). Not affiliated. */
private val CpYellow = Color(0xFFFFEB0B)
private val CpDelayed = Color(0xFFFDF901)
private val CpCyan = Color(0xFF25E1ED)
private val CpMagenta = Color(0xFFED1E79)
private val CpAlert = Color(0xFFFF4A57)

private val CyberpunkScheme = darkColorScheme(
    primary = CpYellow,
    onPrimary = Color(0xFF0A0812),
    primaryContainer = Color(0xFF2A1030),
    onPrimaryContainer = CpDelayed,
    secondary = CpCyan,
    onSecondary = Color(0xFF0A0812),
    background = Color(0xFF07060C),
    onBackground = CpDelayed,
    surface = Color(0xCC140E1C),
    onSurface = CpDelayed,
    surfaceVariant = Color(0xFF241428),
    onSurfaceVariant = Color(0xFF8C9EB2),
    outline = CpCyan,
    error = CpAlert,
    onError = Color.White
)

/** Film digital-rain phosphor: head near-white, trail #00FF41. */
private val MxGreen = Color(0xFF00FF41)
private val MxHead = Color(0xFFEBFFF5)
private val MxTrail = Color(0xFF008F11)

private val MatrixScheme = darkColorScheme(
    primary = MxGreen,
    onPrimary = Color(0xFF040804),
    primaryContainer = Color(0xFF0A1A0A),
    onPrimaryContainer = MxHead,
    secondary = Color(0xFF33FF99),
    onSecondary = Color(0xFF040804),
    background = Color(0xFF040804),
    onBackground = MxGreen,
    surface = Color(0xCC061406),
    onSurface = MxGreen,
    surfaceVariant = Color(0xFF0A1A0A),
    onSurfaceVariant = MxTrail,
    outline = MxGreen,
    error = Color(0xFF66FFAA),
    onError = Color(0xFF040804)
)

/** MAGI / NERV console: nerv-ui amber #FF7A18, magi-theme #F06800, Unit-01 purple. Not affiliated. */
private val EvaOrange = Color(0xFFFF7A18)
private val EvaAmber = Color(0xFFFFB300)
private val EvaPlug = Color(0xFFF06800)
private val EvaPurple = Color(0xFF5C3D82)
private val EvaKhaki = Color(0xFFC4B07A)
private val EvaYellow = Color(0xFFF0F0A0)

private val EvaScheme = darkColorScheme(
    primary = EvaOrange,
    onPrimary = Color(0xFF050504),
    primaryContainer = EvaPurple,
    onPrimaryContainer = EvaYellow,
    secondary = EvaAmber,
    onSecondary = Color(0xFF050504),
    background = Color(0xFF050504),
    onBackground = EvaYellow,
    surface = Color(0xCC0E0E0A),
    onSurface = EvaYellow,
    surfaceVariant = Color(0xFF2A2210),
    onSurfaceVariant = EvaKhaki,
    outline = EvaOrange,
    error = Color(0xFFF0321E),
    onError = Color.White
)

/** Original Signal messenger tokens (signal.org / DESIGN.md): ultramarine #3A76F0. Not affiliated. */
private val SigBlue = Color(0xFF3A76F0)
private val SigDeep = Color(0xFF2E62D8)
private val SigCyan = Color(0xFF5FB3F9)
private val SigGreen = Color(0xFF3EAA6C)

private val SignalScheme = darkColorScheme(
    primary = SigBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF1E2A4A),
    onPrimaryContainer = SigCyan,
    secondary = SigCyan,
    onSecondary = Color(0xFF0B0C10),
    background = Color(0xFF0F1014),
    onBackground = Color(0xFFE8EAED),
    surface = Color(0xE61B1D24),
    onSurface = Color(0xFFE8EAED),
    surfaceVariant = Color(0xFF252830),
    onSurfaceVariant = Color(0xFF9AA0A6),
    outline = SigBlue,
    error = Color(0xFFD04A3C),
    onError = Color.White
)

private fun schemeFor(skin: VcSkin) = when (skin) {
    VcSkin.Desktop -> DesktopScheme
    VcSkin.Cyberpunk -> CyberpunkScheme
    VcSkin.Matrix -> MatrixScheme
    VcSkin.Evangelion -> EvaScheme
    VcSkin.Signal -> SignalScheme
}

fun skinHeaderBrush(skin: VcSkin): Brush = when (skin) {
    VcSkin.Desktop -> Brush.verticalGradient(
        listOf(Color(0xFF3B9AE8), VcDesktopBlue, Color(0xFF0756A4))
    )
    VcSkin.Cyberpunk -> Brush.linearGradient(listOf(CpYellow, CpMagenta, CpCyan))
    VcSkin.Matrix -> Brush.linearGradient(listOf(Color(0xFF003B14), MxGreen, Color(0xFF001A08)))
    VcSkin.Evangelion -> Brush.linearGradient(listOf(EvaPlug, EvaPurple, EvaOrange))
    VcSkin.Signal -> Brush.linearGradient(listOf(SigDeep, SigBlue, SigCyan))
}

private fun typeFor(skin: VcSkin): Typography {
    val family = when (skin) {
        VcSkin.Cyberpunk -> Rajdhani
        VcSkin.Matrix -> Vt323
        VcSkin.Evangelion -> Oswald
        VcSkin.Signal -> IbmPlex
        VcSkin.Desktop -> IbmPlex
    }
    val ink = schemeFor(skin).onBackground
    val tracking = when (skin) {
        VcSkin.Cyberpunk -> 1.6.sp
        VcSkin.Evangelion -> 1.4.sp
        VcSkin.Matrix -> 0.6.sp
        VcSkin.Desktop -> 0.15.sp
        else -> 0.2.sp
    }
    val bodySize = if (skin == VcSkin.Matrix) 18.sp else 16.sp
    return Typography(
        headlineMedium = TextStyle(
            fontFamily = family,
            fontWeight = FontWeight.SemiBold,
            fontSize = 26.sp,
            color = ink,
            letterSpacing = tracking
        ),
        titleLarge = TextStyle(
            fontFamily = family,
            fontWeight = FontWeight.SemiBold,
            fontSize = 20.sp,
            letterSpacing = tracking
        ),
        titleMedium = TextStyle(
            fontFamily = family,
            fontWeight = FontWeight.Medium,
            fontSize = 16.sp,
            letterSpacing = tracking * 0.5f
        ),
        bodyLarge = TextStyle(
            fontFamily = family,
            fontSize = bodySize,
            lineHeight = 22.sp
        ),
        bodySmall = TextStyle(
            fontFamily = family,
            fontSize = if (skin == VcSkin.Matrix) 15.sp else 13.sp,
            lineHeight = 18.sp
        ),
        labelSmall = TextStyle(
            fontFamily = family,
            fontSize = 11.sp,
            letterSpacing = tracking
        )
    )
}

@Composable
fun VcPortTheme(skin: VcSkin = VcSkin.Desktop, content: @Composable () -> Unit) {
    val scheme = schemeFor(skin)
    val round = if (skin == VcSkin.Signal) 16.dp else if (skin == VcSkin.Desktop) 4.dp else 2.dp
    CompositionLocalProvider(LocalVcSkin provides skin) {
        MaterialTheme(
            colorScheme = scheme,
            shapes = Shapes(
                extraSmall = RoundedCornerShape(if (skin == VcSkin.Signal) 14.dp else 2.dp),
                small = RoundedCornerShape(if (skin == VcSkin.Signal) 14.dp else 4.dp),
                medium = RoundedCornerShape(round),
                large = RoundedCornerShape(if (skin == VcSkin.Signal) 20.dp else 6.dp)
            ),
            typography = typeFor(skin)
        ) {
            Box(Modifier.fillMaxSize().background(scheme.background)) {
                SkinChrome(skin)
                content()
            }
        }
    }
}

private fun skinBackdropBrush(skin: VcSkin, t: Float, size: Size): Brush {
    val ang = t * 6.2831855f
    val x = size.width * (0.5f + 0.38f * cos(ang))
    val y = size.height * (0.5f + 0.38f * sin(ang))
    return when (skin) {
        VcSkin.Cyberpunk -> Brush.linearGradient(
            listOf(Color(0xFF07060C), Color(0xFF2A1030), Color(0xFF071820), Color(0xFF1A0814)),
            start = Offset(x, 0f),
            end = Offset(size.width - x, size.height)
        )
        VcSkin.Matrix -> Brush.radialGradient(
            listOf(Color(0xFF0A2A12), Color(0xFF040804), Color(0xFF020402)),
            center = Offset(size.width * 0.5f, y),
            radius = size.minDimension * 0.9f
        )
        VcSkin.Evangelion -> Brush.linearGradient(
            listOf(Color(0xFF050504), EvaPurple.copy(alpha = 0.55f), Color(0xFF1A1008), Color(0xFF050504)),
            start = Offset(0f, y * 0.3f),
            end = Offset(size.width, size.height - y * 0.2f)
        )
        VcSkin.Signal -> Brush.linearGradient(
            listOf(Color(0xFF0F1014), SigDeep.copy(alpha = 0.45f), Color(0xFF12141C), SigCyan.copy(alpha = 0.18f)),
            start = Offset(x * 0.4f, 0f),
            end = Offset(size.width, size.height)
        )
        VcSkin.Desktop -> Brush.linearGradient(
            listOf(Color(0xFFF7FBFE), Color(0xFFE8EEF4), Color(0xFFD4E4F4), Color(0xFFE8EEF4)),
            start = Offset(x * 0.15f, 0f),
            end = Offset(size.width, size.height)
        )
    }
}

@Composable
private fun SkinChrome(skin: VcSkin) {
    val motion = rememberInfiniteTransition(label = "skin-clock")
    val duration = when (skin) {
        VcSkin.Desktop -> 22000
        VcSkin.Matrix -> 7200
        VcSkin.Cyberpunk -> 8800
        VcSkin.Evangelion -> 14000
        else -> 11000
    }
    val t by motion.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(duration, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "drift"
    )
    val pulse by motion.animateFloat(
        initialValue = 0.42f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )
    val rain = remember {
        List(26) { i ->
            floatArrayOf(
                (i + 0.4f) / 26f,
                0.11f + (i * 17 % 11) * 0.025f,
                (7 + i % 13).toFloat(),
                (i * 53).toFloat()
            )
        }
    }
    val glyphPaint = remember {
        android.graphics.Paint().apply {
            isAntiAlias = false
            textSize = 22f
            typeface = android.graphics.Typeface.MONOSPACE
        }
    }
    Box(
        Modifier
            .fillMaxSize()
            .drawBehind { drawRect(skinBackdropBrush(skin, t, size)) }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            when (skin) {
                VcSkin.Cyberpunk -> {
                    val scanY = (t * (h + 80f)) % (h + 80f) - 40f
                    drawRect(
                        CpCyan.copy(alpha = 0.12f * pulse),
                        topLeft = Offset(0f, scanY),
                        size = Size(w, 18f)
                    )
                    for (i in 0 until 18) {
                        val on = ((i + (t * 18f).toInt()) % 2 == 0)
                        drawRect(
                            if (on) CpYellow else Color(0xFF0A0812),
                            topLeft = Offset(i * 18f, 0f),
                            size = Size(18f, 9f)
                        )
                    }
                    val hex = "0123456789ABCDEF"
                    glyphPaint.color = android.graphics.Color.argb(
                        (90 * pulse).toInt().coerceIn(40, 120),
                        37, 225, 237
                    )
                    glyphPaint.textSize = 11f
                    for (row in 0 until 12) {
                        for (col in 0 until 8) {
                            val ch = hex[(row * 8 + col + (t * 16).toInt()) % hex.length].toString()
                            drawContext.canvas.nativeCanvas.drawText(
                                ch,
                                16f + col * (w / 9f),
                                64f + row * 22f,
                                glyphPaint
                            )
                        }
                    }
                    drawLine(CpCyan.copy(alpha = pulse), Offset(10f, 16f), Offset(w - 10f, 16f), 2.2f)
                    drawLine(CpYellow, Offset(10f, 10f), Offset(10f, h - 10f), 3f)
                    drawLine(CpMagenta.copy(alpha = 0.85f), Offset(w - 10f, 10f), Offset(w - 10f, h - 10f), 3f)
                    drawLine(CpCyan, Offset(10f, h - 14f), Offset(w - 10f, h - 14f), 2.2f)
                    drawCircle(CpMagenta.copy(alpha = 0.22f * pulse), 90f, Offset(w * 0.82f, h * 0.22f))
                    drawCircle(CpCyan.copy(alpha = 0.16f * pulse), 120f, Offset(w * 0.18f, h * 0.72f))
                }
                VcSkin.Matrix -> {
                    val glyphs = "0123456789ABCDEF¥$*+<>|"
                    glyphPaint.textSize = 18f
                    rain.forEach { col ->
                        val x = col[0] * w
                        val speed = col[1]
                        val len = col[2].toInt()
                        val seed = col[3].toInt()
                        val head = ((t * speed * h * 3f) + seed) % (h + 160f) - 40f
                        for (n in 0 until len) {
                            val y = head - n * 18f
                            if (y < -20f || y > h + 20f) continue
                            val ch = glyphs[(seed + n + (t * 24).toInt()) % glyphs.length].toString()
                            val alpha = if (n == 0) 1f else (1f - n / len.toFloat()).coerceIn(0.15f, 0.75f)
                            val c = if (n == 0) MxHead else MxGreen
                            glyphPaint.color = android.graphics.Color.argb(
                                (alpha * 255).toInt().coerceIn(30, 255),
                                (c.red * 255).toInt(),
                                (c.green * 255).toInt(),
                                (c.blue * 255).toInt()
                            )
                            drawContext.canvas.nativeCanvas.drawText(ch, x, y, glyphPaint)
                        }
                    }
                    var y = 0f
                    while (y < h) {
                        drawLine(MxTrail.copy(alpha = 0.08f), Offset(0f, y), Offset(w, y), 1f)
                        y += 3f
                    }
                }
                VcSkin.Evangelion -> {
                    drawRect(EvaPurple.copy(alpha = 0.85f), topLeft = Offset(0f, 0f), size = Size(12f, h))
                    drawRect(EvaAmber.copy(alpha = pulse), topLeft = Offset(12f, 0f), size = Size(4f, h))
                    drawRect(EvaKhaki, topLeft = Offset(0f, h - 16f), size = Size(w, 16f))
                    drawRect(EvaOrange, topLeft = Offset(0f, h - 18f), size = Size(w, 3f))
                    val chev = ((t * 40f) % 24f)
                    var cy = 80f - chev
                    while (cy < h - 40f) {
                        val p = Path()
                        p.moveTo(22f, cy)
                        p.lineTo(44f, cy + 10f)
                        p.lineTo(22f, cy + 20f)
                        p.close()
                        drawPath(p, EvaOrange.copy(alpha = 0.55f))
                        cy += 24f
                    }
                    rotate(t * 360f, Offset(w * 0.5f, h * 0.42f)) {
                        drawCircle(
                            EvaAmber.copy(alpha = 0.4f * pulse),
                            78f,
                            Offset(w * 0.5f, h * 0.42f),
                            style = Stroke(width = 5f, cap = StrokeCap.Square)
                        )
                        drawLine(
                            EvaOrange,
                            Offset(w * 0.5f - 96f, h * 0.42f),
                            Offset(w * 0.5f + 96f, h * 0.42f),
                            2f
                        )
                        drawLine(
                            EvaOrange,
                            Offset(w * 0.5f, h * 0.42f - 96f),
                            Offset(w * 0.5f, h * 0.42f + 96f),
                            2f
                        )
                    }
                    val cores = listOf(0.28f, 0.5f, 0.72f)
                    cores.forEachIndexed { i, xf ->
                        val a = if ((i + (t * 3f).toInt()) % 3 == 0) pulse else 0.45f
                        drawCircle(EvaOrange.copy(alpha = a), 7f, Offset(w * xf, 40f))
                    }
                    drawRect(EvaOrange, topLeft = Offset(20f, 52f), size = Size(w - 40f, 7f))
                }
                VcSkin.Signal -> {
                    drawCircle(
                        SigBlue.copy(alpha = 0.22f * pulse),
                        w * 0.42f,
                        Offset(w * (0.25f + 0.08f * sin(t * 6.28f)), h * 0.28f)
                    )
                    drawCircle(
                        SigCyan.copy(alpha = 0.16f * pulse),
                        w * 0.36f,
                        Offset(w * (0.78f - 0.06f * cos(t * 6.28f)), h * 0.62f)
                    )
                    drawCircle(
                        SigGreen.copy(alpha = 0.10f),
                        w * 0.2f,
                        Offset(w * 0.5f, h * (0.8f + 0.04f * sin(t * 12f)))
                    )
                    drawRoundRect(
                        SigBlue.copy(alpha = 0.18f),
                        topLeft = Offset(28f, 88f),
                        size = Size(w * 0.44f, h * 0.22f),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(28f, 28f)
                    )
                    drawLine(SigBlue.copy(alpha = 0.7f), Offset(0f, 72f), Offset(w, 72f), 3f)
                    drawLine(SigCyan.copy(alpha = 0.45f), Offset(0f, h - 48f), Offset(w, h - 48f), 4f)
                }
                VcSkin.Desktop -> {
                    drawRect(
                        Brush.verticalGradient(
                            listOf(Color.White.copy(alpha = 0.45f), Color.Transparent)
                        ),
                        topLeft = Offset.Zero,
                        size = Size(w, 140f)
                    )
                    drawLine(
                        VcDesktopBlue.copy(alpha = 0.18f * pulse),
                        Offset(0f, 8f),
                        Offset(w, 8f),
                        6f
                    )
                    drawCircle(
                        VcDesktopBlue.copy(alpha = 0.07f * pulse),
                        w * 0.55f,
                        Offset(w * 0.82f, h * 0.12f)
                    )
                    drawCircle(
                        Color(0xFF3B9AE8).copy(alpha = 0.06f),
                        w * 0.4f,
                        Offset(w * 0.08f, h * 0.78f)
                    )
                }
            }
        }
    }
}

@Composable
fun WorkOverlay(
    visible: Boolean,
    title: String,
    percent: Int
) {
    val colors = MaterialTheme.colorScheme
    val shown = title.ifBlank { "On this phone" }
    val animated by animateFloatAsState(
        targetValue = if (percent in 0..100) percent / 100f else 0f,
        animationSpec = tween(180),
        label = "work-percent"
    )
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(140)),
        exit = fadeOut(tween(140))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0x66000000))
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            awaitPointerEvent()
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .padding(32.dp)
                    .widthIn(max = 340.dp)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = colors.surface,
                shadowElevation = 16.dp
            ) {
                Column(
                    Modifier.padding(horizontal = 28.dp, vertical = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        "This step",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant
                    )
                    Text(
                        shown,
                        style = MaterialTheme.typography.titleLarge,
                        textAlign = TextAlign.Center
                    )
                    WorkMeter(percent = percent, fill = animated, color = colors.primary)
                    if (percent in 0..100) {
                        LinearProgressIndicator(
                            progress = { animated },
                            modifier = Modifier.fillMaxWidth().height(6.dp),
                            color = colors.primary,
                            trackColor = colors.surfaceVariant,
                            strokeCap = StrokeCap.Round
                        )
                        Text(
                            "$percent%",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    } else {
                        Text(
                            "This step has no percent. The cells move until it finishes.",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                    Text(
                        "Nothing runs out of sight.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
private fun WorkMeter(percent: Int, fill: Float, color: Color) {
    if (percent in 0..100) {
        MeterRow(on = { i -> fill * 8f > i }, color = color)
    } else {
        val pulse by rememberInfiniteTransition(label = "kdf-pulse").animateFloat(
            initialValue = 0f,
            targetValue = 8f,
            animationSpec = infiniteRepeatable(
                animation = tween(960, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "kdf-cell"
        )
        MeterRow(on = { i -> pulse.toInt() % 8 == i }, color = color)
    }
}

@Composable
private fun MeterRow(on: (Int) -> Boolean, color: Color) {
    val track = MaterialTheme.colorScheme.surfaceVariant
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        repeat(8) { i ->
            Box(
                Modifier
                    .weight(1f)
                    .height(10.dp)
                    .background(
                        if (on(i)) color else track,
                        RoundedCornerShape(3.dp)
                    )
            )
        }
    }
}

@Composable
fun StatusBanner(
    status: String,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val lower = status.lowercase()
    val tone = when {
        listOf("fail", "could not", "wrong password", "name is empty", "must be", "choose at least", "enter the wrap", "select a container", "tap a file", "open a volume first").any { it in lower } ->
            colors.error
        listOf("opened", "copied", "created", "moved", "wiped", "complete", "saved", "unwrapped", "wrapped", "renamed", "deleted").any { it in lower } ->
            Color(0xFF1B7A3A)
        else ->
            colors.primary
    }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = colors.surface,
        shadowElevation = 1.dp
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                Modifier
                    .width(4.dp)
                    .height(36.dp)
                    .background(tone, RoundedCornerShape(2.dp))
            )
            Text(
                status,
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurface,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun VcCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val skin = LocalVcSkin.current
    val body: @Composable () -> Unit = {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content
        )
    }
    if (skin == VcSkin.Desktop) {
        Surface(
            modifier = modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 2.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f))
        ) {
            Column {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(skinHeaderBrush(VcSkin.Desktop))
                )
                body()
            }
        }
    } else {
        Box(
            modifier
                .fillMaxWidth()
                .background(skinHeaderBrush(skin), MaterialTheme.shapes.medium)
                .padding(1.5.dp)
        ) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 0.dp,
                content = body
            )
        }
    }
}

/** Password field that never writes IME or Autofill history. */
@Composable
fun SecretField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.None,
            autoCorrect = false,
            keyboardType = KeyboardType.Password,
            imeAction = ImeAction.Done
        )
    )
}

@Composable
fun EntropyPad(
    percent: Int,
    enabled: Boolean,
    onSample: (ByteArray) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val marks = remember { mutableStateListOf<Offset>() }
    val bar by animateFloatAsState(
        targetValue = (percent.coerceIn(0, 100)) / 100f,
        animationSpec = tween(120),
        label = "entropy-bar"
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Randomness (move your finger)", style = MaterialTheme.typography.titleSmall)
        Text(
            "Same idea as moving the mouse in the VeraCrypt volume wizard. Keep scribbling in the blank area until the bar is full. This takes longer on purpose. That motion is mixed into the volume keys with the phone CSPRNG.",
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant
        )
        LinearProgressIndicator(
            progress = { bar },
            modifier = Modifier.fillMaxWidth().height(6.dp),
            color = colors.primary,
            trackColor = colors.surfaceVariant,
            strokeCap = StrokeCap.Round
        )
        Text("$percent%", style = MaterialTheme.typography.bodySmall)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(240.dp)
                .border(1.dp, colors.outline, RoundedCornerShape(4.dp))
                .background(colors.surface, RoundedCornerShape(4.dp))
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            event.changes.forEach { change ->
                                if (change.pressed) {
                                    marks.add(change.position)
                                    if (marks.size > 96) marks.removeAt(0)
                                    val x = change.position.x
                                    val y = change.position.y
                                    val t = System.nanoTime()
                                    val p = change.pressure
                                    val bytes = ByteArray(24)
                                    var o = 0
                                    fun putInt(v: Int) {
                                        bytes[o++] = v.toByte()
                                        bytes[o++] = (v shr 8).toByte()
                                        bytes[o++] = (v shr 16).toByte()
                                        bytes[o++] = (v shr 24).toByte()
                                    }
                                    putInt(x.toRawBits())
                                    putInt(y.toRawBits())
                                    putInt(t.toInt())
                                    putInt((t ushr 32).toInt())
                                    putInt(p.toRawBits())
                                    putInt(change.id.hashCode())
                                    onSample(bytes)
                                    change.consume()
                                }
                            }
                        }
                    }
                }
        ) {
            Canvas(Modifier.fillMaxSize()) {
                if (marks.size >= 2) {
                    val path = Path()
                    path.moveTo(marks[0].x, marks[0].y)
                    for (i in 1 until marks.size) {
                        path.lineTo(marks[i].x, marks[i].y)
                    }
                    drawPath(
                        path = path,
                        color = colors.primary.copy(alpha = 0.72f),
                        style = Stroke(
                            width = 3.dp.toPx(),
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )
                }
                marks.lastOrNull()?.let { tip ->
                    drawCircle(
                        color = colors.primary,
                        radius = 6.dp.toPx(),
                        center = tip
                    )
                }
            }
            Text(
                if (percent >= 100) "Entropy ready" else "Move your finger randomly here",
                color = colors.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OptionDropdown(
    label: String,
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    enabled: Boolean = true
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { if (enabled) expanded = it }) {
        OutlinedTextField(
            value = selected,
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
fun InFrontShareBar(
    label: String,
    canShareEncrypted: Boolean,
    canShareDecrypted: Boolean,
    busy: Boolean,
    onShareEncrypted: () -> Unit,
    onShareDecrypted: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val skin = LocalVcSkin.current
    Surface(
        color = colors.surface,
        shadowElevation = 8.dp
    ) {
        Column(Modifier.fillMaxWidth()) {
            if (skin == VcSkin.Desktop) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(skinHeaderBrush(VcSkin.Desktop))
                )
            }
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "In front of you",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant
                )
                Text(
                    label,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onShareEncrypted,
                        enabled = !busy && canShareEncrypted,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.Share, contentDescription = null)
                        Spacer(Modifier.padding(4.dp))
                        Text("Share encrypted")
                    }
                    Button(
                        onClick = onShareDecrypted,
                        enabled = !busy && canShareDecrypted,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = colors.primary)
                    ) {
                        Text("Share decrypted")
                    }
                }
            }
        }
    }
}
