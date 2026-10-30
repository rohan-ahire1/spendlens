package com.rohan.spend.ui

import com.rohan.spend.data.Channel
import com.rohan.spend.data.Txn
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test
import java.time.LocalDate

class AppStateTest {
    private val txns = listOf(Txn(1, 1_700_000_000_000, 500_00, true, "Zepto", "zepto", "1234", Channel.UPI, null, "", "HDFC"))
    private val state = AppState(stage = Stage.READY, txns = txns, today = LocalDate.of(2026, 10, 30))

    @Test fun changesThatTouchNoNumbersKeepTheSameStats() {
        val stats = state.stats
        assertSame(stats, state.copy(refreshing = true).stats)
        assertSame(stats, state.copy(foundCount = 9, target = 100, notes = mapOf(1L to "lunch")).stats)
        assertSame(stats, state.copy(txns = txns.toList()).stats)
    }

    @Test fun changesThatMoveTheNumbersBuildNewStats() {
        val stats = state.stats
        assertNotSame(stats, state.copy(hidden = setOf(1L)).stats)
        assertNotSame(stats, state.copy(today = state.today.plusDays(1)).stats)
    }
}
