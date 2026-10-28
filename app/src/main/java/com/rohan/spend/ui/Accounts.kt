package com.rohan.spend.ui

import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.unit.sp
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Close
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.rohan.spend.data.Cat
import com.rohan.spend.data.RuleInfo
import com.rohan.spend.data.ruleInfos
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.text.style.TextOverflow
import com.rohan.spend.data.Stats
import com.rohan.spend.data.inr
import com.rohan.spend.data.groupIndian
import androidx.compose.material3.CircularProgressIndicator
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountsScreen(s: AppState, vm: AppViewModel, nav: Nav) {
    var editTarget by rememberSaveable { mutableStateOf(false) }
    var showRules by rememberSaveable { mutableStateOf(false) }
    var confirmReset by rememberSaveable { mutableStateOf(false) }
    var addBudget by rememberSaveable { mutableStateOf(false) }
    if (addBudget) AddBudgetDialog(s, vm::setBudget) { addBudget = false }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        uri?.let(vm::exportCsv)
    }
    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = BarClearance),
    ) {
        item { TabHeader("Accounts", nav) }
        item(contentType = "split") {
            val accounts = s.stats.accounts
            if (accounts.isEmpty()) EmptyNote("No accounts found in your messages yet.")
            else MonthSplit(accounts)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
                itemsIndexed(accounts) { i, a -> Box(Modifier.rise(i + 1)) { AccountCard(a) } }
            }
        }

        item { SectionTitle("Counting", Modifier.rise(2)) }
        item {
            FrostCard(Modifier.rise(3), pad = 6.dp) {
                Column {
                    Setting("Monthly target", if (s.target > 0) inr(s.target) else "Auto, ${inr(s.stats.usualMonth)}", onClick = { editTarget = true }) {
                        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = C.faint)
                    }
                    Setting("Leave out family", "Money sent home doesn't count as living") {
                        FrostSwitch(s.excludeFamily, vm::setExcludeFamily)
                    }
                    Setting("Count card bill payments", "Off avoids counting card spends twice") {
                        FrostSwitch(s.countCardBills, vm::setCountCardBills)
                    }
                    Setting("Check for new messages on open", null) {
                        FrostSwitch(s.autoRefresh, vm::setAutoRefresh)
                    }
                }
            }
        }

        item { SectionTitle("Budgets", Modifier.rise(4)) }
        item { BudgetList(s, nav, { addBudget = true }, Modifier.rise(4)) }

        item { SectionTitle("Your data", Modifier.rise(4)) }
        item {
            FrostCard(Modifier.rise(5), pad = 6.dp) {
                Column {
                    Setting("Payee rules", "${s.payeeRules.size} saved", onClick = { showRules = true }) {
                        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = C.faint)
                    }
                    val hiddenCount = s.txns.count { it.id in s.hidden }
                    Setting("Hidden payments", if (hiddenCount == 0) "None" else "$hiddenCount left out of totals", onClick = { nav.push(Route.Hidden) }) {
                        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = C.faint)
                    }
                    Setting("Export to CSV", "${s.visible.size} transactions", onClick = { export.launch("spend-${LocalDate.now()}.csv") }) {
                        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = C.faint)
                    }
                    Setting("Reset my edits", "Categories, notes, budgets, splits, hidden items and settings", onClick = { confirmReset = true }) {}
                    RescanRow(s, vm)
                }
            }
        }
        item {
            Text("Spend Lens has no internet permission. Your messages and edits never leave this phone.",
                style = T.tiny, modifier = Modifier.padding(top = 18.dp))
        }
    }

    if (editTarget) {
        var text by rememberSaveable { mutableStateOf(if (s.target > 0) (s.target / 100).toString() else "") }
        AlertDialog(
            onDismissRequest = { editTarget = false },
            title = { Text("Monthly target") },
            text = {
                Column {
                    Text("Leave empty to use your usual month (${inr(s.stats.usualMonth)}).", style = T.small)
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(text, { v -> text = v.filter(Char::isDigit).take(8) }, singleLine = true,
                        prefix = { Text("₹") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                }
            },
            confirmButton = { TextButton({ vm.setTarget((text.toLongOrNull() ?: 0) * 100); editTarget = false }) { Text("Save", color = C.ink) } },
            dismissButton = { TextButton({ editTarget = false }) { Text("Cancel", color = C.muted) } },
            containerColor = Color.White,
        )
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Reset your edits?") },
            text = { Text("Your categories, notes, budgets, splits, hidden items and settings go back to default. Your messages aren't touched.") },
            confirmButton = { TextButton({ vm.resetEdits(); confirmReset = false }) { Text("Reset", color = C.red) } },
            dismissButton = { TextButton({ confirmReset = false }) { Text("Cancel", color = C.muted) } },
            containerColor = Color.White,
        )
    }

    if (showRules) {
        val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet({ showRules = false }, sheetState = sheet, containerColor = Color(0xFFF7F8FB)) {
            Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
                Text("Payee rules", style = T.h2)
                Text("Payments to these always go to the category shown.", style = T.small)
                Spacer(Modifier.height(10.dp))
                if (s.payeeRules.isEmpty()) EmptyNote("None yet. Change a category and keep \"Do this for all\" on.")
                val rules = remember(s.payeeRules, s.txns) { ruleInfos(s.payeeRules, s.txns) }
                LazyColumn(Modifier.weight(1f, fill = false)) {
                    items(rules, key = { it.key }) { r ->
                        RuleRow(r) { vm.undoable("Rule for ${r.name} removed") { vm.removeRule(r.key) } }
                    }
                }
                // The sheet covers the app-wide host, so it shows its own.
                val snack by vm.snack.collectAsStateWithLifecycle()
                SnackHost(snack, vm::undo, Modifier.padding(top = 8.dp))
            }
        }
    }
}

