package app.harbor.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BeeMoodTest {

    @Test
    fun `every flower has a bee`() {
        // `when` is exhaustive, so this cannot fail to compile -- it is here so
        // a new flower added without a thought for its bee shows up as a
        // failing test name rather than as a silent default somewhere.
        FlowerKind.entries.forEach { BeeMood.of(it) }
    }

    @Test
    fun `every bee is summoned by some flower`() {
        val used = FlowerKind.entries.map { BeeMood.of(it) }.toSet()
        assertEquals(BeeMood.entries.toSet(), used)
    }

    @Test
    fun `no one bee takes most of the garden`() {
        // Twenty into eight: nobody should get more than three, or the arch
        // mostly shows the same bee and the moods stop being information.
        val counts = FlowerKind.entries.groupingBy { BeeMood.of(it) }.eachCount()
        assertTrue(counts.toString(), counts.values.all { it <= 3 })
    }

    @Test
    fun `the obvious ones land where they should`() {
        assertEquals(BeeMood.HAPPY, BeeMood.of(FlowerKind.GLAD_WE_TALKED))
        assertEquals(BeeMood.LOVED, BeeMood.of(FlowerKind.FELT_LOVED))
        assertEquals(BeeMood.CALM, BeeMood.of(FlowerKind.EASY_SILENCE))
        assertEquals(BeeMood.BRAVE, BeeMood.of(FlowerKind.SAID_THE_HARD_THING))
        assertEquals(BeeMood.ANXIOUS, BeeMood.of(FlowerKind.DREADED_THIS_ONE))
    }

    @Test
    fun `a glad flower never gets a worried bee`() {
        listOf(FlowerKind.GLAD_WE_TALKED, FlowerKind.GLAD_SHE_PICKED_UP, FlowerKind.FELT_LOVED)
            .forEach { assertTrue(it.name, BeeMood.of(it) != BeeMood.ANXIOUS) }
    }
}
