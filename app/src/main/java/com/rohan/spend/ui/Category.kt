package com.rohan.spend.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rohan.spend.data.Cat
import com.rohan.spend.data.inr
import java.time.format.DateTimeFormatter

@Composable
fun CategoryScreen(cat: Cat, s: AppState, nav: Nav) {
    val st = s.stats
    val months = remember(st, cat) { st.sinceHistory(st.catMonthly(cat)) }
    val payees = remember(st, cat) { st.payeesThisMonth(cat) }
    val recent = remember(st, cat) { st.inCat(cat).take(30) }
    val avg = months.dropLast(1).map { it.second }.filter { it > 0 }.let { if (it.isEmpty()) 0L else it.sum() / it.size }
    val color = cat.style().fg
    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 40.dp),
    ) {
        item { TopBar(cat.label, nav::pop) }
        item {
            Row(Modifier.rise(0).padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                CatIcon(cat, 52.dp)
                Spacer(Modifier.width(14.dp))
                Column {
                    Text("This month", style = T.label)
                    CountUp(months.lastOrNull()?.second ?: 0L, T.title)
                }
            }
        }
        item { FrostCard(Modifier.rise(1)) { MonthBars(months, avg, color) } }

        if (cat == Cat.SUBSCRIPTIONS && st.subs.isNotEmpty()) {
            val dupes = st.subs.filter { it.duplicate }
            if (dupes.isNotEmpty()) item {
                FrostCard(Modifier.padding(top = 12.dp).rise(2)) {
                    Column {
                        Pill("Possible double charge", C.warnInk, C.warnBg)
                        Spacer(Modifier.height(8.dp))
                        Text("You pay ${dupes.first().payee} more than once a month. Cancelling the extra one saves " +
                            "${inr(dupes.sumOf { it.amount } * 12)} a year.", style = T.small.copy(color = C.ink))
                    }
                }
            }
            item { SectionTitle("Running", Modifier.rise(3)) }
            item {
                FrostCard(Modifier.rise(4), pad = 14.dp) {
                    Column {
                        st.subs.forEach { sub ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(sub.payee, style = T.body)
                                    Text("Next around ${sub.next.format(DateTimeFormatter.ofPattern("d MMM"))} · ${sub.months} months", style = T.tiny)
                                }
                                Text(inr(sub.amount), style = T.num)
                            }
                        }
                    }
                }
            }
        }

        if (payees.isNotEmpty()) {
            item { SectionTitle("Who you paid this month", Modifier.rise(5)) }
            item {
                FrostCard(Modifier.rise(6), pad = 14.dp) {
                    Column {
                        val top = payees.first().second.coerceAtLeast(1)
                        payees.take(8).forEach { (name, total, count) ->
                            Column(Modifier.padding(vertical = 8.dp)) {
                                Row {
                                    Text(name, style = T.body, modifier = Modifier.weight(1f), maxLines = 1)
                                    Text(inr(total), style = T.num)
                                }
                                Text(if (count == 1) "1 payment" else "$count payments", style = T.tiny)
                                Spacer(Modifier.height(6.dp))
                                GrowBar(total.toFloat() / top, color, height = 4.dp)
                            }
                        }
                    }
                }
            }
        }

        item { SectionTitle("Recent", Modifier.rise(7)) }
        item {
            FrostCard(Modifier.rise(8), pad = 14.dp) {
                Column {
                    if (recent.isEmpty()) EmptyNote("Nothing in ${cat.label.lowercase()} yet.")
                    recent.forEach { t -> TxnRow(t, cat, s.notes[t.id], { nav.push(Route.TxnDetail(t.id)) }, showDay = true) }
                }
            }
        }
    }
}
