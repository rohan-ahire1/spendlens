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
import androidx.compose.runtime.remember
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
    var picking by remember { mutableStateOf(false) }
    var editingNote by remember { mutableStateOf(false) }
    var showSms by remember { mutableStateOf(false) }
    val hidden = t.id in s.hidden
    val (atPayee, visits) = s.stats.atPayeeThisMonth(t)

    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()
            .verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
    ) {
        TopBar("", nav::pop)
        Column(Modifier.fillMaxWidth().padding(vertical = 18.dp).rise(0), horizontalAlignment = Alignment.CenterHorizontally) {
            CatIcon(cat, 64.dp)
            Spacer(Modifier.height(14.dp))
            Text(t.payee, style = T.h2, textAlign = TextAlign.Center)
            Spacer(Modifier.height(6.dp))
            Text(inr(t.signed, signed = !t.debit), style = T.hero.copy(color = if (t.debit) C.ink else C.green))
            Text(t.date().format(DateTimeFormatter.ofPattern("EEE, d MMM yyyy")) + " · " + t.timeLabel(), style = T.label)
            if (hidden) Box(Modifier.padding(top = 10.dp)) { Pill("Hidden from totals", C.warnInk, C.warnBg) }
        }

        FrostCard(Modifier.rise(1), pad = 6.dp) {
            Column {
                Line("Category", onClick = { picking = true }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CatIcon(cat, 24.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(cat.label, style = T.body)
                        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = C.faint)
                    }
                }
                Line("Always file ${t.payee.take(18)} here") { FrostSwitch(always) { vm.setAlways(t, it) } }
                Line("Paid with") { Text(t.channel.label + (t.account?.let { " ··$it" } ?: ""), style = T.body) }
                if (t.balance != null) Line("Balance after") { Text(inr(t.balance), style = T.num) }
                Line("Note", onClick = { editingNote = true }) {
                    Text(s.notes[t.id] ?: "Add", style = T.body.copy(color = if (s.notes[t.id] == null) C.tint else C.ink))
                }
            }
        }

        if (t.debit && visits > 1) {
            FrostCard(Modifier.padding(top = 12.dp).rise(2)) {
                Text("${inr(atPayee)} at ${t.payee} this month across $visits payments.", style = T.small.copy(color = C.ink))
            }
        }

        SectionTitle("Original message", Modifier.rise(3)) {
            Text(if (showSms) "Hide" else "Show", style = T.label.copy(color = C.tint), modifier = Modifier.press({ showSms = !showSms }))
        }
        if (showSms) {
            FrostCard {
                Column {
                    Text(t.sender, style = T.tiny)
                    Spacer(Modifier.height(6.dp))
                    Text(t.body, style = T.small.copy(color = C.ink, fontFamily = FontFamily.Monospace))
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        GhostButton(if (hidden) "Count this again" else "Hide from totals", { vm.toggleHidden(t) })
        Spacer(Modifier.height(24.dp))
    }

    if (picking) {
        val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        var keep by remember { mutableStateOf(true) }
        ModalBottomSheet({ picking = false }, sheetState = sheet, containerColor = Color(0xFFF7F8FB)) {
            CategoryPicker(
                current = cat,
                options = if (t.debit) Cat.spendable else Cat.entries.filter { it !in Cat.spendable },
                keep = keep, onKeep = { keep = it }, payee = t.payee,
            ) { picked ->
                vm.setCategory(t, picked, keep)
                picking = false
            }
        }
    }

    if (editingNote) {
        var text by remember { mutableStateOf(s.notes[t.id] ?: "") }
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
        Text(label, style = T.label, modifier = Modifier.weight(1f))
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
