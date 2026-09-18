package app.harbor.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

/**
 * The numbers behind the garden's three pictures.
 *
 * Worth testing precisely because nobody can check them by looking. A cluster
 * of flowers is persuasive whatever it is built on: if the counting is wrong
 * the picture is still pretty, and it is making a claim about somebody's week
 * that they have no way to audit. These are that audit.
 */
class GrowthTest {

    private val zone = ZoneOffset.UTC
    private val today: LocalDate = LocalDate.of(2026, 9, 19)
    private val mum: UUID = UUID.randomUUID()
    private val dad: UUID = UUID.randomUUID()

    private fun entry(
        who: UUID? = mum,
        kind: FlowerKind? = FlowerKind.GLAD_WE_TALKED,
        minutes: Int? = 1,
        daysAgo: Long = 0,
        resolution: Resolution = Resolution.CALLED,
    ) = LedgerEntry(
        id = UUID.randomUUID(),
        entryDate = today.minusDays(daysAgo),
        cueId = null,
        contactId = who,
        triggerSource = TriggerSource.MANUAL,
        thresholdSnapshot = Thresholds.SUGGESTED,
        resolution = resolution,
        // The model insists these two agree, which is the constraint doing
        // its job -- the fixture bends to it rather than the other way.
        proposedTime = if (resolution == Resolution.PROPOSED_LATER) {
            today.atTime(18, 0).toInstant(ZoneOffset.UTC)
        } else {
            null
        },
        feedbackPulse = null,
        callMinutes = minutes,
        feeling = null,
        flower = kind,
        topic = null,
        occurredAt = today.minusDays(daysAgo)
            .atTime(12, 0).toInstant(ZoneOffset.UTC),
    )

    private fun summarise(
        entries: List<LedgerEntry>,
        span: Growth.Span = Growth.Span.WEEK,
    ) = Growth.summarise(entries, span, today, zone)

    // --- nothing ----------------------------------------------------------

    @Test
    fun `an empty ledger is empty, and has no range to name`() {
        val summary = summarise(emptyList())
        assertTrue(summary.isEmpty)
        assertNull(summary.from)
        assertEquals(0, summary.flowers)
        assertEquals(emptyList<Growth.Person>(), summary.people)
    }

    @Test
    fun `only calls that grew something count`() {
        // A dismissal is not a smaller flower, it is not a flower. The
        // garden records what happened and has nothing to say about what did
        // not, which is the whole reason it cannot be failed -- and a
        // "you dismissed four" number here would undo that in one line.
        val summary = summarise(
            listOf(
                entry(resolution = Resolution.DISMISSED, kind = null),
                entry(resolution = Resolution.PROPOSED_LATER, kind = null),
                entry(resolution = Resolution.MESSAGE, kind = null),
                entry(resolution = Resolution.CALLED, kind = null),
            ),
        )
        assertTrue(summary.isEmpty)
    }

    @Test
    fun `a call whose contact has been deleted is left out rather than shown as nobody`() {
        val summary = summarise(listOf(entry(who = null), entry(who = mum)))
        assertEquals(1, summary.people.size)
        assertEquals(mum, summary.people.first().contactId)
    }

    // --- the two units ----------------------------------------------------

    @Test
    fun `flowers are minutes and calls are calls`() {
        // The distinction the whole panel rests on. One twenty-minute call
        // grows twenty flowers and is one call; the cards that show size use
        // the first and the card that shows petals uses the second.
        val summary = summarise(listOf(entry(minutes = 20)))
        assertEquals(20, summary.flowers)
        assertEquals(1, summary.calls)
        assertEquals(20, summary.people.first().flowers)
        assertEquals(1, summary.people.first().calls)
        assertEquals(1, summary.people.first().petals.size)
    }

    @Test
    fun `a call with no duration still grew something`() {
        val summary = summarise(listOf(entry(minutes = null)))
        assertEquals(1, summary.flowers)
    }

