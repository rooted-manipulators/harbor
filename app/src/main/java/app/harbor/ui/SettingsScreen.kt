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
        Box(Modifier.padding(horizontal = 28.dp)) {
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
                SmallCopy("Only used to say hello. It never leaves this phone.")
            }

            Surface {
                SectionHeader("When a reminder can come", "suggestions, not rules")
                SmallCopy(
                    "A reminder is Harbor offering you one person, on its own, at a " +
                        "moment it thinks you have room - usually just after a " +
                        "walk ends. It shows their face and plays their sound, " +
                        "and the only thing it ever does is offer. Ignoring one " +
                        "costs nothing and there is no streak to break.",
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
                // The quiet gap between two cues is back, against the reasoning
                // that took it out.
                //
                // That reasoning still holds for a participant: nobody opens
                // this screen to decide how many minutes apart their
                // interruptions should be. What it missed is that this number,
                // not the two above it, decides how often a reminder can
                // arrive at all. At the suggested 120 minutes, combined with
                // the rule that any connection ends the day, a participant
                // gets at most one sensed reminder a day in practice -- so a
                // study week is about seven observations per person, and no
                // amount of walking changes that. It was also unreachable: the
                // reminders screen quotes the number at you while nothing
                // anywhere could move it.
                //
                // Steps coarsely up high and finely down low, because the two
                // reasons to touch it are opposite: shortening it to minutes
                // to test the pipeline, or nudging it in hours to be left
                // alone.
                Stepper(
                    label = "Quiet gap between reminders",
                    value = gapLabel(settings.thresholds.cooldownMinutes),
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

            TextLink("When you are busy", onEditSchedule)
            TextLink("Set up a daily reminder", onOpenCues)
            // Last of the three, because it is the only one that is optional.
            // Harbor works signed out; an account is what lets you ask
            // somebody whether you may see when they are free.
            TextLink("Your account", onOpenAccount)
            TextLink("Back", onDone)
        }
    }
}

/**
 * Hours read as hours once there are enough of them. "120 min" is a number to
 * be converted; "2 h" is a length of time somebody can picture.
 */
private fun gapLabel(minutes: Int): String = when {
    minutes < 60 -> "$minutes min"
    minutes % 60 == 0 -> "${minutes / 60} h"
    else -> "${minutes / 60} h ${minutes % 60}"
}

/** Quarter-hours above an hour, five minutes below it. Clamped to what
 *  `Thresholds` will accept, so the stepper can never build a rejected value. */
private fun stepGap(minutes: Int, up: Boolean): Int {
    val step = if (minutes >= 60) 15 else 5
    val moved = if (up) minutes + step else minutes - step
    return moved.coerceIn(1, 1440)
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
                modifier = Modifier.padding(horizontal = 10.dp).size(width = 76.dp, height = 20.dp),
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 16.sp),
            )
            StepButton("+", onUp)
        }
    }
}

@Composable
private fun StepButton(glyph: String, onClick: () -> Unit) = Box(
    Modifier
        .size(38.dp)
        .clip(CircleShape)
        .background(MaterialTheme.colorScheme.secondaryContainer)
        .clickable(onClick = onClick),
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

/** A chip that fills in when chosen, as `.cue-topic` does. */
@Composable
internal fun Pill(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
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
        .clickable(onClick = onClick)
        .padding(horizontal = 14.dp, vertical = 10.dp),
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
