package app.harbor.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import app.harbor.domain.CallStats
import app.harbor.domain.Feeling
import app.harbor.domain.Flowers
import app.harbor.domain.LedgerEntry
import app.harbor.ui.theme.CardEdge
import app.harbor.ui.theme.Chalk
import app.harbor.ui.theme.Hairline
import app.harbor.ui.theme.LocalReducedMotion
import app.harbor.ui.theme.Motion
import app.harbor.ui.theme.Muted
import app.harbor.ui.theme.NavGlass
import app.harbor.ui.theme.PrimaryAction
import app.harbor.ui.theme.Space
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.sin

/**
 * One flower, and the call that grew it.
 *
 * Opened by tapping a bloom in the field. The layout is the reference card's:
 * the artwork on a pale panel, the flower's name with who it was with, the
 * flower's own line, then four facts and one way out.
 *
 * ## What it shows, and what it does not
 *
 * Only what the ledger holds. The date, how long it ran, what it was about
 * and how it felt are all things the person said or Harbor saw; a row with no
 * answer is left out rather than filled with a dash, because a card of blanks
 * reads as a form nobody finished.
 *
 * The one control is "Leave it growing", which closes the card. There is no
 * delete here and there should not be: a flower is a record of a call that
 * happened, and a garden where the reward can be pulled up with one tap is a
 * garden people learn not to trust.
 *
 * ## Why the panel is light
 *
 * Everything else in the app is dark glass. The panel is the one pale thing on
 * purpose -- it is the flower's own ground, like a pressed specimen on paper,
 * and the artwork was painted to be seen on something light.
 */
@Composable
internal fun FlowerCard(
    entry: LedgerEntry,
    /** Who the call was with. */
    who: String,
    onDismiss: () -> Unit,
) {
    val kind = entry.flower ?: return
    val spec = Flowers.spec(kind)
    val still = LocalReducedMotion.current

    // The card arrives: fades in and rises a little, then the flower in it
    // opens a beat later. Two movements in sequence rather than one, because
    // the card is the frame and the flower is the thing -- and the eye should
    // land on the frame first.
    val card = remember { Animatable(if (still) 1f else 0f) }
    val bloom = remember { Animatable(if (still) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (still) return@LaunchedEffect
        card.animateTo(1f, tween(Motion.ENTER, easing = Motion.Emphasised))
        bloom.animateTo(1f, tween(Motion.SLOW, easing = Motion.Emphasised))
    }

    // And then it sways, a degree or two, for as long as the card is open.
    // A flower that stands perfectly still on a card is a sticker.
    val sway = if (still) null else rememberInfiniteTransition(label = "sway").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(4200, easing = LinearEasing)),
        label = "sway",
    )

    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    alpha = card.value
                    translationY = (1f - card.value) * 24.dp.toPx()
                }
                .clip(RoundedCornerShape(32.dp))
                .background(NavGlass)
                .border(1.dp, CardEdge, RoundedCornerShape(32.dp))
                .padding(Space.two),
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.5f)
                    .clip(RoundedCornerShape(24.dp))
                    .background(
                        Brush.verticalGradient(listOf(Color(0xFFF7FAF4), Color(0xFFDDEBD9))),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                // A soft shadow under the flower, so it stands on the panel.
                Canvas(Modifier.fillMaxSize()) {
                    drawOval(
                        color = Color(0x2A3E5A36),
                        topLeft = Offset(size.width * 0.34f, size.height * 0.80f),
                        size = androidx.compose.ui.geometry.Size(size.width * 0.32f, size.height * 0.07f),
                    )
                }
                FlowerMark(
                    kind,
                    Modifier
                        .fillMaxSize()
                        .padding(Space.two)
                        .graphicsLayer {
                            val open = bloom.value
                            scaleX = 0.6f + 0.4f * open
                            scaleY = 0.6f + 0.4f * open
                            alpha = open
                            // Pivot at the foot, so it grows up out of the
                            // ground rather than swelling from its middle.
                            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0.9f)
                            rotationZ = (sway?.value ?: 0f).let { 2.2f * sin(it * 2f * Math.PI.toFloat()) }
                        },
                )
            }

            Spacer(Modifier.height(Space.three))

            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    spec.name,
                    style = MaterialTheme.typography.headlineSmall.copy(fontSize = 26.sp, color = Chalk),
                )
                Text(
                    "with $who",
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, color = Muted),
                )
            }
            Spacer(Modifier.height(Space.one))
            Text(
                spec.note,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp, color = Muted),
            )

            Spacer(Modifier.height(Space.two))
            facts(entry).forEachIndexed { i, (label, value) ->
                if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(Hairline))
                Row(
                    Modifier.fillMaxWidth().padding(vertical = Space.oneHalf),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp, color = Muted),
                    )
                    Text(
                        value,
                        textAlign = TextAlign.End,
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp, color = Chalk),
                    )
                }
            }

            Spacer(Modifier.height(Space.two))
            PrimaryAction("Leave it growing", onClick = onDismiss)
        }
    }
}

/** The rows the card shows, in order, each only if the ledger has it. */
private fun facts(entry: LedgerEntry): List<Pair<String, String>> = buildList {
    add(
        "Date" to entry.occurredAt.atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("EEE, d MMMM yyyy")),
    )
    entry.callMinutes?.let { add("Duration" to CallStats.formatDuration(it)) }
    entry.topic?.takeIf { it.isNotBlank() }?.let { add("What it was about" to it) }
    entry.feeling?.let { add("How you felt" to it.said) }
}

/** A feeling in the words somebody would use for it afterwards. */
private val Feeling.said: String
    get() = when (this) {
        Feeling.LIGHT -> "Lighter"
        Feeling.WARM -> "Warm"
        Feeling.STEADY -> "Steadier"
        Feeling.TENDER -> "Tender"
    }
