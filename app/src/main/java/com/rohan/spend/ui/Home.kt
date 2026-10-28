package com.rohan.spend.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rohan.spend.data.Period
import com.rohan.spend.data.inr
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun HomeScreen(s: AppState, vm: AppViewModel, nav: Nav) {
    var period by rememberSaveable { mutableStateOf(Period.MONTH) }
    val st = s.stats
    val spent = st.spent(period)
    val usual = if (period == Period.MONTH) s.usual() else st.usual(period)
    val cats = st.byCat(period)
    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = BarClearance),
    ) {
        item {
            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d MMMM")), style = T.label)
                    Text("Spend", style = T.title)
                }
                Crossfade(s.refreshing, label = "refresh") { busy ->
                    if (busy) Box(Modifier.size(42.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(20.dp), color = C.ink, strokeWidth = 2.dp)
                    } else RoundButton(Icons.Rounded.Refresh, "Refresh", vm::refresh)
                }
            }
        }
        item {
            Row(Modifier.rise(0).padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Period.entries.forEach { p -> Chip(p.label, p == period, { period = p }) }
            }
        }
        item { Box(Modifier.rise(1).padding(top = 8.dp)) { SpentCard(period, spent, usual, st.daysLeft) } }
        item {
            SectionTitle("Where it went", Modifier.rise(2)) {
                Text("Insights", style = T.label.copy(color = C.tint), modifier = Modifier.press({ nav.tab(Tab.INSIGHTS) }))
            }
        }
        item {
            FrostCard(Modifier.rise(3), pad = 6.dp) {
                Column {
                    if (cats.isEmpty()) EmptyNote("Nothing spent yet in this ${period.label.lowercase()}.")
                    val top = cats.firstOrNull()?.total ?: 1L
                    cats.take(6).forEach { c ->
                        Row(
                            Modifier.fillMaxWidth().press({ nav.push(Route.Category(c.cat)) }).padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CatIcon(c.cat, 36.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Row {
                                    Text(c.cat.label, style = T.body, modifier = Modifier.weight(1f))
                                    Text(inr(c.total), style = T.num)
                                }
                                Spacer(Modifier.height(6.dp))
                                GrowBar(c.total.toFloat() / top, c.cat.style().fg, height = 4.dp)
                            }
                        }
                    }
                }
            }
        }
        item { SectionTitle(if (st.hasToday) "Today" else "Recent", Modifier.rise(4)) }
        item {
            FrostCard(Modifier.rise(5), pad = 14.dp) {
                Column {
                    val recent = st.recent()
                    if (recent.isEmpty()) EmptyNote("No transactions yet.")
                    recent.forEach { t -> TxnRow(t, s.catOf(t), s.notes[t.id], { nav.push(Route.TxnDetail(t.id)) }, showDay = !st.hasToday) }
                }
            }
        }
    }
}

@Composable
private fun SpentCard(period: Period, spent: Long, usual: Long, daysLeft: Int) {
    FrostCard(radius = 28.dp, pad = 22.dp) {
        Column {
            Text("Spent this ${period.label.lowercase()}", style = T.label)
            Spacer(Modifier.height(6.dp))
            CountUp(spent, T.hero)
            Spacer(Modifier.height(16.dp))
            if (usual > 0) {
                val ratio = spent.toFloat() / usual
                GrowBar(ratio, if (ratio > 1f) C.peach else C.ink, height = 8.dp)
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Usual ${inr(usual)}", style = T.tiny, modifier = Modifier.weight(1f))
                    val left = usual - spent
                    AnimatedVisibility(true, enter = fadeIn(), exit = fadeOut()) {
                        if (left >= 0) Pill("${inr(left)} left" + if (period == Period.MONTH) " · $daysLeft days" else "", C.green, C.green.copy(alpha = .1f))
                        else Pill("${inr(-left)} over", C.warnInk, C.warnBg)
                    }
                }
            } else {
                Text("Your usual will show after a full month of data.", style = T.tiny)
            }
        }
    }
}
