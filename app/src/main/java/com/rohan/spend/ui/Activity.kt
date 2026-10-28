package com.rohan.spend.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import com.rohan.spend.data.Channel
import com.rohan.spend.data.Txn
import com.rohan.spend.data.date
import com.rohan.spend.data.inr

private enum class Filter(val label: String, val test: (Txn) -> Boolean) {
    ALL("All", { true }),
    OUT("Spent", { it.debit }),
    IN("Received", { !it.debit }),
    UPI("UPI", { it.channel == Channel.UPI }),
    CARD("Card", { it.channel == Channel.CARD }),
    BANK("Bank", { it.channel == Channel.BANK || it.channel == Channel.ATM }),
}

@Composable
fun ActivityScreen(s: AppState, nav: Nav, onRefresh: () -> Unit = {}) {
    var filter by rememberSaveable { mutableStateOf(Filter.ALL) }
    var dayEpoch by rememberSaveable { mutableStateOf<Long?>(null) }
    val day = dayEpoch?.let(LocalDate::ofEpochDay)
    val list = remember(s.visible, filter, day) { s.visible.filter { filter.test(it) && (day == null || it.date() == day) } }
    val days = remember(s.stats) { s.stats.lastDays(14) }
    Refreshable(s.refreshing, onRefresh) {
    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = BarClearance),
    ) {
        item {
            TabHeader("Activity", nav)
        }
        item {
            Fortnight(Modifier.rise(0).padding(bottom = 14.dp), days, s.usual() / 30, day) { d -> dayEpoch = if (d == day) null else d.toEpochDay() }
        }
        item {
            LazyRow(Modifier.padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(Filter.entries) { f -> Chip(f.label, f == filter, { filter = f }) }
            }
        }
        dayGroups(list, s, nav)
        if (list.isEmpty()) item { EmptyNote("Nothing here yet.") }
    }
    }
}

/** Two weeks of daily spend as heat-shaded bars. Tap one to see only that day. */
@Composable
private fun Fortnight(modifier: Modifier, days: List<Pair<LocalDate, Long>>, perDay: Long, sel: LocalDate?, onPick: (LocalDate) -> Unit) {
    val max = days.maxOf { it.second }.coerceAtLeast(1L)
    val shown = sel?.let { d -> days.firstOrNull { it.first == d } }
    FrostCard(modifier, pad = 16.dp) {
        Column {
            Row(verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Text(shown?.let { dayLabel(it.first) } ?: "Last 14 days", style = T.label)
                    CountUp(shown?.second ?: days.sumOf { it.second }, T.h2)
                }
                Text(if (sel != null) "Tap again to clear" else "Tap a day", style = T.tiny)
            }
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth().height(96.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.Bottom) {
                days.forEachIndexed { i, (d, v) ->
                    val on = d == sel
                    val lvl = level(v, perDay)
                    val frac = if (v == 0L) 0f else (kotlin.math.sqrt(v.toFloat() / max)).coerceAtLeast(.08f)
                    Box(Modifier.weight(1f).fillMaxHeight().press({ onPick(d) }), contentAlignment = Alignment.BottomCenter) {
                        Box(
                            Modifier.fillMaxWidth().fillMaxHeight(grown(frac.coerceAtLeast(.04f), 60L + 25L * i).coerceAtLeast(.001f)).clip(RoundedCornerShape(6.dp))
                                .background(if (v == 0L) C.noSpend else C.heat[lvl])
                                .then(if (on) Modifier.border(2.dp, C.today, RoundedCornerShape(6.dp)) else Modifier),
                        )
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                days.forEach { (d, _) ->
                    Text(
                        d.dayOfMonth.toString(), textAlign = TextAlign.Center, modifier = Modifier.weight(1f),
                        style = T.tiny.copy(fontSize = 11.sp, color = if (d == sel) C.today else C.faint, fontWeight = if (d == sel) FontWeight.Bold else FontWeight.Normal),
                    )
                }
            }
        }
    }
}

private val dow = DateTimeFormatter.ofPattern("EEEE")
private val mon = DateTimeFormatter.ofPattern("MMMM")

/** A big date number beside each day's name and total, then that day's card. */
fun androidx.compose.foundation.lazy.LazyListScope.dayGroups(list: List<Txn>, s: AppState, nav: Nav) {
    val days = list.groupBy { it.date() }.entries.toList()
    val today = LocalDate.now()
    days.forEachIndexed { i, (day, txns) ->
        item(key = day.toString()) {
            Column(Modifier.animateItem().rise(i)) {
                Row(Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        day.dayOfMonth.toString().padStart(2, '0'),
                        style = T.title.copy(color = if (day == today) C.today else C.ink, fontFeatureSettings = "tnum"),
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(if (day == today) "Today" else if (day == today.minusDays(1)) "Yesterday" else day.format(dow), style = T.h3)
                        Text(day.format(mon) + if (day.year != today.year) " ${day.year}" else "", style = T.tiny)
                    }
                    val out = txns.filter { it.debit }.sumOf { it.amount }
                    if (out > 0) Text(inr(out), style = T.num.copy(color = C.muted))
                }
                FrostCard(pad = 14.dp) {
                    Column(Modifier.animateContentSize()) {
                        txns.forEach { t -> TxnRow(t, s.catOf(t), s.notes[t.id], { nav.push(Route.TxnDetail(t.id)) }) }
                    }
                }
            }
        }
    }
}

@Composable
fun SearchScreen(s: AppState, nav: Nav) {
    var q by rememberSaveable { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val hits = remember(q, s.visible) {
        val term = q.trim().lowercase()
        if (term.isEmpty()) emptyList()
        else s.visible.filter { t ->
            term in t.payee.lowercase() || term in s.catOf(t).label.lowercase() ||
                (s.notes[t.id]?.lowercase()?.contains(term) == true) || term in (t.amount / 100).toString()
        }
    }
    Column(Modifier.fillMaxSize().statusBarsPadding().imePadding().padding(horizontal = 20.dp)) {
        Row(Modifier.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier.weight(1f).height(50.dp).frost(shape = CircleShape).padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Search, null, tint = C.muted, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f)) {
                    if (q.isEmpty()) Text("Payee, category, note or amount", style = T.body.copy(color = C.faint))
                    BasicTextField(q, { q = it }, Modifier.fillMaxWidth().focusRequester(focus), textStyle = T.body,
                        singleLine = true, cursorBrush = SolidColor(C.tint))
                }
            }
            Spacer(Modifier.width(10.dp))
            RoundButton(Icons.Rounded.Close, "Close", nav::pop)
        }
        if (q.isNotBlank()) {
            Row(Modifier.padding(vertical = 6.dp)) {
                Text("${hits.size} found", style = T.label, modifier = Modifier.weight(1f))
                Text(inr(hits.filter { it.debit }.sumOf { it.amount }) + " spent", style = T.label)
            }
        }
        LazyColumn(contentPadding = PaddingValues(bottom = 40.dp)) {
            dayGroups(hits.take(300), s, nav)
            if (q.isNotBlank() && hits.isEmpty()) item { EmptyNote("No matches for \"$q\".") }
        }
    }
}
