package app.harbor.ui

import androidx.compose.ui.graphics.Color
import app.harbor.domain.Weather

/**
 * The daylight palette, ported from the web prototype's `lib/harbor/scene.ts`.
 *
 * Harbor's field was drawn as a dark void with dark-green land on it. The
 * prototype draws the same terrain as a lit meadow: pale sky, sun, white
 * cloud, green ground. This is that palette, one entry per [Weather], carried
 * across so the two can be compared honestly rather than from memory.
 *
 * ## What changes and what does not
 *
 * Only colour. The projection, the plots, the tufts that stand up as the lens
 * tilts in, the flowers and the way they fade into the page are all untouched
 * -- the grass already only grows when you lean in, which is the behaviour the
 * reference has and Harbor already had.
 *
 * ## The one thing that does not port cleanly
 *
 * The prototype paints this onto a white page. Harbor paints it onto
 * [app.harbor.ui.theme.Paper], which is near-black, and `Field.VEG`'s current
 * values were tuned for exactly that -- its comment records the luminance
 * ladder it hits against both the page and the old sky, and notes the two
 * cannot both be strong.
 *
 * Lighting the meadow inverts that problem rather than solving it: the land
 * becomes the brightest thing on the screen, and the cards below it stay dark.
 * That is the look the reference has and it is the look that was asked for,
 * but it means the horizon is now a bright-to-dark seam rather than a
 * dark-to-dark one, and [haze] is what has to carry it. Worth looking at on a
 * phone before anybody calls it done.
 */
internal data class Meadow(
    /** Overhead, midway, and at the horizon. */
    val sky: Triple<Color, Color, Color>,
    val sun: Color,
    val sunGlow: Color,
    val cloud: Color,
    val cloudCount: Int,
    /** Far hills, near to far. */
    val hills: List<Color>,
    /** The ground in light, lightest first. */
    val field: List<Color>,
    /** The ground in shadow, lightest first. */
    val fieldDeep: List<Color>,
    val water: Color,
    /** What distance fades into. The horizon seam lives or dies on this. */
    val haze: Color,
    /** Overall exposure, 1.0 being a plain clear day. */
    val light: Float,
)

/**
 * The five, as drawn. Values are the prototype's own, unretuned on purpose:
 * changing them in the same pass that ports them would make it impossible to
 * tell a porting mistake from a taste decision.
 */
internal fun meadowFor(weather: Weather): Meadow = when (weather) {
    Weather.CLEAR -> Meadow(
        sky = Triple(Color(0xFF8EC9EC), Color(0xFFB9DFF1), Color(0xFFE4F1F0)),
        sun = Color(0xFFFFE9A8), sunGlow = Color(0xFFFFE096).copy(alpha = 0.55f),
        cloud = Color.White.copy(alpha = 0.92f), cloudCount = 5,
        hills = listOf(Color(0xFFBFD9C4), Color(0xFFA6CCA4), Color(0xFF8FBE86), Color(0xFF7BAE6E)),
        field = listOf(Color(0xFF9CC77E), Color(0xFF8ABB6C), Color(0xFF7BAE60)),
        fieldDeep = listOf(Color(0xFF7FAE64), Color(0xFF6E9F55), Color(0xFF5F914B)),
        water = Color(0xFFB7DCE8), haze = Color(0xFFF0F8EC), light = 1.00f,
    )
    Weather.BRIGHT -> Meadow(
        sky = Triple(Color(0xFF7CC0E8), Color(0xFFAEDAF0), Color(0xFFEAF4E8)),
        sun = Color(0xFFFFDE8A), sunGlow = Color(0xFFFFD678).copy(alpha = 0.62f),
        cloud = Color.White.copy(alpha = 0.85f), cloudCount = 3,
        hills = listOf(Color(0xFFC7DCB8), Color(0xFFAFD096), Color(0xFF98C27C), Color(0xFF85B369)),
        field = listOf(Color(0xFFA6CE80), Color(0xFF93C26A), Color(0xFF83B65D)),
        fieldDeep = listOf(Color(0xFF88B567), Color(0xFF77A857), Color(0xFF699C4C)),
        water = Color(0xFFAFD9E8), haze = Color(0xFFFAF7E6), light = 1.05f,
    )
    Weather.CLOUDY -> Meadow(
        sky = Triple(Color(0xFFA9BCC8), Color(0xFFC4D3D9), Color(0xFFDFE6E0)),
        sun = Color(0xFFF2EBD8), sunGlow = Color(0xFFECE8D6).copy(alpha = 0.40f),
        cloud = Color(0xFFFCFCFA).copy(alpha = 0.95f), cloudCount = 6,
        hills = listOf(Color(0xFFB3C2B0), Color(0xFF9DB295), Color(0xFF89A37F), Color(0xFF78946D)),
        field = listOf(Color(0xFF93AE7E), Color(0xFF84A36D), Color(0xFF769863)),
        fieldDeep = listOf(Color(0xFF7C9868), Color(0xFF6E8D59), Color(0xFF62834F)),
        water = Color(0xFFAEC4CC), haze = Color(0xFFE2E8E2), light = 0.93f,
    )
    Weather.RAIN -> Meadow(
        sky = Triple(Color(0xFF8496A4), Color(0xFFA6B6BE), Color(0xFFC6D0C9)),
        sun = Color(0xFFDDE0DA), sunGlow = Color(0xFFDCE0D8).copy(alpha = 0.22f),
        cloud = Color(0xFFECF0F0).copy(alpha = 0.96f), cloudCount = 7,
        hills = listOf(Color(0xFF98A89A), Color(0xFF869A85), Color(0xFF748A72), Color(0xFF657C62)),
        field = listOf(Color(0xFF7E9770), Color(0xFF728C63), Color(0xFF668158)),
        fieldDeep = listOf(Color(0xFF6A8460), Color(0xFF5F7A53), Color(0xFF556F4A)),
        water = Color(0xFF98B2BC), haze = Color(0xFFCED8D4), light = 0.85f,
    )
    Weather.STORM -> Meadow(
        sky = Triple(Color(0xFF5E6C79), Color(0xFF7C8892), Color(0xFFA3ABA4)),
        sun = Color(0xFFC3C7C0), sunGlow = Color(0xFFBEC2BA).copy(alpha = 0.16f),
        cloud = Color(0xFFD6DCDE).copy(alpha = 0.96f), cloudCount = 8,
        hills = listOf(Color(0xFF7D8B80), Color(0xFF6C7C6E), Color(0xFF5C6D5D), Color(0xFF4E5F4F)),
        field = listOf(Color(0xFF657C58), Color(0xFF5B724E), Color(0xFF526845)),
        fieldDeep = listOf(Color(0xFF546A49), Color(0xFF4B6141), Color(0xFF43583A)),
        water = Color(0xFF7D939C), haze = Color(0xFFB2BCB8), light = 0.76f,
    )
}
