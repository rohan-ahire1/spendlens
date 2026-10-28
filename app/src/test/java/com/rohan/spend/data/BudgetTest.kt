package com.rohan.spend.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class BudgetTest {
    @Test fun underPaceIsFine() {
        // ₹3,000 by day 15 of 30 heads for ₹6,000, under ₹10,000.
        assertEquals(6_000_00L, projected(3_000_00, 15, 30))
        assertEquals(Pace.FINE, pace(3_000_00, 10_000_00, 15, 30))
    }

    @Test fun onPaceToGoOver() {
        // ₹6,000 by day 15 heads for ₹12,000.
        assertEquals(Pace.ON_PACE_OVER, pace(6_000_00, 10_000_00, 15, 30))
    }

    @Test fun exactlyOnPaceIsFine() {
        assertEquals(Pace.FINE, pace(5_000_00, 10_000_00, 15, 30))
    }

    @Test fun alreadyOver() {
        assertEquals(Pace.OVER, pace(10_000_01, 10_000_00, 10, 30))
    }

    @Test fun spendingExactlyTheBudgetIsNotOver() {
        assertEquals(Pace.FINE, pace(10_000_00, 10_000_00, 30, 30))
    }

    @Test fun earlyInTheMonthOnlyWarnsWhenOver() {
        // Day 1 still projects the whole month, but that's too thin to warn on.
        assertEquals(31_000_00L, projected(1_000_00, 1, 31))
        assertEquals(Pace.FINE, pace(1_000_00, 30_000_00, 1, 31))
        assertEquals(Pace.FINE, pace(5_000_00, 30_000_00, 6, 31))
        assertEquals(Pace.OVER, pace(30_000_01, 30_000_00, 1, 31))
        assertEquals(Pace.ON_PACE_OVER, pace(7_000_00, 30_000_00, 7, 31))
    }

    @Test fun zeroBudgetNeverWarns() {
        assertEquals(Pace.FINE, pace(5_000_00, 0, 10, 30))
        assertEquals(Pace.FINE, pace(5_000_00, -1, 10, 30))
    }

    @Test fun monthEndProjectionIsWhatWasSpent() {
        assertEquals(9_000_00L, projected(9_000_00, 31, 31))
        assertEquals(Pace.FINE, pace(9_000_00, 10_000_00, 31, 31))
    }

    @Test fun oddDaysAreClamped() {
        assertEquals(projected(1_000_00, 1, 30), projected(1_000_00, 0, 30))
        assertEquals(1_000_00L, projected(1_000_00, 40, 30))
        assertEquals(1_000_00L, projected(1_000_00, 5, 0))
    }

    @Test fun pastMonthOnlyWarnsWhenOver() {
        val ym = YearMonth.of(2026, 9)
        val today = LocalDate.of(2026, 10, 2)
        assertEquals(30, daysIn(ym, today))
        assertEquals(Pace.FINE, budgetState(Cat.SHOPPING, 10_000_00, 9_000_00, ym, today).pace)
        assertEquals(Pace.OVER, budgetState(Cat.SHOPPING, 10_000_00, 11_000_00, ym, today).pace)
    }

    @Test fun currentMonthUsesToday() {
        val today = LocalDate.of(2026, 2, 7)
        val s = budgetState(Cat.FOOD_DELIVERY, 4_000_00, 1_400_00, YearMonth.from(today), today)
        assertEquals(7, daysIn(YearMonth.from(today), today))
        assertEquals(5_600_00L, s.projected)
        assertEquals(Pace.ON_PACE_OVER, s.pace)
        assertTrue(s.warn)
        assertEquals(2_600_00L, s.left)
    }

    @Test fun futureMonthIsFine() {
        val today = LocalDate.of(2026, 10, 29)
        val s = budgetState(Cat.SHOPPING, 1_00, 5_00, YearMonth.of(2026, 11), today)
        assertEquals(0, daysIn(YearMonth.of(2026, 11), today))
        assertFalse(s.warn)
    }

    @Test fun fractionHandlesZeroBudget() {
        assertEquals(0f, BudgetState(Cat.SHOPPING, 0, 100, 100, Pace.FINE).fraction)
        assertEquals(1.5f, BudgetState(Cat.SHOPPING, 200, 300, 300, Pace.OVER).fraction)
    }

    private fun txn(id: Long, day: LocalDate, amount: Long, payee: String) = Txn(
        id, day.atTime(12, 0).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli(), amount, true,
        payee, payee.lowercase(), "1234", Channel.UPI, null, "", "VM-HDFCBK-S",
    )

    @Test fun statsBudgetsUseCountedSpendAndFallBackForLeftOut() {
        val today = LocalDate.of(2026, 10, 10)
        val cats = mapOf("swiggy" to Cat.FOOD_DELIVERY, "mom" to Cat.FAMILY, "zara" to Cat.SHOPPING)
        val txns = listOf(
            txn(1, today, 3_000_00, "Swiggy"),
            txn(2, today.minusDays(2), 1_000_00, "Swiggy"),
            txn(3, today, 5_000_00, "Mom"),
            txn(4, today.minusMonths(1), 9_000_00, "Swiggy"),
            txn(5, today, 500_00, "Zara"),
        )
        val st = Stats(txns, { cats.getValue(it.payeeKey) }, Counting(excludeFamily = true), today)
        val l = st.budgets(mapOf(Cat.FOOD_DELIVERY to 10_000_00, Cat.FAMILY to 20_000_00, Cat.SHOPPING to 5_000_00, Cat.MOVIES to 0), st.thisMonth, today)
        assertEquals(listOf(Cat.FOOD_DELIVERY, Cat.FAMILY, Cat.SHOPPING), l.map { it.cat })
        assertEquals(4_000_00L, l[0].spent)
        assertEquals(Pace.ON_PACE_OVER, l[0].pace)
        assertEquals(5_000_00L, l[1].spent)
        assertEquals(Pace.FINE, l[2].pace)
    }
}
