package app.harbor.domain

import java.time.DayOfWeek
import java.time.LocalTime
import java.util.UUID

/**
 * What one person is allowed to see of another, and in what shape.
 *
 * Harbor got a server on 17 Sep so that the people you call could have the app
 * too — see ADR-013, which reverses three older decisions and is worth reading
 * before changing anything here. This file is the device's half of that: the
 * rules about sharing, written as arithmetic, so they can be tested without a
 * network and cannot drift away from the policies in
 * `backend/supabase/migrations/0011_people_and_sharing.sql`.
 *
 * Nothing here does IO. It decides what *may* go, and what shape it takes when
 * it does; the sending is somebody else's job.
 *
 * ## The one rule that matters more than the others
 *
 * A week goes out as its **shape** and never as its **content**.
 * [WeekBlock.label] is documented "Never leaves the device", and [forWire] is
 * the only door out — it copies four fields and cannot copy a fifth, because
 * [SharedBlock] has nowhere to put one. The table on the server has no column
 * for it either. Three layers agreeing is what a promise like that takes.
 */
object Sharing {

    /** Where an ask has got to. Mirrors `link_state` in 0011. */
    enum class LinkState { PENDING, ACCEPTED, DECLINED, REVOKED }

    /**
     * One account asking another whether it may see their week, and the answer.
     *
     * Directed. That my aunt lets me see her Tuesdays does not put my Tuesdays
     * in front of her; two people who both want that have two of these and have
     * answered two questions.
     */
    data class Link(
        val id: UUID,
        val requester: UUID,
        val addressee: UUID,
        val state: LinkState,
    )

    /**
     * A block as it crosses the wire: when, how long, busy or free.
     *
     * Deliberately not [WeekBlock]. Sharing the app's own type would mean the
     * label rides along by default and stays out only by everybody remembering
     * to strip it, which is the kind of promise that survives exactly until the
     * first person in a hurry. A separate type with no such field cannot forget.
     */
    data class SharedBlock(
        /** ISO-8601, Monday is 1 — the column in 0011 stores this number. */
        val day: Int,
        val start: LocalTime,
        val end: LocalTime,
        val kind: BlockKind,
    )

    /**
     * A week, ready to send. Labels are dropped here and nowhere else.
     */
    fun forWire(blocks: List<WeekBlock>): List<SharedBlock> =
        blocks.map {
            SharedBlock(
                day = it.day.value,
                start = it.start,
                end = it.end,
                kind = it.kind,
            )
        }

    /**
     * A week that has arrived, as the app's own type.
     *
     * The label comes back null because it was never sent. Anything reading one
     * of these has to cope with that, which is honest: you are looking at when
     * somebody is busy, not at what they are doing.
     */
    fun fromWire(blocks: List<SharedBlock>): List<WeekBlock> =
        blocks.map {
            WeekBlock(
                day = DayOfWeek.of(it.day),
                start = it.start,
                end = it.end,
                kind = it.kind,
                label = null,
            )
        }

    /**
     * Whether [viewer] may currently see [owner]'s week.
     *
     * Only an accepted ask, and only in the direction it was asked. Everything
     * else — never asked, still pending, declined, taken back — is no, and they
     * are all the same no.
     *
     * Deliberately not cached anywhere by the caller. A revocation has to take
     * effect the next time the question is asked, which is the same contract
     * the row-level policy on the server keeps.
     */
    fun maySee(links: List<Link>, viewer: UUID, owner: UUID): Boolean =
        links.any {
            it.state == LinkState.ACCEPTED &&
                it.requester == viewer &&
                it.addressee == owner
        }

    /**
     * The asks waiting on [me] to answer.
     *
     * The addressee's side of the inbox. The requester's side is
     * [asked], and the two are separate because they are different
     * questions with different answers available.
     */
    fun waitingOn(links: List<Link>, me: UUID): List<Link> =
        links.filter { it.addressee == me && it.state == LinkState.PENDING }

    /** The asks [me] has made that nobody has answered yet. */
    fun asked(links: List<Link>, me: UUID): List<Link> =
        links.filter { it.requester == me && it.state == LinkState.PENDING }
}