    @Test
    fun `a mis-tapped marathon cannot flood the picture`() {
        // Flowers.flowerCount caps at 180. Without it one bad duration would
        // own every chart on the screen for a month.
        val summary = summarise(listOf(entry(minutes = 100_000)))
        assertEquals(180, summary.flowers)
    }

    // --- the window -------------------------------------------------------

    @Test
    fun `a week is the last seven days, today included`() {
        val summary = summarise(
            listOf(
                entry(daysAgo = 0),
                entry(daysAgo = 6),
                entry(daysAgo = 7),
            ),
        )
        assertEquals(2, summary.calls)
    }

    @Test
    fun `a month reaches further and all time reaches everything`() {
        val old = listOf(entry(daysAgo = 3), entry(daysAgo = 20), entry(daysAgo = 300))
        assertEquals(1, summarise(old, Growth.Span.WEEK).calls)
        assertEquals(2, summarise(old, Growth.Span.MONTH).calls)
        assertEquals(3, summarise(old, Growth.Span.ALL).calls)
    }

    @Test
    fun `the range is the days something happened, not the window's edges`() {
        // A week with one call on Friday reads "Friday to Friday". Drawing
        // it back to Sunday would claim six days of nothing as part of the
        // picture, which is the small lie that makes a chart feel fuller
        // than the life behind it.
        val summary = summarise(listOf(entry(daysAgo = 4), entry(daysAgo = 1)))
        assertEquals(today.minusDays(4), summary.from)
        assertEquals(today.minusDays(1), summary.to)
    }

    @Test
    fun `with nothing in the span the range collapses rather than inventing one`() {
        val summary = summarise(listOf(entry(daysAgo = 200)))
        assertNull(summary.from)
        assertEquals(today, summary.to)
    }

    // --- the ordering the pictures rely on --------------------------------

    @Test
    fun `people come back most grown first`() {
        val summary = summarise(
            listOf(
                entry(who = mum, minutes = 5),
                entry(who = dad, minutes = 30),
            ),
        )
        assertEquals(listOf(dad, mum), summary.people.map { it.contactId })
    }

    @Test
    fun `kinds come back largest first, and ties do not reshuffle`() {
        // Stability matters more than it looks. These are drawn in this
        // order -- biggest seat first, dominant colour at the centre of the
        // disc -- so an unstable sort would rearrange somebody's week every
        // time they opened the screen, with nothing having changed.
        val a = FlowerKind.GLAD_WE_TALKED
        val b = FlowerKind.FELT_LOVED
        val c = FlowerKind.EASY_SILENCE
        val entries = listOf(
            entry(kind = c, minutes = 4),
            entry(kind = a, minutes = 4),
            entry(kind = b, minutes = 9),
        )
        val once = summarise(entries).everything
        val again = summarise(entries.reversed()).everything
        assertEquals(b, once.first().kind)
        assertEquals(once, again)
        // a before c on a tie, because a is earlier in the library.
        assertEquals(listOf(b, a, c), once.map { it.kind })
    }

    @Test
    fun `petals are one per call, in the order the calls happened`() {
        // The petal list is the person's bloom. One petal per call is what
        // makes "petal count and colour are the whole breakdown" true, and
        // the order is what keeps the shape stable as calls are added rather
        // than recolouring the whole flower each time.
        val first = FlowerKind.FELT_LOVED
        val second = FlowerKind.EASY_SILENCE
        val summary = summarise(
            listOf(
                entry(kind = second, minutes = 30, daysAgo = 1),
                entry(kind = first, minutes = 2, daysAgo = 5),
            ),
        )
        assertEquals(listOf(first, second), summary.people.first().petals)
    }

