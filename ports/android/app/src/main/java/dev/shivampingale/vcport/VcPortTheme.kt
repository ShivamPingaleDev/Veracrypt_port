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
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Shapes
import androidx.compose.material3.Surface
import androidx.compose.material3.TabPosition
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
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
    Evangelion("MAGI", "skin_eva"),
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
private val CpTerm = Color(0xFFFF1E2D)

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

/** MAGI console + Unit-01 geometry. Inspired drawing, not affiliated. */
private val EvaOrange = Color(0xFFFF7A18)
private val EvaAmber = Color(0xFFFFB300)
private val EvaPlug = Color(0xFFF06800)
private val EvaPurple = Color(0xFF5C3D82)
private val EvaArmor = Color(0xFF5A2D8A)
private val EvaVisor = Color(0xFFB8FF3A)
private val EvaKhaki = Color(0xFFC4B07A)
private val EvaYellow = Color(0xFFF0F0A0)

private val EvaScheme = darkColorScheme(
    primary = EvaOrange,
    onPrimary = Color(0xFF050504),
    primaryContainer = EvaArmor,
    onPrimaryContainer = EvaVisor,
    secondary = EvaVisor,
    onSecondary = Color(0xFF050504),
    background = Color(0xFF050504),
    onBackground = EvaYellow,
    surface = Color(0xA60E0E0A),
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
        listOf(Color(0xFF6FA8D6), Color(0xFF3D7EB8), Color(0xFF2E6A9E))
    )
    VcSkin.Cyberpunk -> Brush.linearGradient(listOf(CpYellow, CpMagenta, CpCyan))
    VcSkin.Matrix -> Brush.linearGradient(listOf(Color(0xFF003B14), MxGreen, Color(0xFF001A08)))
    VcSkin.Evangelion -> Brush.linearGradient(listOf(EvaArmor, EvaVisor, EvaOrange))
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
        VcSkin.Cyberpunk -> 0.45.sp
        VcSkin.Evangelion -> 0.4.sp
        VcSkin.Matrix -> 0.25.sp
        VcSkin.Desktop -> 0.08.sp
        else -> 0.15.sp
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
    val round = when (skin) {
        VcSkin.Signal -> 16.dp
        VcSkin.Desktop -> 4.dp
        else -> 0.dp
    }
    CompositionLocalProvider(LocalVcSkin provides skin) {
        MaterialTheme(
            colorScheme = scheme,
            shapes = Shapes(
                extraSmall = RoundedCornerShape(if (skin == VcSkin.Signal) 14.dp else round),
                small = RoundedCornerShape(if (skin == VcSkin.Signal) 14.dp else round),
                medium = RoundedCornerShape(round),
                large = RoundedCornerShape(if (skin == VcSkin.Signal) 20.dp else round)
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
            listOf(Color(0xFF12081C), EvaArmor.copy(alpha = 0.7f), Color(0xFF0A1808), Color(0xFF1A1008)),
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

/** Original MAGI three-core seal. Majority vote is 2 of 3. Not a NERV mark. */
private fun DrawScope.drawMagiSeal(center: Offset, r: Float, pulse: Float, lit: Int) {
    drawCircle(EvaOrange.copy(alpha = 0.95f), r, center, style = Stroke(width = 3.4f))
    drawCircle(EvaKhaki.copy(alpha = 0.35f), r * 0.78f, center, style = Stroke(width = 1.4f))
    for (i in 0..2) {
        val a = Math.toRadians(-90.0 + i * 120.0).toFloat()
        val p = Offset(center.x + r * 0.46f * cos(a), center.y + r * 0.46f * sin(a))
        val on = i == lit || i == (lit + 1) % 3
        drawCircle(if (on) EvaOrange.copy(alpha = 0.55f + 0.45f * pulse) else EvaPurple.copy(alpha = 0.4f), r * 0.20f, p)
        drawCircle(EvaYellow.copy(alpha = 0.9f), r * 0.20f, p, style = Stroke(width = 1.8f))
    }
    val hex = Path()
    for (i in 0..5) {
        val a = Math.toRadians(60.0 * i - 30.0).toFloat()
        val px = center.x + r * 0.16f * cos(a)
        val py = center.y + r * 0.16f * sin(a)
        if (i == 0) hex.moveTo(px, py) else hex.lineTo(px, py)
    }
    hex.close()
    drawPath(hex, EvaVisor.copy(alpha = 0.4f * pulse))
    drawPath(hex, EvaOrange, style = Stroke(width = 2f))
}

/** Original Unit-01 helm / visor / pylons. Inspired geometry, not official art. */
private fun DrawScope.drawUnit01(center: Offset, s: Float, pulse: Float) {
    val at = Path()
    for (i in 0..5) {
        val a = Math.toRadians(60.0 * i - 30.0).toFloat()
        val px = center.x + s * 1.42f * cos(a)
        val py = center.y + s * 1.42f * sin(a)
        if (i == 0) at.moveTo(px, py) else at.lineTo(px, py)
    }
    at.close()
    drawPath(at, EvaVisor.copy(alpha = 0.16f + 0.12f * pulse))
    drawPath(at, EvaVisor.copy(alpha = 0.55f + 0.35f * pulse), style = Stroke(width = 6f))
    drawRect(
        EvaArmor,
        topLeft = Offset(center.x - s * 1.08f, center.y + s * 0.12f),
        size = Size(s * 0.30f, s * 0.92f)
    )
    drawRect(
        EvaArmor,
        topLeft = Offset(center.x + s * 0.78f, center.y + s * 0.12f),
        size = Size(s * 0.30f, s * 0.92f)
    )
    drawRect(EvaOrange, topLeft = Offset(center.x - s * 1.08f, center.y + s * 0.12f), size = Size(s * 0.30f, 8f))
    drawRect(EvaOrange, topLeft = Offset(center.x + s * 0.78f, center.y + s * 0.12f), size = Size(s * 0.30f, 8f))
    val chest = Path()
    chest.moveTo(center.x, center.y + s * 0.18f)
    chest.lineTo(center.x - s * 0.58f, center.y + s * 1.05f)
    chest.lineTo(center.x + s * 0.58f, center.y + s * 1.05f)
    chest.close()
    drawPath(chest, EvaArmor.copy(alpha = 0.92f))
    drawPath(chest, EvaOrange.copy(alpha = 0.75f), style = Stroke(width = 2.4f))
    drawCircle(
        EvaVisor.copy(alpha = 0.55f + 0.45f * pulse),
        s * 0.13f,
        Offset(center.x, center.y + s * 0.68f)
    )
    val helm = Path()
    helm.moveTo(center.x, center.y - s * 1.02f)
    helm.lineTo(center.x - s * 0.13f, center.y - s * 0.58f)
    helm.lineTo(center.x - s * 0.46f, center.y - s * 0.44f)
    helm.lineTo(center.x - s * 0.40f, center.y - s * 0.02f)
    helm.lineTo(center.x + s * 0.40f, center.y - s * 0.02f)
    helm.lineTo(center.x + s * 0.46f, center.y - s * 0.44f)
    helm.lineTo(center.x + s * 0.13f, center.y - s * 0.58f)
    helm.close()
    drawPath(helm, EvaArmor)
    drawPath(helm, EvaOrange.copy(alpha = 0.85f), style = Stroke(width = 2.6f))
    drawRect(
        Color(0xFF140818),
        topLeft = Offset(center.x - s * 0.34f, center.y - s * 0.40f),
        size = Size(s * 0.68f, s * 0.20f)
    )
    val eyeA = 0.88f + 0.12f * pulse
    drawCircle(EvaVisor.copy(alpha = eyeA), s * 0.09f, Offset(center.x - s * 0.13f, center.y - s * 0.30f))
    drawCircle(EvaVisor.copy(alpha = eyeA), s * 0.09f, Offset(center.x + s * 0.13f, center.y - s * 0.30f))
    drawCircle(Color.White.copy(alpha = 0.55f * pulse), s * 0.03f, Offset(center.x - s * 0.13f, center.y - s * 0.30f))
    drawCircle(Color.White.copy(alpha = 0.55f * pulse), s * 0.03f, Offset(center.x + s * 0.13f, center.y - s * 0.30f))
    val jaw = Path()
    jaw.moveTo(center.x - s * 0.24f, center.y - s * 0.02f)
    jaw.lineTo(center.x - s * 0.17f, center.y + s * 0.20f)
    jaw.lineTo(center.x + s * 0.17f, center.y + s * 0.20f)
    jaw.lineTo(center.x + s * 0.24f, center.y - s * 0.02f)
    jaw.close()
    drawPath(jaw, Color(0xFF2A1040))
}

private fun DrawScope.drawSkinFrame(skin: VcSkin) {
    val w = size.width
    val h = size.height
    val c = 14f
    fun corner(x: Float, y: Float, sx: Float, sy: Float, color: Color) {
        drawLine(color, Offset(x, y), Offset(x + c * sx, y), 1.6f)
        drawLine(color, Offset(x, y), Offset(x, y + c * sy), 1.6f)
    }
    when (skin) {
        VcSkin.Evangelion -> {
            corner(0f, 0f, 1f, 1f, EvaOrange)
            corner(w, 0f, -1f, 1f, EvaOrange)
            corner(0f, h, 1f, -1f, EvaOrange)
            corner(w, h, -1f, -1f, EvaOrange)
            drawLine(EvaVisor.copy(alpha = 0.9f), Offset(1.5f, 0f), Offset(1.5f, h), 3f)
            drawLine(EvaKhaki, Offset(0f, h - 2f), Offset(w, h - 2f), 4f)
        }
        VcSkin.Cyberpunk -> {
            corner(0f, 0f, 1f, 1f, CpYellow)
            corner(w, 0f, -1f, 1f, CpCyan)
            corner(0f, h, 1f, -1f, CpMagenta)
            corner(w, h, -1f, -1f, CpTerm)
        }
        VcSkin.Matrix -> {
            corner(0f, 0f, 1f, 1f, MxGreen)
            corner(w, 0f, -1f, 1f, MxGreen)
            corner(0f, h, 1f, -1f, MxHead)
            corner(w, h, -1f, -1f, MxGreen)
        }
        else -> {}
    }
}

@Composable
fun SkinTabIndicator(position: TabPosition) {
    val skin = LocalVcSkin.current
    val color = when (skin) {
        VcSkin.Evangelion -> EvaOrange
        VcSkin.Cyberpunk -> CpYellow
        VcSkin.Matrix -> MxGreen
        VcSkin.Signal -> SigBlue
        VcSkin.Desktop -> VcDesktopBlue
    }
    Box(
        Modifier
            .tabIndicatorOffset(position)
            .fillMaxWidth()
            .height(if (skin == VcSkin.Evangelion) 5.dp else 3.dp)
            .background(color)
    )
}

@Composable
fun skinTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = MaterialTheme.colorScheme.primary,
    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
    focusedLabelColor = MaterialTheme.colorScheme.primary,
    unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
    cursorColor = MaterialTheme.colorScheme.primary,
    focusedTextColor = MaterialTheme.colorScheme.onSurface,
    unfocusedTextColor = MaterialTheme.colorScheme.onSurface
)

@Composable
private fun SkinCardCap() {
    val skin = LocalVcSkin.current
    when (skin) {
        VcSkin.Desktop -> Box(
            Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(VcDesktopBlue.copy(alpha = 0.55f))
        )
        VcSkin.Evangelion -> Column {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(EvaOrange.copy(alpha = 0.55f))
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                repeat(3) { i ->
                    Box(
                        Modifier
                            .weight(1f)
                            .height(4.dp)
                            .background(if (i < 2) EvaVisor else EvaKhaki.copy(alpha = 0.4f))
                    )
                }
            }
        }
        VcSkin.Cyberpunk -> Row(Modifier.fillMaxWidth().height(3.dp)) {
            repeat(18) { i ->
                Box(
                    Modifier
                        .weight(1f)
                        .height(3.dp)
                        .background(if (i % 2 == 0) CpYellow.copy(alpha = 0.55f) else Color(0xFF0A0812))
                )
            }
        }
        VcSkin.Matrix -> Box(
            Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(MxGreen)
        )
        VcSkin.Signal -> {}
    }
}

@Composable
private fun SkinChrome(skin: VcSkin) {
    val motion = rememberInfiniteTransition(label = "skin-clock")
    val duration = when (skin) {
        VcSkin.Desktop -> 22000
        VcSkin.Matrix -> 7200
        VcSkin.Cyberpunk -> 8800
        VcSkin.Evangelion -> 11000
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
        List(16) { i ->
            floatArrayOf(
                (i + 0.4f) / 16f,
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
    Canvas(
        Modifier
            .fillMaxSize()
            .graphicsLayer { compositingStrategy = CompositingStrategy.ModulateAlpha }
    ) {
            drawRect(skinBackdropBrush(skin, t, size))
            val w = size.width
            val h = size.height
            // Signal keeps its own voice. Desktop + the other Looks stay quieter.
            val veiled = skin != VcSkin.Signal
            if (veiled) {
                val veil = when (skin) {
                    VcSkin.Desktop -> 120
                    VcSkin.Matrix -> 100
                    VcSkin.Cyberpunk -> 92
                    VcSkin.Evangelion -> 72
                    else -> 255
                }
                drawContext.canvas.nativeCanvas.saveLayer(
                    0f,
                    0f,
                    w,
                    h,
                    android.graphics.Paint().apply { alpha = veil }
                )
            }
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
                    for (row in 0 until 8) {
                        for (col in 0 until 6) {
                            val ch = hex[(row * 6 + col + (t * 16).toInt()) % hex.length].toString()
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
                    glyphPaint.textSize = 15f
                    val prompts = arrayOf(
                        "> sys.ready",
                        "> clk",
                        "> io /xts",
                        "> buf 4096",
                        "> link ok",
                        "> net.run",
                        "> wait",
                        "> ack"
                    )
                    for (row in 0 until 10) {
                        val line = prompts[(row + (t * 11f).toInt()) % prompts.size]
                        glyphPaint.color = android.graphics.Color.argb(
                            (190 * pulse).toInt().coerceIn(90, 230),
                            255, 30, 45
                        )
                        drawContext.canvas.nativeCanvas.drawText(
                            line,
                            22f,
                            88f + row * 20f + ((t * 36f) % 20f),
                            glyphPaint
                        )
                    }
                    val termTop = h * 0.68f
                    val termH = h * 0.22f
                    val termW = w * 0.58f
                    drawRect(Color(0xE608000A), topLeft = Offset(16f, termTop), size = Size(termW, termH))
                    drawRect(CpTerm, topLeft = Offset(16f, termTop), size = Size(termW, termH), style = Stroke(width = 2f))
                    drawLine(CpTerm, Offset(16f, termTop), Offset(16f + termW, termTop), 3f)
                    val termScan = termTop + ((t * termH) % termH)
                    drawRect(CpTerm.copy(alpha = 0.22f), topLeft = Offset(16f, termScan), size = Size(termW, 4f))
                    glyphPaint.textSize = 14f
                    glyphPaint.color = android.graphics.Color.argb(255, 255, 30, 45)
                    val clk = ((t * 97f) % 10f).toInt()
                    val termLines = arrayOf(
                        "> sys.ready",
                        "> clk $clk.${((t * 53f) % 10f).toInt()}",
                        "> io /xts",
                        "> buf 4096",
                        "> ${hex[(t * 16f).toInt() % hex.length]}f wait",
                        "> link ok"
                    )
                    termLines.forEachIndexed { i, line ->
                        drawContext.canvas.nativeCanvas.drawText(
                            line,
                            28f,
                            termTop + 24f + i * 18f,
                            glyphPaint
                        )
                    }
                    if (pulse > 0.62f) {
                        drawRect(
                            CpTerm,
                            topLeft = Offset(28f + 78f, termTop + 24f + termLines.size * 18f - 16f),
                            size = Size(8f, 13f)
                        )
                    }
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
                        y += 12f
                    }
                }
                VcSkin.Evangelion -> {
                    var scan = 0f
                    while (scan < h) {
                        drawLine(EvaYellow.copy(alpha = 0.11f), Offset(0f, scan), Offset(w, scan), 1.2f)
                        scan += 12f
                    }
                    val hexR = 42f
                    val hexDx = hexR * 1.78f
                    val hexDy = hexR * 1.54f
                    var row = 0
                    var hy = 48f
                    while (hy < h - 28f) {
                        val ox = if (row % 2 == 0) 40f else 40f + hexDx * 0.5f
                        var hx = ox
                        var col = 0
                        while (hx < w - 28f) {
                            val lit = ((row * 11 + col * 7 + (t * 19f).toInt()) % 9) == 0
                            val hot = ((row + col + (pulse * 6f).toInt()) % 13) == 0
                            if (lit || hot) {
                                val hex = Path()
                                for (i in 0..5) {
                                    val a = Math.toRadians(60.0 * i - 30.0).toFloat()
                                    val px = hx + hexR * cos(a)
                                    val py = hy + hexR * sin(a)
                                    if (i == 0) hex.moveTo(px, py) else hex.lineTo(px, py)
                                }
                                hex.close()
                                drawPath(
                                    hex,
                                    (if (hot) EvaOrange else EvaPurple).copy(
                                        alpha = if (hot) 0.42f * pulse else 0.28f
                                    )
                                )
                                drawPath(hex, EvaOrange.copy(alpha = 0.32f), style = Stroke(width = 1.4f))
                            }
                            hx += hexDx
                            col++
                        }
                        hy += hexDy
                        row++
                    }
                    drawRect(EvaPurple.copy(alpha = 0.96f), topLeft = Offset(0f, 0f), size = Size(22f, h))
                    drawRect(EvaAmber.copy(alpha = 0.55f + 0.45f * pulse), topLeft = Offset(22f, 0f), size = Size(6f, h))
                    var tape = -48f + (t * 32f) % 32f
                    while (tape < h) {
                        val stripe = Path()
                        stripe.moveTo(0f, tape)
                        stripe.lineTo(22f, tape + 10f)
                        stripe.lineTo(22f, tape + 22f)
                        stripe.lineTo(0f, tape + 12f)
                        stripe.close()
                        drawPath(stripe, EvaOrange)
                        tape += 32f
                    }
                    var chev = 90f - ((t * 48f) % 26f)
                    while (chev < h - 36f) {
                        val p = Path()
                        p.moveTo(30f, chev)
                        p.lineTo(56f, chev + 12f)
                        p.lineTo(30f, chev + 24f)
                        p.close()
                        drawPath(p, EvaOrange.copy(alpha = 0.85f))
                        chev += 26f
                    }
                    drawRect(EvaOrange, topLeft = Offset(w - 16f, 0f), size = Size(16f, h))
                    drawRect(EvaPurple.copy(alpha = 0.7f), topLeft = Offset(w - 22f, 0f), size = Size(6f, h))
                    var tick = 64f
                    var tn = 0
                    while (tick < h - 28f) {
                        val long = tn % 5 == 0
                        drawLine(
                            EvaYellow.copy(alpha = 0.85f),
                            Offset(w - 22f, tick),
                            Offset(w - (if (long) 40f else 30f), tick),
                            if (long) 2.4f else 1.2f
                        )
                        tick += 18f
                        tn++
                    }
                    drawUnit01(Offset(w * 0.52f, h * 0.46f), minOf(w, h) * 0.34f, pulse)
                    drawUnit01(Offset(w * 0.84f, h * 0.81f), minOf(w, h) * 0.13f, pulse)
                    val cores = listOf(
                        Triple("MELCHIOR", "MAGI-1  SCI", 0.22f),
                        Triple("BALTHASAR", "MAGI-2  MOT", 0.50f),
                        Triple("CASPER", "MAGI-3  WOM", 0.78f)
                    )
                    val vote = (t * 3f).toInt() % 3
                    val panelW = w * 0.24f
                    cores.forEachIndexed { i, triple ->
                        val (label, sub, xf) = triple
                        val cx = w * xf
                        val yes = i == vote || i == (vote + 1) % 3
                        val left = cx - panelW / 2f
                        val top = h * 0.12f
                        val ph = h * 0.22f
                        drawRect(
                            EvaArmor.copy(alpha = if (yes) 0.55f else 0.22f),
                            topLeft = Offset(left, top),
                            size = Size(panelW, ph)
                        )
                        drawRect(
                            EvaOrange.copy(alpha = 0.95f),
                            topLeft = Offset(left, top),
                            size = Size(panelW, ph),
                            style = Stroke(width = 3f)
                        )
                        drawRect(if (yes) EvaVisor else EvaOrange, topLeft = Offset(left, top), size = Size(panelW, 6f))
                        drawMagiSeal(Offset(cx, top + 44f), 22f, pulse, i)
                        glyphPaint.textSize = 13f
                        glyphPaint.color = android.graphics.Color.argb(255, 240, 240, 160)
                        drawContext.canvas.nativeCanvas.drawText(label, left + 8f, top + 78f, glyphPaint)
                        glyphPaint.textSize = 11f
                        glyphPaint.color = android.graphics.Color.argb(
                            230,
                            if (yes) 184 else 196,
                            if (yes) 255 else 176,
                            if (yes) 58 else 122
                        )
                        drawContext.canvas.nativeCanvas.drawText(
                            if (yes) "$sub  YES" else "$sub  NO",
                            left + 8f,
                            top + 96f,
                            glyphPaint
                        )
                        for (cell in 0 until 4) {
                            val on = yes && cell <= (pulse * 4f).toInt()
                            drawRect(
                                if (on) EvaOrange.copy(alpha = 0.9f) else EvaKhaki.copy(alpha = 0.22f),
                                topLeft = Offset(left + 10f, top + 108f + cell * (ph * 0.08f)),
                                size = Size(panelW - 20f, ph * 0.055f)
                            )
                        }
                    }
                    drawMagiSeal(Offset(w * 0.22f, h * 0.74f), 64f, pulse, vote)
                    glyphPaint.textSize = 14f
                    glyphPaint.color = android.graphics.Color.argb(230, 255, 122, 24)
                    drawContext.canvas.nativeCanvas.drawText("MAGI  2/3", w * 0.22f - 36f, h * 0.74f + 86f, glyphPaint)
                    glyphPaint.textSize = 56f
                    glyphPaint.color = android.graphics.Color.argb((80 * pulse).toInt().coerceIn(40, 100), 184, 255, 58)
                    drawContext.canvas.nativeCanvas.drawText("UNIT-01", w * 0.18f, h * 0.58f, glyphPaint)
                    fun corner(x: Float, y: Float, s: Float, flipX: Boolean, flipY: Boolean) {
                        val sx = if (flipX) -1f else 1f
                        val sy = if (flipY) -1f else 1f
                        drawLine(EvaOrange, Offset(x, y), Offset(x + s * sx, y), 4f)
                        drawLine(EvaOrange, Offset(x, y), Offset(x, y + s * sy), 4f)
                    }
                    corner(40f, 64f, 36f, false, false)
                    corner(w - 40f, 64f, 36f, true, false)
                    corner(40f, h - 56f, 36f, false, true)
                    corner(w - 40f, h - 56f, 36f, true, true)
                    val plug = Offset(w * 0.78f, h * 0.78f)
                    for (r in 1..4) {
                        drawCircle(
                            EvaPlug.copy(alpha = 0.28f + 0.08f * r),
                            18f * r,
                            plug,
                            style = Stroke(width = 3f, cap = StrokeCap.Square)
                        )
                    }
                    rotate(t * 220f, plug) {
                        drawLine(EvaAmber, Offset(plug.x - 86f, plug.y), Offset(plug.x + 86f, plug.y), 2.4f)
                        drawLine(EvaOrange, Offset(plug.x, plug.y - 86f), Offset(plug.x, plug.y + 86f), 2.4f)
                    }
                    val wave = Path()
                    val mid = h * 0.905f
                    wave.moveTo(36f, mid)
                    var wx = 36f
                    var wi = 0
                    while (wx < w - 36f) {
                        val amp = 14f + 10f * sin(t * 14f + wi)
                        wave.lineTo(wx, mid + amp * sin(wx / 16f + t * 22f))
                        wx += 8f
                        wi++
                    }
                    drawPath(wave, EvaAmber.copy(alpha = 0.75f), style = Stroke(width = 2.6f))
                    drawRect(EvaKhaki, topLeft = Offset(0f, h - 26f), size = Size(w, 26f))
                    drawRect(EvaOrange, topLeft = Offset(0f, h - 32f), size = Size(w, 6f))
                    val sync = ((pulse * 99f).toInt())
                    val fill = (w - 48f) * (sync / 99f)
                    drawRect(EvaOrange, topLeft = Offset(24f, h - 20f), size = Size(fill, 10f))
                    glyphPaint.textSize = 13f
                    glyphPaint.color = android.graphics.Color.argb(240, 20, 16, 8)
                    drawContext.canvas.nativeCanvas.drawText(
                        "MAGI  2/3  ·  UNIT-01  SYNC  $sync%",
                        28f,
                        h - 8f,
                        glyphPaint
                    )
                    glyphPaint.textSize = 12f
                    glyphPaint.color = android.graphics.Color.argb(200, 184, 255, 58)
                    drawContext.canvas.nativeCanvas.save()
                    drawContext.canvas.nativeCanvas.rotate(90f, w - 14f, h * 0.42f)
                    drawContext.canvas.nativeCanvas.drawText(
                        "MAGI  CORE  LINK",
                        w - 14f,
                        h * 0.42f,
                        glyphPaint
                    )
                    drawContext.canvas.nativeCanvas.restore()
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
            if (veiled) {
                drawContext.canvas.nativeCanvas.restore()
            }
    }
}

@Composable
fun SkinProgress(
    progress: Float,
    modifier: Modifier = Modifier,
    indeterminate: Boolean = false
) {
    val skin = LocalVcSkin.current
    val scheme = MaterialTheme.colorScheme
    val sweep = if (indeterminate) {
        val s by rememberInfiniteTransition(label = "skin-bar").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(1100, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "bar-sweep"
        )
        s
    } else {
        0f
    }
    val h = when (skin) {
        VcSkin.Signal -> 12.dp
        VcSkin.Cyberpunk -> 11.dp
        VcSkin.Evangelion -> 14.dp
        VcSkin.Matrix -> 12.dp
        VcSkin.Desktop -> 8.dp
    }
    Canvas(modifier.fillMaxWidth().height(h)) {
        val w = size.width
        val bh = size.height
        val p = progress.coerceIn(0f, 1f)
        val fillW = if (indeterminate) {
            val win = w * 0.28f
            val x = (sweep * (w + win)) - win
            x to win
        } else {
            0f to (p * w)
        }
        when (skin) {
            VcSkin.Desktop -> {
                drawRect(scheme.surfaceVariant, size = Size(w, bh))
                drawRect(scheme.outline.copy(alpha = 0.7f), style = Stroke(width = 1.2f), size = Size(w, bh))
                if (indeterminate) {
                    drawRect(scheme.primary, topLeft = Offset(fillW.first.coerceAtLeast(0f), 1f), size = Size(fillW.second, bh - 2f))
                } else {
                    drawRect(scheme.primary, topLeft = Offset(1f, 1f), size = Size((fillW.second - 2f).coerceAtLeast(0f), bh - 2f))
                }
            }
            VcSkin.Cyberpunk -> {
                val cells = 16
                val gap = 2.2f
                val cw = (w - gap * (cells - 1)) / cells
                drawRect(Color(0xFF1A0810), size = Size(w, bh))
                drawLine(CpTerm, Offset(0f, bh), Offset(w, bh), 1.6f)
                for (i in 0 until cells) {
                    val x = i * (cw + gap)
                    val on = if (indeterminate) {
                        val head = (sweep * cells).toInt()
                        i == head % cells || i == (head + 1) % cells
                    } else {
                        (i + 1) / cells.toFloat() <= p
                    }
                    drawRect(
                        if (on) CpYellow else Color(0xFF2A1030),
                        topLeft = Offset(x, 0f),
                        size = Size(cw, bh - 2f)
                    )
                    if (on) {
                        drawRect(CpCyan.copy(alpha = 0.55f), topLeft = Offset(x, 0f), size = Size(cw, 2f))
                    }
                }
            }
            VcSkin.Matrix -> {
                val cells = 18
                val gap = 1.5f
                val cw = (w - gap * (cells - 1)) / cells
                for (i in 0 until cells) {
                    val x = i * (cw + gap)
                    val on = if (indeterminate) {
                        val head = (sweep * cells).toInt()
                        i <= head % cells
                    } else {
                        (i + 1) / cells.toFloat() <= p
                    }
                    val head = !indeterminate && on && i == ((p * cells).toInt() - 1).coerceAtLeast(0)
                    drawRect(
                        if (head) MxHead else if (on) MxGreen else Color(0xFF0A1A0A),
                        topLeft = Offset(x, 0f),
                        size = Size(cw, bh)
                    )
                }
            }
            VcSkin.Evangelion -> {
                val cells = 12
                val gap = 3f
                val cw = (w - gap * (cells - 1)) / cells
                drawRect(EvaKhaki.copy(alpha = 0.25f), size = Size(w, bh), style = Stroke(width = 2f))
                for (i in 0 until cells) {
                    val x = i * (cw + gap)
                    val on = if (indeterminate) {
                        (i + (sweep * cells).toInt()) % 3 == 0
                    } else {
                        (i + 1) / cells.toFloat() <= p
                    }
                    val head = on && (indeterminate || i == ((p * cells).toInt() - 1).coerceAtLeast(0))
                    val chev = Path()
                    chev.moveTo(x, bh)
                    chev.lineTo(x + cw * 0.2f, 0f)
                    chev.lineTo(x + cw, 0f)
                    chev.lineTo(x + cw * 0.8f, bh)
                    chev.close()
                    drawPath(
                        chev,
                        when {
                            head -> EvaVisor
                            on -> EvaOrange
                            else -> EvaArmor.copy(alpha = 0.55f)
                        }
                    )
                }
            }
            VcSkin.Signal -> {
                val r = CornerRadius(bh / 2f, bh / 2f)
                drawRoundRect(scheme.surfaceVariant, cornerRadius = r, size = Size(w, bh))
                if (indeterminate) {
                    drawRoundRect(
                        SigBlue,
                        topLeft = Offset(fillW.first.coerceIn(0f, w), 0f),
                        size = Size(fillW.second, bh),
                        cornerRadius = r
                    )
                } else if (p > 0f) {
                    drawRoundRect(
                        SigBlue,
                        size = Size((p * w).coerceAtLeast(bh), bh),
                        cornerRadius = r
                    )
                    drawCircle(SigCyan.copy(alpha = 0.55f), bh * 0.35f, Offset((p * w).coerceIn(bh, w - 4f), bh / 2f))
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
    val skin = LocalVcSkin.current
    val shown = title.ifBlank { "On this phone" }
    val animated by animateFloatAsState(
        targetValue = if (percent in 0..100) percent / 100f else 0f,
        animationSpec = tween(180),
        label = "work-percent"
    )
    val dim = when (skin) {
        VcSkin.Cyberpunk -> Color(0xCC08000A)
        VcSkin.Evangelion -> Color(0xCC050504)
        VcSkin.Matrix -> Color(0x99020804)
        else -> Color(0x66000000)
    }
    val panelShape = when (skin) {
        VcSkin.Signal -> RoundedCornerShape(20.dp)
        VcSkin.Desktop -> RoundedCornerShape(8.dp)
        else -> RoundedCornerShape(2.dp)
    }
    val panelBorder = when (skin) {
            VcSkin.Cyberpunk -> BorderStroke(1.dp, CpTerm.copy(alpha = 0.45f))
            VcSkin.Evangelion -> BorderStroke(1.dp, EvaOrange.copy(alpha = 0.45f))
            VcSkin.Matrix -> BorderStroke(1.dp, MxGreen.copy(alpha = 0.4f))
        VcSkin.Signal -> BorderStroke(0.dp, Color.Transparent)
        VcSkin.Desktop -> BorderStroke(1.dp, colors.outline.copy(alpha = 0.55f))
    }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(140)),
        exit = fadeOut(tween(140))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(dim)
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
                    .fillMaxWidth()
                    .drawBehind { drawSkinFrame(skin) },
                shape = panelShape,
                color = if (skin == VcSkin.Cyberpunk) Color(0xF2100008) else colors.surface,
                shadowElevation = if (skin == VcSkin.Desktop || skin == VcSkin.Signal) 16.dp else 0.dp,
                border = panelBorder
            ) {
                Column {
                    SkinCardCap()
                    Column(
                        Modifier.padding(horizontal = 28.dp, vertical = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                    Text(
                        "This step",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (skin == VcSkin.Cyberpunk) CpTerm else colors.onSurfaceVariant
                    )
                    Text(
                        shown,
                        style = MaterialTheme.typography.titleLarge,
                        textAlign = TextAlign.Center,
                        color = if (skin == VcSkin.Cyberpunk) CpTerm else colors.onSurface
                    )
                    WorkMeter(percent = percent, fill = animated, color = colors.primary)
                    if (percent in 0..100) {
                        SkinProgress(progress = animated)
                        Text(
                            "$percent%",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = if (skin == VcSkin.Cyberpunk) CpTerm else colors.onSurface
                        )
                    } else {
                        SkinProgress(progress = 0f, indeterminate = true)
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
    val skin = LocalVcSkin.current
    val track = MaterialTheme.colorScheme.surfaceVariant
    val shape = when (skin) {
        VcSkin.Signal -> RoundedCornerShape(8.dp)
        VcSkin.Desktop -> RoundedCornerShape(2.dp)
        VcSkin.Cyberpunk, VcSkin.Evangelion, VcSkin.Matrix -> RoundedCornerShape(0.dp)
    }
    val gap = if (skin == VcSkin.Signal) 8.dp else 4.dp
    val cellH = when (skin) {
        VcSkin.Evangelion -> 12.dp
        VcSkin.Cyberpunk -> 8.dp
        else -> 10.dp
    }
    val fill = when (skin) {
        VcSkin.Cyberpunk -> CpYellow
        VcSkin.Matrix -> MxGreen
        VcSkin.Evangelion -> EvaOrange
        else -> color
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(gap)
    ) {
        repeat(8) { i ->
            Box(
                Modifier
                    .weight(1f)
                    .height(cellH)
                    .background(if (on(i)) fill else track, shape)
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
    val skin = LocalVcSkin.current
    val lower = status.lowercase()
    val tone = when {
        listOf("fail", "could not", "wrong password", "name is empty", "must be", "choose at least", "enter the wrap", "select a container", "tap a file", "open a volume first").any { it in lower } ->
            colors.error
        listOf("opened", "copied", "created", "moved", "wiped", "complete", "saved", "unwrapped", "wrapped", "renamed", "deleted").any { it in lower } ->
            if (skin == VcSkin.Evangelion) EvaVisor else Color(0xFF1B7A3A)
        else ->
            colors.primary
    }
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .drawBehind { drawSkinFrame(skin) },
        shape = MaterialTheme.shapes.medium,
        color = colors.surface,
        shadowElevation = if (skin == VcSkin.Desktop || skin == VcSkin.Signal) 1.dp else 0.dp,
        border = when (skin) {
            VcSkin.Evangelion -> BorderStroke(1.5.dp, EvaOrange)
            VcSkin.Cyberpunk -> BorderStroke(1.dp, CpCyan)
            VcSkin.Matrix -> BorderStroke(1.dp, MxGreen)
            VcSkin.Signal -> BorderStroke(0.dp, Color.Transparent)
            VcSkin.Desktop -> BorderStroke(1.dp, colors.outline.copy(alpha = 0.4f))
        }
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (skin == VcSkin.Evangelion) {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    repeat(3) { i ->
                        Box(
                            Modifier
                                .width(10.dp)
                                .height(8.dp)
                                .background(if (i < 2) tone else EvaKhaki.copy(alpha = 0.35f))
                        )
                    }
                }
            } else {
                Box(
                    Modifier
                        .width(if (skin == VcSkin.Signal) 6.dp else 4.dp)
                        .height(36.dp)
                        .background(
                            tone,
                            if (skin == VcSkin.Signal) RoundedCornerShape(8.dp) else RoundedCornerShape(0.dp)
                        )
                )
            }
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
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .drawBehind { drawSkinFrame(skin) },
        shape = MaterialTheme.shapes.medium,
        color = colors.surface,
        shadowElevation = if (skin == VcSkin.Desktop || skin == VcSkin.Signal) 2.dp else 0.dp,
        border = when (skin) {
            VcSkin.Desktop -> BorderStroke(1.dp, colors.outline.copy(alpha = 0.35f))
            VcSkin.Evangelion -> BorderStroke(1.dp, EvaOrange.copy(alpha = 0.4f))
            VcSkin.Cyberpunk -> BorderStroke(1.dp, CpCyan.copy(alpha = 0.35f))
            VcSkin.Matrix -> BorderStroke(1.dp, MxGreen.copy(alpha = 0.35f))
            VcSkin.Signal -> BorderStroke(0.dp, Color.Transparent)
        }
    ) {
        Column {
            SkinCardCap()
        Column(
            Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content
        )
        }
    }
}

@Composable
fun VcHint(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
    )
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
        shape = MaterialTheme.shapes.small,
        colors = skinTextFieldColors(),
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
    val skin = LocalVcSkin.current
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
        SkinProgress(progress = bar)
        Text("$percent%", style = MaterialTheme.typography.bodySmall)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(240.dp)
                .border(
                    if (skin == VcSkin.Evangelion) 2.dp else 1.dp,
                    colors.outline,
                    MaterialTheme.shapes.small
                )
                .background(colors.surface, MaterialTheme.shapes.small)
                .drawBehind { drawSkinFrame(skin) }
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
    enabled: Boolean = true,
    modifier: Modifier = Modifier.fillMaxWidth()
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
            shape = MaterialTheme.shapes.small,
            colors = skinTextFieldColors(),
            modifier = modifier.menuAnchor()
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
fun SizeUnitPicker(
    selected: SizeUnit,
    onSelect: (SizeUnit) -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .width(84.dp)
            .height(52.dp),
        contentAlignment = Alignment.Center
    ) {
        OutlinedButton(
            onClick = { if (enabled) expanded = true },
            enabled = enabled,
            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            Text(selected.label, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            SizeUnit.entries.forEach { unit ->
                DropdownMenuItem(
                    text = { Text(unit.label) },
                    onClick = {
                        onSelect(unit)
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
        shadowElevation = if (skin == VcSkin.Desktop || skin == VcSkin.Signal) 8.dp else 0.dp,
        border = when (skin) {
            VcSkin.Evangelion -> BorderStroke(1.dp, EvaOrange.copy(alpha = 0.4f))
            VcSkin.Cyberpunk -> BorderStroke(1.dp, CpTerm.copy(alpha = 0.4f))
            VcSkin.Matrix -> BorderStroke(1.dp, MxGreen.copy(alpha = 0.35f))
            else -> BorderStroke(0.dp, Color.Transparent)
        },
        modifier = Modifier.drawBehind { drawSkinFrame(skin) }
    ) {
        Column(Modifier.fillMaxWidth()) {
            SkinCardCap()
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
