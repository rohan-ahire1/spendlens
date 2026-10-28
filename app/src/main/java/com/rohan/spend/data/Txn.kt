package com.rohan.spend.data

import kotlin.math.abs

enum class Channel(val label: String) { UPI("UPI"), CARD("Card"), BANK("Bank"), ATM("ATM") }

/** One money movement read from a bank SMS. Amounts are in paise. */
data class Txn(
    val id: Long,
    val time: Long,
    val amount: Long,
    val debit: Boolean,
    val payee: String,
    val payeeKey: String,
    val account: String?,
    val channel: Channel,
    val balance: Long?,
    val body: String,
    val sender: String,
) {
    val signed: Long get() = if (debit) -amount else amount
}

/** ₹1,20,966 style grouping. */
fun inr(paise: Long, signed: Boolean = false): String {
    val rupees = (abs(paise) + 50) / 100
    val sign = when {
        !signed -> if (paise < 0) "−" else ""
        paise < 0 -> "−"
        else -> "+"
    }
    return "$sign₹${groupIndian(rupees)}"
}

fun groupIndian(n: Long): String {
    val s = n.toString()
    if (s.length <= 3) return s
    val last = s.takeLast(3)
    var rest = s.dropLast(3)
    val parts = ArrayList<String>()
    while (rest.length > 2) {
        parts.add(0, rest.takeLast(2))
        rest = rest.dropLast(2)
    }
    if (rest.isNotEmpty()) parts.add(0, rest)
    return parts.joinToString(",") + "," + last
}

/** ₹42k, ₹1.2L */
fun inrShort(paise: Long): String {
    val r = abs(paise) / 100.0
    val sign = if (paise < 0) "−" else ""
    return when {
        r >= 100_000 -> sign + "₹" + trim(r / 100_000) + "L"
        r >= 1_000 -> sign + "₹" + (r / 1_000).toLong() + "k"
        else -> sign + "₹" + r.toLong()
    }
}

private fun trim(d: Double): String {
    val s = String.format(java.util.Locale.US, "%.1f", d)
    return if (s.endsWith(".0")) s.dropLast(2) else s
}
