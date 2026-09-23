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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.height
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.harbor.data.HarborRepository
import app.harbor.ui.theme.Flow
import app.harbor.ui.theme.PageIntro
import app.harbor.ui.theme.SectionHeader
import app.harbor.ui.theme.SmallCopy
import app.harbor.ui.theme.Surface
import app.harbor.ui.theme.pageContent
import app.harbor.domain.Thresholds
import app.harbor.domain.UserSettings
import androidx.compose.material3.OutlinedTextField
import kotlinx.coroutines.launch

/**
 * Everything the user is allowed to change, which is deliberately everything
 * that decides when Harbor speaks.
 *
 * The numbers here ship as a suggestion and are never locked. That is a
 * guardrail from the design audit, not a preference — and they are what the
 * study measures drift against.
 */
@Composable
fun SettingsScreen(
    store: HarborRepository,
    onEditSchedule: () -> Unit,
    onOpenCues: () -> Unit,
    /** The account, which only matters for sharing a week with somebody. */
    onOpenAccount: () -> Unit,
    onOpenStudyCode: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val settings by store.settings.collectAsState()

    fun save(next: UserSettings) = scope.launch { store.setSettings(next) }
    fun thresholds(next: Thresholds) = save(settings.copy(thresholds = next))

    Column(
        modifier
            .fillMaxSize()
            // No ground of its own: HarborShell paints the ground and the
            // dusk over it, and a second opaque background here covered
            // that gradient -- which is what made every screen read flat.
            .verticalScroll(rememberScrollState()),
    ) {
        Box(Modifier.padding(horizontal = 32.dp)) {
            PageIntro(
                eyebrow = "Always on your terms",
                title = "Your pace.",
                subtitle = "Suggestions, not rules. Move them until Harbor fits your week.",
            )
        }

        Flow(Modifier.pageContent()) {
            Surface {
                SectionHeader("What you call yourself", "never leaves this phone")
                OutlinedTextField(
                    value = settings.name,
                    onValueChange = { save(settings.copy(name = it.take(40))) },
                    placeholder = { Text("Your name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                // The caption above already says where the name stays, and
                // saying it twice on one card reads as insistence rather than
                // reassurance. This half is the part that is not obvious.
                SmallCopy("Only used to say hello.")
            }

            Surface(order = 1) {
                SectionHeader("When a reminder can come", "suggestions, not rules")
                SmallCopy(
                    "One person, offered after a walk" +
                        (if (settings.scrollCues) " or a long scroll" else "") +
                        ". Ignoring it costs nothing.",
                )
                Stepper(
                    label = "Walk before a reminder",
                    value = settings.thresholds.walkingMinutes.toString() + " min",
                    onDown = {
                        thresholds(
                            settings.thresholds.copy(
                                walkingMinutes =
                                    (settings.thresholds.walkingMinutes - 1).coerceAtLeast(1),
                            ),
                        )
                    },
                    onUp = {
                        thresholds(
                            settings.thresholds.copy(
                                walkingMinutes =
                                    (settings.thresholds.walkingMinutes + 1).coerceAtMost(120),
                            ),
                        )
                    },
                )
                // The other trigger's threshold, and only for the people
                // who have it.
                //
                // It shipped without a control anywhere, which made 20
                // minutes exactly the locked default the gap below spent a
                // paragraph arguing against -- and it is the number most
                // worth moving, since what counts as "a long stretch" is
                // more personal than what counts as a walk.
                //
                // Five at a time. One-minute steps over a range that runs to
                // three hours is a stepper nobody finishes using.
                if (settings.scrollCues) {
                    Stepper(
                        label = "Time in one app",
                        value = settings.thresholds.sessionMinutes.toString() + " min",
                        onDown = {
                            thresholds(
                                settings.thresholds.copy(
                                    sessionMinutes =
                                        (settings.thresholds.sessionMinutes - 5)
                                            .coerceAtLeast(1),
                                ),
                            )
                        },
                        onUp = {
                            thresholds(
                                settings.thresholds.copy(
                                    sessionMinutes =
                                        (settings.thresholds.sessionMinutes + 5)
                                            .coerceAtMost(180),
                                ),
                            )
                        },
                    )
                }
                Stepper(
                    label = "Most reminders a day",
                    value = settings.thresholds.dailyCap.toString(),
                    onDown = {
                        thresholds(
                            settings.thresholds.copy(
                                dailyCap = (settings.thresholds.dailyCap - 1).coerceAtLeast(1),
                            ),
                        )
                    },
                    onUp = {
                        thresholds(
                            settings.thresholds.copy(
                                dailyCap = (settings.thresholds.dailyCap + 1).coerceAtMost(10),
                            ),
                        )
                    },
                )
                // The quiet gap between two reminders, back after a spell of
                // being enforced with no way to change it.
                //
                // The argument for removing it was sound as far as it went --
                // nobody opens a settings screen wanting to pick the minutes
                // between their own interruptions. But "thresholds are
                // user-set, never a locked default" is a guardrail in
                // CLAUDE.md rather than a preference, and this was a locked
                // default: two hours, enforced by CuePolicy ahead of
                // everything else, invisible and unreachable. It also meant
                // the reminders screen promised a choice that did not exist,
                // and it made the app untestable -- a day of test walks
                // produced one reminder every two hours however the daily
                // number was set, with nothing anywhere saying why.
                //
                // Fine steps low down and coarse ones higher up, because the
                // bottom of this range is where testing lives and the top is
                // where people do.
                Stepper(
                    label = "Quiet gap between reminders",
                    value = gapPhrase(settings.thresholds.cooldownMinutes),
                    onDown = {
                        thresholds(
                            settings.thresholds.copy(
                                cooldownMinutes = stepGap(
                                    settings.thresholds.cooldownMinutes,
                                    up = false,
                                ),
                            ),
                        )
                    },
                    onUp = {
                        thresholds(
                            settings.thresholds.copy(
                                cooldownMinutes = stepGap(
                                    settings.thresholds.cooldownMinutes,
                                    up = true,
                                ),
                            ),
                        )
                    },
                )
                SmallCopy("Suggested values, always editable.")
            }

            // Handing over the week is no longer something the participant
            // has to do. Harbor records what the study needs as it happens
            // (domain/Telemetry) and the file is assembled from that, so the
            // card that used to ask somebody to remember to export is gone.
            // The export itself still exists for whoever collects it.

            // The way out of this screen, drawn as though it were.
            //
            // These four were plain muted text stacked in a column: no rule,
            // no chevron, nothing to press. They are the whole navigation off
            // Account and they read as captions that had lost their headings.
            // A row with a mark on the end is the least that says "this goes
            // somewhere".
            Destination("When you are busy", onEditSchedule)
            Destination("Set up a daily reminder", onOpenCues)
            // Last of the three, because it is the only one that is optional.
            // Harbor works signed out; an account is what lets you ask
            // somebody whether you may see when they are free.
            Destination("Your account", onOpenAccount)

            // Last, and labelled as somebody else's.
            //
            // Everything above this row is the participant's: their name,
            // their thresholds, their week. This one is the study team's,
            // and the difference was invisible -- it read as one more
            // setting in a list of settings, on a screen where every other
            // row is an invitation to change something.
            //
            // The screen behind it still carries the warning about what it
            // destroys. This is only about whose row it is.
            Destination(
                "Study code",
                onOpenStudyCode,
                note = "For whoever set this phone up",
            )

            // Not a fourth destination.
            //
            // "Back" sat in that list looking like one, which raised a
            // question the other three do not: back to where? It is the way
            // out, it is not a place, and on a tab you reached from the bar it
            // is barely needed at all. Kept, quiet, and clearly separate.
            Spacer(Modifier.height(8.dp))
            TextLink("Back", onDone)
        }
    }
}

/**
 * One place this screen goes, as a row you can see is a row.
 *
 * Deliberately not a card. The cards above hold settings you change in place;
 * these go somewhere else, and giving them the same weight would make Account
 * a wall of identical boxes. A line, a chevron, and a hairline under it is
 * enough to read as a list of doors.
 */
@Composable
private fun Destination(label: String, onClick: () -> Unit, note: String? = null) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    label,
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 16.sp),
                )
                // Only the one row uses this, and it is the row that needs
                // it: everything else under Account is the participant's to
                // change, and this one is not.
                if (note != null) {
                    Text(
                        note,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    )
                }
            }
            // Drawn, not a library icon -- see docs/05-changing-the-ui.md.
            val ink = MaterialTheme.colorScheme.onSurfaceVariant
            Canvas(Modifier.size(9.dp)) {
                val w = size.width
                drawLine(ink, Offset(w * 0.15f, 0f), Offset(w * 0.85f, size.height / 2), strokeWidth = 3f)
                drawLine(ink, Offset(w * 0.85f, size.height / 2), Offset(w * 0.15f, size.height), strokeWidth = 3f)
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.14f))
    }
}

