package app.harbor.cue

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.harbor.domain.Contact
import app.harbor.domain.FeedbackPulse
import app.harbor.domain.Resolution
import app.harbor.domain.TriggerSource
import app.harbor.ui.theme.Avatar
import app.harbor.ui.theme.AvatarSize
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.lerp
import app.harbor.ui.theme.MarkSky
import app.harbor.ui.theme.Paper
import app.harbor.ui.theme.SmallCopy
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/**
 * The cue, hand-translated from `cue.tsx` and its `.cue-*` rules.
 *
 * Call-shaped and never claiming to be a call (ADR-009): the harbor mark sits
 * at the top, the copy is Harbor's own voice, and the privacy line is on the
 * screen itself. What makes it land is the person — their face, their sound —
 * not an impersonation of the system dialer.
 */
@Composable
internal fun CueSurface(
    contact: Contact?,
    usualMinutes: Int?,
    source: TriggerSource,
    onRecord: (Resolution, Instant?, FeedbackPulse?) -> Unit,
    onCall: (topic: String?, number: String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var step by remember { mutableStateOf(CueStep.Cue) }
    var line by remember { mutableStateOf("") }
    var settled by remember { mutableStateOf(false) }

    // What the user chose, held so the pulse can amend that same entry rather
    // than writing a second one.
    var chosen by remember { mutableStateOf<Resolution?>(null) }
    var proposed by remember { mutableStateOf<Instant?>(null) }

    BackHandler(enabled = step == CueStep.Cue) { onDismiss() }

    val who = contact?.label ?: "someone at home"
    // Null stays null. This used to fall back to twelve, so a contact added
    // ninety seconds ago was announced with "calls with Mom usually run ~12
    // min" -- a statistic about a relationship Harbor had never once observed.
    // It is the first thing somebody reads about a person they just added, and
    // it was invented. Everything that quoted it is conditional now.
    val usual = usualMinutes

    Column(
        Modifier
            .fillMaxSize()
            // .cue-screen — the ground.
            //
            // The one screen the design gives a gradient of its own, and it
            // gives it the strongest one in the app: a straight fall from
            // evening blue through ember to the ground, no radial softening.
            // This is the screen somebody sees when their phone lights up in
            // their hand, and it is meant to look like a time of day.
            .background(
                Brush.verticalGradient(
                    colorStops = arrayOf(
                        0.00f to Color(0xFF1F4560),
                        0.38f to Color(0xFF8D5230),
                        0.66f to Color(0xFF3B1F18),
                        0.92f to Paper,
                    ),
                ),
            )
            // The bars, kept out of the type.
            //
            // This is a full-screen activity on a phone that draws edge to
            // edge, so 22dp of flat top padding put "harbor" and the time
            // underneath the clock and the wifi icons. The gradient still runs
            // the whole height -- the background is applied above this, so it
            // fills the window and only the content is inset -- which is the
            // point: this screen is meant to look like a time of day, and a
            // black strip across the top of it would break that before anybody
            // read a word.
            //
            // systemBars rather than statusBars because the footer is just as
            // close to the gesture bar at the other end.
            .windowInsetsPadding(WindowInsets.systemBars)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 26.dp, vertical = 22.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // .cue-top — whose app this is, and when. Never "incoming call".
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                "harbor",
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
            Text(
                "now",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        }

        // .cue-person — the face, breathing
        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            PulsingRing {
                if (contact != null) {
                    Avatar(contact.label, contact.tone, size = AvatarSize.XL)
                } else {
                    Box(
                        Modifier
                            .size(96.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.secondaryContainer),
                    )
                }
            }
            Text(
                who,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
        }

        when (step) {
            CueStep.Cue -> Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(13.dp),
            ) {
                // No headline. "Looks like you're free." was Harbor telling
                // somebody how their own afternoon was going, and the line
                // under it already says the true, smaller thing.
                CueSub(source.opening)

                // .cue-length: the ask, with a stated size, but only once
                // there have been enough calls to know one.
                if (usual != null) {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(99.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        Text(
                            "calls with $who usually run ~$usual min",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 13.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                        )
                    }
                }

                Spacer(Modifier.size(2.dp))

                // .cue-paths — three ways through, equal weight, no default
                CuePath(
                    main = "Call now",
                    sub = usual?.let { "~$it min, usually" },
                    // The one path with a colour under it.
                    //
                    // Three identical cards is three equal options, and these
                    // three are not equal: one of them is the entire point of
                    // the screen and the other two are ways of not doing it.
                    // Amber is spoken for -- the tab, the primary button, the
                    // selected chip -- and it would also make a reminder shout
                    // at somebody who has just stopped walking. A little blue
                    // lifts the call off the other two without raising a voice.
                    lead = true,
                    mark = PathMark.Phone,
                    enabled = contact?.phoneE164 != null,
                ) {
                    settled = true
                    // No topic any more. The parameter stays because the
                    // ledger and the study's beats both carry it, and a call
                    // placed from home never had one either.
                    onCall(null, contact?.phoneE164)
                }
                CuePath(
                    main = "Send a reaction",
                    mark = PathMark.Heart,
                ) {
                    step = CueStep.React
                }
                CuePath(
                    main = "Propose a later time",
                    mark = PathMark.Clock,
                ) {
                    step = CueStep.Later
                }

                Spacer(Modifier.size(4.dp))
                CueOut("not now") { onDismiss() }

                // .cue-privacy
                SmallCopy(
                    "Your walking stays on this phone. $who never sees it.",
                    size = 13,
                )
            }

            CueStep.React -> Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(13.dp),
            ) {
                CueTitle("A little love, then.", small = true)
                CueSub("One line is plenty. No call, no explanation.")
                OutlinedTextField(
                    value = line,
                    onValueChange = { line = it.take(120) },
                    placeholder = { Text("thinking of you, that's all") },
                    modifier = Modifier.fillMaxWidth(),
                )
                CuePath(
                    main = "Send it",
                    sub = "nothing owed either way",
                    mark = PathMark.Heart,
                    enabled = line.isNotBlank(),
                ) {
                    settled = true
                    chosen = Resolution.REACTED
                    onRecord(Resolution.REACTED, null, null)
                    step = CueStep.Pulse
                }
                CueOut("back") { step = CueStep.Cue }
            }

            CueStep.Later -> Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(13.dp),
            ) {
                CueTitle("When would suit you?", small = true)
                CueSub("A possibility, not a promise. It becomes today's next nudge and nothing more.")

                // Presets rather than a clock face. At the end of a walk, with
                // a phone half out of a pocket, three taps of a time picker is
                // friction the moment will not survive.
                val zone = ZoneId.systemDefault()
                val now = Instant.now()
                listOf(
                    "In an hour" to now.plus(Duration.ofHours(1)),
                    "This evening" to now.atZone(zone).with(LocalTime.of(20, 0))
                        .let { if (it.toInstant().isAfter(now)) it.toInstant() else it.plusDays(1).toInstant() },
                    "Tomorrow" to now.atZone(zone).plusDays(1).with(LocalTime.of(18, 0)).toInstant(),
                ).forEach { (label, at) ->
                    CuePath(main = label, sub = "a reminder inside Harbor", mark = PathMark.Clock) {
                        settled = true
                        chosen = Resolution.PROPOSED_LATER
                        proposed = at
                        onRecord(Resolution.PROPOSED_LATER, at, null)
                        step = CueStep.Pulse
                    }
                }
                CueOut("back") { step = CueStep.Cue }
            }

            // Stage 8. Asked once, after a choice the user actually made.
            //
            // Deliberately not asked after a dismissal: dismissing has to cost
            // nothing, and a question is a cost. That leaves the dismiss rate
            // itself as the signal for those — see docs/03, study question 1.
            CueStep.Pulse -> Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(13.dp),
            ) {
                CueTitle("Kept.", small = true)
                CueSub("Was this a good moment to be asked?")

                CuePath(main = "Good time", sub = "ask me at moments like this") {
                    onRecord(chosen ?: Resolution.REACTED, proposed, FeedbackPulse.GOOD_TIME)
                    onDismiss()
                }
                CuePath(main = "Not this time", sub = "this one caught me wrong") {
                    onRecord(chosen ?: Resolution.REACTED, proposed, FeedbackPulse.BAD_TIME)
                    onDismiss()
                }

                CueOut("skip") { onDismiss() }
            }
        }
    }
}

