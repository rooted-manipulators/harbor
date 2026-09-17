package app.harbor.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.harbor.BuildConfig
import app.harbor.data.SupabaseClient

/**
 * Forwarding a class group message instead of drawing the week (ADR-014).
 *
 * Sits under the grid, beside "Bring in a calendar", because it is the same
 * kind of offer: a way of not doing the thing the screen has just asked for.
 * It is drawn in the same bordered row, at the same weight, on purpose — the
 * grid is still the thing on this screen worth looking at.
 *
 * ## It disappears rather than explaining itself
 *
 * With no number configured there is no card. A build that has not been given
 * a WhatsApp number would otherwise advertise a bot that does not answer,
 * which is worse than the feature being absent. Same posture as
 * [SupabaseClient.configured]: not set up is a supported state, and it looks
 * like the app did before the feature existed.
 */
@Composable
internal fun ColumnScope.ForwardYourChats(
    client: SupabaseClient,
    /** Somewhere to sign in, because a code needs an account to belong to. */
    onOpenAccount: () -> Unit,
) {
    val number = BuildConfig.WHATSAPP_NUMBER
    if (number.isBlank() || !client.configured) return

    // Null while it is being fetched, and if it cannot be. Fetched rather than
    // held: the code lives on the server so that two devices signed into one
    // account see the same one, and it is small enough to ask for each time
    // this screen is opened.
    var code by remember { mutableStateOf<String?>(null) }
    val signedIn = client.signedIn

    LaunchedEffect(signedIn) {
        if (signedIn) code = client.whatsappCode()
    }

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .border(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                RoundedCornerShape(10.dp),
            )
            .then(if (signedIn) Modifier else Modifier.clickable(onClick = onOpenAccount))
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text(
            "Forward your class chats",
            style = MaterialTheme.typography.titleLarge.copy(
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        )
        Text(
            if (signedIn) {
                "Send anything from a class group to $number and the times in it " +
                    "appear here on their own."
            } else {
                "Sign in first — a code has to belong to an account."
            },
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        )

        if (signedIn) {
            Spacer(Modifier.height(10.dp))
            Text(
                code ?: "· · · · · ·",
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.titleLarge.copy(
                    fontSize = 22.sp,
                    letterSpacing = 6.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                ),
            )
            Text(
                "Message that once, from the phone you use WhatsApp on.",
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
            Spacer(Modifier.height(8.dp))
            // The one line on this card that is not an instruction, and the
            // reason it is here: the rest of this screen promises that what
            // you draw stays on the phone. A message you forward does not, and
            // the place to say so is where the offer is made, not in a policy
            // somewhere. See ADR-014.
            Text(
                "A forwarded message goes to WhatsApp and to Harbor's server, " +
                    "which reads the days and times out of it and keeps nothing else — " +
                    "not the message, not the subject, not the group.",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        }
    }
}
