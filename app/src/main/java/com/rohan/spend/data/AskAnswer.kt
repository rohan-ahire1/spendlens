package com.rohan.spend.data

import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

data class Bar(val label: String, val value: Long, val text: String)

data class Answer(
    val title: String,
    /** Paise, or a plain number when [isCount]. */
    val value: Long,
    val isCount: Boolean,
    val sentence: String,
    /** Exactly the payments behind the number, newest first. */
    val txns: List<Txn>,
    /** Everything that fits the subject, for the list before it's narrowed to [txns]. */
    val matches: List<Txn>,
    val bars: List<Bar> = emptyList(),
)

sealed interface Reply {
    data class Done(val q: Question, val a: Answer) : Reply
    data class Unclear(val why: String) : Reply
}

object Ask {
    private val L = Locale.ENGLISH
    private val dm = DateTimeFormatter.ofPattern("d MMM", L)
    private val wdm = DateTimeFormatter.ofPattern("EEE d MMM", L)
    private val mmm = DateTimeFormatter.ofPattern("MMM", L)
    private val mmmm = DateTimeFormatter.ofPattern("MMMM", L)

    /** Null when [text] isn't a question; plain search handles it. */
    fun reply(text: String, today: LocalDate, txns: List<Txn>, catOf: (Txn) -> Cat, counts: (Txn) -> Boolean): Reply? {
        val payees = txns.distinctBy { it.payeeKey }.map { it.payee to it.payeeKey }
        return when (val p = AskParser.parse(text, today, payees)) {
            null -> null
            is Parsed.Unclear -> Reply.Unclear(p.why)
            is Parsed.Ok -> Reply.Done(p.q, answer(p.q, today, txns, catOf, counts))
        }
    }

    fun examples(topPayee: String?): List<String> = listOfNotNull(
        topPayee?.let { "How much on $it this month" } ?: "Spent last month",
        "Food delivery this month vs last month",
        "Biggest payment this year",
        "Average daily spend",
    )

    private fun plural(n: Int, w: String) = if (n == 1) "1 $w" else "$n ${w}s"

    fun dayText(d: LocalDate, today: LocalDate): String = when (d) {
        today -> "today"
        today.minusDays(1) -> "yesterday"
        else -> d.format(wdm) + if (d.year != today.year) " ${d.year}" else ""
    }

    fun rangeText(sp: Span, today: LocalDate, allTime: Boolean = false): String {
        val (a, b) = sp.from to sp.to
        fun d(x: LocalDate) = x.format(dm) + if (x.year != today.year) " ${x.year}" else ""
        return when {
            allTime -> "since ${d(a)}"
            a == b -> dayText(a, today)
            a.dayOfMonth == 1 && b == YearMonth.from(a).atEndOfMonth() -> a.format(mmmm) + if (a.year != today.year) " ${a.year}" else ""
            a.dayOfYear == 1 && b.year == a.year && b.dayOfYear == b.lengthOfYear() -> "${a.year}"
            else -> "${d(a)} – ${if (b == today) "today" else d(b)}"
        }
    }

    private fun label(s: Subject, income: Boolean) = when (s) {
        Subject.All -> if (income) "All money in" else "All spending"
        is Subject.Cats -> s.name
        is Subject.Payee -> s.name
    }

    /**
     * Spend questions about everything use the app's own counting rules. A category or payee
     * counts every debit to it, so "investing" or "mom" still answer when they're left out of totals.
     */
    fun filterOf(s: Subject, income: Boolean, catOf: (Txn) -> Cat, counts: (Txn) -> Boolean): (Txn) -> Boolean = when (s) {
        // Moving money between your own accounts or getting it back isn't money in.
        Subject.All -> if (income) { t -> !t.debit && catOf(t) != Cat.TRANSFER && catOf(t) != Cat.REFUND } else counts
        is Subject.Cats -> { t -> t.debit != income && catOf(t) in s.cats }
        is Subject.Payee -> { t -> t.debit != income && t.payeeKey in s.keys }
    }

    private fun months(sp: Span): List<YearMonth> {
        val out = ArrayList<YearMonth>()
        var m = YearMonth.from(sp.from)
        while (m <= YearMonth.from(sp.to)) { out += m; m = m.plusMonths(1) }
        return out
    }

    private fun monthBars(l: List<Txn>, ms: List<YearMonth>, count: Boolean = false): List<Bar> {
        val by = l.groupBy { it.month() }
        return ms.takeLast(12).map { m ->
            val g = by[m].orEmpty()
            val v = if (count) g.size.toLong() else g.sumOf { it.amount }
            Bar(m.format(mmm), v, if (count) "$v" else inrShort(v))
        }
    }

