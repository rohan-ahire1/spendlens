package com.rohan.spend.ui

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
fun ActivityScreen(s: AppState, nav: Nav) {
    var filter by rememberSaveable { mutableStateOf(Filter.ALL) }
    val list = remember(s.visible, filter) { s.visible.filter(filter.test) }
    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = BarClearance),
    ) {
        item {
            Text("Activity", style = T.title, modifier = Modifier.padding(vertical = 10.dp))
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

/** Day headers with a net total, then a frosted card per day. */
fun androidx.compose.foundation.lazy.LazyListScope.dayGroups(list: List<Txn>, s: AppState, nav: Nav) {
    val days = list.groupBy { it.date() }.entries.toList()
    days.forEachIndexed { i, (day, txns) ->
        item(key = day.toString()) {
            Column(Modifier.rise(i)) {
                Row(Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 8.dp)) {
                    Text(dayLabel(day), style = T.label, modifier = Modifier.weight(1f))
                    val out = txns.filter { it.debit }.sumOf { it.amount }
                    if (out > 0) Text(inr(out), style = T.label)
                }
                FrostCard(pad = 14.dp) {
                    Column {
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
