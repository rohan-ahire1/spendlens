package com.rohan.spend.ui

import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rohan.spend.data.Cat
import com.rohan.spend.data.Txn
import com.rohan.spend.data.date
import com.rohan.spend.data.inr
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val longDay = DateTimeFormatter.ofPattern("EEEE, d MMMM")
private val monthYear = DateTimeFormatter.ofPattern("MMMM yyyy")

/** Every payment on one day, with when in the day the money went. Arrows step to the next and previous day. */
@Composable
fun DayScreen(start: LocalDate, s: AppState, nav: Nav) {
    var epoch by rememberSaveable { mutableStateOf(start.toEpochDay()) }
    var only by rememberSaveable { mutableStateOf<Boolean?>(null) }
    var biggest by rememberSaveable { mutableStateOf(true) }
    val day = LocalDate.ofEpochDay(epoch)
    val today = LocalDate.now()
    val st = s.stats
    val all = remember(s.visible, day) { s.visible.filter { it.date() == day } }
    val spent = all.filter(st::counts).sumOf { it.amount }
    val shown = all.filter { only == null || it.debit == only }.let { l -> if (biggest) l.sortedByDescending { it.amount } else l.sortedByDescending { it.time } }
    val perDay = s.usual() / 30

    LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 40.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                RoundButton(Icons.AutoMirrored.Rounded.ArrowBack, "Back", nav::pop)
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(day.format(monthYear), style = T.label)
                    Text(day.format(longDay), style = T.h3.copy(fontSize = 19.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                RoundButton(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, "Previous day", { epoch -= 1 }, size = 36.dp)
                Spacer(Modifier.width(6.dp))
                Box(Modifier.alpha(if (day < today) 1f else .35f)) {
                    RoundButton(Icons.AutoMirrored.Rounded.KeyboardArrowRight, "Next day", { if (day < today) epoch += 1 }, size = 36.dp)
                }
            }
        }
        item {
            Row(Modifier.rise(0).fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Text("Spent this day", style = T.label)
                    CountUp(spent, T.display)
                }
                if (spent > 0 && perDay > 0) {
                    val x = spent.toFloat() / perDay
                    Box(Modifier.padding(bottom = 8.dp)) {
                        when {
                            x >= 1.5f -> Pill("${if (x >= 10) x.toInt().toString() else String.format(Locale.US, "%.1f", x)}× a usual day", C.warnInk, C.warnBg)
                            x < .5f -> Pill("A light day", C.green, C.green.copy(alpha = .1f))
                            else -> Pill("About a usual day", C.muted, C.noSpend)
                        }
                    }
                }
            }
        }
        item { Hours(Modifier.rise(1).padding(top = 16.dp), all) }
        item {
            Row(Modifier.rise(2).padding(top = 16.dp, bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Chip("All · ${all.size}", only == null, { only = null })
                Chip("Spent", only == true, { only = true })
                Chip("Received", only == false, { only = false })
                Spacer(Modifier.weight(1f))
                Text(if (biggest) "Biggest" else "Newest", style = T.label, modifier = Modifier.press({ biggest = !biggest }).padding(6.dp))
            }
        }
        item {
            FrostCard(Modifier.rise(3), pad = 14.dp) {
                AnimatedContent(
                    epoch, label = "day",
                    transitionSpec = {
                        val dir = if (targetState > initialState) 1 else -1
                        (slideInHorizontally(tween(300)) { it / 4 * dir } + fadeIn(tween(300))) togetherWith fadeOut(tween(150)) using SizeTransform(clip = false)
                    },
                ) { _ ->
                Column(Modifier.animateContentSize()) {
                    if (shown.isEmpty()) EmptyNote(if (day > today) "This day hasn't happened yet." else "Nothing here on this day.")
                    shown.forEach { t ->
                        val share = if (t.debit && spent > 0 && st.counts(t)) (t.amount * 100 / spent).let { if (it < 1) "<1% of day" else "$it% of day" } else null
                        PayRow(t, s.catOf(t), s.notes[t.id], share) { nav.push(Route.TxnDetail(t.id)) }
                    }
                }
                }
            }
        }
    }
}

/** 24 small bars, one per hour, so you can see when in the day money went. */
@Composable
private fun Hours(modifier: Modifier, txns: List<Txn>) {
    val zone = ZoneId.systemDefault()
    val byHour = LongArray(24).also { a -> txns.filter { it.debit }.forEach { a[Instant.ofEpochMilli(it.time).atZone(zone).hour] += it.amount } }
    val max = byHour.max().coerceAtLeast(1)
    FrostCard(modifier, pad = 14.dp) {
        Column {
            Row {
                Text("When it went", style = T.label, modifier = Modifier.weight(1f))
                Text(txns.count { it.debit }.let { if (it == 1) "1 payment" else "$it payments" }, style = T.label)
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth().height(36.dp), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.Bottom) {
                byHour.forEachIndexed { i, v ->
                    val f = grown(if (v == 0L) .12f else kotlin.math.sqrt(v.toFloat() / max).coerceAtLeast(.25f), 80L + 15L * i).coerceAtLeast(.001f)
                    val color by animateColorAsState(
                        when {
                            v == 0L -> C.noSpend
                            v * 4 >= max -> C.heat[3]
                            else -> C.heat[1]
                        }, label = "hour",
                    )
                    Box(Modifier.weight(1f).fillMaxHeight(f).clip(RoundedCornerShape(3.dp)).background(color))
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                listOf("12a", "6a", "12p", "6p", "12a").forEach { Text(it, style = T.tiny.copy(fontSize = 11.sp, color = C.faint)) }
            }
        }
    }
}

/**
 * A payment with everything you'd want at a glance: category, time, the account it came from,
 * the note, and the balance left after. [extra] replaces the balance line when given.
 */
@Composable
fun PayRow(t: Txn, cat: Cat, note: String?, extra: String?, lead: (@Composable () -> Unit)? = null, iconSize: Dp = 40.dp, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().press(onClick).padding(vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
        if (lead != null) lead() else CatIcon(cat, iconSize)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(t.payee, style = T.body.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
            val sub = buildList {
                if (lead != null) add(t.timeLabel()) else add("${cat.label} · ${t.timeLabel()}")
                add(t.channel.label + (t.account?.let { " ··$it" } ?: ""))
                if (!note.isNullOrBlank()) add(note)
            }.joinToString(" · ")
            Text(sub, style = T.tiny, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(inr(t.signed, signed = !t.debit), style = T.num.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = if (t.debit) C.ink else C.green))
            val below = extra ?: t.balance?.let { "bal ${inr(it)}" }
            if (below != null) Text(below, style = T.tiny.copy(fontSize = 12.sp, color = C.faint))
        }
    }
}
