package com.rohan.spend.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rohan.spend.data.Txn
import com.rohan.spend.data.date
import com.rohan.spend.data.inr
import java.time.format.DateTimeFormatter

private val shortDay = DateTimeFormatter.ofPattern("d MMM")

/** How this transaction ties to others: refunds it got, or the payment it refunds. */
@Composable
fun TxnLinks(t: Txn, s: AppState, nav: Nav) {
    val rows = buildList<Pair<String, Txn>> {
        s.refundsOf[t.id]?.forEach { r -> add("${inr(r.amount)} refunded on ${r.date().format(shortDay)}" to r) }
        s.refundOf[t.id]?.let(s.byId::get)?.let { o -> add("Refund for ${o.payee} on ${o.date().format(shortDay)}" to o) }
    }
    if (rows.isEmpty()) return
    FrostCard(Modifier.padding(top = 12.dp), pad = 6.dp) {
        Column { rows.forEach { (text, other) -> LinkRow(text) { nav.push(Route.TxnDetail(other.id)) } } }
    }
}

@Composable
fun LinkRow(text: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().press(onClick).padding(horizontal = 12.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = T.body, modifier = Modifier.weight(1f))
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = C.faint, modifier = Modifier.size(20.dp))
    }
}
