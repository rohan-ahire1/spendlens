package com.rohan.spend.ui

import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rohan.spend.data.BudgetState
import com.rohan.spend.data.CatTotal
import com.rohan.spend.data.inr
import com.rohan.spend.data.inrShort
import com.rohan.spend.data.renewals
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as JTextStyle
import java.util.Locale

@Composable
fun HomeScreen(s: AppState, vm: AppViewModel, nav: Nav) {
    val st = s.stats
    val today = s.today
    val sel = today
    // The month paged back to, by name so midnight on the 1st doesn't shift it; null follows the current month.
    var viewed by rememberSaveable { mutableStateOf<String?>(null) }
    val ym = viewed?.let(YearMonth::parse)?.takeIf { it < st.thisMonth } ?: st.thisMonth
    val current = ym == st.thisMonth
    val daily = remember(st, ym) { st.dailyIn(ym) }
    val spent = daily.values.sum()
    val usual = s.usual()
    val due = remember(st) { renewals(st.subs, today, within = 2) }
    val lastMonth = st.thisMonth.minusMonths(1)
    val showRecap = recapDue(today) && lastMonth >= st.firstMonth && lastMonth.toString() !in s.recapsClosed
    val budgets = s.budgetsIn(ym)
    Refreshable(s.refreshing, vm::refresh) {
    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = BarClearance),
    ) {
        item {
            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(today.format(DateTimeFormatter.ofPattern("EEEE, d MMMM")), style = T.label, modifier = Modifier.weight(1f))
                RoundButton(Icons.Rounded.Search, "Search", { nav.push(Route.Search) })
                Spacer(Modifier.width(8.dp))
                Crossfade(s.refreshing, label = "refresh") { busy ->
                    if (busy) Box(Modifier.size(42.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(20.dp), color = C.ink, strokeWidth = 2.dp)
                    } else RoundButton(Icons.Rounded.Refresh, "Refresh", vm::refresh)
                }
            }
        }
        item { Headline(Modifier.rise(0), ym, current, spent, usual, s.target > 0) }
        if (current && showRecap) item {
            val last = remember(st) { st.dailyIn(lastMonth).values.sum() }
            RecapNudge(lastMonth, last, Modifier.rise(1).padding(top = 16.dp), { nav.push(Route.Recap(lastMonth.toString())) }, { vm.closeRecap(lastMonth) })
        }
        if (current && due.isNotEmpty()) item {
            RenewalCard(due, Modifier.rise(1).padding(top = if (showRecap) 8.dp else 16.dp)) { nav.push(Route.Category(com.rohan.spend.data.Cat.SUBSCRIPTIONS)) }
        }
        if (!current) item {
            Row(Modifier.rise(1).padding(top = 12.dp)) { Chip("See the ${ym.monthName()} recap", false, { nav.push(Route.Recap(ym.toString())) }) }
        }
        item {
            Calendar(
                Modifier.rise(1).padding(top = 18.dp), ym, today, daily, usual / ym.lengthOfMonth(), sel,
                prev = if (ym > st.firstMonth) ({ viewed = ym.minusMonths(1).toString() }) else null,
                next = if (!current) ({ viewed = ym.plusMonths(1).takeIf { it < st.thisMonth }?.toString() }) else null,
            ) { nav.push(Route.Day(it.toEpochDay())) }
        }
        item {
            // Each side draws its own month, so the outgoing one fades out with its own categories.
            Crossfade(ym, Modifier.rise(2), tween(320), label = "treemap") { m ->
                val cats = remember(st, m) { st.byCatIn(m) }
                val now = m == st.thisMonth
                Treemap(Modifier.padding(top = 10.dp), cats, { nav.push(Route.Category(it, if (now) null else m.toString())) }, { nav.push(Route.Cats(m.toString())) }, s.budgetsIn(m).associateBy { it.cat })
            }
        }
        if (current && budgets.any { it.warn }) item { PaceCard(budgets, nav, Modifier.rise(3)) }
        if (current) item {
            val dayTxns = st.onDay(sel)
            SectionTitle(dayLabel(sel, today), Modifier.rise(3)) {
                Text(inr(st.daily[sel] ?: 0L), style = T.num.copy(color = C.muted))
            }
            FrostCard(Modifier.rise(4), pad = 14.dp) {
                Column {
                    if (dayTxns.isEmpty()) EmptyNote(if (sel.isAfter(today)) "This day hasn't happened yet." else "Nothing spent on this day.")
                    dayTxns.forEach { t -> TxnRow(t, s.catOf(t), s.notes[t.id], { nav.push(Route.TxnDetail(t.id)) }) }
                }
            }
        }
        if (!current) item(key = "past-$ym") { BiggestPayments(ym, s, nav, 3) }
    }
    }
}

