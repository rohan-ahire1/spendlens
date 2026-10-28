package com.rohan.spend.data

import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.abs

/** A category's month against its average over the months before. */
data class CatShift(val cat: Cat, val now: Long, val before: Long) {
    val by: Long get() = now - before
    val pct: Int get() = if (before > 0) ((now - before) * 100 / before).toInt() else 0
}

/** One month in numbers, for the recap card. */
data class Recap(
    val month: YearMonth,
    val spent: Long,
    /** Median of the six months before that had spending, or the user's own target. 0 when unknown. */
    val usual: Long,
    val count: Int,
    val top: CatTotal?,
    val shift: CatShift?,
    val noSpend: Int,
    val days: Int,
    val biggest: Txn?,
    val place: PlaceTotal?,
    /** Spend on each day of the month, the 1st first, up to [days]. */
    val daily: List<Long>,
    /** [usual] is the user's target rather than history. */
    val isTarget: Boolean = false,
) {
    val vsUsual: Int? get() = if (usual > 0) ((spent - usual) * 100 / usual).toInt() else null
}

/**
 * Crunches [ym] from [txns], counting only what [counts] says. For the current month it stops at
 * [today]. [usual] overrides the history-based usual month, e.g. with the user's target.
 */
fun recap(
    txns: List<Txn>, counts: (Txn) -> Boolean, catOf: (Txn) -> Cat, ym: YearMonth,
    today: LocalDate = LocalDate.now(), usual: Long = 0,
): Recap {
    // A fully refunded payment cost nothing, so it isn't one of the month's payments.
    val counted = txns.filter { counts(it) && (!it.debit || it.amount > 0) }
    val byMonth = counted.groupBy { it.month() }
    val first = txns.minOfOrNull { it.month() } ?: ym
    val l = byMonth[ym].orEmpty()
    val spent = l.sumOf { it.amount }

    val history = (1..6L).map { ym.minusMonths(it) }.filter { it >= first }
        .map { m -> byMonth[m].orEmpty().sumOf { it.amount } }.filter { it > 0 }.sorted()
    val median = if (history.isEmpty()) 0 else history[history.size / 2]

    val cats = l.groupBy(catOf).map { (c, x) -> CatTotal(c, x.sumOf { it.amount }, x.size) }.sortedByDescending { it.total }

    // Biggest rupee move against the 3 months before, ignoring small categories that swing on one payment.
    val prev = (1..3L).map { ym.minusMonths(it) }.filter { it >= first }
    val shift = if (prev.isEmpty()) null else {
        val now = l.groupBy(catOf).mapValues { e -> e.value.sumOf { it.amount } }
        val before = prev.flatMap { byMonth[it].orEmpty() }.groupBy(catOf).mapValues { e -> e.value.sumOf { it.amount } / prev.size }
        (now.keys + before.keys).map { CatShift(it, now[it] ?: 0, before[it] ?: 0) }
            .filter { maxOf(it.now, it.before) >= 1_000_00 && it.by != 0L }
            .maxByOrNull { abs(it.by) }
    }

    val last = if (ym == YearMonth.from(today)) today.dayOfMonth else ym.lengthOfMonth()
    val perDay = l.groupBy { it.date().dayOfMonth }.mapValues { e -> e.value.sumOf { it.amount } }
    val daily = (1..last).map { perDay[it] ?: 0L }

    val place = l.filter { catOf(it) != Cat.PEOPLE && catOf(it) != Cat.FAMILY }.groupBy { it.payeeKey }
        .map { (_, x) -> PlaceTotal(x.maxBy { it.time }.payee, catOf(x.first()), x.sumOf { it.amount }, x.size) }
        .maxByOrNull { it.total }

    return Recap(
        month = ym, spent = spent, usual = if (usual > 0) usual else median, count = l.size,
        top = cats.firstOrNull(), shift = shift, noSpend = daily.count { it == 0L }, days = last,
        biggest = l.maxByOrNull { it.amount }, place = place, daily = daily, isTarget = usual > 0,
    )
}
