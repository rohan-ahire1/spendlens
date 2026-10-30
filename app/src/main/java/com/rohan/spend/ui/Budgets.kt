package com.rohan.spend.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.rohan.spend.data.BudgetState
import com.rohan.spend.data.Bucket
import com.rohan.spend.data.Cat
import com.rohan.spend.data.Pace
import com.rohan.spend.data.budgetState
import com.rohan.spend.data.budgets
import com.rohan.spend.data.inr
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

/** Every budget's state in [ym], worst first. */
@Composable
fun AppState.budgetsIn(ym: YearMonth): List<BudgetState> = remember(stats, budgets, ym) { stats.budgets(budgets, ym) }

/** Money in and card bill payments (moving money, not spending it) don't get budgets. */
val Cat.budgetable: Boolean get() = bucket != Bucket.INCOME && bucket != Bucket.MOVE && bucket != Bucket.OWN

private fun monthName(ym: YearMonth): String =
    ym.month.getDisplayName(TextStyle.FULL, Locale.getDefault()) + if (ym.year != LocalDate.now().year) " ${ym.year}" else ""

private fun paceLabel(p: Pace) = if (p == Pace.OVER) "Over budget" else "On pace to go over"

@Composable
fun PacePill(b: BudgetState) {
    if (b.warn) Pill(paceLabel(b.pace), C.warnInk, C.warnBg)
}

/** A small ring for a Home tile: how much of the budget is gone. Sits on the tile's own colour. */
@Composable
fun BudgetRing(b: BudgetState, modifier: Modifier = Modifier, size: Dp = 18.dp) {
    val f = growth(b.fraction.coerceAtMost(1f), 200)
    val over = b.pace == Pace.OVER
    Canvas(modifier.size(size)) {
        val w = 2.5.dp.toPx()
        val inset = w / 2
        val arc = Size(this.size.width - w, this.size.height - w)
        drawArc(Color.White.copy(alpha = .3f), 0f, 360f, false, Offset(inset, inset), arc, style = Stroke(w))
        drawArc(if (over) C.warnBg else Color.White, -90f, 360f * f.value, false, Offset(inset, inset), arc, style = Stroke(w, cap = StrokeCap.Round))
    }
}

/** Spent against budget for one category, on its own screen. Tap to change it. */
@Composable
fun BudgetCard(cat: Cat, ym: YearMonth, s: AppState, modifier: Modifier = Modifier, onEdit: () -> Unit) {
    val budget = s.budgets[cat]
    FrostCard(modifier, pad = 16.dp, onClick = onEdit) {
        if (budget == null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Set a monthly budget", style = T.body)
                    Text("Get a heads-up when you're on pace to go over", style = T.tiny)
                }
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = C.faint)
            }
            return@FrostCard
        }
        val b = s.budgetsIn(ym).firstOrNull { it.cat == cat } ?: budgetState(cat, budget, 0, ym, LocalToday.current)
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Budget for ${monthName(ym)}", style = T.label, modifier = Modifier.weight(1f))
                PacePill(b)
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text("${inr(b.spent)} of ${inr(b.budget)}", style = T.h3.copy(fontFeatureSettings = "tnum"), modifier = Modifier.weight(1f))
                Text(if (b.left >= 0) "${inr(b.left)} left" else "${inr(-b.left)} over", style = T.tiny.copy(color = if (b.left < 0) C.warnInk else C.subtle))
            }
            Spacer(Modifier.height(10.dp))
            GrowBar(b.fraction, if (b.warn) C.warnInk else cat.style().fg, height = 8.dp, delayMs = 150)
            if (ym == YearMonth.from(LocalToday.current) && b.projected != b.spent) {
                Spacer(Modifier.height(8.dp))
                Text("At this rate, about ${inr(b.projected)} by the month's end", style = T.tiny)
            }
        }
    }
}

/** Home's nudge for the current month: budgets that are over or heading there. */
@Composable
fun PaceCard(list: List<BudgetState>, nav: Nav, modifier: Modifier = Modifier) {
    val warn = list.filter { it.warn }
    if (warn.isEmpty()) return
    FrostCard(modifier.padding(top = 12.dp), pad = 14.dp) {
        Column {
            Text(
                (if (warn.all { it.pace == Pace.OVER }) "Over budget: " else "On pace to go over: ") + warn.joinToString(", ") { it.cat.label },
                style = T.label.copy(color = C.warnInk), modifier = Modifier.padding(horizontal = 2.dp, vertical = 2.dp),
            )
            warn.forEach { b -> BudgetRow(b, Modifier.padding(top = 10.dp)) { nav.push(Route.Category(b.cat)) } }
        }
    }
}