@Composable
private fun Headline(modifier: Modifier, ym: YearMonth, current: Boolean, spent: Long, usual: Long, isTarget: Boolean) {
    Row(modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.Bottom) {
        Column(Modifier.weight(1f)) {
            val name = ym.month.getDisplayName(JTextStyle.FULL, Locale.getDefault())
            Text(if (current) "$name so far" else if (ym.year == LocalToday.current.year) "Spent in $name" else "Spent in $name ${ym.year}", style = T.label)
            CountUp(spent, T.display)
        }
        if (usual > 0) Column(Modifier.padding(bottom = 6.dp), horizontalAlignment = Alignment.End) {
            val pct = ((spent - usual) * 100 / usual).toInt()
            Text(
                (if (pct >= 0) "+" else "−") + "${kotlin.math.abs(pct)}%",
                style = T.label.copy(color = if (pct > 0) C.warnInk else C.green, fontWeight = FontWeight.SemiBold),
            )
            Text("vs ${if (isTarget) "target" else "usual"} ${inr(usual)}", style = T.tiny)
        }
    }
}

/** Heat bucket for a day's spend against an ordinary day's share of the usual month. */
fun level(amount: Long, perDay: Long): Int = when {
    perDay <= 0 -> if (amount > 0) 1 else 0
    amount < perDay / 2 -> 0
    amount < perDay -> 1
    amount < perDay * 3 -> 2
    else -> 3
}

/** 1.7k, 25k, 30: tight enough for a calendar cell. */
fun short(paise: Long): String {
    val r = paise / 100
    return when {
        r >= 10_000 -> "${r / 1000}k"
        r >= 1_000 -> String.format(Locale.US, "%.1fk", r / 1000.0).replace(".0k", "k")
        else -> "$r"
    }
}

@Composable
private fun Calendar(
    modifier: Modifier, ym: YearMonth, today: LocalDate, daily: Map<LocalDate, Long>, perDay: Long, sel: LocalDate,
    prev: (() -> Unit)?, next: (() -> Unit)?, onPick: (LocalDate) -> Unit,
) {
    val gap = 6.dp
    FrostCard(modifier, pad = 14.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(gap)) {
            Row(Modifier.padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(ym.format(DateTimeFormatter.ofPattern("MMMM yyyy")), style = T.h3, modifier = Modifier.weight(1f))
                MonthArrow(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, "Previous month", prev)
                Spacer(Modifier.width(6.dp))
                MonthArrow(Icons.AutoMirrored.Rounded.KeyboardArrowRight, "Next month", next)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                listOf("M", "T", "W", "T", "F", "S", "S").forEach {
                    Text(it, style = T.tiny.copy(color = C.faint, fontSize = 12.sp, fontWeight = FontWeight.SemiBold), textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                }
            }
            AnimatedContent(
                ym, label = "month",
                transitionSpec = {
                    val dir = if (targetState > initialState) 1 else -1
                    (slideInHorizontally(tween(320)) { it / 3 * dir } + fadeIn(tween(320))) togetherWith
                        (slideOutHorizontally(tween(320)) { -it / 3 * dir } + fadeOut(tween(200))) using SizeTransform(clip = false)
                },
            ) { ym ->
            val first = ym.atDay(1)
            val offset = first.dayOfWeek.value - 1
            val weeks = (offset + ym.lengthOfMonth() + 6) / 7
            Column(verticalArrangement = Arrangement.spacedBy(gap)) {
            repeat(weeks) { w ->
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    repeat(7) { i ->
                        val n = w * 7 + i - offset + 1
                        if (n < 1 || n > ym.lengthOfMonth()) Spacer(Modifier.weight(1f))
                        else {
                            val d = first.withDayOfMonth(n)
                            DayCell(d, daily[d] ?: 0L, perDay, d.isAfter(today), d == sel) { onPick(d) }
                        }
                    }
                }
            }
            }
            }
            Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Less", style = T.tiny)
                C.heat.forEach { Box(Modifier.size(14.dp).clip(RoundedCornerShape(4.dp)).background(it)) }
                Text("More", style = T.tiny)
                Spacer(Modifier.weight(1f))
                Text("Tap a day", style = T.tiny)
            }
        }
    }
}

