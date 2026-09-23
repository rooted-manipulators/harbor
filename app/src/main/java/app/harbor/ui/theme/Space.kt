package app.harbor.ui.theme

import androidx.compose.ui.unit.dp

/**
 * The eight-point grid everything measures against.
 *
 * Material's baseline grid, and the reason for it is not tidiness: a screen
 * built on one step reads as one screen. Harbor's did not. `pageContent` was
 * 24 across and 20 down, `Flow` gapped at 14, a card padded at 18, a quiet row
 * at 16 and 13 — five numbers that nothing chose, sitting next to each other,
 * and the eye reads the difference between 13 and 14 as a mistake without ever
 * being able to name it.
 *
 * Use these rather than a literal. A number that is not on this scale should
 * have a comment saying what bought the exception.
 *
 * ## The exceptions that already exist, and why they stay
 *
 * - **Touch targets are 48**, which is six steps and needs no exception.
 * - **A hairline is 1.** A rule is not a space.
 * - **Pill radii are 99** — a number meaning "however round it needs to be".
 * - **Drawn artwork is not on the grid at all.** The garden, the flowers and
 *   the day dial are measured in fractions of whatever they are drawn into.
 *   A grid is for the interface, not for a picture.
 */
object Space {
    /** Half a step, for the gap inside a chip. Use sparingly. */
    val half = 4.dp

    /** One step. The gap between a label and the thing it labels. */
    val one = 8.dp

    /** Two thirds of a card's padding, and the gap inside a row. */
    val oneHalf = 12.dp

    /** Two steps. A card's padding, and the rhythm down a page. */
    val two = 16.dp

    /** Three steps. The page's own margin. */
    val three = 24.dp

    /** Four steps. Between one section of a page and the next. */
    val four = 32.dp

    /** Six steps. The minimum anything may be pressed at. */
    val target = 48.dp
}