private enum class CueStep { Cue, React, Later, Pulse }

/** `.cue-title` — serif, large, centred, tight. Harbor's own voice. */
@Composable
private fun CueTitle(text: String, small: Boolean = false) = Text(
    text,
    textAlign = TextAlign.Center,
    style = MaterialTheme.typography.displayMedium.copy(
        fontSize = if (small) 26.sp else 34.sp,
        lineHeight = if (small) 30.sp else 37.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.5).sp,
    ),
)

/** `.cue-sub` — one muted line under the title, deliberately narrow. */
@Composable
private fun CueSub(text: String) = Text(
    text,
    textAlign = TextAlign.Center,
    modifier = Modifier.widthIn(max = 320.dp),
    style = MaterialTheme.typography.bodyLarge.copy(
        fontSize = 15.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    ),
)

/**
 * `.cue-path` — one of the three ways through.
 *
 * A card with a mark, a serif line and a muted one. Equal visual weight
 * between them is the guardrail: no option is the default, and none is a
 * lesser answer.
 */
@Composable
private fun CuePath(
    main: String,
    sub: String? = null,
    mark: PathMark? = null,
    enabled: Boolean = true,
    /** The one path worth taking, tinted so the eye lands on it first. */
    lead: Boolean = false,
    onClick: () -> Unit,
) {
    // Eight per cent of the palette's own blue over the card's own surface,
    // rather than a second surface colour. Laid over the weather wash it stays
    // a tint of whatever is behind it -- so the card lifts in every weather
    // instead of matching one of them and disappearing into another.
    val base = MaterialTheme.colorScheme.surface
    val ground = if (lead) lerp(base, MarkSky.copy(alpha = base.alpha), 0.22f) else base
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(ground)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 15.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (mark != null) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(15.dp))
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                val ink = MaterialTheme.colorScheme.onSecondaryContainer
                Canvas(Modifier.size(21.dp)) { drawPathMark(mark, ink) }
            }
        }
        Column {
            Text(
                main,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
            sub?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                )
            }
        }
    }
}

