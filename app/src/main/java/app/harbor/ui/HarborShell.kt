package app.harbor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import app.harbor.ui.theme.pressScale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.snap
import app.harbor.ui.theme.LocalReducedMotion
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.animateColorAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import app.harbor.ui.theme.Chalk
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Path
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.harbor.ui.theme.Hairline
import app.harbor.ui.theme.NavGlass

/** The pill the nav and its tabs are both cut from. */
private val NavShape = RoundedCornerShape(99.dp)

/**
 * The app shell.
 *
 * A floating pill of three tabs, and nothing else. The prototype's wordmark
 * header is deliberately not here: it spent 70dp on every screen telling
 * someone which app they had opened.
 *
 * The pill is deliberately not a Material navigation bar either. It sits above
 * the content rather than dividing the screen, which keeps the garden feeling
 * like the whole surface rather than a pane with a bar under it.
 */
@Composable
fun HarborShell(
    tab: HarborTab?,
    onSelect: (HarborTab) -> Unit,
    onBack: (() -> Unit)?,
    title: String?,
    content: @Composable () -> Unit,
) {
    // No wash here.
    //
    // The shell used to paint the dusk behind every screen, which gave
    // schedule and account a band of sunset above their content and nothing
    // below it -- a gradient that started nowhere and stopped halfway. The
    // weather belongs to the screen the field is on; every other screen is
    // the ground and the glass, and that is enough.
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Column(Modifier.fillMaxSize()) {
            // No wordmark. It cost 70dp on every screen to tell someone which
            // app they had just opened, which they know — and on home that
            // space belongs to the garden.
            //
            // A pushed screen still gets a way back, because system back is
            // not a visible affordance and this is the only one.
            if (onBack != null) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        // This Column is the whole window - the Scaffold's
                        // inset goes to the content inside, not to here - so
                        // without this the word "Back" sits under the clock.
                        .statusBarsPadding()
                        .padding(start = 16.dp, end = 24.dp, top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        "Back",
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(onClick = onBack)
                            // 12 top and bottom: with the label that is a
                            // 44dp target, which is what a thumb needs.
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        style = MaterialTheme.typography.labelLarge.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    )
                    title?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp),
                        )
                    }
                }
            }

            Box(Modifier.weight(1f)) { content() }

            // Room for the floating pill, so nothing hides beneath it.
            //
            // The content inside already carries the system navigation inset
            // from the Scaffold, and the pill sits above that inset too, so
            // this only has to cover the pill's own height -- reserving the
            // whole band twice left everything below the fold stopping short
            // by an inch of nothing.
            //
            // 96 rather than 72 because the pill grew: each tab is now a 40dp
            // disc over its label rather than a line of text, which took it
            // from about 56dp tall to about 78dp, and at 72 it sat over the
            // last card on home.
            Spacer(Modifier.height(if (tab != null) 96.dp else 16.dp))
        }

        if (tab != null) {
            // A frosted pill, the same glass the cards are made of, holding
            // three sans labels. The bar used to be a block of colour with an
            // icon disc per tab; the design has neither, and against a ground
            // this dark the one amber tab is enough to say where you are.
            Row(
                Modifier
                    .align(Alignment.BottomCenter)
                    // Above the system navigation bar, not on top of it. This
                    // Box is not inset by the Scaffold - it is the full window
                    // - so the pill has to step over the gesture bar itself.
                    .navigationBarsPadding()
                    .padding(bottom = 12.dp)
                    .clip(NavShape)
                    .background(NavGlass)
                    .border(1.dp, Hairline, NavShape)
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                HarborTab.entries.forEach { candidate ->
                    NavItem(candidate, candidate == tab) { onSelect(candidate) }
                }
            }
        }
    }
}

/**
 * The dusk every screen stands in.
 *
 * This is the design, not a backdrop to it. The reference it was drawn from is
 * a photograph of a sunset with a glass card laid across the middle of it, and
 * every screen here is built the same way: a lit gradient, and then translucent
 * white over the top. If this is weak, the cards have nothing to be windows
 * onto and the whole language collapses into "dark mode".
 *
 * Two layers, because one will not do it. The wash carries the colour down the
 * page; the glow is the sun itself, a small bright core just off the top edge
 * that keeps the brightest point genuinely bright rather than leaving the
 * gradient to peak at a mid-tone.
 *
 * Drawn rather than declared because the design states it in percentages of
 * the screen and a Brush needs pixels. Behind the content, so anything that
 * paints its own ground -- the field, the garden -- covers it rather than
 * fighting it.
 */
