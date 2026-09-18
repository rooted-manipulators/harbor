package app.harbor.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Animatable
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Spring
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.harbor.domain.Tone

/**
 * Harbor's building blocks, in the language of "Harbor Specimen - All Screens".
 *
 * Compose has no stylesheet, so a design has to become composables or it
 * becomes a hundred copies of the same padding number. Everything here is
 * layout and colour only; no behaviour lives in this file.
 *
 * ## What the dark pass actually changed
 *
 * The light specimen was a printed page: near-white plinths on bone, an ink
 * button, every radius collapsed to 8-10dp. The dark design is glass on dusk,
 * and four moves account for nearly all of the difference:
 *
 *  1. **Cards are still glass, held at six percent.** White over near-black,
 *     rimmed in [CardEdge] -- still a line *lighter* than both, which is what
 *     makes an edge read as a catch of light rather than a border. There is no
 *     shadow anywhere in this design. See [Surface].
 *  2. **The action is amber.** Every filled button is an [EmberLight] to
 *     [Ember] gradient carrying a brown-black label, and the design is strict
 *     that amber appears *only* on the current tab, the primary action and the
 *     selected chip.
 *  3. **Labels are bold, everything else is regular.** This reverses the
 *     light specimen. One typeface carries the whole app now (Manjari, see
 *     `Type.kt`) rather than a serif-and-sans pairing, so the split that used
 *     to come from switching faces now comes from weight alone: a name or a
 *     headline is regular, and anything the interface says about itself --
 *     a button label, a tab, a chip, a caption -- is bold.
 *  4. **Radii opened back up.** 24dp for a card and a full pill for anything
 *     you press. A piece of glass with a tight corner reads as a dialog.
 *
 * No screen's structure, order or controls changed. This is the same app in
 * different clothes.
 */

/** A card. The design draws them at 24px. */
private val CardShape = RoundedCornerShape(24.dp)

/**
 * An action.
 *
 * A full pill. In the dark design every single thing you press is one, so the
 * pill stopped being the exception the light specimen kept for chips and
 * became the rule.
 */
private val ActionShape = RoundedCornerShape(99.dp)

/** A chip. The same pill, named apart because it means something different. */
private val ChipShape = RoundedCornerShape(99.dp)

/**
 * A quiet row, and the reason [ActionShape] cannot just be used everywhere.
 *
 * A row of text is not a button, and a pill drawn around two lines of it reads
 * as a lozenge rather than a card. The design draws these at 20px.
 */
private val RowShape = RoundedCornerShape(20.dp)

/** The vertical rhythm every page is built on. */
@Composable
fun Flow(
    modifier: Modifier = Modifier,
    gap: Int = 14,
    content: @Composable ColumnScope.() -> Unit,
) = Column(modifier, verticalArrangement = Arrangement.spacedBy(gap.dp), content = content)

/** The page's own margin. */
fun Modifier.pageContent(): Modifier = padding(horizontal = 24.dp, vertical = 20.dp)

/**
 * A card: frosted glass laid on the page.
 *
 * In the specimen this is white at about 70% over the ground, rimmed with
 * white at 90%. The rim is the whole trick — it is lighter than the card *and*
 * lighter than the ground, so the edge reads as a catch of light rather than
 * as a border, and the card appears to float without any shadow at all.
 *
 * The fill is composited rather than genuinely translucent. See [Cream].
 */
@Composable
fun Surface(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) = Flow(
    modifier
        .entrance()
        .fillMaxWidth()
        .clip(CardShape)
        .background(MaterialTheme.colorScheme.surface)
        .border(1.dp, CardEdge, CardShape)
        .padding(18.dp),
    content = content,
)

/**
 * The gentler of two adjacent cards.
 *
 * This used to be pale green. It is neutral now on purpose: its one use holds
 * a contact's avatar, and the avatar already carries that person's tone.
 * Tinting the card as well gave the page two competing colours and left the
 * avatar with nothing to stand out against.
 */
