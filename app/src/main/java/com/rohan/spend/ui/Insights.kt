package com.rohan.spend.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import com.rohan.spend.data.Change
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.rohan.spend.data.Bucket
import com.rohan.spend.data.inr
import com.rohan.spend.data.inrShort
import java.time.YearMonth
import java.time.format.DateTimeFormatter

private val monthFmt = DateTimeFormatter.ofPattern("MMM")
private val monthLong = DateTimeFormatter.ofPattern("MMMM yyyy")

@Composable
fun InsightsScreen(s: AppState, nav: Nav) {
    val st = s.stats
    val months = remember(st) { st.sinceHistory(st.monthly(12)) }
    val investing = remember(st) { st.avgMonthly { it.debit && s.catOf(it).bucket == Bucket.INVESTING } }
    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = BarClearance),
    ) {
        item { Text("Insights", style = T.title, modifier = Modifier.padding(vertical = 10.dp)) }
        item { Box(Modifier.rise(0)) { KeptCard(st.income, st.avgLiving, if (s.excludeFamily) st.avgFamily else 0L, investing) } }
        item { SectionTitle("Living cost by month", Modifier.rise(1)) }
        item {
            FrostCard(Modifier.rise(2)) { MonthBars(months, s.usual(), C.heat[3]) }
        }
        val changes = st.changes()
        if (changes.isNotEmpty()) {
            item { SectionTitle("Biggest changes", Modifier.rise(3)) }
            item {
                Row(Modifier.rise(4).fillMaxWidth().height(132.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    changes.forEach { c -> ChangeTile(c, Modifier.weight(1f).fillMaxHeight()) { nav.push(Route.Category(c.cat)) } }
                }
            }
        }
        if (st.subs.isNotEmpty()) {
            item { SectionTitle("Subscriptions", Modifier.rise(5)) }
            item {
                Row(
                    Modifier.rise(6).fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(C.heat[0])
                        .press({ nav.push(Route.Category(com.rohan.spend.data.Cat.SUBSCRIPTIONS)) }).padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(inr(st.subsMonthly), style = T.big.copy(color = C.heat[3]))
                        Text("a month on ${st.subs.size} subscriptions", style = T.small)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(inrShort(st.subsMonthly * 12), style = T.h3.copy(color = C.heat[3]))
                        Text("a year", style = T.tiny)
                    }
                }
            }
        }
    }
}