/**
 * The quiet gap in words: minutes while they are still countable, hours once
 * they are not.
 *
 * "120 min" is the same fact as "2 h" and is harder to hold. Below an hour the
 * minutes are the unit people think in; above it they are not.
 *
 * Shared with onboarding rather than copied. The first run used to print
 * "0 min" for the same setting Account called "off" -- the same number, two
 * readings, on two screens a minute apart. Zero is not a duration; it is the
 * gap being switched off, which is what one of them said.
 */
internal fun gapPhrase(minutes: Int): String = when {
    minutes == 0 -> "off"
    minutes < 60 -> "$minutes min"
    minutes % 60 == 0 -> "${minutes / 60} h"
    else -> "${minutes / 60} h ${minutes % 60} min"
}

/**
 * One press of the gap stepper.
 *
 * Quarter-hours from fifteen minutes up, single minutes below it. A stepper
 * that moved in ones would take a hundred and five presses to get from the
 * suggested two hours to a quarter of an hour; one that moved in fifteens
 * could never reach the one-minute setting that makes the trigger testable at
 * all. The range is [Thresholds]' own, and it validates on construction, so
 * the bounds here are the same ones or the app crashes on a tap.
 */
private fun stepGap(minutes: Int, up: Boolean): Int = when {
    up && minutes < 15 -> minutes + 1
    up -> (minutes + 15).coerceAtMost(1440)
    minutes > 15 -> minutes - 15
    else -> (minutes - 1).coerceAtLeast(0)
}

