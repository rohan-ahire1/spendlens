package com.rohan.spend.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class GlanceTest {
    private val ist = ZoneId.of("Asia/Kolkata")
    private val ninePm = LocalTime.of(SUMMARY_HOUR, 0)

    @Test fun leftThisMonth() {
        val m = MonthLeft(spent = 31_700_00, usual = 40_000_00, today = 1_240_00)
        assertEquals(8_300_00L, m.left)
        assertFalse(m.over)
        assertEquals("₹8,300 left this month", m.line)
        assertEquals(0.7925f, m.fraction, 0.0001f)
    }

    @Test fun overShowsHowFarOver() {
        val m = MonthLeft(spent = 41_200_00, usual = 40_000_00, today = 0)
        assertTrue(m.over)
        assertEquals("₹1,200", m.amount)
        assertEquals("over this month", m.label)
        assertEquals(1f, m.fraction)
    }

    @Test fun exactlyOnTargetIsNotOver() {
        val m = MonthLeft(spent = 40_000_00, usual = 40_000_00, today = 0)
        assertFalse(m.over)
        assertEquals("₹0 left this month", m.line)
    }

    @Test fun withNothingToMeasureAgainstShowsSpent() {
        val m = MonthLeft(spent = 5_000_00, usual = 0, today = 0)
        assertFalse(m.over)
        assertEquals("₹5,000 spent this month", m.line)
        assertEquals(0f, m.fraction)
    }

    @Test fun nudgeCopyWithSpending() {
        val (title, text) = nudgeCopy(MonthLeft(31_700_00, 40_000_00, 1_240_00))
        assertEquals("You spent ₹1,240 today", title)
        assertEquals("₹8,300 left this month", text)
    }

    @Test fun nudgeCopyOnANoSpendDay() {
        val (title, text) = nudgeCopy(MonthLeft(41_200_00, 40_000_00, 0))
        assertEquals("No spending today", title)
        assertEquals("₹1,200 over this month", text)
    }

    @Test fun summaryOncePerEvening() {
        val at = LocalDateTime.of(2026, 10, 29, 21, 0, 30)
        assertTrue(summaryDue(at, ""))
        assertTrue(summaryDue(at, "2026-10-28"))
        assertFalse(summaryDue(at, "2026-10-29"))
        // An early alarm and one held up past midnight both stay quiet.
        assertFalse(summaryDue(LocalDateTime.of(2026, 10, 29, 20, 59), ""))
        assertFalse(summaryDue(LocalDateTime.of(2026, 10, 30, 0, 20), "2026-10-29"))
    }

    @Test fun nextNinePmIsTonightBeforeNine() {
        val now = ZonedDateTime.of(2026, 10, 29, 14, 5, 0, 0, ist)
        assertEquals(ZonedDateTime.of(2026, 10, 29, 21, 0, 0, 0, ist), nextAt(now, ninePm))
    }

    @Test fun nextNinePmIsTomorrowAfterNine() {
        val now = ZonedDateTime.of(2026, 10, 29, 22, 30, 0, 0, ist)
        assertEquals(ZonedDateTime.of(2026, 10, 30, 21, 0, 0, 0, ist), nextAt(now, ninePm))
    }

    @Test fun firingRightAtNineSetsTomorrow() {
        val now = ZonedDateTime.of(2026, 10, 29, 21, 0, 0, 0, ist)
        assertEquals(ZonedDateTime.of(2026, 10, 30, 21, 0, 0, 0, ist), nextAt(now, ninePm))
    }

    @Test fun nextNinePmCrossesMonthAndYear() {
        val now = ZonedDateTime.of(2026, 12, 31, 23, 59, 0, 0, ist)
        assertEquals(ZonedDateTime.of(2027, 1, 1, 21, 0, 0, 0, ist), nextAt(now, ninePm))
    }

    @Test fun istIsAlwaysExactlyADayApart() {
        // India has no daylight saving, so every evening is 24 hours after the last, all year.
        var t = ZonedDateTime.of(2026, 1, 1, 21, 0, 0, 0, ist)
        repeat(365) {
            val n = nextAt(t, ninePm)
            assertEquals(24 * 3600L, java.time.Duration.between(t, n).seconds)
            t = n
        }
    }

    @Test fun midnightJobLandsJustAfterMidnight() {
        val now = ZonedDateTime.of(2026, 10, 29, 23, 59, 59, 0, ist)
        assertEquals(ZonedDateTime.of(2026, 10, 30, 0, 0, 5, 0, ist), nextAt(now, LocalTime.of(0, 0, 5)))
    }

    @Test fun aSkippedHourResolvesForward() {
        // Where clocks do jump, 2:30 on the change day doesn't exist and lands at 3:30.
        val ny = ZoneId.of("America/New_York")
        val now = ZonedDateTime.of(2026, 3, 8, 1, 0, 0, 0, ny)
        assertEquals(ZonedDateTime.of(2026, 3, 8, 3, 30, 0, 0, ny), nextAt(now, LocalTime.of(2, 30)))
    }
}
