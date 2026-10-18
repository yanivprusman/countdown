package com.automatelinux.countdown.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.automatelinux.countdown.data.today
import com.automatelinux.countdown.ui.theme.T
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlin.math.abs
import kotlin.math.roundToInt

/** The same bar on every screen: back, title, and whatever the screen needs on the right. */
@Composable
fun ScreenHeader(
    title: String,
    onBack: (() -> Unit)? = null,
    subtitle: String? = null,
    large: Boolean = false,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            RoundIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Back", onBack)
            Spacer(Modifier.width(4.dp))
        } else {
            Spacer(Modifier.width(10.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = if (large) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleLarge,
                color = T.text,
                maxLines = 1,
            )
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = T.textDim, maxLines = 1)
            }
        }
        trailing()
    }
}

@Composable
fun RoundIconButton(icon: ImageVector, description: String, onClick: () -> Unit, tint: Color? = null) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = Color.Transparent,
        contentColor = tint ?: T.textDim,
        modifier = Modifier.size(44.dp),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(icon, description, Modifier.size(22.dp))
        }
    }
}

/**
 * The ring that empties as you count. The coloured arc is what is *left*, starting at
 * twelve o'clock, so each tap visibly takes a slice away — the same way a countdown
 * reads in words. It springs to its new length rather than jumping, so the tap that
 * changed it is felt.
 */
@Composable
fun ProgressRing(
    left: Float,
    color: Color,
    modifier: Modifier = Modifier,
    stroke: Dp = 14.dp,
    track: Color = T.surfaceAlt,
) {
    val animated by animateFloatAsState(
        targetValue = left.coerceIn(0f, 1f),
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow),
        label = "ring",
    )
    Canvas(modifier) {
        val w = stroke.toPx()
        val inset = w / 2
        val arcSize = Size(size.width - w, size.height - w)
        drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(w))
        if (animated > 0f) {
            // Round caps reach half a stroke past each end, so at 99 of 100 the arc's tail
            // would close the gap and look full. Below full, keep at least a visible notch.
            val radius = (size.minDimension - w) / 2
            val capsDeg = (w / radius) * (180f / kotlin.math.PI.toFloat())
            val sweep = if (animated >= 1f) 360f else minOf(360f * animated, 360f - capsDeg - 6f).coerceAtLeast(0.1f)
            drawArc(
                color, -90f, sweep, false, Offset(inset, inset), arcSize,
                style = Stroke(w, cap = StrokeCap.Round),
            )
        }
    }
}

/** A thin straight version of the ring, for the cards on the list. */
@Composable
fun ProgressLine(left: Float, color: Color, modifier: Modifier = Modifier) {
    val animated by animateFloatAsState(left.coerceIn(0f, 1f), tween(420), label = "line")
    Box(modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(T.surfaceAlt)) {
        if (animated > 0f) {
            Box(Modifier.fillMaxWidth(animated).height(6.dp).clip(RoundedCornerShape(3.dp)).background(color))
        }
    }
}

/**
 * The tear-off page from the launcher icon — "−1" on today's sheet — reused wherever a
 * screen is empty or waiting. (A ring of ticks was tried first and read as a loading
 * spinner, which is the last thing an empty screen should look like.)
 */
