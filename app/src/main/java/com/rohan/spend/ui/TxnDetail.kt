package com.rohan.spend.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import com.rohan.spend.data.ref
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.rohan.spend.data.Cat
import com.rohan.spend.data.Txn
import com.rohan.spend.data.date
import com.rohan.spend.data.inr
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TxnScreen(t: Txn, s: AppState, vm: AppViewModel, nav: Nav) {
    val cat = s.catOf(t)
    val always = s.payeeRules[t.payeeKey] != null
    var picking by rememberSaveable { mutableStateOf(false) }
    var editingNote by rememberSaveable { mutableStateOf(false) }
    var showSms by rememberSaveable { mutableStateOf(false) }
    val hidden = t.id in s.hidden
    val (atPayee, visits) = s.stats.atPayeeThisMonth(t)

    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()
            .verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
    ) {
        TopBar("", nav::pop)
        val style = cat.style()
        Column(Modifier.rise(0).padding(top = 6.dp).fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(style.bg).padding(18.dp)) {
            Row(
                Modifier.clip(CircleShape).background(Color.White.copy(alpha = .7f)).press({ picking = true }).padding(start = 4.dp, end = 10.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CatIcon(cat, 26.dp)
                Spacer(Modifier.width(6.dp))
                Text(cat.label, style = T.label.copy(color = style.fg))
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = style.fg, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.height(16.dp))
            Text(t.payee, style = T.h2, maxLines = 2)
            Text(inr(t.signed, signed = !t.debit), style = T.display.copy(color = if (t.debit) C.ink else C.green))
            Text(t.date().format(DateTimeFormatter.ofPattern("EEE, d MMM yyyy")) + " · " + t.timeLabel(), style = T.label)
            if (hidden) Box(Modifier.padding(top = 10.dp)) { Pill("Hidden from totals", C.warnInk, C.warnBg) }
        }

        FrostCard(Modifier.rise(1).padding(top = 12.dp), pad = 6.dp) {
            Column {
                Line("Paid with") { Text(bankName(t.sender) + " " + t.channel.label + (t.account?.let { " ··$it" } ?: ""), style = T.body) }
                t.ref?.let { r -> Line("Reference") { Text(r, style = T.num) } }
                if (t.balance != null) Line("Balance after") { Text(inr(t.balance), style = T.num) }
                if (t.debit && visits > 0) Line("At ${t.payee.take(16)} this month") {
                    Text(inr(atPayee) + " · " + if (visits == 1) "1 visit" else "$visits visits", style = T.num)
                }
                Line("Note", onClick = { editingNote = true }) {
                    Text(s.notes[t.id] ?: "Add", style = T.body.copy(color = if (s.notes[t.id] == null) C.tint else C.heat[3]))
                }
            }
        }
        TxnLinks(t, s, nav)
        SplitCard(t, s, vm, nav)

        FrostCard(Modifier.rise(2).padding(top = 12.dp), pad = 6.dp) {
            Line("Always file ${t.payee.take(18)} here") { FrostSwitch(always) { on ->
                vm.undoable(if (on) "${t.payee} always goes to ${cat.label}" else "Rule for ${t.payee} removed") { vm.setAlways(t, on) }
            } }
        }

        Row(Modifier.rise(3).padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip("Change category", false, { picking = true })
            Chip(if (hidden) "Count again" else "Hide", false, { vm.undoable(if (hidden) "Counted again" else "Hidden from totals") { vm.toggleHidden(t) } })
            Chip(if (showSms) "Hide SMS" else "See SMS", showSms, { showSms = !showSms })
        }
        if (showSms) {
            FrostCard(Modifier.padding(top = 12.dp)) {
                Column {
                    Text(t.sender, style = T.tiny)
                    Spacer(Modifier.height(6.dp))
                    Text(t.body, style = T.small.copy(color = C.ink, fontFamily = FontFamily.Monospace))
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }

    if (picking) {
        val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        var keep by rememberSaveable { mutableStateOf(true) }
        ModalBottomSheet({ picking = false }, sheetState = sheet, containerColor = Color(0xFFF7F8FB)) {
            CategoryPicker(
                current = cat,
                options = if (t.debit) Cat.spendable else Cat.entries.filter { it !in Cat.spendable || it == Cat.TRANSFER },
                keep = keep, onKeep = { keep = it }, payee = t.payee,
            ) { picked ->
                val msg = if (keep) "All of ${t.payee} moved to ${picked.label}" else "Moved to ${picked.label}"
                vm.undoable(msg) { vm.setCategory(t, picked, keep) }
                picking = false
            }
        }
    }

    if (editingNote) {
        var text by rememberSaveable { mutableStateOf(s.notes[t.id] ?: "") }
        AlertDialog(
            onDismissRequest = { editingNote = false },
            title = { Text("Note") },
            text = { OutlinedTextField(text, { text = it }, singleLine = true, placeholder = { Text("e.g. split with Arjun") }) },
            confirmButton = { TextButton({ vm.setNote(t, text); editingNote = false }) { Text("Save", color = C.ink) } },
            dismissButton = { TextButton({ editingNote = false }) { Text("Cancel", color = C.muted) } },
            containerColor = Color.White,
        )
    }
}

@Composable
private fun Line(label: String, onClick: (() -> Unit)? = null, value: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().then(if (onClick != null) Modifier.press(onClick) else Modifier).padding(horizontal = 12.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // A gap so a long label and value never run together with large text.
        Text(label, style = T.label, modifier = Modifier.weight(1f).padding(end = 12.dp))
        value()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryPicker(current: Cat, options: List<Cat>, keep: Boolean, onKeep: (Boolean) -> Unit, payee: String, onPick: (Cat) -> Unit) {
    Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
        Text("Category", style = T.h2)
        Spacer(Modifier.height(14.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(10.dp), maxItemsInEachRow = 4) {
            options.forEachIndexed { i, c ->
                val on = c == current
                Column(
                    Modifier.weight(1f).rise(i).press({ onPick(c) }, haptic = true)
                        .clip(RoundedCornerShape(16.dp)).background(if (on) Color.White else Color.Transparent).padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CatIcon(c, 44.dp)
                    Spacer(Modifier.height(6.dp))
                    Text(c.label, style = T.tiny.copy(color = if (on) C.ink else C.subtle), textAlign = TextAlign.Center, maxLines = 1, softWrap = false)
                }
            }
            // Pad the last row so its cells keep the same width as the rows above.
            repeat((4 - options.size % 4) % 4) { Spacer(Modifier.weight(1f)) }
        }
        Spacer(Modifier.height(18.dp))
        Row(Modifier.fillMaxWidth().frost(18.dp).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Do this for all of $payee", style = T.body)
                Text("Past and future payments", style = T.tiny)
            }
            FrostSwitch(keep, onKeep)
        }
    }
}

