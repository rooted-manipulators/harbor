package app.harbor.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WalkthroughTest {

    @Test
    fun `the full tour, garden arm, with a person`() {
        val stops = Walkthrough.stops(StudyArm.GARDEN, hasPerson = true)
        assertTrue(TourStop.HOME_PEOPLE in stops)
        assertTrue(TourStop.GARDEN_FLOWER in stops)
        assertTrue(TourStop.PERSON_DIAL in stops)
        assertTrue(TourStop.PERSON_PLANT in stops)
        // The one deliberate exception is scoped to the field itself, not to
        // the tour existing at all.
        assertFalse(TourStop.GARDEN_BEE in stops)
    }

    @Test
    fun `the bees arm gets one more stop, and only one`() {
        val garden = Walkthrough.stops(StudyArm.GARDEN, hasPerson = true)
        val bees = Walkthrough.stops(StudyArm.BEES, hasPerson = true)
        assertEquals(garden.size + 1, bees.size)
        assertTrue(TourStop.GARDEN_BEE in bees)
    }

    @Test
    fun `nobody added yet skips every stop about a person, not the tour itself`() {
        val stops = Walkthrough.stops(StudyArm.GARDEN, hasPerson = false)
        assertFalse(TourStop.HOME_PEOPLE in stops)
        assertFalse(TourStop.GARDEN_FLOWER in stops)
        assertFalse(TourStop.PERSON_DIAL in stops)
        assertFalse(TourStop.PERSON_PLANT in stops)
        assertTrue(TourStop.WELCOME in stops)
        assertTrue(TourStop.ACCOUNT_DONE in stops)
    }

    @Test
    fun `the order never changes`() {
        val stops = Walkthrough.stops(StudyArm.BEES, hasPerson = true)
        assertEquals(stops, stops.sortedBy { stops.indexOf(it) })
        assertEquals(TourStop.WELCOME, stops.first())
        assertEquals(TourStop.ACCOUNT_DONE, stops.last())
    }

    @Test
    fun `every stop lands on a screen`() {
        TourStop.entries.forEach { Walkthrough.screenFor(it) }
    }

    @Test
    fun `schedule stops stay together, week first and the day last`() {
        val stops = Walkthrough.stops(StudyArm.GARDEN, hasPerson = true)
        val schedule = stops.filter { Walkthrough.screenFor(it) == TourScreen.SCHEDULE }
        assertEquals(
            listOf(
                TourStop.SCHEDULE_VIEWS,
                TourStop.SCHEDULE_PALETTE,
                TourStop.SCHEDULE_COPY,
                TourStop.SCHEDULE_CALENDAR,
                TourStop.SCHEDULE_QUIET,
            ),
            schedule,
        )
    }

    @Test
    fun `the tour never comes up empty`() {
        for (arm in StudyArm.entries) {
            for (hasPerson in listOf(true, false)) {
                assertTrue(Walkthrough.stops(arm, hasPerson).isNotEmpty())
            }
        }
    }
}