/** `.cue-out` — the way out, underlined and quiet. Never styled as a loss. */
@Composable
private fun CueOut(text: String, onClick: () -> Unit) = Text(
    text,
    modifier = Modifier.clickable(onClick = onClick).padding(12.dp),
    style = MaterialTheme.typography.bodyMedium.copy(
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textDecoration = TextDecoration.Underline,
    ),
)

/** `.cue-ring` with `cue-pulse` — the face breathes while the cue waits. */
@Composable
private fun PulsingRing(content: @Composable () -> Unit) {
    val pulse = rememberInfiniteTransition(label = "cue-pulse")
    val scale by pulse.animateFloat(
        initialValue = 1f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(tween(1300), RepeatMode.Reverse),
        label = "cue-pulse-scale",
    )
    Box(
        Modifier
            .scale(scale)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.75f))
            .padding(6.dp),
    ) { content() }
}

/**
 * Why this cue is on screen, in the user's words.
 *
 * The line was hardcoded to the walk, so asking for a cue from the settings
 * screen told you that you had just stopped walking. Harbor is allowed to be
 * wrong about whether this is a good moment -- that is what "not now" is for
 * -- but it is never allowed to tell you something about yourself that did not
 * happen. ADR-009.
 */
private val TriggerSource.opening: String
    get() = when (this) {
        TriggerSource.WALKING_STOP ->
            "You just stopped walking — a good moment, if you want it."
        // Not "you just put something down". That was written when this
        // trigger was going to fire after a session ended, and it fires
        // during one now -- see CuePolicy.waitsOutAStop. Somebody reading it
        // mid-scroll is being told they stopped, which is the exact thing
        // the note above this forbids: Harbor may be wrong about whether
        // this is a good moment, and may never be wrong about what just
        // happened.
        TriggerSource.SESSION_END ->
            "You have been in there a while — a good moment, if you want it."
        TriggerSource.NOTE ->
            "You were just thinking of them anyway."
        TriggerSource.GAME ->
            "You answered today's question. They would like the answer too."
        TriggerSource.MANUAL ->
            "You asked for this one. Here it is."
    }

/** Which mark sits beside a path. */
private enum class PathMark { Phone, Heart, Clock }

/**
 * The path marks, drawn rather than shipped.
 *
 * These were empty coloured squares -- a placeholder that read as three
 * unfinished buttons on the one screen that has to feel finished. Same
 * approach as the nav marks: the lucide shapes, reduced to what survives at
 * 21dp.
 */
private fun DrawScope.drawPathMark(mark: PathMark, ink: Color) {
    val s = size.minDimension
    val line = Stroke(width = s * 0.1f, cap = StrokeCap.Round, join = StrokeJoin.Round)

    when (mark) {
        // A handset, cornered rather than curved so it stays legible small.
        PathMark.Phone -> drawPath(
            Path().apply {
                moveTo(s * 0.26f, s * 0.12f)
                lineTo(s * 0.44f, s * 0.12f)
                lineTo(s * 0.52f, s * 0.36f)
                lineTo(s * 0.38f, s * 0.46f)
                cubicTo(s * 0.46f, s * 0.64f, s * 0.58f, s * 0.74f, s * 0.72f, s * 0.8f)
                lineTo(s * 0.82f, s * 0.66f)
                lineTo(s * 0.94f, s * 0.76f)
                lineTo(s * 0.94f, s * 0.92f)
                cubicTo(s * 0.6f, s * 0.94f, s * 0.22f, s * 0.56f, s * 0.26f, s * 0.12f)
            },
            color = ink,
            style = line,
        )

        PathMark.Heart -> drawPath(
            Path().apply {
                moveTo(s * 0.5f, s * 0.86f)
                cubicTo(s * 0.06f, s * 0.58f, s * 0.16f, s * 0.16f, s * 0.5f, s * 0.34f)
                cubicTo(s * 0.84f, s * 0.16f, s * 0.94f, s * 0.58f, s * 0.5f, s * 0.86f)
                close()
            },
            color = ink,
            style = line,
        )

        PathMark.Clock -> {
            drawCircle(ink, radius = s * 0.42f, center = Offset(s / 2f, s / 2f), style = line)
            drawPath(
                Path().apply {
                    moveTo(s * 0.5f, s * 0.26f)
                    lineTo(s * 0.5f, s * 0.52f)
                    lineTo(s * 0.71f, s * 0.63f)
                },
                color = ink,
                style = line,
            )
        }
    }
}