@Composable
fun CalendarArt(header: Color, modifier: Modifier = Modifier, dim: Boolean = false) {
    val page = if (dim) T.surfaceAlt else T.surface
    val ink = if (dim) T.textFaint else T.text
    val under = T.line
    val rings = T.textFaint
    Canvas(modifier.size(width = 104.dp, height = 96.dp)) {
        val u = size.width / 56f                       // drawn on a 56-unit grid
        fun r(x: Float, y: Float, w: Float, h: Float, c: Color) = drawRoundRect(
            c, Offset(x * u, y * u), Size(w * u, h * u), androidx.compose.ui.geometry.CornerRadius(8 * u),
        )
        r(2f, 9f, 54f, 39f, under)
        r(0f, 5f, 54f, 39f, page)
        clipRect(0f, 5 * u, 54 * u, 15 * u) { r(0f, 5f, 54f, 39f, header) }
        val sw = 4.5f * u
        drawLine(rings, Offset(13 * u, 1.5f * u), Offset(13 * u, 9 * u), sw, StrokeCap.Round)
        drawLine(rings, Offset(41 * u, 1.5f * u), Offset(41 * u, 9 * u), sw, StrokeCap.Round)
        val nw = 5.5f * u
        drawLine(ink, Offset(11 * u, 29 * u), Offset(23 * u, 29 * u), nw, StrokeCap.Round)
        drawLine(ink, Offset(31 * u, 23 * u), Offset(37.5f * u, 18.5f * u), nw, StrokeCap.Round)
        drawLine(ink, Offset(37.5f * u, 18.5f * u), Offset(37.5f * u, 39 * u), nw, StrokeCap.Round)
    }
}

/**
 * Today's date, kept current. A countdown left open overnight must stop saying
 * "counted today" at midnight rather than on the next unrelated recomposition.
 */
@Composable
fun rememberToday(): LocalDate {
    var day by remember { mutableStateOf(today()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            val now = today()
            if (now != day) day = now
        }
    }
    return day
}

/**
 * Drag a row left to delete it. Hand-rolled rather than SwipeToDismissBox so the reveal,
 * the threshold and the fling-back all behave the same on every platform.
 */
@Composable
fun SwipeToDelete(onDelete: () -> Unit, content: @Composable () -> Unit) {
    val scope = rememberCoroutineScope()
    val offset = remember { Animatable(0f) }
    var width by remember { mutableStateOf(1f) }

    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .onSizeChanged { width = it.width.toFloat().coerceAtLeast(1f) },
    ) {
        // The reveal only paints once the row has actually started to move.
        if (offset.value < -1f) {
            Box(
                Modifier.matchParentSize().background(T.dangerSoft).padding(end = 22.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(
                    Icons.Rounded.DeleteOutline,
                    "Delete",
                    Modifier.size((18 + 8 * (abs(offset.value) / width).coerceIn(0f, 1f)).dp),
                    tint = T.danger,
                )
            }
        }
        Box(
            Modifier
                .offset { IntOffset(offset.value.roundToInt(), 0) }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onHorizontalDrag = { _, delta ->
                            scope.launch { offset.snapTo((offset.value + delta).coerceIn(-width, 0f)) }
                        },
                        onDragEnd = {
                            scope.launch {
                                if (abs(offset.value) > width * 0.35f) {
                                    offset.animateTo(-width, tween(160))
                                    onDelete()
                                } else {
                                    offset.animateTo(0f, tween(220))
                                }
                            }
                        },
                        onDragCancel = { scope.launch { offset.animateTo(0f, tween(220)) } },
                    )
                },
        ) { content() }
    }
}

/** Sentence-case label above a group. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.labelSmall, color = T.textFaint, modifier = modifier)
}

/** One dialog for every act worth a second look, so they always read the same way. */
@Composable
fun ConfirmDialog(
    title: String,
    body: String,
    confirmLabel: String,
    destructive: Boolean = true,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = T.surface,
        titleContentColor = T.text,
        textContentColor = T.textDim,
        shape = RoundedCornerShape(24.dp),
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = { Text(body, style = MaterialTheme.typography.bodyLarge) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmLabel, color = if (destructive) T.danger else T.brandInk, style = MaterialTheme.typography.labelLarge)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = T.textDim, style = MaterialTheme.typography.labelLarge)
            }
        },
    )
}

/** A menu row that matches the app rather than the platform default. */
@Composable
fun MenuRow(label: String, icon: ImageVector, tint: Color, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label, color = tint, style = MaterialTheme.typography.bodyLarge) },
        leadingIcon = { Icon(icon, null, Modifier.size(20.dp), tint = tint) },
        onClick = onClick,
    )
}
