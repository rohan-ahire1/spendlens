package com.rohan.spend.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class RecapTest {
    private val cats = mapOf(
        "zomato" to Cat.FOOD_DELIVERY, "zepto" to Cat.GROCERIES, "uber" to Cat.COMMUTE,
        "rahul" to Cat.PEOPLE, "mom" to Cat.FAMILY, "salary" to Cat.SALARY,
    )
    private val catOf: (Txn) -> Cat = { cats[it.payeeKey] ?: Cat.SMALL }
    private val counts: (Txn) -> Boolean = { it.debit && catOf(it).bucket == Bucket.LIVING }
    private val sep = YearMonth.parse("2026-09")
    private val today = LocalDate.parse("2026-10-02")

    private val history = listOf(
        // June to August: groceries 3k, delivery 2k a month.
        txn("2026-06-03", 3000, "Zepto"), txn("2026-06-10", 2000, "Zomato"),
        txn("2026-07-03", 3000, "Zepto"), txn("2026-07-10", 2000, "Zomato"),
        txn("2026-08-03", 3000, "Zepto"), txn("2026-08-10", 2000, "Zomato"),
    )
    private val september = listOf(
        txn("2026-09-01", 3000, "Zepto"),
        txn("2026-09-05", 2500, "Zomato"), txn("2026-09-06", 2500, "Zomato"), txn("2026-09-07", 2000, "Zomato"),
        txn("2026-09-07", 400, "Uber"),
        txn("2026-09-15", 9000, "Rahul"),
        txn("2026-09-20", 5000, "Mom"),
        txn("2026-09-25", 90000, "Salary", debit = false),
    )

    @Test fun monthInNumbers() {
        val r = recap(history + september, counts, catOf, sep, today)
        assertEquals(19_400_00L, r.spent)
        assertEquals(6, r.count)
        assertEquals(5_000_00L, r.usual)
        assertEquals(288, r.vsUsual)
        assertEquals(Cat.PEOPLE, r.top!!.cat)
        assertEquals(9_000_00L, r.top!!.total)
        assertEquals(Cat.PEOPLE, r.shift!!.cat)
        assertEquals(9_000_00L, r.biggest!!.amount)
        // Rahul is a person, so the top place is the delivery app.
        assertEquals("Zomato", r.place!!.payee)
        assertEquals(3, r.place!!.visits)
        assertEquals(30, r.days)
        // Spent on the 1st, 5th, 6th, 7th and 15th.
        assertEquals(25, r.noSpend)
    }

    @Test fun biggestShiftIsInRupees() {
        val r = recap(history + september.filter { it.payee != "Rahul" }, counts, catOf, sep, today)
        val s = r.shift!!
        assertEquals(Cat.FOOD_DELIVERY, s.cat)
        assertEquals(7_000_00L, s.now)
        assertEquals(2_000_00L, s.before)
        assertEquals(250, s.pct)
    }

    @Test fun targetOverridesUsual() {
        assertEquals(20_000_00L, recap(history + september, counts, catOf, sep, today, usual = 20_000_00L).usual)
    }

    @Test fun noHistoryBeforeTheMonth() {
        val r = recap(september, counts, catOf, sep, today)
        assertEquals(0L, r.usual)
        assertNull(r.vsUsual)
        assertNull(r.shift)
    }

    @Test fun currentMonthStopsAtToday() {
        val r = recap(listOf(txn("2026-10-01", 100, "Zepto")), counts, catOf, YearMonth.parse("2026-10"), today)
        assertEquals(2, r.days)
        assertEquals(1, r.noSpend)
    }

    @Test fun emptyMonth() {
        val r = recap(history, counts, catOf, sep, today)
        assertEquals(0L, r.spent)
        assertNull(r.top)
        assertNull(r.biggest)
        assertEquals(30, r.noSpend)
    }
}
