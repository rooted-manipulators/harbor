package app.harbor.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.net.Uri
import app.harbor.data.SupabaseClient
import app.harbor.data.SyncJson
import app.harbor.ui.theme.Eyebrow
import app.harbor.ui.theme.Flow
import app.harbor.ui.theme.Notice
import app.harbor.ui.theme.PageIntro
import app.harbor.ui.theme.PrimaryAction
import app.harbor.ui.theme.QuietAction
import app.harbor.ui.theme.SmallCopy
import app.harbor.ui.theme.Surface
import app.harbor.ui.theme.pageContent
import kotlinx.coroutines.launch

/**
 * Signing in, so the people you call can be people who have Harbor too.
 *
 * ## This screen is a door, not a gate
 *
 * Harbor works signed out and always will. Sensing, the reminder, the ledger,
 * the garden and the study file are all on the device and none of them ask
 * anything of this screen — see ADR-013, which reverses the no-server decision
 * and keeps its reasoning as a constraint. Nothing routes here on launch and
 * nothing is withheld until somebody arrives.
 *
 * That is also why a build with no backend configured is a supported state
 * rather than a broken one: with no URL set this says so plainly instead of
 * showing three buttons that quietly do nothing.
 *
 * ## Three ways in, and the third is the one that always works
 *
 * Google and Microsoft are one tap and go through the browser — a password
 * field on a screen Harbor drew is the shape of a phishing page, and both
 * providers refuse to load in a WebView for exactly that reason.
 *
 * The code by email is not the consolation prize. Supabase has no Yahoo
 * provider and cannot be given one, so this is the route for Yahoo, for a
 * university address, and for anybody whose mail is not Google's or
 * Microsoft's. It needs no provider configured at all.
 */
@Composable
internal fun SignInScreen(
    client: SupabaseClient,
    /**
     * The redirect a provider came home on, or null. Consumed once: the
     * activity clears it after this screen has had it, so a rotation does not
     * replay a sign-in that already happened.
     */
    redirect: String?,
    onRedirectHandled: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var email by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var sent by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var trouble by remember { mutableStateOf<String?>(null) }
    var signedIn by remember { mutableStateOf(client.signedIn) }

    // The browser has come back. Finish the sign-in with what it carried.
    LaunchedEffect(redirect) {
        val came = redirect ?: return@LaunchedEffect
        busy = true
        trouble = if (client.completeSignIn(came)) {
            signedIn = true
            null
        } else {
            // A cancelled sign-in comes home on the same address as a
            // successful one, so this is the ordinary case of somebody
            // changing their mind, not an error to alarm them about.
            "That sign-in did not finish. Nothing has changed."
        }
        busy = false
        onRedirectHandled()
    }

    fun openProvider(provider: SyncJson.Provider) {
        val url = client.authorizeUrl(provider) ?: return
        trouble = try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            null
        } catch (_: ActivityNotFoundException) {
            "There is no browser on this phone to sign in with. The code by email works without one."
        }
    }

    Box(modifier.fillMaxSize()) {
        Flow(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .pageContent(),
        ) {
            PageIntro(
                title = if (signedIn) "You are signed in" else "Your account",
                subtitle = if (signedIn) {
                    "Harbor can ask the people you call whether you may see when they are free."
                } else {
                    "Only needed to share a week with somebody. Everything else in " +
                        "Harbor works without it."
                },
                eyebrow = "Account",
            )

            if (!client.configured) {
                // Nothing is wrong. There is simply no backend pointed at yet,
                // and saying so is better than three buttons that do nothing.
                Notice(
                    "This build has no server set up, so there is nothing to sign in to. " +
                        "Harbor works exactly as it does now without one.",
                )
                return@Flow
            }

            trouble?.let { Notice(it) }

            if (signedIn) {
                Surface {
                    SmallCopy(
                        "Signing out leaves everything on this phone where it is. " +
                            "Your garden, your week and your notes are not stored anywhere else.",
                    )
                    Spacer(Modifier.size(4.dp))
                    QuietAction("Sign out", enabled = !busy) {
                        client.signOut()
                        signedIn = false
                        sent = false
                        code = ""
                    }
                }
                Spacer(Modifier.size(8.dp))
                PrimaryAction("Done", onClick = onDone)
                return@Flow
            }

            Surface {
                Eyebrow("One tap")
                PrimaryAction("Continue with Google", enabled = !busy) {
                    openProvider(SyncJson.Provider.GOOGLE)
                }
                Spacer(Modifier.size(2.dp))
                QuietAction("Continue with Outlook", enabled = !busy) {
                    openProvider(SyncJson.Provider.MICROSOFT)
                }
                SmallCopy("Opens your browser, so your password is only ever typed on their page.")
            }

            Surface {
                Eyebrow("Any other address")
                SmallCopy(
                    "Yahoo, a university address, anything else. We send a six digit code.",
                )
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it; trouble = null },
                    label = { Text("Email") },
                    singleLine = true,
                    enabled = !sent && !busy,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth(),
                )

                if (sent) {
                    OutlinedTextField(
                        value = code,
                        onValueChange = { code = it.filter { c -> c.isDigit() }.take(8) },
                        label = { Text("The code we sent") },
                        singleLine = true,
                        enabled = !busy,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    PrimaryAction("Sign in", enabled = code.isNotBlank() && !busy) {
                        scope.launch {
                            busy = true
                            if (client.signIn(email, code)) {
                                signedIn = true
                                trouble = null
                            } else {
                                trouble = "That code did not work. It may have expired."
                            }
                            busy = false
                        }
                    }
                    QuietAction("Use a different address", enabled = !busy) {
                        sent = false
                        code = ""
                        trouble = null
                    }
                } else {
                    PrimaryAction(
                        "Email me a code",
                        enabled = email.contains("@") && email.contains(".") && !busy,
                    ) {
                        scope.launch {
                            busy = true
                            if (client.requestCode(email)) {
                                sent = true
                                trouble = null
                            } else {
                                trouble = "We could not send that. Check the address and try again."
                            }
                            busy = false
                        }
                    }
                }
            }

            Text(
                "Harbor never sees what is in your week. Only its shape goes up — " +
                    "when you are busy, not what you are doing.",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )

            QuietAction("Not now", enabled = !busy, onClick = onDone)
        }
    }
}
