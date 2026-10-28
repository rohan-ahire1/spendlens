package com.rohan.spend.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rohan.spend.data.month
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

/** A past month has no "today" to list, so Home shows where the money went that month instead. */
@Composable
fun BiggestPayments(ym: YearMonth, s: AppState, nav: Nav, first: Int) {
    val top = remember(s.stats, ym) {
        s.visible.filter { s.stats.counts(it) && it.month() == ym }.sortedByDescending { it.amount }.take(5)
    }
    val name = ym.month.getDisplayName(TextStyle.FULL, Locale.getDefault()) + if (ym.year != LocalDate.now().year) " ${ym.year}" else ""
    Column {
        SectionTitle("Biggest payments in $name", Modifier.rise(first)) {
            if (top.isNotEmpty()) Text("See all", style = T.label, modifier = Modifier.press({ nav.push(Route.Cats(ym.toString())) }))
        }
        FrostCard(Modifier.rise(first + 1), pad = 14.dp) {
            Column {
                if (top.isEmpty()) EmptyNote("Nothing counted was spent this month.")
                top.forEach { t -> TxnRow(t, s.catOf(t), s.notes[t.id], { nav.push(Route.TxnDetail(t.id)) }, showDay = true) }
            }
        }
    }
}
