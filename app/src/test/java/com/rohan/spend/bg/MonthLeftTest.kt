package com.rohan.spend.bg

import com.rohan.spend.data.Cat
import com.rohan.spend.data.Split
import com.rohan.spend.data.txn
import com.rohan.spend.ui.AppState
import com.rohan.spend.ui.Stage
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class MonthLeftTest {
    @Test fun matchesTheAppsCountingRules() {
        val groceries = txn("2026-10-29", 1_000, "BigBasket")
        val dinner = txn("2026-10-10", 900, "Toit")
        val home = txn("2026-10-05", 2_000, "Mom")
        val lastMonth = txn("2026-09-29", 5_000, "BigBasket")
        val s = AppState(
            stage = Stage.READY, today = LocalDate.parse("2026-10-29"), target = 10_000_00,
            txns = listOf(groceries, dinner, home, lastMonth),
            payeeRules = mapOf("bigbasket" to Cat.GROCERIES, "toit" to Cat.EATING_OUT, "mom" to Cat.FAMILY),
            // Dinner split three ways: only my ₹300 counts.
            splits = mapOf(dinner.id to Split(3, 300_00)),
        )
        val m = s.monthLeft()
        assertEquals(1_300_00L, m.spent)
        assertEquals(8_700_00L, m.left)
        assertEquals(1_000_00L, m.today)
    }

    @Test fun fallsBackToTheUsualMonth() {
        val s = AppState(stage = Stage.READY, today = LocalDate.parse("2026-10-29"), txns = listOf(txn("2026-10-29", 100, "Toit")),
            payeeRules = mapOf("toit" to Cat.EATING_OUT))
        assertEquals(0L, s.monthLeft().usual)
        assertEquals("₹100 spent this month", s.monthLeft().line)
    }
}
