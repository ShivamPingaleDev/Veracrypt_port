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
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
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

private val Scheme = lightColorScheme(
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

@Composable
fun VcPortTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = Scheme,
        shapes = Shapes(
            extraSmall = RoundedCornerShape(2.dp),
            small = RoundedCornerShape(4.dp),
            medium = RoundedCornerShape(4.dp),
            large = RoundedCornerShape(6.dp)
        ),
        typography = Typography(
            headlineMedium = TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.SemiBold,
                fontSize = 26.sp,
                color = Ink
            ),
            titleLarge = TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.SemiBold,
                fontSize = 20.sp
            ),
            titleMedium = TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Medium,
                fontSize = 16.sp
            ),
            bodyLarge = TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontSize = 16.sp,
                lineHeight = 22.sp
            ),
            bodySmall = TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontSize = 13.sp,
                lineHeight = 18.sp
            ),
            labelSmall = TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontSize = 11.sp,
                letterSpacing = 0.3.sp
            )
        ),
        content = content
    )
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
                        if (on(i)) color else Color(0xFFD9E3EE),
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
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 1.dp
    ) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content
        )
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
                .background(Color(0xFFF7FAFC), RoundedCornerShape(4.dp))
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
                        color = Color(0xFF0A6CCE).copy(alpha = 0.72f),
                        style = Stroke(
                            width = 3.dp.toPx(),
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )
                }
                marks.lastOrNull()?.let { tip ->
                    drawCircle(
                        color = Color(0xFF0A6CCE),
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
    Surface(
        color = colors.surface,
        shadowElevation = 8.dp
    ) {
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