@Composable
private fun MonthArrow(icon: ImageVector, label: String, onClick: (() -> Unit)?) {
    Box(
        Modifier.size(34.dp).clip(CircleShape).background(C.bg).alpha(if (onClick != null) 1f else .35f)
            .then(if (onClick != null) Modifier.press(onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, label, tint = C.ink, modifier = Modifier.size(20.dp)) }
}

@Composable
private fun RowScope.DayCell(d: LocalDate, amount: Long, perDay: Long, future: Boolean, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    val lvl = level(amount, perDay)
    val bg = if (future) C.bg else if (amount == 0L) C.noSpend else C.heat[lvl]
    val fg = if (future) C.faint else if (amount > 0 && lvl >= 2) Color.White else C.ink
    Column(
        Modifier.weight(1f).aspectRatio(1f).clip(shape).background(bg)
            .then(if (selected) Modifier.border(2.dp, C.today, shape) else Modifier)
            .press(onClick).padding(horizontal = 5.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Text("${d.dayOfMonth}", style = T.tiny.copy(color = fg, fontSize = 12.sp, fontWeight = FontWeight.Bold, lineHeight = 13.sp))
        if (!future && amount > 0) Text(
            short(amount), style = T.tiny.copy(color = fg, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, lineHeight = 11.sp, fontFeatureSettings = "tnum"),
            textAlign = TextAlign.End, maxLines = 1, softWrap = false, modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun Treemap(modifier: Modifier, cats: List<CatTotal>, open: (com.rohan.spend.data.Cat) -> Unit, more: () -> Unit, rings: Map<com.rohan.spend.data.Cat, BudgetState> = emptyMap()) {
    if (cats.isEmpty()) return
    val gap = 6.dp
    Row(modifier.fillMaxWidth().height(140.dp), horizontalArrangement = Arrangement.spacedBy(gap)) {
        Tile(cats[0], Modifier.weight(1f).fillMaxHeight(), 18.dp, big = true, ring = rings[cats[0].cat]) { open(cats[0].cat) }
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(gap)) {
            cats.getOrNull(1)?.let { c -> Tile(c, Modifier.weight(1.4f).fillMaxWidth(), 18.dp, ring = rings[c.cat]) { open(c.cat) } }
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                cats.drop(2).take(2).forEach { c -> Tile(c, Modifier.weight(1f).fillMaxHeight(), 14.dp, small = true, ring = rings[c.cat]) { open(c.cat) } }
                val rest = cats.drop(4).sumOf { it.total }
                if (rest > 0) SmallTile("More", inrShort(rest).removePrefix("₹"), C.ink, Color.White, Modifier.weight(1f).fillMaxHeight(), more)
            }
        }
    }
}

@Composable
private fun Tile(c: CatTotal, modifier: Modifier, radius: Dp, big: Boolean = false, small: Boolean = false, ring: BudgetState? = null, onClick: () -> Unit) {
    val style = c.cat.style()
    if (small) return SmallTile(null, inrShort(c.total).removePrefix("₹"), style.fg, Color.White, modifier, onClick) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(style.icon, c.cat.label, tint = Color.White, modifier = Modifier.size(16.dp))
            Spacer(Modifier.weight(1f))
            if (ring != null) BudgetRing(ring, size = 14.dp)
        }
    }
    Column(
        modifier.clip(RoundedCornerShape(radius)).background(style.fg).press(onClick).padding(12.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(c.cat.label, style = T.label.copy(color = Color.White.copy(alpha = .85f)), maxLines = 1, modifier = Modifier.weight(1f))
            if (ring != null) BudgetRing(ring, Modifier.padding(start = 6.dp))
        }
        Text(inr(c.total), style = (if (big) T.h2 else T.h3.copy(fontWeight = FontWeight.Bold)).copy(color = Color.White, fontFeatureSettings = "tnum"), maxLines = 1)
    }
}

@Composable
private fun SmallTile(label: String?, amount: String, bg: Color, fg: Color, modifier: Modifier, onClick: () -> Unit, icon: @Composable () -> Unit = {}) {
    Column(
        modifier.clip(RoundedCornerShape(14.dp)).background(bg).press(onClick).padding(8.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        if (label != null) Text(label, style = T.tiny.copy(color = fg, fontSize = 12.sp, fontWeight = FontWeight.SemiBold), maxLines = 1, softWrap = false) else icon()
        Text(amount, style = T.tiny.copy(color = fg, fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum"), maxLines = 1)
    }
}
