package com.rohan.spend.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rohan.spend.data.Cat
import com.rohan.spend.data.date
import com.rohan.spend.data.inr
import com.rohan.spend.data.inrShort
import com.rohan.spend.data.month
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

private enum class Span(val label: String, val title: String) {
    MONTH("This month", "This month"), QUARTER("3 months", "Last 3 months"), YEAR("Year", "Last 12 months"), ALL("All", "All time");

    fun from(today: LocalDate): LocalDate = when (this) {
        MONTH -> today.withDayOfMonth(1)
        QUARTER -> today.minusMonths(3)
        YEAR -> today.minusYears(1)
        ALL -> LocalDate.MIN
    }
}

private val dowFmt = DateTimeFormatter.ofPattern("EEE")
private val monFmt = DateTimeFormatter.ofPattern("MMM")

/** Every payment in a category, filterable by time span and by who was paid. */
@Composable
fun CategoryScreen(cat: Cat, s: AppState, nav: Nav) {
    val st = s.stats
    val style = cat.style()
    val today = LocalDate.now()
    var span by rememberSaveable { mutableStateOf(Span.MONTH) }
    var payee by rememberSaveable { mutableStateOf<String?>(null) }
    var biggest by rememberSaveable { mutableStateOf(false) }
    val inSpan = remember(st, cat, span) { st.inCat(cat).filter { !it.date().isBefore(span.from(today)) } }
    val payees = remember(inSpan) {
        inSpan.groupBy { it.payee }.map { (p, l) -> p to l.sumOf { it.amount } }.sortedByDescending { it.second }
    }
    val list = inSpan.filter { payee == null || it.payee == payee }
        .let { l -> if (biggest) l.sortedByDescending { it.amount } else l.sortedByDescending { it.time } }
    val total = list.sumOf { it.amount }
    // What a usual month in this category looks like, from the months before this one.
    val usual = remember(st, cat) {
        st.sinceHistory(st.catMonthly(cat)).filter { it.first < YearMonth.from(today) }.map { it.second }.filter { it > 0 }
            .let { if (it.isEmpty()) 0L else it.sum() / it.size }
    }
    val biggestId = inSpan.maxByOrNull { it.amount }?.id

    LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 40.dp)) {
        item { TopBar(cat.label, nav::pop) }
        item {
            Column(Modifier.rise(0).padding(top = 6.dp).fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(style.bg).padding(18.dp)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Column(Modifier.weight(1f)) {
                        Text("${span.title} · ${list.size} ${if (list.size == 1) "payment" else "payments"}", style = T.label.copy(color = style.fg))
                        CountUp(total, T.display.copy(fontSize = 46.sp))
                    }
                    if (span == Span.MONTH && payee == null && usual > 0) {
                        val pace = usual * today.dayOfMonth / today.lengthOfMonth()
                        val pct = if (pace > 0) ((total - pace) * 100 / pace).toInt() else 0
                        Text(
                            (if (pct >= 0) "+" else "−") + "${kotlin.math.abs(pct)}% vs usual",
                            style = T.tiny.copy(color = if (pct > 0) C.warnInk else C.green, fontWeight = FontWeight.SemiBold),
                            modifier = Modifier.padding(bottom = 6.dp).clip(CircleShape).background(Color.White).padding(horizontal = 10.dp, vertical = 5.dp),
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Span.entries.forEach { sp ->
                        val on = sp == span
                        Text(
                            sp.label, style = T.label.copy(color = if (on) Color.White else C.ink, fontSize = 13.sp),
                            modifier = Modifier.clip(CircleShape).background(if (on) C.ink else Color.White.copy(alpha = .7f))
                                .press({ span = sp; payee = null }).padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                    }
                }
            }
        }

        if (payees.size > 1) {
            item { SectionTitle("Where · tap to filter", Modifier.rise(1)) }
            item {
                val top = payees.take(3)
                val rest = payees.drop(3)
                Row(Modifier.rise(2).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    top.forEach { (name, amt) ->
                        PayeeTile(name, inrShort(amt), payee == name, style.fg, Modifier.weight(kotlin.math.sqrt(amt.toFloat() / payees[0].second).coerceAtLeast(.55f))) {
                            payee = if (payee == name) null else name
                        }
                    }
                    if (rest.isNotEmpty()) PayeeTile("${rest.size} more", inrShort(rest.sumOf { it.second }), false, style.fg, Modifier.weight(.55f), null)
                }
            }
        }

        if (cat == Cat.SUBSCRIPTIONS && st.subs.isNotEmpty()) {
            val dupes = st.subs.filter { it.duplicate }
            if (dupes.isNotEmpty()) item {
                FrostCard(Modifier.padding(top = 12.dp)) {
                    Column {
                        Pill("Possible double charge", C.warnInk, C.warnBg)
                        Spacer(Modifier.height(8.dp))
                        Text("You pay ${dupes.first().payee} more than once a month. Cancelling the extra one saves " +
                            "${inr(dupes.sumOf { it.amount } * 12)} a year.", style = T.small.copy(color = C.ink))
                    }
                }
            }
            item { SectionTitle("Running") }
            item {
                FrostCard(pad = 14.dp) {
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

        item {
            SectionTitle(payee ?: "All payments", Modifier.rise(3)) {
                Text(if (biggest) "Biggest" else "Newest", style = T.label, modifier = Modifier.press({ biggest = !biggest }))
            }
        }
        if (list.isEmpty()) item { FrostCard { EmptyNote("Nothing in ${cat.label.lowercase()} for this span.") } }
        items(list, key = { it.id }) { t ->
            val tag = when {
                t.id == biggestId && list.size > 2 -> "Biggest"
                else -> null
            }
            Box(Modifier.animateItem().padding(bottom = 8.dp).frost(22.dp).padding(horizontal = 14.dp)) {
                PayRow(t, cat, s.notes[t.id], tag, lead = { DateBlock(t.date(), span != Span.MONTH) }) { nav.push(Route.TxnDetail(t.id)) }
            }
        }
    }
}

@Composable
private fun DateBlock(d: LocalDate, showMonth: Boolean) {
    Column(Modifier.width(40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(d.dayOfMonth.toString().padStart(2, '0'), style = T.h2.copy(fontSize = 22.sp, lineHeight = 24.sp, fontFeatureSettings = "tnum"))
        Text((if (showMonth) d.format(monFmt) else d.format(dowFmt)).uppercase(), style = T.tiny.copy(fontSize = 11.sp, color = C.faint, fontWeight = FontWeight.SemiBold))
    }
}

@Composable
private fun PayeeTile(name: String, amount: String, on: Boolean, accent: Color, modifier: Modifier, onClick: (() -> Unit)?) {
    val bg by animateColorAsState(if (on) accent else C.card, label = "payee")
    Column(
        modifier.clip(RoundedCornerShape(14.dp)).background(bg)
            .then(if (onClick != null) Modifier.press(onClick) else Modifier).padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Text(name, style = T.tiny.copy(color = if (on) Color.White else C.subtle, fontSize = 12.sp, fontWeight = FontWeight.SemiBold), maxLines = 1, softWrap = false,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        Text(amount, style = T.label.copy(color = if (on) Color.White else C.ink, fontWeight = FontWeight.Bold), maxLines = 1, textAlign = TextAlign.Start)
    }
}
