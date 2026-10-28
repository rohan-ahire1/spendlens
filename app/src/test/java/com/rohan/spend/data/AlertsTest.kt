package com.rohan.spend.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class AlertsTest {
    private val today = LocalDate.parse("2026-10-20")

    private fun netflix(lastDay: String = "2026-09-21") = detectSubs(listOf(
        txn("2026-07-21", 649), txn("2026-08-21", 649), txn(lastDay, 649),
    ), today)

    @Test fun renewalTomorrow() {
        val a = subAlerts(netflix(), today, emptySet())
        assertEquals(1, a.size)
        assertEquals("Netflix renews tomorrow · ₹649", a[0].title)
        assertEquals("renew|netflix|2026-10-21", a[0].key)
    }

    @Test fun sameRenewalIsToldOnce() {
        val first = subAlerts(netflix(), today, emptySet())
        val seen = first.map { it.key }.toSet()
        assertTrue(subAlerts(netflix(), today, seen).isEmpty())
        // Still quiet the next morning, on the day it charges.
        assertTrue(subAlerts(netflix(), today.plusDays(1), seen).isEmpty())
    }

    @Test fun nextMonthsRenewalIsNew() {
        val seen = setOf("renew|netflix|2026-10-21")
        val later = LocalDate.parse("2026-11-20")
        val subs = detectSubs(listOf(
            txn("2026-07-21", 649), txn("2026-08-21", 649), txn("2026-09-21", 649), txn("2026-10-21", 649),
        ), later)
        assertEquals(listOf("renew|netflix|2026-11-21"), subAlerts(subs, later, seen).map { it.key })
    }

    @Test fun nothingDueBeyondTwoDays() {
        assertTrue(subAlerts(netflix("2026-09-25"), today, emptySet()).isEmpty())
        assertEquals(1, subAlerts(netflix("2026-09-22"), today, emptySet()).size)
    }

    @Test fun priceRiseToldOnce() {
        val day = LocalDate.parse("2026-10-08")
        val subs = detectSubs(listOf(
            txn("2026-08-05", 119, "Spotify"), txn("2026-09-05", 119, "Spotify"), txn("2026-10-05", 139, "Spotify"),
        ), day)
        val a = subAlerts(subs, day, emptySet())
        assertEquals(listOf("Spotify went up ₹20 to ₹139"), a.map { it.title })
        assertTrue(subAlerts(subs, day, a.map { it.key }.toSet()).isEmpty())
    }

    @Test fun oldPriceRiseIsNotNews() {
        val day = LocalDate.parse("2026-10-20")
        val subs = detectSubs(listOf(
            txn("2026-08-05", 119, "Spotify"), txn("2026-09-05", 139, "Spotify"), txn("2026-10-05", 139, "Spotify"),
        ), day)
        assertTrue(subAlerts(subs, day, emptySet()).none { it.key.startsWith("rise") })
    }

    @Test fun pruneDropsOnlyLongPastKeys() {
        val seen = setOf("renew|netflix|2026-07-01", "renew|netflix|2026-10-21", "rise|spotify|13900|2026-10-05", "odd")
        assertEquals(setOf("renew|netflix|2026-10-21", "rise|spotify|13900|2026-10-05", "odd"), pruneSeen(seen, today))
    }
}
