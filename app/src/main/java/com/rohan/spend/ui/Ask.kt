package com.rohan.spend.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rohan.spend.data.Answer
import com.rohan.spend.data.Ask
import com.rohan.spend.data.Bar
import com.rohan.spend.data.Reply
import com.rohan.spend.data.Txn
import java.time.LocalDate

/** What the search box makes of a question, and whether the list is narrowed to the answer. */
class AskState(val reply: Reply?, private val picked: MutableState<Boolean>) {
    val narrowed: Boolean get() = picked.value && reply is Reply.Done
    fun toggle() { picked.value = !picked.value }

    /** Plain hits when there are some; otherwise what the question is about, or exactly what was counted. */
    fun list(hits: List<Txn>): List<Txn> = when (val r = reply) {
        is Reply.Done -> if (picked.value) r.a.txns else hits.ifEmpty { r.a.matches }
        else -> hits
    }
}

@Composable
fun rememberAsk(q: String, s: AppState): AskState {
    val reply = remember(q, s.stats) { Ask.reply(q, LocalDate.now(), s.visible, s::catOf, s.stats::counts) }
    val picked = rememberSaveable(q) { mutableStateOf(false) }
    return remember(reply, picked) { AskState(reply, picked) }
}

/** The answer card, a short note when a question didn't parse, or example questions on an empty box. */
fun LazyListScope.askItems(ask: AskState, q: String, s: AppState, onExample: (String) -> Unit) {
    when (val r = ask.reply) {
        is Reply.Done -> item(key = "ask") { AnswerCard(r.a, ask.narrowed, ask::toggle, Modifier.rise(0).padding(top = 4.dp, bottom = 6.dp)) }
        is Reply.Unclear -> item(key = "ask") { Text(r.why, style = T.small, modifier = Modifier.padding(vertical = 8.dp)) }
        null -> if (q.isBlank()) item(key = "ask") { Examples(s, onExample) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Examples(s: AppState, onPick: (String) -> Unit) {
    val list = remember(s.stats) { Ask.examples(s.stats.topPlaces(30, 1).firstOrNull()?.payee) }
    Column(Modifier.rise(0).padding(top = 10.dp)) {
        Text("Or ask", style = T.label, modifier = Modifier.padding(bottom = 10.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            list.forEach { Chip(it, false, { onPick(it) }) }
        }
    }
}

@Composable
private fun AnswerCard(a: Answer, narrowed: Boolean, onPick: () -> Unit, modifier: Modifier) {
    FrostCard(modifier, pad = 20.dp, onClick = if (a.txns.isEmpty()) null else onPick) {
        Column(Modifier.animateContentSize()) {
            Text(a.title, style = T.label)
            if (a.isCount) Text(a.value.toString(), style = T.display, maxLines = 1) else CountUp(a.value, T.display)
            Spacer(Modifier.height(6.dp))
            Text(a.sentence, style = T.small)
            if (a.bars.size > 3) MonthBars(a.bars) else if (a.bars.isNotEmpty()) TopBars(a.bars)
            if (a.txns.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                val n = a.txns.size
                Text(
                    when {
                        narrowed -> "Showing just what was counted. Tap to see all matches."
                        n == 1 -> "Tap to list just this one"
                        else -> "Tap to list just these $n"
                    },
                    style = T.tiny.copy(color = C.tint),
                )
            }
        }
    }
}

/** Up to 12 small columns, one per month. */
@Composable
private fun MonthBars(bars: List<Bar>) {
    val max = bars.maxOf { it.value }.coerceAtLeast(1L)
    Spacer(Modifier.height(16.dp))
    Row(Modifier.fillMaxWidth().height(64.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.Bottom) {
        bars.forEachIndexed { i, b ->
            val frac = if (b.value == 0L) 0f else (b.value.toFloat() / max).coerceAtLeast(.06f)
            Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.BottomCenter) {
                Box(
                    Modifier.fillMaxWidth().fillMaxHeight(grown(frac.coerceAtLeast(.03f), 60L + 25L * i).coerceAtLeast(.001f))
                        .clip(RoundedCornerShape(5.dp)).background(if (b.value == 0L) C.noSpend else C.heat[2]),
                )
            }
        }
    }
    Spacer(Modifier.height(6.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        bars.forEach { b ->
            Text(b.label.take(3), textAlign = TextAlign.Center, modifier = Modifier.weight(1f), style = T.tiny.copy(fontSize = 11.sp, color = C.faint), maxLines = 1)
        }
    }
}

/** Two or three named rows with a bar each. */
@Composable
private fun TopBars(bars: List<Bar>) {
    val max = bars.maxOf { it.value }.coerceAtLeast(1L)
    Spacer(Modifier.height(8.dp))
    bars.forEachIndexed { i, b ->
        Column(Modifier.padding(top = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(b.label, style = T.small.copy(color = C.ink), modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.width(8.dp))
                Text(b.text, style = T.label.copy(color = C.ink))
            }
            Spacer(Modifier.height(4.dp))
            GrowBar(b.value.toFloat() / max, if (i == 0) C.tint else C.heat[1], delayMs = 80L * i)
        }
    }
}