@Composable
fun SoftSurface(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) = Flow(
    modifier
        .fillMaxWidth()
        .clip(CardShape)
        .background(MaterialTheme.colorScheme.surfaceVariant)
        .border(1.dp, CardEdge, CardShape)
        .padding(18.dp),
    content = content,
)

/** Gold mixed into the card colour. The accent card, used sparingly. */
@Composable
fun GoldSurface(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) = Flow(
    modifier
        .fillMaxWidth()
        .clip(CardShape)
        .background(SurfaceGold)
        .border(1.dp, CardEdge, CardShape)
        .padding(18.dp),
    content = content,
)

/** The serif title of a page, over its letterspaced label. */
@Composable
fun PageIntro(title: String, subtitle: String? = null, eyebrow: String? = null) {
    Column(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 18.dp)) {
        eyebrow?.let {
            Eyebrow(it)
            Spacer(Modifier.size(10.dp))
        }
        Text(
            title,
            style = MaterialTheme.typography.headlineLarge.copy(fontSize = 34.sp),
        )
        subtitle?.let {
            Spacer(Modifier.size(8.dp))
            SmallCopy(it, size = 14)
        }
    }
}

/**
 * A section's serif heading. Regular weight -- the size is the emphasis.
 *
 * Explicit colour, unlike most of this file's other leaf text ([SmallCopy],
 * [Eyebrow] both set their own too) -- nothing in this app wraps content in
 * a real `androidx.compose.material3.Surface`, so `LocalContentColor` never
 * gets set away from Material's own default of black. Every other text here
 * routes around that by setting colour explicitly; this one did not, which
 * made it render as black text on this app's near-black ground everywhere
 * it appears -- invisible rather than merely low-contrast.
 */
@Composable
fun SectionHeading(text: String, modifier: Modifier = Modifier) = Text(
    text,
    modifier = modifier,
    style = MaterialTheme.typography.titleLarge.copy(
        fontSize = 19.sp,
        color = MaterialTheme.colorScheme.onBackground,
    ),
)

/**
 * The quiet line under something: small, muted, and in sentences.
 *
 * It used to uppercase its own text and track it out to 2sp, which is what the
 * light specimen wanted -- it was imitating a printed catalogue, where a label
 * under a plate is set in small caps.
 *
 * The dark design does not do that anywhere. Its labels are ordinary sentence
 * case at 11-13px in a muted grey ("Golden Hour", "Cloud Cover", "Quality"),
 * and the reference it comes from has no capitalised line on it at all. Caps
 * also cost real legibility at this size, and they make a line of plain
 * English read as a heading for a table that is not there.
 *
 * So this no longer transforms the string it is given. Anything that wants to
 * be shouted has to say so itself, and nothing should.
 */
@Composable
fun Eyebrow(
    text: String,
    modifier: Modifier = Modifier,
    /** End-aligned when it is the right half of a [SectionHeader]. */
    textAlign: TextAlign? = null,
) = Text(
    text,
    modifier = modifier,
    textAlign = textAlign,
    style = MaterialTheme.typography.bodySmall.copy(
        fontSize = 13.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    ),
)

/** The muted voice Harbor explains itself in. */
@Composable
fun SmallCopy(text: String, modifier: Modifier = Modifier, size: Int = 13) = Text(
    text,
    modifier = modifier,
    style = MaterialTheme.typography.bodyMedium.copy(
        fontSize = size.sp,
        lineHeight = (size * 1.6).sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    ),
)

/**
 * A quiet reassurance with a mark beside it.
 *
 * Almost always the privacy line. Muted rather than warned: this app never has
 * anything alarming to say, and styling it like a warning would make the
 * promise read as a caveat.
 */
@Composable
fun Notice(text: String, modifier: Modifier = Modifier) = Row(
    modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(10.dp),
) {
    Box(
        Modifier
            .padding(top = 6.dp)
            .size(5.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.onSurfaceVariant),
    )
    SmallCopy(text)
}

/**
 * Initials on a tone, or nothing to show yet.
 *
 * The ring in card colour is what lets an avatar sit on a coloured surface
 * without looking stuck to it. With the chrome drained, this is now one of the
 * few places colour appears outside the garden, which is the point -- and the
 * initial is serif, because in the specimen a person's name always is.
 */
