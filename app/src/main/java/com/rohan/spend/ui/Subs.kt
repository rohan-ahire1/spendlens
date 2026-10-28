package com.rohan.spend.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rohan.spend.data.Cadence
import com.rohan.spend.data.Cat
import com.rohan.spend.data.Renewal
import com.rohan.spend.data.Subscription
import com.rohan.spend.data.inr
import com.rohan.spend.data.inrShort
import com.rohan.spend.data.perMonth
import com.rohan.spend.data.renewsIn
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val dayMon = DateTimeFormatter.ofPattern("d MMM")
private val mon = DateTimeFormatter.ofPattern("MMM")

/** "Up ₹100 since Aug", or null when the price hasn't gone up. */
fun Subscription.riseLabel(today: LocalDate = LocalDate.now()): String? = rise?.let {
    "Up ${inr(it.by)} since ${it.since.format(if (it.since.year == today.year) mon else DateTimeFormatter.ofPattern("MMM yyyy"))}"
}

/** What live subscriptions cost, a month and a year. */
@Composable
fun SubsTotal(subs: List<Subscription>, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val live = subs.filter { !it.cancelled }
    val month = subs.perMonth()
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(C.heat[0])
            .then(if (onClick != null) Modifier.press(onClick) else Modifier).padding(20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(inr(month), style = T.big.copy(color = C.heat[3]))
            Text("a month on ${live.size} ${if (live.size == 1) "subscription" else "subscriptions"}", style = T.small)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(inrShort(month * 12), style = T.h3.copy(color = C.heat[3]))
            Text("a year", style = T.tiny)
        }
    }
}

/** Each subscription with when it next charges, how often, and what it costs a month. */
@Composable
fun SubsList(subs: List<Subscription>, modifier: Modifier = Modifier, onOpen: ((Subscription) -> Unit)? = null) {
    val today = LocalToday.current
    FrostCard(modifier, pad = 14.dp) {
        Column {
            subs.forEach { sub -> SubRow(sub, today, onOpen?.let { { it(sub) } }) }
        }
    }
}

@Composable
private fun SubRow(sub: Subscription, today: LocalDate, onClick: (() -> Unit)?) {
    val days = sub.daysUntil(today)
    Row(
        Modifier.fillMaxWidth().then(if (onClick != null) Modifier.press(onClick) else Modifier).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CatIcon(Cat.SUBSCRIPTIONS, 36.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(sub.payee, style = T.body.copy(fontWeight = FontWeight.SemiBold, color = if (sub.cancelled) C.faint else C.ink),
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            val line = when {
                sub.cancelled -> "Was due ${sub.next.format(dayMon)} · ${sub.cadence.label}"
                days in 0..3 -> "${renewsIn(days).replaceFirstChar { it.uppercase() }} · ${sub.cadence.label}"
                else -> "Next ${sub.next.format(dayMon)} · ${sub.cadence.label}"
            }
            Text(line, style = T.tiny, maxLines = 1)
            val rise = sub.riseLabel(today)
            if (sub.cancelled || rise != null) Row(Modifier.padding(top = 5.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (sub.cancelled) Pill("Likely cancelled", C.muted, C.noSpend)
                else if (rise != null) Pill("$rise · +${sub.rise!!.pct}%", C.warnInk, C.warnBg)
            }
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(inr(sub.monthly), style = T.num.copy(color = if (sub.cancelled) C.faint else C.ink))
            Text(if (sub.cadence == Cadence.MONTHLY) "a month" else "${inr(sub.amount)} ${if (sub.cadence == Cadence.YEARLY) "a year" else "a quarter"}", style = T.tiny)
        }
    }
}

/** Home's heads-up when a subscription is about to charge. */
@Composable
fun RenewalCard(r: List<Renewal>, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val first = r.first()
    FrostCard(modifier, pad = 14.dp, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CatIcon(Cat.SUBSCRIPTIONS, 36.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("${first.sub.payee} ${renewsIn(first.days)} · ${inr(first.sub.amount)}", style = T.body.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                val more = r.drop(1)
                val sub = when {
                    more.isNotEmpty() -> "Also ${more.joinToString { it.sub.payee }} · ${inr(r.sumOf { it.sub.amount })} in all"
                    first.sub.rise != null -> first.sub.riseLabel()!!
                    else -> first.sub.cadence.label
                }
                Text(sub, style = T.tiny, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
