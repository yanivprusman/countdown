package com.automatelinux.countdown.data

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The numbers this app exists to show. A day boundary off by one, or a "left" that
 * goes negative, would be wrong on screen every single day with nothing to say so.
 */
class CountdownTest {
    // Israel, deliberately not UTC: the bug class tally hit was exactly a zone mix-up.
    private val zone = TimeZone.of("Asia/Jerusalem")

    private fun at(y: Int, m: Int, d: Int, h: Int = 12, min: Int = 0) =
        LocalDateTime(y, m, d, h, min).toInstant(zone).toEpochMilliseconds()

    private fun countdown(start: Int, vararg ticks: Long) = Countdown(
        id = "c", name = "Work", start = start, createdAt = at(2026, 10, 1),
        ticks = ticks.mapIndexed { i, t -> Tick("t$i", at = t) },
    )

    @Test
    fun theStartStaysAndWhatIsLeftIsDerived() {
        val c = countdown(100, at(2026, 10, 1), at(2026, 10, 2), at(2026, 10, 3))
        assertEquals(100, c.start)
        assertEquals(3, c.done)
        assertEquals(97, c.left)
        assertFalse(c.finished)
    }

    @Test
    fun leftNeverGoesBelowZeroWhenTheStartIsLoweredUnderWhatIsDone() {
        val c = countdown(2, at(2026, 10, 1), at(2026, 10, 2), at(2026, 10, 3))
        assertEquals(0, c.left)
        assertTrue(c.finished)
        assertEquals(1f, c.progress)
    }

    @Test
    fun todayIsTheLocalDayNotTheUtcOne() {
        // 01:30 in Jerusalem on the 5th is still the 4th in UTC.
        val c = countdown(100, at(2026, 10, 5, 1, 30))
        assertEquals(1, c.countedOn(LocalDate(2026, 10, 5), zone).size)
        assertEquals(0, c.countedOn(LocalDate(2026, 10, 4), zone).size)
    }

    @Test
    fun aStreakRunsBackFromToday() {
        val c = countdown(100, at(2026, 10, 3), at(2026, 10, 4), at(2026, 10, 5))
        assertEquals(3, c.streak(LocalDate(2026, 10, 5), zone))
    }

    @Test
    fun todayNotYetCountedDoesNotBreakTheStreak() {
        val c = countdown(100, at(2026, 10, 3), at(2026, 10, 4))
        assertEquals(2, c.streak(LocalDate(2026, 10, 5), zone))
    }

    @Test
    fun aMissedDayEndsTheStreak() {
        val c = countdown(100, at(2026, 10, 1), at(2026, 10, 3))
        assertEquals(0, c.streak(LocalDate(2026, 10, 5), zone))
        assertEquals(1, c.streak(LocalDate(2026, 10, 3), zone))
    }

    @Test
    fun twoTicksOnOneDayAreOneDayOfStreak() {
        val c = countdown(100, at(2026, 10, 4, 9), at(2026, 10, 4, 18), at(2026, 10, 5))
        assertEquals(2, c.streak(LocalDate(2026, 10, 5), zone))
    }

    @Test
    fun chronologicalOrderNumbersTheTicks() {
        val c = countdown(100, at(2026, 10, 3), at(2026, 10, 1), at(2026, 10, 2))
        assertEquals(listOf("t1", "t2", "t0"), c.chronological.map { it.id })
    }

    @Test
    fun unitsReadRightNextToANumber() {
        assertEquals("1 day", countOf(1, "days"))
        assertEquals("73 days", countOf(73, "days"))
        assertEquals("1 entry", countOf(1, "entries"))
        assertEquals("1 class", countOf(1, "classes"))
        assertEquals("1 km", countOf(1, "km"))
        assertEquals("1 run", countOf(1, "runs"))
    }

    @Test
    fun copiesNameThemselvesWithoutStacking() {
        assertEquals("Work copy", nextCopyName("Work", setOf("Work")))
        assertEquals("Work copy 2", nextCopyName("Work", setOf("Work", "Work copy")))
        assertEquals("Work copy 2", nextCopyName("Work copy", setOf("Work", "Work copy")))
        val long = "x".repeat(MaxNameLength)
        val copy = nextCopyName(long, setOf(long))
        assertEquals(MaxNameLength, copy.length)
        assertTrue(copy.endsWith(" copy"))
    }

    @Test
    fun countsGroupThousands() {
        assertEquals("100", formatCount(100))
        assertEquals("1,000", formatCount(1000))
        assertEquals("100,000", formatCount(100000))
    }
}
