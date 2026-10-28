package com.rohan.spend.data

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.max

enum class Cadence(val label: String, val months: Int, val days: IntRange) {
    MONTHLY("Monthly", 1, 20..40),
    QUARTERLY("Every 3 months", 3, 80..100),
    YEARLY("Yearly", 12, 330..400);

    companion object {
        fun of(gapDays: Long): Cadence? = entries.firstOrNull { gapDays.toInt() in it.days }
    }
}

data class Charge(val date: LocalDate, val amount: Long, val id: Long)

/** The latest price against the one before it, and when it changed. */
data class PriceRise(val from: Long, val to: Long, val since: LocalDate) {
    val by: Long get() = to - from
    val pct: Int get() = if (from > 0) ((to - from) * 100 / from).toInt() else 0
}

/** One recurring charge, tracked across price changes. */
data class Subscription(
    val payee: String,
    val payeeKey: String,
    val cadence: Cadence,
    /** Oldest first. */
    val charges: List<Charge>,
    val next: LocalDate,
    val rise: PriceRise?,
    /** The expected charge is more than [MISSED_DAYS] late. */
    val cancelled: Boolean,
    /** Another live charge from the same payee at about the same price. */
    val duplicate: Boolean,
) {
    val amount: Long get() = charges.last().amount
    val last: LocalDate get() = charges.last().date
    val months: Int get() = charges.map { it.date.withDayOfMonth(1) }.distinct().size
    val monthly: Long get() = amount / cadence.months
    fun daysUntil(today: LocalDate): Long = ChronoUnit.DAYS.between(today, next)
}

/** A subscription due within a few days, for the Home card and reminders. */
data class Renewal(val sub: Subscription, val days: Long)

const val MISSED_DAYS = 10
/** Past this many days late we stop listing it at all. */
private const val GONE_DAYS = 60
/** Two charges closer than this are separate plans, not one plan renewing. */
private const val MIN_GAP = 20

/**
 * Finds recurring charges in [txns] (debits already picked as subscriptions). Groups by payee rather
 * than by exact amount so a price rise keeps one subscription, and splits a payee into separate plans
 * only when two charges land too close together to be one plan renewing.
 */
fun detectSubs(txns: List<Txn>, today: LocalDate): List<Subscription> {
    val found = txns.filter { it.debit }.groupBy { it.payeeKey }.flatMap { (_, l) ->
        streams(l.sortedBy { it.time }).mapNotNull { s -> build(s, today) }
    }
    return found.map { s ->
        val twin = found.any { o ->
            o !== s && o.payeeKey == s.payeeKey && !o.cancelled && !s.cancelled &&
                abs(o.amount - s.amount) * 10 <= max(o.amount, s.amount) &&
                (o.amount > s.amount || (o.amount == s.amount && o.last > s.last))
        }
        s.copy(duplicate = twin)
    }.sortedWith(compareBy<Subscription> { it.cancelled }.thenByDescending { it.monthly })
}

/** Live subscriptions due between today and [within] days from now, soonest first. */
fun renewals(subs: List<Subscription>, today: LocalDate, within: Int = 3): List<Renewal> =
    subs.filter { !it.cancelled }.map { Renewal(it, it.daysUntil(today)) }
        .filter { it.days in 0..within }.sortedBy { it.days }

/** Everything a reminder needs in one call: renewals due within [within] days. */
fun upcomingRenewals(txns: List<Txn>, catOf: (Txn) -> Cat, today: LocalDate = LocalDate.now(), within: Int = 3): List<Renewal> =
    renewals(detectSubs(txns.filter { it.debit && catOf(it) == Cat.SUBSCRIPTIONS }, today), today, within)

/** What live subscriptions cost a month, with yearly and quarterly plans spread out. */
fun List<Subscription>.perMonth(): Long = filter { !it.cancelled }.sumOf { it.monthly }

private fun streams(sorted: List<Txn>): List<List<Txn>> {
    val out = ArrayList<MutableList<Txn>>()
    for (t in sorted) {
        val d = t.date()
        val best = out.filter { ChronoUnit.DAYS.between(it.last().date(), d) >= MIN_GAP }
            .minByOrNull { ratio(it.last().amount, t.amount) }
        if (best != null && ratio(best.last().amount, t.amount) <= 3.0) best.add(t) else out.add(mutableListOf(t))
    }
    return out
}

private fun ratio(a: Long, b: Long): Double = max(a, b).toDouble() / minOf(a, b).coerceAtLeast(1)

private fun build(l: List<Txn>, today: LocalDate): Subscription? {
    if (l.size < 2) return null
    val charges = l.map { Charge(it.date(), it.amount, it.id) }
    val gaps = charges.zipWithNext { a, b -> ChronoUnit.DAYS.between(a.date, b.date) }.sorted()
    val cadence = Cadence.of(gaps[gaps.size / 2]) ?: return null
    val last = charges.last()
    val next = last.date.plusMonths(cadence.months.toLong())
    val late = ChronoUnit.DAYS.between(next, today)
    if (late > GONE_DAYS) return null
    val i = (1 until charges.size).lastOrNull { charges[it].amount != charges[it - 1].amount } ?: 0
    val rise = if (i > 0 && charges[i].amount - charges[i - 1].amount >= 100 && charges[i].date.isAfter(today.minusMonths(12)))
        PriceRise(charges[i - 1].amount, last.amount, charges[i].date) else null
    return Subscription(l.last().payee, l.last().payeeKey, cadence, charges, next, rise, late > MISSED_DAYS, false)
}