@Composable
fun Avatar(
    label: String,
    tone: Tone,
    modifier: Modifier = Modifier,
    size: AvatarSize = AvatarSize.MD,
) {
    Box(
        modifier
            .size(size.dp.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface)
            .padding(2.dp)
            .clip(CircleShape)
            .background(tone.fill),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            initialsOf(label),
            style = MaterialTheme.typography.titleLarge.copy(
                fontSize = size.text.sp,
                color = Ink,
            ),
        )
    }
}

enum class AvatarSize(val dp: Int, val text: Int) {
    XS(26, 12), SM(34, 15), MD(44, 18), LG(54, 21), XL(96, 36),
}

/** First letters of the first two words, as the prototype does it. */
fun initialsOf(name: String): String =
    name.trim().split(Regex("\\s+")).take(2)
        .mapNotNull { it.firstOrNull()?.uppercaseChar() }
        .joinToString("")
        .ifEmpty { "·" }

/** The four tones, tuned to sit under [Ink] so there is one ink and not two. */
internal val Tone.fill: Color
    get() = when (this) {
        Tone.GREEN -> SurfaceGreen
        Tone.GOLD -> SurfaceGold
        Tone.ORANGE -> SurfaceOrange
        Tone.SKY -> SurfaceSky
    }

/** The same tone at full strength, for a mark too small to carry a tint. */
internal val Tone.mark: Color
    get() = when (this) {
        Tone.GREEN -> MarkGreen
        Tone.GOLD -> MarkGold
        Tone.ORANGE -> MarkOrange
        Tone.SKY -> MarkSky
    }

/**
 * The one thing a screen is actually asking for.
 *
 * Written here rather than reached for as a Material Button because a filled
 * Button brings Material's own shape, elevation and ripple, and those are the
 * three things this design most wants to not have.
 *
 * Amber, and the only amber fill on most screens. The design draws it as a
 * top-lit gradient rather than a flat colour, which is the one piece of
 * shading it allows itself -- everything else is flat glass and a rim.
 */
@Composable
fun PrimaryAction(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val press = remember { MutableInteractionSource() }
    Box(
        modifier
            .pressScale(press)
            .fillMaxWidth()
            .clip(ActionShape)
        .background(
            if (enabled) Brush.verticalGradient(listOf(EmberLight, Ember))
            else SolidColor(MaterialTheme.colorScheme.surfaceVariant),
        )
            .clickable(
                enabled = enabled,
                interactionSource = press,
                indication = null,
                onClick = onClick,
            )
            .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.titleMedium.copy(
                fontSize = 15.sp,
                color = if (enabled) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        )
    }
}

/**
 * A press that gives under the thumb.
 *
 * Three percent and a spring, which is small enough that nobody looking at a
 * screenshot would find it and large enough that the button feels like a
 * physical thing the moment you touch it. The ripple is turned off wherever
 * this is used: a ripple is Material announcing that it handled a touch, and
 * this design has no other Material tell left anywhere in it.
 *
 * `indication = null` on the clickable is what makes that true -- without it
 * the scale and the ripple both play and the button does two things at once.
 */
@Composable
fun Modifier.pressScale(
    source: MutableInteractionSource,
    down: Float = 0.97f,
): Modifier {
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) down else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "press",
    )
    return graphicsLayer { scaleX = scale; scaleY = scale }
}

/**
 * An action that must not compete with the primary one.
 *
 * The design's outline chip: a pill, a hairline of white, a sans label and
 * nothing else. It is the same silhouette as [PrimaryAction] with the fill
 * taken away, which is exactly how the design distinguishes the two.
 */
@Composable
fun QuietAction(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val press = remember { MutableInteractionSource() }
    Box(
        modifier
            .pressScale(press)
            .clip(ChipShape)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, ChipShape)
            .clickable(
                enabled = enabled,
                interactionSource = press,
                indication = null,
                onClick = onClick,
            )
            // Fourteen, not ten. Fourteen plus a 14sp line is the 48dp
            // minimum touch target; ten made this chip 39dp, which is small
            // enough to miss and is the size guidance exists to prevent.
            .padding(horizontal = 16.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.titleMedium.copy(
                fontSize = 14.sp,
                color = if (enabled) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        )
    }
}

