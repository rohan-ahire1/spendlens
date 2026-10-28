package com.rohan.spend.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

private var ids = 0L

fun txn(date: String, rupees: Long, payee: String = "Netflix", debit: Boolean = true) = Txn(
    id = ++ids,
    time = LocalDate.parse(date).atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
    amount = rupees * 100, debit = debit, payee = payee, payeeKey = payee.lowercase(),
    account = "1234", channel = Channel.CARD, balance = null, body = "", sender = "HDFCBK",
)

class SubsTest {
    private val today = LocalDate.parse("2026-10-20")

    @Test fun priceRiseKeepsOneSubscription() {
        val subs = detectSubs(listOf(
            txn("2026-06-05", 549), txn("2026-07-05", 549), txn("2026-08-05", 649),
            txn("2026-09-05", 649), txn("2026-10-05", 649),
        ), today)
        assertEquals(1, subs.size)
        val s = subs[0]
        assertEquals(Cadence.MONTHLY, s.cadence)
        assertEquals(649_00L, s.amount)
        assertEquals(LocalDate.parse("2026-11-05"), s.next)
        val r = s.rise!!
        assertEquals(549_00L, r.from)
        assertEquals(100_00L, r.by)
        assertEquals(18, r.pct)
        assertEquals(LocalDate.parse("2026-08-05"), r.since)
    }

    @Test fun priceDropIsNotARise() {
        val s = detectSubs(listOf(txn("2026-08-05", 649), txn("2026-09-05", 649), txn("2026-10-05", 549)), today).single()
        assertNull(s.rise)
    }

    @Test fun singleChargeIsNotASubscription() {
        assertTrue(detectSubs(listOf(txn("2026-10-05", 649)), today).isEmpty())
    }

    @Test fun yearlyAndQuarterly() {
        val subs = detectSubs(listOf(
            txn("2025-03-01", 1499, "Prime"), txn("2026-03-02", 1499, "Prime"),
            txn("2026-04-10", 299, "Zee5"), txn("2026-07-10", 299, "Zee5"), txn("2026-10-10", 299, "Zee5"),
        ), today)
        val prime = subs.first { it.payee == "Prime" }
        assertEquals(Cadence.YEARLY, prime.cadence)
        assertEquals(LocalDate.parse("2027-03-02"), prime.next)
        assertEquals(124_91L, prime.monthly)
        val zee = subs.first { it.payee == "Zee5" }
        assertEquals(Cadence.QUARTERLY, zee.cadence)
        assertEquals(LocalDate.parse("2027-01-10"), zee.next)
        assertEquals(124_91L + 99_66L, subs.perMonth())
    }

    @Test fun irregularGapsAreNotASubscription() {
        assertTrue(detectSubs(listOf(txn("2026-03-01", 99, "Play"), txn("2026-05-01", 99, "Play"), txn("2026-07-01", 99, "Play")), today).isEmpty())
    }

    @Test fun renewsInDays() {
        val subs = detectSubs(listOf(
            txn("2026-08-22", 649), txn("2026-09-22", 649),
            txn("2026-08-23", 119, "Spotify"), txn("2026-09-23", 119, "Spotify"),
            txn("2026-09-28", 1950, "Claude"), txn("2026-08-28", 1950, "Claude"),
        ), today)
        val r = renewals(subs, today, within = 3)
        assertEquals(listOf("Netflix" to 2L, "Spotify" to 3L), r.map { it.sub.payee to it.days })
        assertEquals(listOf("Netflix"), renewals(subs, today, within = 2).map { it.sub.payee })
        assertEquals(8L, subs.first { it.payee == "Claude" }.daysUntil(today))
    }

    @Test fun dueTodayCounts() {
        val subs = detectSubs(listOf(txn("2026-08-20", 649), txn("2026-09-20", 649)), today)
        assertEquals(0L, renewals(subs, today).single().days)
    }

    @Test fun missedChargeIsLikelyCancelled() {
        // Expected 2026-09-05; today is 45 days on.
        val s = detectSubs(listOf(txn("2026-07-05", 199), txn("2026-08-05", 199)), today).single()
        assertTrue(s.cancelled)
        assertTrue(renewals(listOf(s), today, within = 400).isEmpty())
        assertEquals(0L, listOf(s).perMonth())
    }

    @Test fun aFewDaysLateIsStillLive() {
        val s = detectSubs(listOf(txn("2026-08-12", 199), txn("2026-09-12", 199)), today).single()
        assertFalse(s.cancelled)
    }

    @Test fun longGoneIsDropped() {
        assertTrue(detectSubs(listOf(txn("2026-04-05", 199), txn("2026-05-05", 199)), today).isEmpty())
    }

    @Test fun twoPlansAtOnePayeeStaySeparate() {
        val subs = detectSubs(listOf(
            txn("2026-08-01", 130, "Google Play"), txn("2026-08-03", 650, "Google Play"),
            txn("2026-09-01", 130, "Google Play"), txn("2026-09-03", 650, "Google Play"),
            txn("2026-10-01", 130, "Google Play"), txn("2026-10-03", 650, "Google Play"),
        ), today)
        assertEquals(listOf(650_00L, 130_00L), subs.map { it.amount })
        assertTrue(subs.none { it.duplicate })
    }

    @Test fun samePriceTwiceIsAPossibleDoubleCharge() {
        val subs = detectSubs(listOf(
            txn("2026-09-01", 199, "Spotify"), txn("2026-09-04", 199, "Spotify"),
            txn("2026-10-01", 199, "Spotify"), txn("2026-10-04", 199, "Spotify"),
        ), today)
        assertEquals(2, subs.size)
        assertEquals(1, subs.count { it.duplicate })
    }

    @Test fun upcomingRenewalsFiltersToSubscriptions() {
        val txns = listOf(
            txn("2026-08-22", 649), txn("2026-09-22", 649),
            txn("2026-08-22", 900, "Landlord"), txn("2026-09-22", 900, "Landlord"),
        )
        val r = upcomingRenewals(txns, { if (it.payee == "Netflix") Cat.SUBSCRIPTIONS else Cat.PEOPLE }, today)
        assertEquals(listOf("Netflix"), r.map { it.sub.payee })
    }
}
