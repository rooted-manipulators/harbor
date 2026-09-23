package app.harbor.ui

import android.app.TimePickerDialog
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.harbor.ui.theme.Chalk
import app.harbor.ui.theme.Gold
import app.harbor.ui.theme.Leaf
import app.harbor.ui.theme.Motion
import app.harbor.ui.theme.NavGlass
import app.harbor.ui.theme.Space
import java.time.LocalTime

/*
 * The small tools under the week: do not disturb, copying a day on, pulling
 * from the calendar, and the undo that follows each of them. Apart from the
 * editor because the editor is two thousand lines already and none of these
 * know anything about drawing a day.
 */

private val Pill = RoundedCornerShape(99.dp)

/**
 * A secondary action on the schedule: an outlined pill.
 *
 * Not amber. Amber is the primary action and there is none on this screen --
 * copying a day or reading a calendar is something you might do, not the
 * thing the screen is for. It sinks under the thumb and springs back.
 */
@Composable
internal fun ToolPill(
    label: String,
    skin: WeekSkin,
    modifier: Modifier = Modifier,
    icon: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    val presses = remember { MutableInteractionSource() }
    val pressed by presses.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.95f else 1f, Motion.bouncy(), label = "press")
    Row(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(Pill)
            .background(skin.tile.copy(alpha = 0.35f))
            .border(1.dp, skin.line, Pill)
            .clickable(interactionSource = presses, indication = null, role = Role.Button, onClick = onClick)
            .padding(horizontal = Space.two, vertical = Space.one + Space.half / 2),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            icon()
            Spacer(Modifier.width(Space.one))
        }
        Text(
            label,
            maxLines = 1,
            style = MaterialTheme.typography.titleMedium.copy(fontSize = 14.sp, color = skin.ink),
        )
    }
}

/** Two pages, the top one turned down: "copy". Drawn, like every mark here. */
@Composable
internal fun CopyMark(tint: Color) = Canvas(Modifier.size(Space.two)) {
    val w = 1.6.dp.toPx()
    val r = 2.dp.toPx()
    val s = size.width
    drawRoundRect(
        tint.copy(alpha = 0.6f),
        topLeft = Offset(0f, s * 0.3f),
        size = androidx.compose.ui.geometry.Size(s * 0.7f, s * 0.7f),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(r),
        style = Stroke(w),
    )
    drawRoundRect(
        tint,
        topLeft = Offset(s * 0.3f, 0f),
        size = androidx.compose.ui.geometry.Size(s * 0.7f, s * 0.7f),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(r),
        style = Stroke(w),
    )
}

/** A calendar page: a box with two rings on top and a rule under them. */
@Composable
internal fun CalendarMark(tint: Color) = Canvas(Modifier.size(Space.two)) {
    val w = 1.6.dp.toPx()
    val s = size.width
    drawRoundRect(
        tint,
        topLeft = Offset(w, s * 0.18f),
        size = androidx.compose.ui.geometry.Size(s - 2 * w, s * 0.8f),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()),
        style = Stroke(w),
    )
    drawLine(tint, Offset(w, s * 0.42f), Offset(s - w, s * 0.42f), w)
    drawLine(tint, Offset(s * 0.3f, 0f), Offset(s * 0.3f, s * 0.28f), w)
    drawLine(tint, Offset(s * 0.7f, 0f), Offset(s * 0.7f, s * 0.28f), w)
}

/** A rounded square, struck through once: "clear". */
@Composable
internal fun ClearMark(tint: Color) = Canvas(Modifier.size(Space.two)) {
    val w = 1.6.dp.toPx()
    drawRoundRect(
        tint,
        topLeft = Offset(w, w),
        size = androidx.compose.ui.geometry.Size(size.width - 2 * w, size.height - 2 * w),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()),
        style = Stroke(w),
    )
    drawLine(tint, Offset(w * 1.6f, w * 1.6f), Offset(size.width - w * 1.6f, size.height - w * 1.6f), w)
}

/** A crescent, for do not disturb. */
@Composable
private fun MoonMark(tint: Color) = Canvas(Modifier.size(Space.two + Space.half)) {
    val r = size.width / 2
    val path = Path().apply {
        addOval(androidx.compose.ui.geometry.Rect(Offset(r, r), r * 0.9f))
    }
    val bite = Path().apply {
        addOval(androidx.compose.ui.geometry.Rect(Offset(r * 1.45f, r * 0.65f), r * 0.75f))
    }
    drawPath(
        Path().apply { op(path, bite, androidx.compose.ui.graphics.PathOperation.Difference) },
        tint,
    )
}

/**
 * Do not disturb: one period, every day, busy.
 *
 * The setting and the blocks are the same thing (see [app.harbor.domain.Windows.setQuiet]):
 * the times here are read back off the week, and changing them re-stamps
 * every day. A single day's can still be dragged on the board below.
 *
 * Two times and a switch. No sentence: the moon and the words say it.
 */