/**
 * A heading with its note, set on the same line.
 *
 * The specimen almost never leaves a heading alone: "Your people" carries
 * "one patch each" out at the right margin, in the same tracked caps as every
 * other caption. It is a small thing that does a lot of the work of making a
 * screen read as a printed page rather than a settings list.
 */
@Composable
fun SectionHeader(title: String, meta: String, modifier: Modifier = Modifier) = Column(
    modifier.fillMaxWidth(),
) {
    // Stacked, after two attempts at sharing a row.
    //
    // SpaceBetween let the two overlap outright. Weighting the row 1.7 to 1
    // stopped the overlap and left the real problem: both halves still wrap,
    // so a two-line title sat a few pixels from a two-line caption and the
    // eye could not tell which words belonged to which. On Account that was
    // "What you call / yourself" against "never leaves this / phone".
    //
    // A caption under its heading needs no arithmetic to be legible, reads in
    // the order it is written, and gives the title the whole width -- which
    // is usually enough for it to stop wrapping at all.
    SectionHeading(title)
    Eyebrow(meta)
}

/**
 * The quietest row in the design: one line of something, and where it came from.
 *
 * Frosted like a card but a fraction of the height, so a list of ten of them
 * still reads as a page rather than a stack of boxes. This is what the
 * specimen uses for the notes on home and for a conversation, and it is the
 * reason those screens look like a catalogue index instead of a feed.
 */
@Composable
fun QuietRow(text: String, meta: String, modifier: Modifier = Modifier) = Row(
    modifier
        .fillMaxWidth()
        .clip(RowShape)
        .background(MaterialTheme.colorScheme.surface)
        .border(1.dp, CardEdge, RowShape)
        .padding(horizontal = 16.dp, vertical = 13.dp),
    horizontalArrangement = Arrangement.spacedBy(12.dp),
    verticalAlignment = Alignment.CenterVertically,
) {
    Text(
        text,
        modifier = Modifier.weight(1f),
        style = MaterialTheme.typography.bodyMedium,
    )
    Text(
        meta,
        style = MaterialTheme.typography.bodySmall.copy(
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    )
}

/**
 * A card arriving, rather than being there already.
 *
 * Twelve dp and a fade, once, the first time a card is composed. It is the
 * cheapest way to make a screen feel like it was dealt rather than switched
 * on, and at 260ms it is over before anybody could call it an animation.
 *
 * Deliberately not staggered. A stagger down a list is lovely on a marketing
 * page and wrong here: these cards are a schedule and a set of controls, and
 * making somebody wait 80ms per card to see the last one is a cost paid on
 * every single visit for an effect that only lands on the first.
 *
 * Somebody who has asked for less movement gets the card, in place, with no
 * animation at all -- that setting exists for people who find movement
 * genuinely unpleasant, so it has to reach the small things too.
 */
@Composable
fun Modifier.entrance(): Modifier {
    if (LocalReducedMotion.current) return this
    val arrived = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        arrived.animateTo(1f, tween(durationMillis = 260, easing = FastOutSlowInEasing))
    }
    val t = arrived.value
    return graphicsLayer {
        alpha = t
        translationY = (1f - t) * 12.dp.toPx()
    }
}

/**
 * Whether this app should be still.
 *
 * A CompositionLocal because the setting lives in the store and the things
 * that need to honour it are leaf composables all over the app -- threading a
 * boolean through every card to reach [entrance] would be a worse cost than
 * the animation.
 */
val LocalReducedMotion = staticCompositionLocalOf { false }

/** A rule inside a card, between one row and the next. */
@Composable
fun RowDivider(modifier: Modifier = Modifier) = Box(
    modifier
        .fillMaxWidth()
        .height(1.dp)
        .background(MaterialTheme.colorScheme.outlineVariant),
)
