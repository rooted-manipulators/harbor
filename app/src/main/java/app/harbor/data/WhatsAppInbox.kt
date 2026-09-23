package app.harbor.data

import app.harbor.domain.BlockOrigin
import app.harbor.domain.Moment
import app.harbor.domain.Sharing
import app.harbor.domain.WeekBlock
import app.harbor.domain.Windows

/**
 * Taking the week the bot read out of a forwarded message and putting it on
 * the grid.
 *
 * The server end of this is `backend/supabase/functions/whatsapp`. It parses
 * and proposes; this is where a proposal becomes part of somebody's week.
 *
 * ## Why the phone places them and the server does not
 *
 * The device is authoritative for its own week (ADR-003, and `putWeek`
 * replaces rather than merges for the same reason). A server writing into
 * `week_blocks` would be overwritten by the next upload, and worse, would be
 * a second author of a thing that has exactly one. So the bot fills a queue
 * and the phone drains it, placing each block through the same
 * [Windows.place] a finger goes through, which does not know or care who is
 * calling it. [app.harbor.domain.BlockOrigin] is what lets the schedule
 * screen still tell the two apart afterwards, for the one thing that turned
 * out to matter: which colour to draw the block in.
 *
 * ## It is allowed to do nothing
 *
 * Signed out, no backend, no network, nothing waiting: all of those return 0
 * and change nothing. Nothing in the cue pipeline waits on this.
 */
internal object WhatsAppInbox {

    /**
     * Place everything waiting, and return how many blocks landed.
     *
     * Ordered: the local week is saved *before* the rows are cleared, so a
     * drain interrupted between the two repeats itself rather than losing a
     * block. Placing twice is harmless — [Windows.place] clears what it lands
     * on, so the same block placed again is the same week.
     */
    suspend fun drain(client: SupabaseClient, store: HarborRepository): Int {
        if (!client.configured || !client.signedIn) return 0

        val arrived = client.inbox() ?: return 0
        if (arrived.isEmpty()) return 0

        val placed: List<WeekBlock> = Sharing.fromWire(arrived.map { it.block })
            .map { it.copy(origin = BlockOrigin.WHATSAPP) }
        var week = store.weekBlocks.value
        placed.forEach { week = Windows.place(week, it) }
        store.setWeekBlocks(week)

        // Same beat as a hand-drawn edit, with a detail that says where it
        // came from. The study wants to know whether the bot is what people
        // actually used; it does not want their messages, and does not get
        // them — this is a category and a count.
        store.note(Moment.WEEK_EDITED, "whatsapp", placed.size)

        client.clearInbox(arrived.map { it.id })
        return placed.size
    }
}
