package app.harbor.domain

import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

/**
 * What grew, counted: the numbers behind the garden's pictures.
 *
 * Pure, and separate from the drawing, because the pictures are the part
 * people will argue about and the counting is the part that can be wrong
 * without anybody noticing. A cluster that looks plausible and is built on a
 * miscount is worse than no cluster: it is a claim about somebody's week that
 * they have no way to check. So the claim lives here with tests on it, and
 * `ui/GardenActivity.kt` only draws what this returns.
 *
 * ## The two units, and why there are two
 *
 * A **flower** is what the field grows: one a minute, from
 * [Flowers.flowerCount], bounded so a mis-tapped three-hour call cannot flood
 * a patch. It is the garden's own currency and it is what "most grown" and
 * "everything you grew" are counted in, because both of those are about the
 * garden.
 *
 * A **call** is one conversation, however long. It is what a petal stands for
 * on a person's bloom, because a petal is a thing you can count by looking and
 * nobody can count sixty of them. "We spoke four times" is also the sentence
 * a person would say; "we grew fifty-one flowers" is not.
 *
 * Mixing the two silently would be a trap, so every card that shows one says
 * which it is showing.
 */
object Growth {

    /** How far back a panel looks. */
    enum class Span {
        /** The last seven days, today included. */
        WEEK,

        /** The last thirty. Not a calendar month: see [from]. */
        MONTH,

        /** Everything the ledger still holds. */
        ALL,
        ;

        val label: String
            get() = when (this) {
                WEEK -> "Weekly"
                MONTH -> "Monthly"
                ALL -> "All time"
            }
    }

    /** One kind of flower, and how much of it. */
    data class Bloom(val kind: FlowerKind, val flowers: Int)

    /**
     * One person's growth over the span.
     *
     * [blooms] is ordered by how much grew, largest first, with ties broken
     * by the library's own order so the picture is stable between openings —
     * a chart that reshuffles itself on every visit reads as noise even when
     * the numbers have not moved.
     */
    data class Person(
        val contactId: UUID,
        val blooms: List<Bloom>,
        /** Flowers, summed across kinds. */
        val flowers: Int,
        /** Conversations. One petal each on this person's bloom. */
        val calls: Int,
        /**
         * The flower of each call, oldest first — one entry per call, which
         * is what makes this the petal list rather than a summary of one.
         */
        val petals: List<FlowerKind>,
    )

    /** Everything the activity panel needs, for one span. */
    data class Summary(
        val span: Span,
        /** Null when nothing grew at all, so there is no range to name. */
        val from: LocalDate?,
        val to: LocalDate,
        /** People with something in this span, most grown first. */
        val people: List<Person>,
        /** Every kind that grew, largest first. */
        val everything: List<Bloom>,
        val flowers: Int,
        val calls: Int,
    ) {
        val isEmpty: Boolean get() = flowers == 0
    }

    /**
     * Count what grew.
     *
     * Only [Resolution.CALLED] entries carrying a flower count. A dismissal
     * is not a smaller flower, it is not a flower — the garden records calls
     * that happened and has nothing to say about the ones that did not, which
     * is the whole reason it cannot be failed.
     *
     * @param today passed in rather than read, so the window is testable.
     */
    fun summarise(
        entries: List<LedgerEntry>,
        span: Span,
        today: LocalDate,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Summary {
        val start = when (span) {
            Span.WEEK -> today.minusDays(6)
            Span.MONTH -> today.minusDays(29)
            Span.ALL -> null
        }

        val grown = entries
            .asSequence()
            .filter { it.resolution == Resolution.CALLED }
            .filter { it.flower != null && it.contactId != null }
            .filter { start == null || !dateOf(it, zone).isBefore(start) }
            .sortedBy { it.occurredAt }
            .toList()

        val people = grown
            .groupBy { it.contactId!! }
            .map { (id, theirs) -> person(id, theirs) }
            .sortedWith(compareByDescending<Person> { it.flowers }.thenByDescending { it.calls })

        val everything = grown
            .groupBy { it.flower!! }
            .map { (kind, of) -> Bloom(kind, of.sumOf { Flowers.flowerCount(it.callMinutes) }) }
            .sortedWith(compareByDescending<Bloom> { it.flowers }.thenBy { it.kind.ordinal })

        return Summary(
            span = span,
            // The first day something actually grew, not the window's edge.
            // A week with one call on Friday is honestly "Friday to Friday";
            // drawing the range back to Sunday would be claiming six days of
            // nothing as part of the picture.
            from = grown.firstOrNull()?.let { dateOf(it, zone) },
            to = grown.lastOrNull()?.let { dateOf(it, zone) } ?: today,
            people = people,
            everything = everything,
            flowers = everything.sumOf { it.flowers },
            calls = grown.size,
        )
    }

    private fun person(id: UUID, theirs: List<LedgerEntry>): Person {
        val blooms = theirs
            .groupBy { it.flower!! }
            .map { (kind, of) -> Bloom(kind, of.sumOf { Flowers.flowerCount(it.callMinutes) }) }
            .sortedWith(compareByDescending<Bloom> { it.flowers }.thenBy { it.kind.ordinal })
        return Person(
            contactId = id,
            blooms = blooms,
            flowers = blooms.sumOf { it.flowers },
            calls = theirs.size,
            petals = theirs.map { it.flower!! },
        )
    }

    private fun dateOf(entry: LedgerEntry, zone: ZoneId): LocalDate =
        entry.occurredAt.atZone(zone).toLocalDate()
}
