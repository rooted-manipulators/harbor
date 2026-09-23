package app.harbor.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.harbor.ui.theme.Chalk
import app.harbor.ui.theme.Hairline
import app.harbor.ui.theme.Motion
import app.harbor.ui.theme.Sand
import app.harbor.ui.theme.Space

private val TileShape = RoundedCornerShape(Space.two)

/**
 * The empty place in the row of people, and the way to fill it.
 *
 * A dashed outline the size of a person's tile: the shape of somebody who is
 * not here yet. It says "add contact" and nothing else -- a plus and two words
 * do the job a sentence used to.
 *
 * It sinks a little under the thumb and springs back, like every other thing
 * in the app you can press.
 */
@Composable
fun AddContactTile(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val presses = remember { MutableInteractionSource() }
    val pressed by presses.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f, Motion.bouncy(), label = "press")

    Box(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(TileShape)
            .background(Sand)
            .drawBehind {
                val w = 1.5.dp.toPx()
                val dash = Space.one.toPx()
                drawRoundRect(
                    color = Hairline,
                    topLeft = Offset(w / 2, w / 2),
                    size = size.copy(width = size.width - w, height = size.height - w),
                    cornerRadius = CornerRadius(Space.two.toPx()),
                    style = Stroke(
                        width = w,
                        cap = StrokeCap.Round,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, dash * 0.75f)),
                    ),
                )
            }
            .clickable(interactionSource = presses, indication = null, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Space.oneHalf),
        ) {
            Box(
                Modifier
                    .size(Space.target)
                    .clip(CircleShape)
                    .border(1.5.dp, Hairline, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier.size(Space.three).drawBehind {
                        val w = 2.dp.toPx()
                        val c = size.width / 2
                        drawLine(Chalk, Offset(c, 0f), Offset(c, size.height), w, StrokeCap.Round)
                        drawLine(Chalk, Offset(0f, c), Offset(size.width, c), w, StrokeCap.Round)
                    },
                )
            }
            Text(
                "Add contact",
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 14.sp, color = Chalk),
            )
        }
    }
}
