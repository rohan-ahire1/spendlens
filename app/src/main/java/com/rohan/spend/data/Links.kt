package com.rohan.spend.data

import kotlin.math.roundToLong

private const val DAY_MS = 24L * 3600 * 1000

/**
 * Ties refunds back to the payment they undo, so the money comes off that payment's
 * category and month instead of showing up as money in.
 *
 * A refund we can't place stays as money in: guessing a payment to take it off would
 * bend some category's numbers more than leaving it out of spending does.
 */
object Refunds {
    const val DAYS = 60L

    /** Refund id → id of the payment it gives money back for. */
    fun match(txns: List<Txn>, isRefund: (Txn) -> Boolean): Map<Long, Long> {
        val debits = txns.filter { it.debit }
        val left = HashMap<Long, Long>()
        val out = HashMap<Long, Long>()
        for (r in txns.filter { !it.debit && isRefund(it) }.sortedBy { it.time }) {
            val pool = debits.filter { it.time in (r.time - DAYS * DAY_MS)..r.time && (left[it.id] ?: it.amount) >= r.amount }
            val named = pool.filter { samePayee(it, r) }
            // Many reversal SMS name no one; then only a lone payment of the same amount on the same account will do.
            val pick = named.ifEmpty { listOfNotNull(pool.singleOrNull { it.amount == r.amount && it.account != null && it.account == r.account }) }
                .maxWithOrNull(compareBy<Txn>({ it.amount == r.amount }, { it.time })) ?: continue
            out[r.id] = pick.id
            left[pick.id] = (left[pick.id] ?: pick.amount) - r.amount
        }
        return out
    }

    private val generic = setOf(
        "money", "payment", "refund", "refunded", "reversal", "reversed", "cashback", "from", "bank", "upi", "the", "and",
        "your", "account", "card", "credit", "debit", "transfer", "received", "towards", "for", "neft", "imps", "via", "ref",
    )

    private fun words(s: String) = s.lowercase().split(Regex("[^a-z]+")).filter { it.length >= 3 && it !in generic }.toSet()

    fun samePayee(a: Txn, b: Txn): Boolean =
        (a.payeeKey == b.payeeKey && words(a.payeeKey).isNotEmpty()) || words(a.payee).intersect(words(b.payee)).isNotEmpty()

    /**
     * What a payment really cost: [mine] if it was split, else the whole [amount], scaled down by
     * whatever was [refunded], since a refund shrinks everyone's share alike.
     */
    fun net(amount: Long, mine: Long?, refunded: Long): Long {
        if (amount <= 0) return 0
        val kept = (amount - refunded).coerceIn(0, amount)
        return ((mine ?: amount).toDouble() * kept / amount).roundToLong()
    }
}