@Composable
private fun RuleRow(r: RuleInfo, onRemove: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        CatIcon(r.cat, 34.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(r.name, style = T.body, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val n = when (r.count) { 0 -> "no payments yet"; 1 -> "1 payment"; else -> "${r.count} payments" }
            Text("${r.cat.label} · $n", style = T.tiny, maxLines = 1)
            if (r.name != r.key) Text(r.key, style = T.tiny.copy(color = C.faint, fontSize = 11.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        RoundButton(Icons.Rounded.Close, "Remove rule", onRemove, size = 34.dp)
    }
}

/** Which account this month's money left from, as one split bar of the heat colors. */
@Composable
private fun MonthSplit(accounts: List<Stats.Account>) {
    val spending = accounts.filter { it.monthSpend > 0 }.sortedByDescending { it.monthSpend }
    if (spending.isEmpty()) return
    val total = spending.sumOf { it.monthSpend }
    val shades = listOf(C.heat[3], C.heat[2], C.heat[1], C.heat[0])
    FrostCard(Modifier.rise(0).padding(top = 4.dp, bottom = 6.dp), radius = 28.dp, pad = 20.dp) {
        Column {
            Text("Left your accounts this month", style = T.label)
            CountUp(total, T.display)
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth().height(28.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                spending.forEachIndexed { i, a ->
                    val g = grown(1f, 100L + 80L * i).coerceAtLeast(.001f)
                    Box(Modifier.weight(a.monthSpend.toFloat() / total).graphicsLayer { scaleX = g; transformOrigin = TransformOrigin(0f, .5f) }.fillMaxHeight().clip(RoundedCornerShape(8.dp)).background(shades[minOf(i, 3)]))
                }
            }
            Spacer(Modifier.height(12.dp))
            spending.forEachIndexed { i, a ->
                Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(shades[minOf(i, 3)]))
                    Spacer(Modifier.width(8.dp))
                    Text("${bankName(a.sender)} ··${a.id}", style = T.small.copy(color = C.ink), modifier = Modifier.weight(1f))
                    Text("${(a.monthSpend * 100 / total)}%", style = T.tiny, modifier = Modifier.padding(end = 10.dp))
                    Text(inr(a.monthSpend), style = T.num.copy(fontSize = T.small.fontSize))
                }
            }
        }
    }
}

@Composable
private fun AccountCard(a: Stats.Account) {
    Box(Modifier.width(220.dp).height(132.dp).clip(RoundedCornerShape(22.dp)).background(if (a.isCard) C.ink else C.heat[3])) {
        // The last digits, set huge and faint, as the card's texture.
        Text(
            a.id.takeLast(4), style = T.display.copy(fontSize = 84.sp, color = Color.White.copy(alpha = .07f), letterSpacing = (-4).sp),
            modifier = Modifier.align(Alignment.BottomEnd).offset(x = 8.dp, y = 22.dp), maxLines = 1, softWrap = false,
        )
        AccountFace(a)
    }
}

@Composable
private fun AccountFace(a: Stats.Account) {
    Column(Modifier.fillMaxSize().padding(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(bankName(a.sender), style = T.label.copy(color = Color.White.copy(alpha = .85f)), modifier = Modifier.weight(1f))
            Text(if (a.isCard) "CARD" else "BANK", style = T.tiny.copy(color = Color.White.copy(alpha = .5f), fontSize = 10.sp, letterSpacing = 1.sp))
        }
        Spacer(Modifier.weight(1f))
        Text(if (a.isCard) "Card ··${a.id}" else "Account ··${a.id}", style = T.tiny.copy(color = Color.White.copy(alpha = .8f)))
        // An old balance is dimmed so it doesn't pass for today's.
        val day = a.balanceTime?.let { java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneId.systemDefault()).toLocalDate() }
        val old = day != null && balanceIsOld(day)
        Text(
            a.balance?.let { inr(it) } ?: inr(a.monthSpend),
            style = T.h2.copy(color = Color.White.copy(alpha = if (old) .55f else 1f)),
        )
        val label = when {
            a.balance == null -> "Spent this month"
            day == null -> "Last known balance"
            else -> balanceWhen(day)
        }
        Text(label, style = T.tiny.copy(color = Color.White.copy(alpha = if (old) .5f else .7f)))
    }
}

