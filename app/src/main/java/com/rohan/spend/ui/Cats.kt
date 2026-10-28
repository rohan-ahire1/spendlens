package com.rohan.spend.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rohan.spend.data.inr
import java.time.YearMonth
import java.time.format.DateTimeFormatter

/** Every category that had spending in a month, biggest first, each with its share of the month. */
@Composable
fun CatsScreen(ym: YearMonth, s: AppState, nav: Nav) {
    val cats = remember(s.stats, ym) { s.stats.byCatIn(ym) }
    val total = cats.sumOf { it.total }.coerceAtLeast(1)
    val top = cats.firstOrNull()?.total?.coerceAtLeast(1) ?: 1
    LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 40.dp)) {
        item { TopBar(ym.format(DateTimeFormatter.ofPattern("MMMM yyyy")), nav::pop) }
        item {
            Column(Modifier.rise(0).padding(top = 6.dp, bottom = 14.dp)) {
                Text("${cats.size} categories", style = T.label)
                CountUp(cats.sumOf { it.total }, T.display)
            }
        }
        if (cats.isEmpty()) item { FrostCard { EmptyNote("Nothing spent this month.") } }
        itemsIndexed(cats, key = { _, c -> c.cat.name }) { i, c ->
            val style = c.cat.style()
            Box(Modifier.rise(i + 1).padding(bottom = 8.dp).frost(22.dp).press({ nav.push(Route.Category(c.cat, ym.toString())) }).padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CatIcon(c.cat, 40.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(c.cat.label, style = T.body.copy(fontWeight = FontWeight.SemiBold), modifier = Modifier.weight(1f))
                            Text(inr(c.total), style = T.num.copy(fontWeight = FontWeight.Bold))
                        }
                        Spacer(Modifier.height(6.dp))
                        GrowBar(c.total.toFloat() / top, style.fg, delayMs = 120L + 40L * i.coerceAtMost(10))
                        Spacer(Modifier.height(6.dp))
                        val pct = c.total * 100 / total
                        Text(
                            "${if (c.count == 1) "1 payment" else "${c.count} payments"} · ${if (pct < 1) "<1" else pct}% of month",
                            style = T.tiny,
                        )
                    }
                }
            }
        }
    }
}
