package app.harbor.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import app.harbor.R
import app.harbor.domain.StudyArm
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.harbor.domain.FlowerKind
import app.harbor.domain.Tone
import app.harbor.ui.theme.CardEdge
import app.harbor.ui.theme.Eyebrow
import app.harbor.ui.theme.Hairline
import app.harbor.ui.theme.SmallCopy
import app.harbor.ui.theme.mark

/**
 * A person, shown the way the specimen sheet shows a flower.
 *
 * This is the signature of the design and the thing a reskin of colour alone
 * cannot produce: a single stem standing under a thin glass arch, on a frosted
 * plinth that names it. It is why the sheet reads as a botanical catalogue
 * rather than a list of contacts, and it is what a person's patch looks like
 * on home now.
 *
 * The arch is drawn, not filled — one hairline, rounded hard at the top and
 * square at the foot, so the glass is implied rather than rendered. Everything
 * inside it is illustration and carries the only saturated colour on the page.
 *
 * An empty arch is deliberate: somebody with no calls yet gets the glass and
 * the label and nothing growing in it, which says "not yet" far better than an
 * empty state sentence would.
 */

/** The glass: a tall arch standing on a square foot. */
private val ArchShape = RoundedCornerShape(
    topStart = 46.dp,
    topEnd = 46.dp,
    bottomEnd = 4.dp,
    bottomStart = 4.dp,
)

/** The label the specimen stands on. */
private val PlinthShape = RoundedCornerShape(5.dp)

/** The little petal mark that carries a person's tone. */
private val MarkShape = RoundedCornerShape(
    topStart = 3.dp,
    topEnd = 3.dp,
    bottomEnd = 6.dp,
    bottomStart = 6.dp,
)

@Composable
fun Specimen(
    name: String,
    caption: String,
    tone: Tone,
    flower: FlowerKind?,
    modifier: Modifier = Modifier,
    archHeight: Int = 150,
) {
    Column(modifier) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(archHeight.dp)
                .padding(horizontal = 4.dp)
                .border(1.dp, Hairline, ArchShape),
        ) {
            if (flower != null) {
                Image(
                    painter = painterResource(plantOf(flower)),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    alignment = Alignment.BottomCenter,
                    contentScale = ContentScale.Fit,
                )
            } else {
                // An empty arch is a third of the screen of nothing, and it is
                // the most prominent thing on home for somebody who has just
                // finished onboarding -- the one moment the app has to say what
                // it is for. A bordered void says only that something failed to
                // load.
                //
                // So the frame holds the invitation instead. Not an error and
                // not a placeholder: the true sentence about what happens next,
                // in the shape the flower will eventually fill.
                Text(
                    "Their first flower\nopens after your\nfirst call",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 14.dp),
                )
            }

            // The bee keeping them company, in the bees arm only.
            //
            // Added beside whatever the arch already holds rather than
            // replacing it. The twenty flowers are the reward this app is
            // built around, and one mascot standing in for all of them would
            // be a smaller product rather than a different metaphor -- so the
            // flower still grows, and the bee is what is new.
            //
            // Bottom left, because the flower is drawn bottom *centre* and the
            // caption sits under the whole arch: the one corner where a 44dp
            // figure lands on neither.
            if (LocalStudyArm.current == StudyArm.BEES) {
                Image(
                    painter = painterResource(R.drawable.bee_standing),
                    contentDescription = null,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 6.dp, bottom = 4.dp)
                        .size(44.dp),
                )
            }
        }

        Column(
            Modifier
                .fillMaxWidth()
                .clip(PlinthShape)
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, CardEdge, PlinthShape)
                .padding(horizontal = 9.dp, vertical = 7.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    name,
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 14.sp),
                )
                Box(Modifier.size(11.dp).clip(MarkShape).background(tone.mark))
            }
            // Sentence case, not the sheet's small caps.
            //
            // The flower sheet does set this line in tracked capitals, and it
            // is the one place in the whole design that does. Everywhere else
            // -- and in the reference the language comes from -- a quiet line
            // is just a quiet line, so it is one here too.
            Text(
                caption,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        }
    }
}

/*
 * The stem and the two leaves used to be drawn here, under a flower head that
 * was also drawn. Both are in the artwork now -- each file is a whole plant --
 * so drawing a second stem under it would have given every specimen two.
 */

/** The card's one action, as the sheet draws it: an ink pill. */
private val ActionPill = RoundedCornerShape(99.dp)

/** The window card's corner. Same 10dp every card in the sheet uses. */
private val WindowShape = RoundedCornerShape(10.dp)

/**
 * A stretch of free time, set large, with a bloom leaning in from the corner.
 *
 * This is the best card in the specimen sheet and the reason the schedule
 * screen has anything to look at. The flower runs off the bottom-right edge
 * and is clipped by the card, which is what stops it reading as an icon.
 *
 * ## The copy is doing careful work
 *
 * The sheet calls this "a little window, together" and shows two people's
 * evenings overlapping. Harbor cannot say that. The parent installs nothing
 * and is never contacted (ADR-007), so there is no second calendar anywhere in
 * this app, and a card that implied one would be claiming a capability the
 * product has deliberately refused. What Harbor knows is *your* free time,
 * from blocks you typed in yourself — so the headline is yours alone, and the
 * caller passes copy that stays on that side of the line. See [app.harbor.domain.Windows].
 */
@Composable
fun LittleWindow(
    headline: String,
    caption: String,
    flower: FlowerKind,
    modifier: Modifier = Modifier,
    action: String? = null,
    /**
     * What the card is made of, for screens that are not on the specimen's
     * bone ground.
     *
     * The default is frosted white over bone, which is invisible on white:
     * the schedule now runs on the onboarding flow's white, so it passes the
     * flow's own warm tile instead. One parameter rather than a second card.
     */
    container: Color = Color.Unspecified,
    edge: Color = Color.Unspecified,
    onAction: (() -> Unit)? = null,
) {
    Box(
        modifier
            .fillMaxWidth()
            .clip(WindowShape)
            .background(container.takeOrElse { MaterialTheme.colorScheme.surface })
            .border(1.dp, edge.takeOrElse { CardEdge }, WindowShape),
    ) {
        Image(
            painter = painterResource(plantOf(flower)),
            contentDescription = null,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(132.dp)
                .offset(x = 20.dp, y = 18.dp)
                .alpha(0.9f),
            alignment = Alignment.BottomCenter,
            contentScale = ContentScale.Fit,
        )

        Column(
            Modifier
                .fillMaxWidth(0.74f)
                .padding(start = 18.dp, top = 20.dp, end = 12.dp, bottom = 18.dp),
        ) {
            Eyebrow("A little window")
            Spacer(Modifier.size(10.dp))
            Text(
                headline,
                style = MaterialTheme.typography.headlineMedium.copy(fontSize = 27.sp),
            )
            Spacer(Modifier.size(5.dp))
            SmallCopy(caption, size = 12)

            // The sheet ends this card with a pill reading "Make a little
            // plan". Harbor has no planning flow for that to open, so the
            // pill dials instead -- and therefore says so. A button that
            // hands you to the dialer must not describe itself as anything
            // gentler than that.
            if (action != null && onAction != null) {
                Spacer(Modifier.size(14.dp))
                Box(
                    Modifier
                        .clip(ActionPill)
                        .background(MaterialTheme.colorScheme.primary)
                        .clickable(onClick = onAction)
                        .padding(horizontal = 15.dp, vertical = 9.dp),
                ) {
                    Text(
                        action,
                        maxLines = 1,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        ),
                    )
                }
            }
        }
    }
}
