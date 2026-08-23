package dev.shivampingale.vcport

import android.app.Activity
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.core.view.WindowCompat
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.delay
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
    Desktop("Original", "skin_desktop"),
    Signal("Dark mode", "skin_signal")
}

/* OFL latin subset: IBM Plex Sans. */
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
    onSurfaceVariant = Color(0xFFB8BEC6),
    outline = SigBlue,
    error = Color(0xFFD04A3C),
    onError = Color.White
)

private fun schemeFor(skin: VcSkin) = when (skin) {
    VcSkin.Desktop -> DesktopScheme
    VcSkin.Signal -> SignalScheme
}

fun skinHeaderBrush(skin: VcSkin): Brush = when (skin) {
    VcSkin.Desktop -> Brush.verticalGradient(
        listOf(Color(0xFF6FA8D6), Color(0xFF3D7EB8), Color(0xFF2E6A9E))
    )
    VcSkin.Signal -> Brush.linearGradient(listOf(SigDeep, SigBlue, SigCyan))
}

private fun typeFor(skin: VcSkin): Typography {
    val family = IbmPlex
    val ink = schemeFor(skin).onBackground
    val tracking = if (skin == VcSkin.Desktop) 0.08.sp else 0.15.sp
    val bodySize = 16.sp
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
            fontSize = 13.sp,
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
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            val statusBar = when (skin) {
                VcSkin.Desktop -> android.graphics.Color.parseColor("#0A6CCE")
                VcSkin.Signal -> android.graphics.Color.parseColor("#2E62D8")
            }
            window.statusBarColor = statusBar
            window.navigationBarColor = scheme.background.toArgb()
            val bars = WindowCompat.getInsetsController(window, view)
            val lightBars = skin == VcSkin.Desktop
            bars.isAppearanceLightStatusBars = lightBars
            bars.isAppearanceLightNavigationBars = lightBars
        }
    }
    val round = if (skin == VcSkin.Signal) 16.dp else 4.dp
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
    return when (skin) {
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
fun SkinTabIndicator(position: TabPosition) {
    val skin = LocalVcSkin.current
    val color = if (skin == VcSkin.Signal) SigBlue else VcDesktopBlue
    Box(
        Modifier
            .tabIndicatorOffset(position)
            .fillMaxWidth()
            .height(3.dp)
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
        VcSkin.Signal -> {}
    }
}

@Composable
private fun SkinChrome(skin: VcSkin) {
    val motion = rememberInfiniteTransition(label = "skin-clock")
    val duration = if (skin == VcSkin.Desktop) 22000 else 11000
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
    Canvas(
        Modifier
            .fillMaxSize()
            .graphicsLayer { compositingStrategy = CompositingStrategy.ModulateAlpha }
    ) {
            drawRect(skinBackdropBrush(skin, t, size))
            val w = size.width
            val h = size.height
            val veiled = skin != VcSkin.Signal
            if (veiled) {
                drawContext.canvas.nativeCanvas.saveLayer(
                    0f,
                    0f,
                    w,
                    h,
                    android.graphics.Paint().apply { alpha = 120 }
                )
            }
            when (skin) {
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
    val h = if (skin == VcSkin.Signal) 12.dp else 8.dp
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
    val dim = Color(0x66000000)
    val panelShape = if (skin == VcSkin.Signal) RoundedCornerShape(20.dp) else RoundedCornerShape(8.dp)
    val panelBorder = if (skin == VcSkin.Signal) {
        BorderStroke(0.dp, Color.Transparent)
    } else {
        BorderStroke(1.dp, colors.outline.copy(alpha = 0.55f))
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
                    .fillMaxWidth(),
                shape = panelShape,
                color = colors.surface,
                shadowElevation = 16.dp,
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
                        color = colors.onSurfaceVariant
                    )
                    Text(
                        shown,
                        style = MaterialTheme.typography.titleLarge,
                        textAlign = TextAlign.Center,
                        color = colors.onSurface
                    )
                    WorkMeter(percent = percent, fill = animated, color = colors.primary)
                    if (percent in 0..100) {
                        SkinProgress(progress = animated)
                        Text(
                            "$percent%",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.onSurface
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
    val shape = if (skin == VcSkin.Signal) RoundedCornerShape(8.dp) else RoundedCornerShape(2.dp)
    val gap = if (skin == VcSkin.Signal) 8.dp else 4.dp
    val cellH = 10.dp
    val fill = color
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
    modifier: Modifier = Modifier,
    resetPulse: Int = 0
) {
    val colors = MaterialTheme.colorScheme
    val skin = LocalVcSkin.current
    val lower = status.lowercase()
    val tone = when {
        listOf("fail", "could not", "wrong password", "name is empty", "must be", "choose at least", "enter the wrap", "select a container", "tap a file", "open a volume first").any { it in lower } ->
            colors.error
        listOf("opened", "copied", "created", "moved", "wiped", "complete", "saved", "unwrapped", "wrapped", "renamed", "deleted", "session cleared", "dismounted").any { it in lower } ->
            Color(0xFF1B7A3A)
        else ->
            colors.primary
    }
    var flash by remember { mutableStateOf(false) }
    LaunchedEffect(resetPulse) {
        if (resetPulse > 0) {
            flash = true
            delay(650)
            flash = false
        }
    }
    val flashAlpha by animateFloatAsState(
        targetValue = if (flash) 1f else 0f,
        animationSpec = tween(320),
        label = "sessionResetFlash"
    )
    val bgColor = androidx.compose.ui.graphics.lerp(
        colors.surface,
        colors.primaryContainer,
        flashAlpha * 0.88f
    )
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("status_banner")
            .testTag("session_reset_banner"),
        shape = MaterialTheme.shapes.medium,
        color = bgColor,
        shadowElevation = if (flash) 4.dp else 1.dp,
        border = if (skin == VcSkin.Signal) {
            BorderStroke(0.dp, Color.Transparent)
        } else {
            BorderStroke(1.dp, colors.outline.copy(alpha = 0.4f))
        }
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                Modifier
                    .width(if (skin == VcSkin.Signal) 6.dp else 4.dp)
                    .height(36.dp)
                    .background(
                        tone,
                        if (skin == VcSkin.Signal) RoundedCornerShape(8.dp) else RoundedCornerShape(0.dp)
                    )
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
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = colors.surface,
        shadowElevation = 2.dp,
        border = if (skin == VcSkin.Signal) {
            BorderStroke(0.dp, Color.Transparent)
        } else {
            BorderStroke(1.dp, colors.outline.copy(alpha = 0.35f))
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
                .testTag("entropy_pad")
                .fillMaxWidth()
                .height(240.dp)
                .border(
                    1.dp,
                    colors.outline,
                    MaterialTheme.shapes.small
                )
                .background(colors.surface, MaterialTheme.shapes.small)
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
    Surface(
        color = colors.surface,
        shadowElevation = 8.dp,
        border = BorderStroke(0.dp, Color.Transparent)
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