internal fun DrawScope.drawDusk(
    /**
     * Where the light is in its slow drift, 0 to 1 and back. The sun wanders a
     * few percent of the width and swells a little, over most of a minute --
     * never enough to notice moving, always enough that the page is alive.
     */
    drift: Float = 0.5f,
) {
    val sway = (drift - 0.5f) * 2f
    // The wash: blue overhead, falling through ember to the ground.
    //
    // Reaching further down the page than the design file's own stops do.
    // Taken literally those go near-black by about 40% of the screen, which on
    // a 2400px phone leaves the colour hiding behind the status bar -- the
    // gradient is there, but almost nowhere you can see it. The reference the
    // file was derived from is a photograph of a sunset filling the top half,
    // and that is the proportion this matches.
    drawRect(
        brush = Brush.radialGradient(
            // Seven stops rather than five. The reference is a photograph
            // and its sky never steps -- blue holds, turns through a band of
            // haze, warms, and only then burns. Four stops crossing that whole
            // distance banded visibly on a tall screen and made the wash read
            // as a printed gradient rather than as light.
            colorStops = arrayOf(
                0.00f to Color(0xFF2F7FB8),
                0.14f to Color(0xFF4C82AE),
                0.28f to Color(0xFFA8703C),
                0.40f to Color(0xFFCC6A2C),
                0.54f to Color(0xFF9E3A20),
                0.72f to Color(0xFF35191A),
                0.96f to Color(0x000D0E11),
            ),
            // Reach matters as much as colour, and this is the second time it
            // has been wrong in the opposite direction.
            //
            // At 1.02 the burn ran to about 60% of the screen, which on a page
            // whose cards had stopped painting over it flooded everything
            // below the field in orange. The reference keeps its whole sunset
            // in the top four-tenths and lets the rest of the page go dark --
            // the card is meant to be glass over the *end* of the light, not a
            // pane in the middle of it. At 0.78 the sunset finishes inside the
            // field's own height and the page below it is night.
            center = Offset(size.width * (0.5f - 0.03f * sway), -size.height * 0.10f),
            radius = size.height * (0.78f + 0.02f * sway),
        ),
        size = size,
    )
    // The sun, sitting just off the top edge. Without it the gradient peaks at
    // a mid-tone and never has a bright point for the glass to catch.
    drawRect(
        brush = Brush.radialGradient(
            colorStops = arrayOf(
                0.00f to Color(0x8CFFD2A0),
                0.40f to Color(0x40F0A35F),
                1.00f to Color(0x00F0783C),
            ),
            center = Offset(size.width * (0.5f + 0.06f * sway), size.height * 0.04f),
            radius = size.width * (0.74f + 0.05f * sway),
        ),
        size = size,
    )
}

enum class HarborTab(val label: String) {
    Home("Home"),
    Schedule("Schedule"),
    Account("Account"),
}

/**
 * A disc, and its name underneath.
 *
 * The design draws each tab as a 40dp circle over an 11px label rather than as
 * a text pill -- the current one is an amber disc with a white label, the rest
 * are faint glass with a muted one. Gold rather than `primary`: the current tab
 * gets the brighter of the two ambers and the [app.harbor.ui.theme.Ember]
 * gradient is saved for a button. This is one of exactly three places amber is
 * allowed to appear.
 */
@Composable
private fun NavItem(tab: HarborTab, current: Boolean, onClick: () -> Unit) {
    val press = remember { MutableInteractionSource() }

    // The amber arrives rather than appears, and the disc it arrives on grows
    // a little as it does. Switching tabs is the most frequent thing anybody
    // does in this app, so it is worth the two hundred milliseconds.
    val disc by animateColorAsState(
        targetValue = if (current) MaterialTheme.colorScheme.tertiary
        else Color.White.copy(alpha = 0.07f),
        animationSpec = tween(220),
        label = "tab disc",
    )
    val ink by animateColorAsState(
        targetValue = if (current) MaterialTheme.colorScheme.onTertiary
        else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(220),
        label = "tab ink",
    )
    val label by animateColorAsState(
        targetValue = if (current) Chalk else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(220),
        label = "tab label",
    )
    // The one part of this that is movement rather than colour.
    //
    // The three cross-fades above stay whatever the setting says: a colour
    // arriving over two hundred milliseconds is not motion, and snapping
    // them would be a harsher screen rather than a calmer one. A bouncing
    // scale is motion, and somebody who asked for less of it gets the tab
    // at its size straight away.
    val still = LocalReducedMotion.current
    val lift by animateFloatAsState(
        targetValue = if (current) 1f else 0.92f,
        animationSpec = if (still) {
            snap()
        } else {
            spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow,
            )
        },
        label = "tab lift",
    )

    Column(
        Modifier
            .width(74.dp)
            .pressScale(press)
            .clip(NavShape)
            .clickable(interactionSource = press, indication = null, onClick = onClick)
            .padding(top = 8.dp, bottom = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            Modifier
                .size(40.dp)
                .graphicsLayer { scaleX = lift; scaleY = lift }
                .clip(CircleShape)
                .background(disc),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.size(19.dp)) { drawTabMark(tab, ink) }
        }
        Text(
            tab.label,
            style = MaterialTheme.typography.titleMedium.copy(
                fontSize = 11.sp,
                color = label,
            ),
        )
    }
}

/**
 * The three marks, drawn rather than imported.
 *
 * Harbor ships no icon library and no bitmaps on purpose -- every mark in the
 * app is geometry, so it restyles with the palette for free. These are the
 * design's own paths on its 100x100 grid: a house, a clock, a person.
 */
private fun DrawScope.drawTabMark(tab: HarborTab, colour: Color) {
    val u = size.minDimension / 100f
    val line = Stroke(width = 11f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
    fun path(build: Path.() -> Unit) = drawPath(Path().apply(build), colour, style = line)
    when (tab) {
        HarborTab.Home -> {
            path {
                moveTo(12f * u, 45f * u); lineTo(50f * u, 12f * u); lineTo(88f * u, 45f * u)
            }
            path {
                moveTo(24f * u, 42f * u); lineTo(24f * u, 88f * u)
                lineTo(76f * u, 88f * u); lineTo(76f * u, 42f * u)
            }
        }

        HarborTab.Schedule -> {
            drawCircle(colour, radius = 40f * u, center = Offset(50f * u, 50f * u), style = line)
            path {
                moveTo(50f * u, 28f * u); lineTo(50f * u, 52f * u); lineTo(70f * u, 62f * u)
            }
        }

        HarborTab.Account -> {
            drawCircle(colour, radius = 20f * u, center = Offset(50f * u, 33f * u), style = line)
            path {
                moveTo(17f * u, 90f * u)
                cubicTo(17f * u, 60f * u, 83f * u, 60f * u, 83f * u, 90f * u)
            }
        }
    }
}
