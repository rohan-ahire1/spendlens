package com.rohan.spend.ui

import com.rohan.spend.data.Channel
import com.rohan.spend.data.Counting
import com.rohan.spend.data.Cat
import com.rohan.spend.data.Stats
import com.rohan.spend.data.Txn
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class BalanceDateTest {
    private val today = LocalDate.of(2026, 10, 29)

    @Test fun labels() {
        assertEquals("Balance today", balanceWhen(today, today))
        assertEquals("Balance yesterday", balanceWhen(today.minusDays(1), today))
        assertEquals("Balance on " + dayLabel(LocalDate.of(2026, 10, 12), today), balanceWhen(LocalDate.of(2026, 10, 12), today))
        assertTrue(balanceWhen(LocalDate.of(2025, 12, 30), today).endsWith("2025"))
    }

    @Test fun oldAfterAWeek() {
        assertFalse(balanceIsOld(today, today))
        assertFalse(balanceIsOld(today.minusDays(7), today))
        assertTrue(balanceIsOld(today.minusDays(8), today))
    }

    @Test fun accountKeepsNewestBalanceAndItsTime() {
        fun at(d: LocalDate) = d.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() + 3_600_000
        fun txn(id: Long, d: LocalDate, bal: Long?) =
            Txn(id, at(d), 100_00, true, "Zepto", "zepto", "1234", Channel.UPI, bal, "", "VM-HDFCBK-S")
        val txns = listOf(txn(1, today.minusDays(9), 500_00), txn(2, today.minusDays(2), 300_00), txn(3, today, null))
        val a = Stats(txns, { Cat.GROCERIES }, Counting(), today).accounts.single()
        assertEquals(300_00L, a.balance)
        assertEquals(at(today.minusDays(2)), a.balanceTime)
    }
}