@Composable
internal fun QuietRow(
    period: Pair<LocalTime, LocalTime>?,
    skin: WeekSkin,
    onChange: (LocalTime?, LocalTime?) -> Unit,
) {
    val context = LocalContext.current
    val on = period != null
    // What switching back on restores. The nights Harbor seeds, unless the
    // row is showing something else already.
    val (from, to) = period ?: (DEFAULT_FROM to DEFAULT_TO)

    fun pick(initial: LocalTime, set: (LocalTime) -> Unit) {
        TimePickerDialog(context, { _, h, m -> set(LocalTime.of(h, m)) }, initial.hour, initial.minute, false).show()
    }

    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Space.two))
            .background(skin.card)
            .border(1.dp, skin.line.copy(alpha = 0.6f), RoundedCornerShape(Space.two))
            .padding(horizontal = Space.two, vertical = Space.oneHalf),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.one),
    ) {
        val tint by animateColorAsState(if (on) Gold else skin.muted, Motion.normal(), label = "moon")
        MoonMark(tint)
        Text(
            "Do not disturb",
            modifier = Modifier.weight(1f),
            maxLines = 1,
            style = MaterialTheme.typography.titleMedium.copy(fontSize = 14.sp, color = skin.ink),
        )
        AnimatedVisibility(on, enter = fadeIn(Motion.normal()), exit = fadeOut(Motion.fast())) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TimeChip(from, skin) { pick(from) { onChange(it, to) } }
                Text(
                    "–",
                    modifier = Modifier.padding(horizontal = Space.half),
                    style = MaterialTheme.typography.bodyMedium.copy(color = skin.muted),
                )
                TimeChip(to, skin) { pick(to) { onChange(from, it) } }
            }
        }
        Toggle(on) { onChange(if (it) from else null, if (it) to else null) }
    }
}

@Composable
private fun TimeChip(at: LocalTime, skin: WeekSkin, onClick: () -> Unit) {
    // Built out here: the specs read reduced motion, which is composable,
    // and transitionSpec is not.
    val enter = fadeIn(Motion.normal()) + slideInVertically(Motion.normal()) { it / 2 }
    val exit = fadeOut(Motion.fast()) + slideOutVertically(Motion.fast()) { -it / 2 }
    AnimatedContent(
        at,
        transitionSpec = { enter togetherWith exit },
        label = "time",
    ) { shown ->
        Box(
            Modifier
                .clip(Pill)
                .background(skin.tile.copy(alpha = 0.5f))
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = Space.one + Space.half / 2, vertical = Space.half + Space.half / 2),
        ) {
            Text(
                timeLabel(shown),
                style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp, color = skin.ink),
            )
        }
    }
}

/**
 * A switch, drawn to match the app's: green when on (see the note on [Leaf]),
 * the knob sliding across on a spring.
 */
@Composable
private fun Toggle(on: Boolean, onFlip: (Boolean) -> Unit) {
    val track by animateColorAsState(if (on) Leaf else Chalk.copy(alpha = 0.18f), Motion.normal(), label = "track")
    val knob by animateDpAsState(if (on) 20.dp else 2.dp, Motion.bouncy(), label = "knob")
    Box(
        Modifier
            .size(width = 44.dp, height = 26.dp)
            .clip(Pill)
            .background(track)
            .clickable(role = Role.Switch) { onFlip(!on) },
    ) {
        Box(
            Modifier
                .offset(x = knob, y = 2.dp)
                .size(22.dp)
                .clip(CircleShape)
                .background(Chalk),
        )
    }
}

/**
 * What just happened, and how to take it back.
 *
 * Copying a day replaces the next one, and a calendar pull can lay a dozen
 * blocks over a week somebody drew by hand. Both are one tap, so both are one
 * tap to undo. Slides up from the foot of the screen and goes by itself.
 */
@Composable
internal fun UndoBar(message: String?, onUndo: (() -> Unit)?, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = message != null,
        modifier = modifier,
        enter = fadeIn(Motion.arriving()) + slideInVertically(Motion.arriving()) { it },
        exit = fadeOut(Motion.fast()) + slideOutVertically(Motion.normal()) { it },
    ) {
        // Held so the bar still has words while it leaves.
        val last = remember { mutableListOf("") }
        if (message != null) last[0] = message
        Row(
            Modifier
                .clip(Pill)
                .background(NavGlass)
                .border(1.dp, Chalk.copy(alpha = 0.12f), Pill)
                .padding(start = Space.two + Space.half, end = Space.one, top = Space.half, bottom = Space.half),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                last[0],
                maxLines = 1,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, color = Chalk),
            )
            if (onUndo != null) {
                Spacer(Modifier.width(Space.one))
                Box(
                    Modifier
                        .clip(Pill)
                        .clickable(role = Role.Button, onClick = onUndo)
                        .padding(horizontal = Space.oneHalf, vertical = Space.one),
                ) {
                    Text(
                        "Undo",
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 14.sp, color = Gold),
                    )
                }
            } else {
                Spacer(Modifier.width(Space.one + Space.half))
            }
        }
    }
}

private val DEFAULT_FROM: LocalTime = LocalTime.of(22, 0)
private val DEFAULT_TO: LocalTime = LocalTime.of(8, 0)
