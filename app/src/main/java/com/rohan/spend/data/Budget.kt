package com.rohan.spend.data

import java.time.LocalDate
import java.time.YearMonth

enum class Pace { FINE, ON_PACE_OVER, OVER }

/** Where one category stands against its monthly budget. */
data class BudgetState(val cat: Cat, val budget: Long, val spent: Long, val projected: Long, val pace: Pace) {
    val fraction: Float get() = if (budget <= 0) 0f else (spent.toFloat() / budget).coerceAtLeast(0f)
    val left: Long get() = budget - spent
    val warn: Boolean get() = pace != Pace.FINE
}

/** What the month ends at if spending keeps the rate it has had so far. */
fun projected(spent: Long, day: Int, days: Int): Long {
    if (days <= 0) return spent
    val d = day.coerceIn(1, days)
    return spent * days / d
}

/**
 * Over once spent passes the budget; on pace to go over when the month-end projection passes it.
 * A budget of zero or less means no budget, so it never warns.
 */
fun pace(spent: Long, budget: Long, day: Int, days: Int): Pace = when {
    budget <= 0 -> Pace.FINE
    spent > budget -> Pace.OVER
    projected(spent, day, days) > budget -> Pace.ON_PACE_OVER
    else -> Pace.FINE
}

/** How far into [ym] we are on [today]: the whole month once it's over, none before it starts. */
fun daysIn(ym: YearMonth, today: LocalDate): Int {
    val now = YearMonth.from(today)
    return when {
        ym < now -> ym.lengthOfMonth()
        ym > now -> 0
        else -> today.dayOfMonth
    }
}

fun budgetState(cat: Cat, budget: Long, spent: Long, ym: YearMonth, today: LocalDate): BudgetState {
    val day = daysIn(ym, today)
    val days = ym.lengthOfMonth()
    if (day == 0) return BudgetState(cat, budget, spent, spent, Pace.FINE)
    return BudgetState(cat, budget, spent, projected(spent, day, days), pace(spent, budget, day, days))
}

/**
 * Each budget's state in [ym], worst first. Spend matches the Home tiles where the category is
 * counted, and falls back to every payment in it otherwise (say, Family while it's left out).
 */
fun Stats.budgets(budgets: Map<Cat, Long>, ym: YearMonth, today: LocalDate = this.today): List<BudgetState> {
    if (budgets.isEmpty()) return emptyList()
    val counted = byCatIn(ym).associate { it.cat to it.total }
    return budgets.filterValues { it > 0 }.map { (cat, b) ->
        val spent = counted[cat] ?: inCat(cat).filter { it.month() == ym }.sumOf { spend(it) }
        budgetState(cat, b, spent, ym, today)
    }.sortedWith(compareByDescending<BudgetState> { it.pace.ordinal }.thenByDescending { it.fraction })
}