/** A category tile with its change against the usual pace as the headline. */
@Composable
private fun ChangeTile(c: Change, modifier: Modifier, onClick: () -> Unit) {
    val st = c.cat.style()
    val up = c.pct >= 0
    Column(
        modifier.clip(RoundedCornerShape(20.dp)).background(st.bg).press(onClick).padding(12.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Icon(st.icon, null, tint = st.fg, modifier = Modifier.size(20.dp))
        Column {
            Text((if (up) "+" else "") + "${c.pct}%", style = T.h2.copy(color = if (up) C.warnInk else C.green), maxLines = 1)
            Text(c.cat.label, style = T.tiny.copy(color = C.ink), maxLines = 1)
            Text(inrShort(c.now) + " so far", style = T.tiny, maxLines = 1)
        }
    }
}

/** How much of the salary is kept: a dark card with 100 squares, one per percent. */
@Composable
private fun KeptCard(income: Long, living: Long, family: Long, investing: Long) {
    val onDark = Color.White.copy(alpha = .62f)
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(C.ink).padding(22.dp)) {
        if (income <= 0) {
            Text("Average month", style = T.label.copy(color = onDark))
            CountUp(living, T.display.copy(color = Color.White))
            Text("No salary messages found yet, so there's nothing to compare against.", style = T.tiny.copy(color = onDark))
            return@Column
        }
        val kept = (income - living - family).coerceAtLeast(0)
        val pct = (kept * 100 / income).toInt()
        Text("You keep", style = T.label.copy(color = onDark))
        Row(verticalAlignment = Alignment.Bottom) {
            Text("$pct%", style = T.display.copy(color = Color.White))
            Spacer(Modifier.width(10.dp))
            Text("of ${inr(income)}\na month", style = T.small.copy(color = onDark), modifier = Modifier.padding(bottom = 6.dp))
        }
        Spacer(Modifier.height(18.dp))
        val parts = listOf(
            Triple("Living", living, C.heat[2]),
            Triple("Family", family, C.today),
            Triple("Kept", kept, C.greenBright),
        ).filter { it.second > 0 }
        val total = parts.sumOf { it.second }.coerceAtLeast(1)
        // Whole squares per part, largest remainder last so they always add up to 100.
        val cells = parts.map { (it.second * 100 / total).toInt() }.toMutableList()
        if (cells.isNotEmpty()) cells[cells.lastIndex] += 100 - cells.sum()
        val colors = parts.flatMapIndexed { i, p -> List(cells[i]) { p.third } }
        val grow = remember { Animatable(0f) }
        LaunchedEffect(Unit) { grow.animateTo(1f, tween(1100, easing = FastOutSlowInEasing)) }
        Canvas(Modifier.fillMaxWidth().aspectRatio(20f / 5f)) {
            val g = 3.dp.toPx()
            val cell = (size.width - g * 19) / 20
            val lit = (grow.value * 100).toInt()
            for (i in 0 until 100) {
                val col = i / 5
                val row = i % 5
                drawRoundRect(
                    if (i < lit) colors.getOrElse(i) { Color.White } else Color.White.copy(alpha = .08f),
                    topLeft = Offset(col * (cell + g), row * (cell + g)),
                    size = Size(cell, cell),
                    cornerRadius = CornerRadius(3.dp.toPx()),
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        parts.forEach { (label, v, c) ->
            Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(c))
                Spacer(Modifier.width(8.dp))
                Text(label, style = T.small.copy(color = onDark), modifier = Modifier.weight(1f))
                Text(inr(v), style = T.num.copy(fontSize = T.small.fontSize, color = Color.White))
            }
        }
        if (investing > 0) Text("Of what you keep, about ${inr(investing)} a month goes into investments.",
            style = T.tiny.copy(color = onDark), modifier = Modifier.padding(top = 10.dp))
    }
}

/** Monthly bars with a dashed "usual" line. Tap a bar to read it. */
@Composable
fun MonthBars(data: List<Pair<YearMonth, Long>>, usual: Long, color: Color) {
    var sel by remember(data) { mutableIntStateOf(data.lastIndex) }
    val grow = remember { Animatable(0f) }
    LaunchedEffect(Unit) { grow.animateTo(1f, tween(900, easing = FastOutSlowInEasing)) }
    val max = (data.maxOfOrNull { it.second } ?: 0L).coerceAtLeast(usual).coerceAtLeast(1L)
    Column {
        val (m, v) = data.getOrNull(sel) ?: (YearMonth.now() to 0L)
        Text(m.format(monthLong), style = T.label)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(inr(v), style = T.h2)
            if (usual > 0) Text("  usual ${inrShort(usual)}", style = T.tiny, modifier = Modifier.padding(bottom = 3.dp))
        }
        Spacer(Modifier.height(14.dp))
        Canvas(
            Modifier.fillMaxWidth().height(140.dp).pointerInput(data) {
                detectTapGestures { p -> sel = (p.x / (size.width / data.size.toFloat())).toInt().coerceIn(0, data.lastIndex) }
            },
        ) {
            val slot = size.width / data.size
            val w = slot * .56f
            data.forEachIndexed { i, (_, value) ->
                val h = size.height * value / max * grow.value
                val on = i == sel
                drawRoundRect(
                    color = if (on) color else color.copy(alpha = .16f),
                    topLeft = Offset(i * slot + (slot - w) / 2, size.height - h),
                    size = Size(w, h.coerceAtLeast(2f)),
                    cornerRadius = CornerRadius(4.dp.toPx()),
                )
            }
            if (usual > 0) {
                val y = size.height - size.height * usual / max
                drawLine(C.peach, Offset(0f, y), Offset(size.width, y), 2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)))
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth()) {
            data.forEachIndexed { i, (mm, _) ->
                Text(mm.format(monthFmt).take(1), style = T.tiny.copy(color = if (i == sel) C.ink else C.faint),
                    modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
    }
}
