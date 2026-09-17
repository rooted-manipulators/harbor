package app.harbor.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalTime
import java.util.UUID

/**
 * What leaves the phone, and who is allowed to see it.
 *
 * The most important tests in this repo now. Everything else here is a feature
 * that could be wrong; this is a promise that could be broken, and the app made
 * it to people before it had a network at all.
 */
class SharingTest {

    private val me = UUID.fromString("11111111-1111-1111-1111-111111111111")
    private val aunt = UUID.fromString("22222222-2222-2222-2222-222222222222")
    private val stranger = UUID.fromString("33333333-3333-3333-3333-333333333333")

    private fun link(
        requester: UUID,
        addressee: UUID,
        state: Sharing.LinkState,
    ) = Sharing.Link(UUID.randomUUID(), requester, addressee, state)

    private val therapy = WeekBlock(
        day = DayOfWeek.TUESDAY,
        start = LocalTime.of(14, 0),
        end = LocalTime.of(16, 0),
        kind = BlockKind.BUSY,
        label = "Therapy",
    )

    // --- what goes -----------------------------------------------------------

    @Test
    fun `a label never leaves the device`() {
        // The whole reason SharedBlock is its own type. If this test can be
        // made to fail by a change, that change is sending somebody's therapy
        // appointment to a server.
        val wire = Sharing.forWire(listOf(therapy))
        assertEquals(1, wire.size)
        val sent = wire.single()
        assertFalse(
            "a label reached the wire: $sent",
            sent.toString().contains("Therapy", ignoreCase = true),
        )
    }

    @Test
    fun `the shape of the week does go`() {
        // The other half of the promise. Dropping the label is only honest if
        // what remains is genuinely useful -- otherwise this is a feature that
        // does not work, dressed up as a privacy win.
        val sent = Sharing.forWire(listOf(therapy)).single()
        assertEquals(DayOfWeek.TUESDAY.value, sent.day)
        assertEquals(LocalTime.of(14, 0), sent.start)
        assertEquals(LocalTime.of(16, 0), sent.end)
        assertEquals(BlockKind.BUSY, sent.kind)
    }

    @Test
    fun `a week that comes back has no label to show`() {
        val back = Sharing.fromWire(Sharing.forWire(listOf(therapy))).single()
        assertEquals(null, back.label)
        assertEquals(DayOfWeek.TUESDAY, back.day)
        assertEquals(BlockKind.BUSY, back.kind)
    }

    @Test
    fun `a day number survives the round trip on every day of the week`() {
        // ISO-8601 both ends, so Monday is 1 in Kotlin and 1 in the column.
        // An off-by-one here moves somebody's whole week by a day and nothing
        // would look broken enough to notice.
        DayOfWeek.entries.forEach { day ->
            val block = WeekBlock(day, LocalTime.of(9, 0), LocalTime.of(10, 0), BlockKind.FREE)
            assertEquals(day, Sharing.fromWire(Sharing.forWire(listOf(block))).single().day)
        }
    }

    // --- who may look --------------------------------------------------------

    @Test
    fun `nobody sees anything without being told yes`() {
        assertFalse(Sharing.maySee(emptyList(), me, aunt))
    }

    @Test
    fun `a pending ask shows nothing`() {
        // Adding somebody's number must never be the same thing as being let in.
        val links = listOf(link(me, aunt, Sharing.LinkState.PENDING))
        assertFalse(Sharing.maySee(links, me, aunt))
    }

    @Test
    fun `a yes lets me look`() {
        val links = listOf(link(me, aunt, Sharing.LinkState.ACCEPTED))
        assertTrue(Sharing.maySee(links, me, aunt))
    }

    @Test
    fun `taking it back ends it, and so does a no`() {
        listOf(Sharing.LinkState.REVOKED, Sharing.LinkState.DECLINED).forEach { state ->
            assertFalse("$state still let someone look", Sharing.maySee(listOf(link(me, aunt, state)), me, aunt))
        }
    }

    @Test
    fun `being let in does not let me in both ways`() {
        // Directed, not mutual. My aunt showing me her Tuesdays does not put
        // mine in front of her, and one row that meant "connected" would have.
        val links = listOf(link(me, aunt, Sharing.LinkState.ACCEPTED))
        assertTrue(Sharing.maySee(links, me, aunt))
        assertFalse(Sharing.maySee(links, aunt, me))
    }

    @Test
    fun `somebody else's yes is not my yes`() {
        val links = listOf(link(stranger, aunt, Sharing.LinkState.ACCEPTED))
        assertFalse(Sharing.maySee(links, me, aunt))
    }

    // --- the two inboxes -----------------------------------------------------

    @Test
    fun `the asks i must answer are not the asks i have made`() {
        val links = listOf(
            link(aunt, me, Sharing.LinkState.PENDING),
            link(me, stranger, Sharing.LinkState.PENDING),
            link(me, aunt, Sharing.LinkState.ACCEPTED),
        )
        assertEquals(listOf(aunt), Sharing.waitingOn(links, me).map { it.requester })
        assertEquals(listOf(stranger), Sharing.asked(links, me).map { it.addressee })
    }
}
