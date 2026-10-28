package com.rohan.spend.data

import java.util.Locale

/**
 * Turns bank SMS into [Txn]s. Built around HDFC's formats with a generic
 * fallback that works for most Indian banks.
 */
object SmsParser {
    private val I = setOf(RegexOption.IGNORE_CASE)

    private val bankSender = Regex(
        "HDFC|ICICI|SBI|AXIS|KOTAK|IDFC|YESB|INDUS|PNB|BOB|CANBNK|UNION|FEDBNK|AUBANK|PAYTM|AMEX|ONECRD|SCB|CITI|RBL|BANK",
        I,
    )
    private val amountRe = Regex("""(?:Rs\.?|INR|₹)\s*([\d,]+(?:\.\d{1,2})?)""", I)
    private val skipRe = Regex(
        """\bOTP\b|one.?time password|verification code|will be debited|is due|due date|minimum (?:amount )?due|""" +
            """has been requested|mandate.*(?:created|registered|set up)|declined|unsuccessful|failed|""" +
            """received towards your .*credit card|payment .*received .*credit card|credit card .*payment .*received""",
        I,
    )
    private val creditRe = Regex("""\b(credited|received|deposited|refund(?:ed)?|reversed|cashback)\b""", I)
    private val debitRe = Regex("""\b(debited|spent|sent|paid|withdrawn|withdrawal|purchase|transferred)\b""", I)
    private val accountMarker = Regex("""a/?c|account|card|upi|vpa""", I)
    private val accountRe = Regex(
        """(?:A/?c|Account|Card)(?:\s*no\.?)?(?:\s*ending)?(?:\s*with)?[:\s]*[*xX]*\s*(\d{4})\b""",
        I,
    )
    private val balanceRe = Regex(
        """(?:Avl\.?\s*Bal(?:ance)?|Available\s*Bal(?:ance)?)\s*(?:is|:)?\s*(?:Rs\.?|INR|₹)\s*([\d,]+(?:\.\d{1,2})?)""",
        I,
    )
    private val vpaRe = Regex("""([\w.\-]+@[a-zA-Z][\w.]*)""")
    private val payeeTo = Regex("""\bto\s+(?:VPA\s+)?([^\n]+?)\s*(?:\n|\s+on\s+\d|\(|\s+UPI|\s+Ref|\.\s|$)""", I)
    private val payeeAt = Regex("""\bat\s+([A-Za-z0-9][^\n]*?)\s+(?:on|by|via)\s""", I)
    private val payeeFrom = Regex("""\b(?:from|by)\s+(?:VPA\s+|a/c linked to VPA\s+)?([^\n]+?)\s*(?:\n|\s+on\s|\s+via|\(|\s+Ref|\.\s|$)""", I)
    private val neftRe = Regex("""(?:NEFT|IMPS|RTGS)\s*Cr-[^-]+-([^-\n]+)""", I)
    private val atmRe = Regex("""\bATM\b|cash withdrawal|withdrawn""", I)
    private val suffixRe = Regex("""\b(pvt|private|ltd|limited|llp|india|technologies|marketplace|payments?)\b\.?""", I)

    fun parse(id: Long, sender: String, body: String, time: Long): Txn? {
        if (!bankSender.containsMatchIn(sender)) return null
        if (skipRe.containsMatchIn(body)) return null
        if (!accountMarker.containsMatchIn(body)) return null
        val amountMatch = amountRe.find(body) ?: return null
        val amount = toPaise(amountMatch.groupValues[1]) ?: return null
        if (amount <= 0) return null

        val c = creditRe.find(body)?.range?.first ?: Int.MAX_VALUE
        val d = debitRe.find(body)?.range?.first ?: Int.MAX_VALUE
        if (c == Int.MAX_VALUE && d == Int.MAX_VALUE) return null
        val debit = d < c

        val channel = when {
            atmRe.containsMatchIn(body) -> Channel.ATM
            Regex("UPI|VPA", I).containsMatchIn(body) -> Channel.UPI
            Regex("""\bcard\b""", I).containsMatchIn(body) -> Channel.CARD
            else -> Channel.BANK
        }

        val rawPayee = if (channel == Channel.ATM) "Cash withdrawal" else findPayee(body, debit)
        val payee = cleanName(rawPayee ?: if (debit) "Payment" else "Money in")
        val key = keyOf(rawPayee ?: payee)

        return Txn(
            id = id,
            time = time,
            amount = amount,
            debit = debit,
            payee = payee,
            payeeKey = key,
            account = accountRe.find(body)?.groupValues?.get(1),
            channel = channel,
            balance = balanceRe.find(body)?.groupValues?.get(1)?.let(::toPaise),
            body = body.trim(),
            sender = sender,
        )
    }

    private fun findPayee(body: String, debit: Boolean): String? {
        if (!debit) {
            neftRe.find(body)?.let { return it.groupValues[1] }
            payeeFrom.findAll(body).map { it.groupValues[1] }
                .firstOrNull { !it.contains("HDFC Bank A/c", true) && !Regex("""^a/?c""", I).containsMatchIn(it) }
                ?.let { return it }
            return null
        }
        // Card SMS end with "To Block... Call", so take whichever of "to X" / "at X" comes first.
        val to = payeeTo.findAll(body)
            .firstOrNull { !Regex("""^(?:your|a/?c|account|block)""", I).containsMatchIn(it.groupValues[1].trim()) }
        val at = payeeAt.find(body)
        listOfNotNull(to, at).minByOrNull { it.range.first }?.let { return it.groupValues[1].trim() }
        vpaRe.find(body)?.let { return it.groupValues[1] }
        return null
    }

    /** Stable key used for rules: the VPA handle or the lowercased name. */
    fun keyOf(raw: String): String {
        val v = vpaRe.find(raw)
        val base = v?.groupValues?.get(1) ?: raw
        return base.lowercase(Locale.ROOT).replace(Regex("""\s+"""), " ").trim()
    }

    fun cleanName(raw: String): String {
        val vpa = vpaRe.find(raw)?.groupValues?.get(1)
        var s = if (vpa != null) {
            val handle = vpa.substringBefore('@').replace(Regex("""[._\-]"""), " ")
            val letters = handle.replace(Regex("""\d+"""), " ").trim()
            if (letters.length >= 3) letters else vpa
        } else raw
        s = s.replace(suffixRe, " ").replace(Regex("""\s+"""), " ").trim().trimEnd('.', ',', '-')
        if (s.isEmpty()) s = raw.trim()
        return s.split(' ').joinToString(" ") { w ->
            if (w.contains('@')) w else w.lowercase(Locale.ROOT).replaceFirstChar { it.titlecase(Locale.ROOT) }
        }.take(40)
    }

    private fun toPaise(s: String): Long? {
        val clean = s.replace(",", "").trimEnd('.')
        val d = clean.toBigDecimalOrNull() ?: return null
        return d.movePointRight(2).toLong()
    }
}