@Composable
private fun BudgetRow(b: BudgetState, modifier: Modifier = Modifier, pill: Boolean = false, onClick: () -> Unit) {
    Row(modifier.fillMaxWidth().press(onClick), verticalAlignment = Alignment.CenterVertically) {
        CatIcon(b.cat, 34.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(b.cat.label, style = T.body, modifier = Modifier.weight(1f), maxLines = 1)
                Text("${inr(b.spent)} of ${inr(b.budget)}", style = T.tiny.copy(color = if (b.warn) C.warnInk else C.subtle, fontWeight = FontWeight.SemiBold))
            }
            Spacer(Modifier.height(6.dp))
            GrowBar(b.fraction, if (b.warn) C.warnInk else b.cat.style().fg)
            if (pill && b.warn) {
                Spacer(Modifier.height(6.dp))
                PacePill(b)
            }
        }
    }
}

/** Every budget and how it's going this month, for Accounts. */
@Composable
fun BudgetList(s: AppState, nav: Nav, onAdd: () -> Unit, modifier: Modifier = Modifier) {
    val list = s.budgetsIn(s.stats.thisMonth)
    FrostCard(modifier, pad = 14.dp) {
        Column {
            list.forEach { b ->
                BudgetRow(b, Modifier.padding(bottom = 14.dp), pill = true) { nav.push(Route.Category(b.cat)) }
            }
            Row(Modifier.fillMaxWidth().press(onAdd), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Add a budget", style = T.body)
                    Text(
                        if (list.isEmpty()) "Get a heads-up when a category is on pace to go over"
                        else "${list.count { it.warn }} of ${list.size} need a look this month",
                        style = T.tiny,
                    )
                }
                Icon(Icons.Rounded.Add, null, tint = C.faint)
            }
        }
    }
}

/** Pick which category to budget, then set the amount. */
@Composable
fun AddBudgetDialog(s: AppState, onSave: (Cat, Long) -> Unit, onDismiss: () -> Unit) {
    var picked by rememberSaveable { mutableStateOf<Cat?>(null) }
    picked?.let { c -> return BudgetDialog(c, s, { onSave(c, it) }, onDismiss) }
    // Busiest categories first, so the likely ones are at the top.
    val order = remember(s.stats) { s.stats.avgByCat.map { it.cat } }
    val cats = Cat.entries.filter { it.budgetable && it !in s.budgets }.sortedBy { c -> order.indexOf(c).let { if (it < 0) Int.MAX_VALUE else it } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Budget for…") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                cats.forEach { c ->
                    Row(Modifier.fillMaxWidth().press({ picked = c }).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        CatIcon(c, 32.dp)
                        Spacer(Modifier.width(12.dp))
                        Text(c.label, style = T.body)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onDismiss) { Text("Cancel", color = C.muted) } },
        containerColor = Color.White,
    )
}

/** Set or clear one category's monthly budget. Empty clears it. */
@Composable
fun BudgetDialog(cat: Cat, s: AppState, onSave: (Long) -> Unit, onDismiss: () -> Unit) {
    val now = s.budgets[cat]
    var text by rememberSaveable(cat) { mutableStateOf(now?.let { (it / 100).toString() } ?: "") }
    // A usual month here, from the last three full months, as a starting point.
    val usual = remember(s.stats, cat) { s.stats.avgByCat.firstOrNull { it.cat == cat }?.total ?: 0L }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Budget for ${cat.label.lowercase()}") },
        text = {
            Column {
                Text(
                    (if (usual > 0) "You usually spend about ${inr(usual)} a month here. " else "") +
                        if (now != null) "Leave empty to remove it." else "Per month, in rupees.",
                    style = T.small,
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(text, { v -> text = v.filter(Char::isDigit).take(8) }, singleLine = true,
                    prefix = { Text("₹") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            }
        },
        confirmButton = { TextButton({ onSave((text.toLongOrNull() ?: 0) * 100); onDismiss() }) { Text("Save", color = C.ink) } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel", color = C.muted) } },
        containerColor = Color.White,
    )
}
