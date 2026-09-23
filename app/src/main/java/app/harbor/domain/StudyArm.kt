package app.harbor.domain

/**
 * Which version of Harbor a participant is in.
 *
 * One build ships both. A device decides once, at first launch, and never
 * changes after — see `HarborStore.arm`. Not a setting, not a toggle, not a
 * build flavour: a participant who could move between arms is a participant
 * whose data cannot be attributed to either.
 *
 * ## Why this exists before any of the artwork
 *
 * The study compares two metaphors. If the arm is not recorded alongside the
 * behaviour, the export is two piles of results nobody can tell apart, and no
 * amount of care afterwards recovers it. So this lands first, invisible, while
 * the bees are still being drawn.
 *
 * ## What is deliberately *not* different between the arms
 *
 * The stored values. Both arms write the same five [Weather] values from the
 * same slider, the same [FlowerKind] labels, the same ledger shape. Only the
 * rendering and the words differ. That is what makes the two arms one table
 * and the comparison possible — an arm that invented its own scale would be
 * measuring itself.
 *
 * `CuePolicy` never reads this. When a reminder fires must be identical in
 * both arms, or a difference in calls could be the trigger rather than the
 * metaphor.
 */
enum class StudyArm {
    /** The field, and a slider that sets the weather. The control. */
    GARDEN,

    /**
     * The same field with bees in it. The slider asks the same question and
     * stores the same value; it moves a bee's face instead of the sky, and the
     * sky follows the clock.
     */
    BEES;

    /** Matches the wire names the export and Postgres use. */
    val wire: String get() = name.lowercase()

    companion object {
        /**
         * Read an arm back from stored or exported text.
         *
         * Unknown text is [GARDEN] rather than an exception: a row written by
         * a future build should not stop somebody opening this week's data.
         */
        fun of(wire: String?): StudyArm =
            entries.firstOrNull { it.wire == wire?.trim()?.lowercase() } ?: GARDEN

        /**
         * The arm a study code puts somebody in.
         *
         * **Deliberately not random.** Batches are assigned by whoever runs the
         * study, not by the phone — "this group gets the bees" is a decision
         * made on paper before anybody is handed a device, and a coin flip
         * inside the app would take it away and make the split unknowable
         * until the data came back.
         *
         * The rule is the first letter, so a code is readable by the person
         * typing it: `B-07` is the bees, anything else is the garden. Blank is
         * the garden, which is what a participant who skips the question gets,
         * and which is the arm that already exists.
         */
        fun fromCode(code: String?): StudyArm =
            if (code?.trim()?.firstOrNull()?.uppercaseChar() == 'B') BEES else GARDEN
    }
}