    @Test
    fun `a person's flowers are the sum of their kinds`() {
        val summary = summarise(
            listOf(
                entry(who = mum, kind = FlowerKind.FELT_LOVED, minutes = 12),
                entry(who = mum, kind = FlowerKind.FELT_LOVED, minutes = 3),
                entry(who = mum, kind = FlowerKind.EASY_SILENCE, minutes = 6),
            ),
        )
        val person = summary.people.single()
        assertEquals(21, person.flowers)
        assertEquals(person.flowers, person.blooms.sumOf { it.flowers })
        assertEquals(15, person.blooms.first().flowers)
        assertEquals(3, person.calls)
    }

    @Test
    fun `the totals are the sum of the people`() {
        // The disc and the per-person pages are drawn from different fields
        // and have to agree, or two cards on one screen disagree about the
        // same week.
        val summary = summarise(
            listOf(
                entry(who = mum, minutes = 7),
                entry(who = dad, minutes = 11, kind = FlowerKind.EASY_SILENCE),
                entry(who = dad, minutes = 2),
            ),
        )
        assertEquals(summary.flowers, summary.people.sumOf { it.flowers })
        assertEquals(summary.calls, summary.people.sumOf { it.calls })
        assertEquals(summary.flowers, summary.everything.sumOf { it.flowers })
    }

    @Test
    fun `a day either side of midnight lands in the right span`() {
        // Dates come from the instant in the reader's zone, not from
        // entryDate, so a call at 23:50 does not drift a day when the ledger
        // is read somewhere else.
        val late = entry(daysAgo = 6).copy(
            occurredAt = today.minusDays(6).atTime(23, 50).toInstant(ZoneOffset.UTC),
        )
        val justOut = entry().copy(
            occurredAt = today.minusDays(7).atTime(0, 5).toInstant(ZoneOffset.UTC),
        )
        val summary = summarise(listOf(late, justOut))
        assertEquals(1, summary.calls)
        assertEquals(today.minusDays(6), summary.from)
    }

    @Test
    fun `a long ledger is summarised without losing anybody`() {
        val entries = (0 until 40).map {
            entry(
                who = if (it % 3 == 0) dad else mum,
                kind = FlowerKind.entries[it % FlowerKind.entries.size],
                minutes = (it % 7) + 1,
                daysAgo = (it % 5).toLong(),
            )
        }
        val summary = summarise(entries)
        assertEquals(2, summary.people.size)
        assertEquals(40, summary.calls)
        assertEquals(summary.flowers, summary.people.sumOf { it.flowers })
        assertEquals(
            entries.sumOf { Flowers.flowerCount(it.callMinutes) },
            summary.flowers,
        )
    }

    @Test
    fun `summarising is not sensitive to the order entries arrive in`() {
        val entries = (0 until 12).map {
            entry(
                who = if (it % 2 == 0) mum else dad,
                kind = FlowerKind.entries[it % 5],
                minutes = it + 1,
                daysAgo = (it % 4).toLong(),
            )
        }
        val forwards = summarise(entries)
        val backwards = summarise(entries.reversed())
        assertEquals(forwards.flowers, backwards.flowers)
        assertEquals(
            forwards.people.map { it.contactId to it.flowers },
            backwards.people.map { it.contactId to it.flowers },
        )
        assertEquals(forwards.everything, backwards.everything)
    }

    @Test
    fun `the span is carried through, so the card can say which it is showing`() {
        assertEquals(
            Growth.Span.MONTH,
            summarise(listOf(entry()), Growth.Span.MONTH).span,
        )
        assertEquals("All time", Growth.Span.ALL.label)
    }

    @Test
    fun `an instant in the future does not break the window`() {
        // Clocks get corrected backwards. A call stamped tomorrow should
        // still be inside "the last seven days" rather than falling out of
        // every span and vanishing from the garden.
        val ahead = entry().copy(occurredAt = Instant.now().plus(Duration.ofDays(1)))
        assertEquals(1, Growth.summarise(listOf(ahead), Growth.Span.WEEK, LocalDate.now(), zone).calls)
    }
}