    private fun <K> top(l: List<Txn>, key: (Txn) -> K, name: (K, List<Txn>) -> String): List<Pair<String, List<Txn>>> =
        l.groupBy(key).map { (k, g) -> name(k, g) to g }.sortedByDescending { p -> p.second.sumOf { it.amount } }

    private fun topBars(groups: List<Pair<String, List<Txn>>>) =
        groups.take(3).map { (n, g) -> g.sumOf { it.amount }.let { Bar(n, it, inr(it)) } }

    private fun pct(part: Long, whole: Long) = if (whole == 0L) 0 else (part * 100 / whole).toInt()

    fun answer(q: Question, today: LocalDate, txns: List<Txn>, catOf: (Txn) -> Cat, counts: (Txn) -> Boolean): Answer {
        val first = txns.minOfOrNull { it.date() } ?: today
        // A fully refunded payment cost nothing, so it isn't one of the payments counted, as in the recap.
        fun kept(t: Txn) = !t.debit || t.amount > 0
        val f = filterOf(q.subject, q.income, catOf, counts)
        val matching = txns.filter { f(it) && kept(it) }.sortedByDescending { it.time }
        val who = label(q.subject, q.income)
        val noun = if (q.income) "credit" else "payment"
        val allTime = q.span == null
        val sp = q.span ?: Span(first, today)
        fun inSpan(s: Span) = matching.filter { it.date() in s }
        fun clipped(s: Span) = if (s.from.isBefore(first) && !first.isAfter(s.to)) Span(first, s.to) else s
        val l = inSpan(sp)
        val total = l.sumOf { it.amount }
        val range = rangeText(sp, today, allTime)
        val subjAll = q.subject == Subject.All
        val wide = if (subjAll) l else matching

        return when (q.kind) {
            Kind.TOTAL -> {
                val ms = months(sp)
                val bars = when {
                    ms.size >= 2 -> monthBars(l, ms)
                    subjAll || ((q.subject as? Subject.Cats)?.cats?.size ?: 1) > 1 -> topBars(top(l, catOf) { c, _ -> c.label })
                    q.subject is Subject.Cats -> topBars(top(l, { it.payeeKey }) { _, g -> g.first().payee })
                    else -> emptyList()
                }.takeIf { it.size >= 2 }.orEmpty()
                Answer(if (q.income) "Received" else "Spent", total, false, "$who, $range, ${plural(l.size, noun)}.", l, wide, bars)
            }
            Kind.COUNT -> {
                val ms = months(sp)
                val bars = if (ms.size >= 2) monthBars(l, ms, count = true) else emptyList()
                Answer(if (q.income) "Credits" else "Payments", l.size.toLong(), true, "$who, $range, ${inr(total)} in all.", l, wide, bars)
            }
            Kind.BIGGEST -> {
                val big = l.sortedByDescending { it.amount }.take(3)
                val t = big.firstOrNull()
                val s = if (t == null) "$who, $range: no ${noun}s." else "$who, $range: biggest of ${plural(l.size, noun)} was ${t.payee} on ${dayText(t.date(), today)}."
                Answer("Biggest $noun", t?.amount ?: 0, false, s, big.sortedByDescending { it.time }, wide, big.map { Bar(it.payee, it.amount, inr(it.amount)) })
            }
            Kind.TOP_DAY, Kind.TOP_MONTH -> {
                val byDay = q.kind == Kind.TOP_DAY
                val groups = if (byDay) top(l, { it.date() }) { d, _ -> dayText(d, today) }
                else top(l, { it.month() }) { m, _ -> m.format(mmmm) + if (m.year != today.year) " ${m.year}" else "" }
                val best = groups.firstOrNull()
                val s = if (best == null) "$who, $range: no ${noun}s." else "$who, $range: ${best.first}, ${plural(best.second.size, noun)}."
                Answer(if (byDay) "Most in a day" else "Most in a month", best?.second?.sumOf { it.amount } ?: 0, false, s,
                    best?.second.orEmpty(), wide, topBars(groups).takeIf { it.size >= 2 }.orEmpty())
            }
            Kind.TOP_PLACE, Kind.TOP_CAT -> {
                // Like Insights' top places: transfers to people and family aren't places.
                val pool = if (q.kind == Kind.TOP_PLACE && subjAll && !q.income) l.filter { catOf(it) != Cat.PEOPLE && catOf(it) != Cat.FAMILY } else l
                val groups = if (q.kind == Kind.TOP_PLACE) top(pool, { it.payeeKey }) { _, g -> g.first().payee } else top(pool, catOf) { c, _ -> c.label }
                val best = groups.firstOrNull()
                val v = best?.second?.sumOf { it.amount } ?: 0
                val s = if (best == null) "$who, $range: no ${noun}s." else "$who, $range: ${best.first}, ${plural(best.second.size, noun)}, ${pct(v, total)}% of ${inr(total)}."
                val title = if (q.kind == Kind.TOP_CAT) "Top category" else if (q.income) "Most from" else "Most spent at"
                Answer(title, v, false, s, best?.second.orEmpty(), wide, topBars(groups).takeIf { it.size >= 2 }.orEmpty())
            }
            Kind.AVG_MONTH -> {
                val now = YearMonth.from(today)
                var ms = months(q.span ?: Span(now.minusMonths(12).atDay(1), now.minusMonths(1).atEndOfMonth()))
                    .filter { it >= YearMonth.from(first) }
                // A month still under way would drag the average down.
                if (ms.size > 1 && ms.last() == now && today.dayOfMonth < now.lengthOfMonth()) ms = ms.dropLast(1)
                if (ms.isEmpty()) ms = listOf(now)
                val s2 = Span(ms.first().atDay(1), minOf(ms.last().atEndOfMonth(), today))
                val l2 = inSpan(s2)
                val t2 = l2.sumOf { it.amount }
                val r = rangeText(s2, today).let { if (ms.size > 1) "${ms.first().format(mmm)} ${ms.first().year} – ${ms.last().format(mmm)} ${ms.last().year}" else it }
                Answer("A month, on average", t2 / ms.size, false, "$who, $r: ${inr(t2)} over ${plural(ms.size, "month")}.", l2, if (subjAll) l2 else matching,
                    monthBars(l2, ms).takeIf { it.size >= 2 }.orEmpty())
            }
            Kind.AVG_WEEK, Kind.AVG_DAY -> {
                val week = q.kind == Kind.AVG_WEEK
                val s2 = clipped(q.span ?: Span(today.minusDays(if (week) 83 else 29), today))
                val l2 = inSpan(s2)
                val t2 = l2.sumOf { it.amount }
                val v = if (week) t2 * 7 / s2.days else t2 / s2.days
                Answer(if (week) "A week, on average" else "A day, on average", v, false,
                    "$who, ${rangeText(s2, today)}: ${inr(t2)} over ${plural(s2.days.toInt(), "day")}.", l2, if (subjAll) l2 else matching)
            }
            Kind.AVG_PAYMENT -> Answer("Per $noun, on average", if (l.isEmpty()) 0 else total / l.size, false,
                "$who, $range: ${inr(total)} over ${plural(l.size, noun)}.", l, wide)
            Kind.COMPARE -> {
                val sb = q.vsSubject
                if (sb != null) {
                    val other = txns.filter { filterOf(sb, q.income, catOf, counts)(it) && kept(it) }.sortedByDescending { it.time }
                    val l2 = other.filter { it.date() in sp }
                    val b = l2.sumOf { it.amount }
                    val who2 = label(sb, q.income)
                    Answer(who, total, false, "$who ${inr(total)} vs $who2 ${inr(b)}, $range. ${diff(total, b)}",
                        (l + l2).sortedByDescending { it.time }, (wide + if (sb == Subject.All) l2 else other).distinct().sortedByDescending { it.time },
                        listOf(Bar(who, total, inr(total)), Bar(who2, b, inr(b))))
                } else {
                    val vs = q.vs!!
                    val l2 = inSpan(vs)
                    val b = l2.sumOf { it.amount }
                    // Matched stretches of days read best side by side: "1–3 Oct vs 1–3 Sep".
                    val same = sp.to == today && vs.to.isBefore(sp.from) && vs.days == sp.days
                    val ra = if (same) days(sp, today) else rangeText(sp, today)
                    val rb = if (same) days(vs, today) else rangeText(vs, today)
                    Answer(if (q.income) "Received" else "Spent", total, false, "$who: ${inr(total)} $ra vs ${inr(b)} $rb. ${diff(total, b)}",
                        (l + l2).sortedByDescending { it.time }, if (subjAll) (l + l2).sortedByDescending { it.time } else matching,
                        listOf(Bar(ra.cap(), total, inr(total)), Bar(rb.cap(), b, inr(b))))
                }
            }
        }
    }

    private fun days(sp: Span, today: LocalDate): String {
        val (a, b) = sp.from to sp.to
        if (a.year != b.year || a.month != b.month) return rangeText(sp, today)
        val m = a.format(mmm) + if (a.year != today.year) " ${a.year}" else ""
        return if (a == b) "${a.dayOfMonth} $m" else "${a.dayOfMonth}–${b.dayOfMonth} $m"
    }

    private fun String.cap() = replaceFirstChar { it.titlecase(L) }

    private fun diff(a: Long, b: Long): String {
        if (b == 0L) return if (a == 0L) "Nothing in either." else "Nothing to compare against."
        val p = ((a - b) * 100 / b).toInt()
        return when {
            p == 0 -> "About the same."
            p > 0 -> "$p% more."
            else -> "${-p}% less."
        }
    }
}
