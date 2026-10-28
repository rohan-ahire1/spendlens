package com.rohan.spend.data

import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZonedDateTime

/** The month at a glance, for the widget and the evening summary. [usual] is the target, or the usual month. */
data class MonthLeft(val spent: Long, val usual: Long, val today: Long) {
    val left: Long get() = usual - spent
    val over: Boolean get() = usual > 0 && spent > usual
    /** How much of the month's money is gone, 0 to 1. */
    val fraction: Float get() = if (usual <= 0) 0f else (spent.toFloat() / usual).coerceIn(0f, 1f)
    /** The big number: what's left, what's over, or what's spent when there's nothing to measure against. */
    val amount: String get() = inr(if (usual <= 0) spent else kotlin.math.abs(left))
    val label: String get() = when {
        usual <= 0 -> "spent this month"
        over -> "over this month"
        else -> "left this month"
    }
    val line: String get() = "$amount $label"
}

/** The evening notification: a title and a line under it. */
fun nudgeCopy(m: MonthLeft): Pair<String, String> =
    (if (m.today > 0) "You spent ${inr(m.today)} today" else "No spending today") to m.line

const val SUMMARY_HOUR = 21
/** Renewal alerts come in the morning, while there's still time to cancel. */
const val RENEWALS_HOUR = 9

/** The evening summary goes out once a day, from 9 pm to midnight. An alarm held up past midnight is dropped. */
fun summaryDue(now: LocalDateTime, lastSent: String): Boolean =
    now.hour >= SUMMARY_HOUR && lastSent != now.toLocalDate().toString()

/** The next time the clock reads [at] after [now], in [now]'s zone. Gaps from clock changes resolve forward. */
fun nextAt(now: ZonedDateTime, at: LocalTime): ZonedDateTime {
    val today = now.toLocalDate().atTime(at).atZone(now.zone)
    return if (now.isBefore(today)) today else now.toLocalDate().plusDays(1).atTime(at).atZone(now.zone)
}