/** "Balance today", "Balance yesterday", "Balance on 12 Oct". */
fun balanceWhen(d: LocalDate, today: LocalDate = LocalDate.now()): String = when (d) {
    today -> "Balance today"
    today.minusDays(1) -> "Balance yesterday"
    else -> "Balance on " + dayLabel(d, today)
}

fun balanceIsOld(d: LocalDate, today: LocalDate = LocalDate.now()) = d.isBefore(today.minusDays(7))

/** "VM-HDFCBK-S" → "HDFC" */
fun bankName(sender: String): String {
    val core = sender.uppercase().split('-').firstOrNull { it.length >= 5 } ?: sender.uppercase()
    val known = listOf("HDFC", "ICICI", "SBI", "AXIS", "KOTAK", "YES", "IDFC", "INDUS", "PNB", "BOB", "CANARA", "FEDERAL", "AU", "ONECARD", "SLICE")
    return known.firstOrNull { it in core } ?: core.take(6)
}

@Composable
private fun Setting(title: String, sub: String?, onClick: (() -> Unit)? = null, trailing: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().then(if (onClick != null) Modifier.press(onClick) else Modifier).padding(horizontal = 12.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = T.body)
            if (sub != null) Text(sub, style = T.tiny)
        }
        Spacer(Modifier.width(10.dp))
        trailing()
    }
}

@Composable
private fun RescanRow(s: AppState, vm: AppViewModel) {
    val sub = when {
        !s.rescanning -> "Start fresh if something looks missing"
        s.total > 0 -> "Reading ${groupIndian(s.done.toLong())} of ${groupIndian(s.total.toLong())}"
        else -> "Reading…"
    }
    Setting("Rescan all messages", sub, onClick = if (s.rescanning) null else vm::fullRescan) {
        if (s.rescanning) CircularProgressIndicator(Modifier.size(20.dp), color = C.ink, strokeWidth = 2.dp)
    }
}
