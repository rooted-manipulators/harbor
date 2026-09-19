package app.harbor.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.unit.dp
import app.harbor.data.HarborRepository
import app.harbor.domain.StudyArm
import app.harbor.ui.theme.PageIntro
import app.harbor.ui.theme.SectionHeading
import app.harbor.ui.theme.SmallCopy
import app.harbor.ui.theme.Surface
import kotlinx.coroutines.launch

/**
 * Changing the study code, which means starting the install again.
 *
 * ## Why this is not a field you edit
 *
 * A code names an arm, and an arm cannot be changed on an install that has
 * already written rows under the old one — see `HarborRepository.startOver`.
 * Offering a quiet "update code" would produce a file with garden behaviour
 * and bee behaviour in it and one arm at the top claiming both, which nobody
 * would catch until the study was over.
 *
 * So this screen does the honest version: it says plainly what it destroys,
 * and the button is worded as the thing that actually happens rather than as
 * the thing you wanted. It exists for a code typed wrong while setting a phone
 * up, which is the case where there is nothing to lose anyway.
 */
@Composable
fun StudyCodeScreen(
    store: HarborRepository,
    onStartOver: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var code by remember { mutableStateOf("") }
    var current by remember { mutableStateOf<String?>(null) }
    var arm by remember { mutableStateOf(StudyArm.GARDEN) }

    LaunchedEffect(Unit) {
        current = store.studyCode()
        arm = store.arm()
    }

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
    ) {
        PageIntro(
            eyebrow = "For the study team",
            title = "Study code.",
            subtitle = "Set once, when this phone was handed over. If you are " +
                "using Harbor rather than running the study, nothing on this " +
                "screen is for you — go back.",
        )

        Spacer(Modifier.height(20.dp))

        Surface {
            SectionHeading("What this phone is on")
            // The raw code as typed, and the arm it put this install in. Both,
            // because the code alone does not say what it did and the arm
            // alone cannot be matched to the sheet it came from.
            Text(
                current.takeIf { !it.isNullOrBlank() } ?: "no code",
                style = androidx.compose.material3.MaterialTheme.typography.titleLarge,
            )
            SmallCopy("Version " + arm.wire + ".")
        }

        Spacer(Modifier.height(24.dp))

        Surface {
            SectionHeading("Hand this phone to someone else")
            SmallCopy(
                "Type the next code and this phone becomes a new one: the " +
                    "person, the week, every call and flower, all gone, back " +
                    "to the first screen. There is nothing to undo it with, " +
                    "and it is the only way the version changes.",
            )
            Spacer(Modifier.height(8.dp))
            // Said out loud because the field accepts anything.
            //
            // A code is only ever read for its first letter, so a typo does
            // not fail -- it silently assigns the other version and wipes a
            // week on the way. The button being dead until something is
            // typed is the only guard there is, and it is worth saying that
            // whatever gets typed is what the phone becomes.
            SmallCopy(
                "Whatever you type is taken as the code. Check it against " +
                    "your sheet before pressing.",
                size = 12,
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = code,
                onValueChange = { code = it.take(12) },
                label = { Text("New code") },
                modifier = Modifier.fillMaxSize(),
            )
            Spacer(Modifier.height(14.dp))
            // Worded as what happens, not as what was wanted. "Save" on a
            // button that wipes a week is how somebody loses one.
            Pill(
                text = "Erase and start again",
                selected = code.isNotBlank(),
                enabled = code.isNotBlank(),
            ) {
                scope.launch {
                    store.startOver(code)
                    onStartOver()
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        TextLink("Back", onDone)
        Spacer(Modifier.height(40.dp))
    }
}
