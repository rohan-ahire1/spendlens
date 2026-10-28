package com.rohan.spend.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rohan.spend.data.Cat
import com.rohan.spend.data.Split
import com.rohan.spend.data.Txn
import com.rohan.spend.data.date
import com.rohan.spend.data.inr
import java.time.format.DateTimeFormatter

private val shortDay = DateTimeFormatter.ofPattern("d MMM")

private fun Split.label() = if (people >= 2) "Split $people ways" else "Split"

/** Splitting a payment with others, and the paybacks that come in for it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SplitCard(t: Txn, s: AppState, vm: AppViewModel, nav: Nav) {
    var editing by rememberSaveable { mutableStateOf(false) }
    val sp = s.splits[t.id]
    val paidFor = s.paybacks[t.id]?.let(s.byId::get)
    when {
        paidFor != null -> FrostCard(Modifier.padding(top = 12.dp), pad = 6.dp) {
            Column {
                LinkRow("Paid back for ${paidFor.payee} on ${paidFor.date().format(shortDay)}") { nav.push(Route.TxnDetail(paidFor.id)) }
                Row(Modifier.padding(start = 12.dp, bottom = 10.dp)) { Chip("Not a payback", false, { vm.unlinkPayback(t) }) }
            }
        }
        !t.debit || s.catOf(t) == Cat.TRANSFER -> return
        sp == null -> FrostCard(Modifier.padding(top = 12.dp), pad = 6.dp) { LinkRow("Split this payment") { editing = true } }
        else -> FrostCard(Modifier.padding(top = 12.dp), pad = 6.dp) {
            Column {
                LinkRow("${sp.label()} · yours ${inr(sp.mine)}") { editing = true }
                val owed = s.owed(t)
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 14.dp)) {
                    Text("Owed to you", style = T.label, modifier = Modifier.weight(1f))
                    Text(if (owed > 0) inr(owed) else "All paid back", style = T.num.copy(color = if (owed > 0) C.ink else C.green))
                }
                s.paidBack[t.id]?.forEach { c ->
                    LinkRow("${inr(c.amount)} back from ${c.payee} on ${c.date().format(shortDay)}") { nav.push(Route.TxnDetail(c.id)) }
                }
                s.paybackHints(t).take(3).forEach { c ->
                    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)) {
                        Text("Got ${inr(c.amount)} back from ${c.payee}?", style = T.body)
                        Text(dayLabel(c.date(), s.today) + " · " + c.timeLabel(), style = T.tiny)
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Chip("Yes", false, { vm.confirmPayback(c, t) })
                            Chip("No", false, { vm.rejectPayback(c, t) })
                            Chip("See it", false, { nav.push(Route.TxnDetail(c.id)) })
                        }
                    }
                }
            }
        }
    }

    if (editing) {
        val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet({ editing = false }, sheetState = sheet, containerColor = Color(0xFFF7F8FB)) {
            SplitPicker(t.amount, sp, onPick = { vm.setSplit(t, it); editing = false })
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SplitPicker(amount: Long, current: Split?, onPick: (Split?) -> Unit) {
    var custom by rememberSaveable { mutableStateOf(current?.takeIf { it.people < 2 }?.let { (it.mine / 100).toString() } ?: "") }
    Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
        Text("Split this payment", style = T.h2)
        Text("Only your share of ${inr(amount)} counts in your totals.", style = T.small)
        Spacer(Modifier.height(16.dp))
        Text("People, you included", style = T.label)
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            (2..10).forEach { n -> Chip("$n", current?.people == n, { onPick(Split.even(amount, n)) }) }
        }
        current?.takeIf { it.people >= 2 }?.let { Text("${inr(it.mine)} each", style = T.tiny, modifier = Modifier.padding(top = 8.dp)) }
        Spacer(Modifier.height(18.dp))
        Text("Or set your share", style = T.label)
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                custom, { v -> custom = v.filter(Char::isDigit).take(8) }, Modifier.weight(1f), singleLine = true,
                prefix = { Text("₹") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            Spacer(Modifier.width(8.dp))
            val mine = custom.toLongOrNull()?.times(100)
            TextButton({ onPick(Split(0, mine!!)) }, enabled = mine != null && mine < amount) { Text("Save", color = C.ink) }
        }
        if (current != null) {
            Spacer(Modifier.height(16.dp))
            Chip("Remove split", false, { onPick(null) })
        }
    }
}

/** A small Insights section: split payments that people still owe you for. */
fun LazyListScope.owedSection(s: AppState, nav: Nav) {
    val owed = s.owedToYou
    if (owed.isEmpty()) return
    item {
        SectionTitle("Owed to you", Modifier.rise(1)) { Text(inr(owed.sumOf { it.second }), style = T.num.copy(color = C.green)) }
    }
    item {
        FrostCard(Modifier.rise(2), pad = 14.dp) {
            Column {
                owed.take(3).forEach { (t, v) ->
                    Row(Modifier.fillMaxWidth().press({ nav.push(Route.TxnDetail(t.id)) }).padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        CatIcon(s.catOf(t))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(t.payee, style = T.body, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            val hints = s.paybackHints(t).size
                            val sub = dayLabel(t.date(), s.today) + " · " + s.splits.getValue(t.id).label().lowercase() +
                                if (hints > 0) " · check $hints ${if (hints == 1) "payback" else "paybacks"}" else ""
                            Text(sub, style = T.tiny, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(inr(v), style = T.num)
                    }
                }
                if (owed.size > 3) Text("and ${owed.size - 3} more", style = T.tiny, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}