/** `.duration-row` — a label, and a round stepper either side of the value. */
@Composable
internal fun Stepper(label: String, value: String, onDown: () -> Unit, onUp: () -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.weight(1f),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            StepButton("-", onDown)
            Text(
                value,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                // Width fixed so the two buttons do not shuffle as the number
                // changes; height left alone. It was pinned at 20dp, which is
                // less than a 16sp line once the system font scale is turned
                // up -- so the one number on this screen that somebody with
                // large text has come here to read was the one clipped by it.
                modifier = Modifier.padding(horizontal = 8.dp).width(76.dp),
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 16.sp),
            )
            StepButton("+", onUp)
        }
    }
}

/**
 * A round step button: 38dp of circle inside 48dp of target.
 *
 * The circle stays the size it was drawn -- 48 would be a heavier mark than
 * this row wants beside a label -- while the thing you press is the outer box.
 * Pressing 5dp outside a small circle is the ordinary way a thumb misses, and
 * these are the controls the study asks people to move.
 */
@Composable
private fun StepButton(glyph: String, onClick: () -> Unit) = Box(
    Modifier
        .size(48.dp)
        .clickable(onClick = onClick),
    contentAlignment = Alignment.Center,
) {
    Box(
        Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            glyph,
            style = MaterialTheme.typography.titleLarge.copy(
                fontSize = 17.sp,
                color = MaterialTheme.colorScheme.primary,
            ),
        )
    }
}

/** A chip that fills in when chosen, as `.cue-topic` does. */
@Composable
internal fun Pill(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    /**
     * Whether it can be pressed at all.
     *
     * Defaults to true so every existing caller is unchanged. It exists for
     * the one on the petal screen, which was the accent pill whether or not
     * there was a line to send -- and pressing it with nothing typed did
     * nothing, silently, because the guard was in the click handler.
     */
    enabled: Boolean = true,
    onClick: () -> Unit,
) = Box(
    modifier
        .clip(RoundedCornerShape(99.dp))
        .background(
            if (selected) MaterialTheme.colorScheme.primary
            else androidx.compose.ui.graphics.Color.Transparent,
        )
        .border(
            1.dp,
            if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.outlineVariant,
            RoundedCornerShape(99.dp),
        )
        .clickable(enabled = enabled, onClick = onClick)
        .alpha(if (enabled) 1f else 0.45f)
        .padding(horizontal = 16.dp, vertical = 8.dp),
    contentAlignment = Alignment.Center,
) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge.copy(
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = if (selected) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurface,
        ),
    )
}
