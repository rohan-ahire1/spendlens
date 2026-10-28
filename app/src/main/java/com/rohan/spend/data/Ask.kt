package com.rohan.spend.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import java.util.Locale

/** What a question asks for. */
enum class Kind { TOTAL, COUNT, BIGGEST, TOP_DAY, TOP_MONTH, TOP_PLACE, TOP_CAT, AVG_DAY, AVG_WEEK, AVG_MONTH, AVG_PAYMENT, COMPARE }

sealed interface Subject {
    data object All : Subject
    data class Cats(val name: String, val cats: Set<Cat>) : Subject
    data class Payee(val name: String, val keys: Set<String>) : Subject
}

data class Span(val from: LocalDate, val to: LocalDate) {
    operator fun contains(d: LocalDate) = !d.isBefore(from) && !d.isAfter(to)
    val days: Long get() = (ChronoUnit.DAYS.between(from, to) + 1).coerceAtLeast(1)
}

data class Question(
    val kind: Kind,
    val income: Boolean,
    val subject: Subject,
    /** Null when no time was named; each kind picks its own default. */
    val span: Span?,
    val vs: Span? = null,
    val vsSubject: Subject? = null,
)

sealed interface Parsed {
    data class Ok(val q: Question) : Parsed
    data class Unclear(val why: String) : Parsed
}

/**
 * Reads a typed question into a [Question]. Rule-based and offline: time phrases are cut
 * out first, then cue words decide the kind, and whatever is left names the subject.
 */
object AskParser {
    private val L = Locale.ENGLISH
    private val monthNames = listOf("january", "february", "march", "april", "may", "june", "july", "august", "september", "october", "november", "december")
    private const val MON = "jan(?:uary)?|feb(?:ruary)?|mar(?:ch)?|apr(?:il)?|may|june?|july?|aug(?:ust)?|sep(?:t(?:ember)?)?|oct(?:ober)?|nov(?:ember)?|dec(?:ember)?"
    private val numWords = listOf("one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten", "eleven", "twelve")
    private fun num(s: String) = s.toIntOrNull() ?: (numWords.indexOf(s) + 1).takeIf { it > 0 }
    private fun monthOf(s: String) = monthNames.indexOfFirst { it.startsWith(s.take(3)) } + 1

    private class Found(val start: Int, val end: Int, val span: Span, val pre: String)

    private fun period(which: String, unit: String, t: LocalDate): Span = when (unit) {
        "week" -> when (which) {
            "past" -> Span(t.minusDays(6), t)
            "this", "current" -> Span(t.minusDays(t.dayOfWeek.value - 1L), t)
            else -> t.minusDays(t.dayOfWeek.value - 1L).minusWeeks(1).let { Span(it, it.plusDays(6)) }
        }
        "month" -> when (which) {
            "past" -> Span(t.minusMonths(1).plusDays(1), t)
            "this", "current" -> Span(t.withDayOfMonth(1), t)
            else -> YearMonth.from(t).minusMonths(1).let { Span(it.atDay(1), it.atEndOfMonth()) }
        }
        else -> when (which) {
            "past" -> Span(t.minusYears(1).plusDays(1), t)
            "this", "current" -> Span(t.withDayOfYear(1), t)
            else -> LocalDate.of(t.year - 1, 1, 1).let { Span(it, it.withMonth(12).withDayOfMonth(31)) }
        }
    }

    private fun day(t: LocalDate, m: Int, d: Int, y: String): Span? {
        if (m < 1) return null
        val year = y.toIntOrNull() ?: t.year
        if (d < 1 || d > YearMonth.of(year, m).lengthOfMonth()) return null
        var date = LocalDate.of(year, m, d)
        if (y.isEmpty() && date.isAfter(t)) date = date.minusYears(1)
        return Span(date, date)
    }

    private fun month(t: LocalDate, m: Int, y: String): Span? {
        if (m < 1) return null
        var ym = YearMonth.of(y.toIntOrNull() ?: t.year, m)
        // A month without a year is the latest one that has started.
        if (y.isEmpty() && ym > YearMonth.from(t)) ym = ym.minusYears(1)
        return Span(ym.atDay(1), clip(ym.atEndOfMonth(), ym.atDay(1), t))
    }

    private fun clip(end: LocalDate, start: LocalDate, t: LocalDate) = if (end.isAfter(t) && !start.isAfter(t)) t else end

    private val timeRules: List<Pair<Regex, (MatchResult, LocalDate) -> Span?>> = listOf<Pair<String, (List<String>, LocalDate) -> Span?>>(
        "(?:last|past|previous) (\\d{1,3}|${numWords.joinToString("|")}) (day|week|month|year)s?" to { g, t ->
            val n = num(g[2])!!.toLong()
            if (n == 0L) null else Span(
                when (g[3]) {
                    "day" -> t.minusDays(n - 1)
                    "week" -> t.minusDays(7 * n - 1)
                    "month" -> t.minusMonths(n).plusDays(1)
                    else -> t.minusYears(n).plusDays(1)
                }, t,
            )
        },
        "(this|current|last|previous|past) (week|month|year)" to { g, t -> period(g[2], g[3], t) },
        "day before yesterday" to { _, t -> t.minusDays(2).let { Span(it, it) } },
        "yesterday" to { _, t -> t.minusDays(1).let { Span(it, it) } },
        "today|tonight|this morning|this evening" to { _, t -> Span(t, t) },
        "ytd|year to date" to { _, t -> period("this", "year", t) },
        "mtd|month to date" to { _, t -> period("this", "month", t) },
        "(\\d{1,2})(?:st|nd|rd|th)? (?:of )?($MON)(?: (\\d{4}))?" to { g, t -> day(t, monthOf(g[3]), g[2].toInt(), g[4]) },
        "($MON) (\\d{1,2})(?:st|nd|rd|th)?\\b(?: (\\d{4}))?" to { g, t -> day(t, monthOf(g[2]), g[3].toInt(), g[4]) },
        "($MON)(?: (\\d{4}))?" to { g, t -> month(t, monthOf(g[2]), g[3]) },
        "((?:19|20)\\d{2})" to { g, t -> LocalDate.of(g[2].toInt(), 1, 1).let { Span(it, clip(it.withDayOfYear(it.lengthOfYear()), it, t)) } },
    ).map { (p, f) -> Regex("""\b(?:(since|from|after) )?(?:the )?(?:$p)\b""") to { m: MatchResult, t: LocalDate -> f(m.groupValues, t) } }

    /** Finds every time phrase, in the order written, and blanks it out of the text. */
    private fun times(s: String, t: LocalDate): Pair<List<Span>, String> {
        val b = StringBuilder(s)
        val found = ArrayList<Found>()
        for ((re, f) in timeRules) {
            for (m in re.findAll(b.toString())) {
                val sp = f(m, t) ?: continue
                found += Found(m.range.first, m.range.last + 1, sp, m.groupValues[1])
                for (i in m.range) b.setCharAt(i, ' ')
            }
        }
        found.sortBy { it.start }
        // "from june to august", "between march and may"
        if (found.size == 2 && s.substring(found[0].end, found[1].start).trim() in setOf("to", "till", "until", "through", "thru", "and")) {
            val (a, z) = found
            for (i in a.end until z.start) b.setCharAt(i, ' ')
            return listOf(Span(a.span.from, z.span.to)) to b.toString()
        }
        return found.map {
            when (it.pre) {
                "since", "from" -> Span(it.span.from, t)
                "after" -> Span(it.span.to.plusDays(1), t)
                else -> it.span
            }
        } to b.toString()
    }

    private val cueRe = Regex("""\b(how|what|whats|where|which|who|total|average|avg|biggest|largest|highest|most|spent|spend|spending|receive|received|earned|income|salary|vs|versus|compared?|often|times|daily|weekly|monthly|per)\b""")
    private val amountRe = Regex("""\b(how much|spent|spend|spending|total|receive|received|earned|income|salary)\b""")
    private val incomeRe = Regex("""\b(receive|received|receiving|got paid|paid me|earn|earned|earning|earnings|income|credited|credits|came in|come in|money in|incoming)\b""")
    private val compareRe = Regex("""\b(vs|versus|compared? (?:to|with)|compared?|against)\b""")
    private val countRe = Regex("""\bhow (?:many|often)\b|\bnumber of\b|\bcount\b|\btimes\b""")
    private val avgRe = Regex("""\b(average|avg|mean|typical|typically|usually)\b""")
    private val perRe = Regex("""\b(?:per|a|an|each|every) (day|week|month)\b|\b(daily|weekly|monthly)\b""")
    private val avgTxnRe = Regex("""\b(?:per|average|avg|mean|typical) (?:\w+ )?(payment|order|transaction|txn|purchase)s?\b""")
    private val bigRe = Regex("""\b(biggest|largest|highest|max|maximum|costliest|priciest|heaviest|most expensive)\b""")
    private val topRe = Regex("""\b(most|top)\b""")
    private val placeRe = Regex("""\b(where|who|whom|places?|payees?|merchants?|shops?|stores?|apps?|brands?)\b""")
    private val catRe = Regex("""\b(category|categories)\b""")
    private val txnRe = Regex("""\b(payment|transaction|txn|purchase|expense|order)s?\b""")

    private fun kindOf(r: String): Kind {
        if (countRe.containsMatchIn(r)) return Kind.COUNT
        val per = perRe.find(r)
        val big = bigRe.containsMatchIn(r)
        val ranked = big || topRe.containsMatchIn(r)
        // "the most I spent in a day" asks for the top day; "a day" only means an average without "most".
        if (avgRe.containsMatchIn(r) || per != null && !ranked) {
            if (avgTxnRe.containsMatchIn(r)) return Kind.AVG_PAYMENT
            return when (per?.groupValues?.drop(1)?.firstOrNull { it.isNotEmpty() }) {
                "day", "daily" -> Kind.AVG_DAY
                "week", "weekly" -> Kind.AVG_WEEK
                else -> Kind.AVG_MONTH
            }
        }
        if (!ranked) return Kind.TOTAL
        return when {
            Regex("""\b(day|date)\b""").containsMatchIn(r) -> Kind.TOP_DAY
            Regex("""\bmonth\b""").containsMatchIn(r) -> Kind.TOP_MONTH
            big && txnRe.containsMatchIn(r) -> Kind.BIGGEST
            placeRe.containsMatchIn(r) -> Kind.TOP_PLACE
            catRe.containsMatchIn(r) -> Kind.TOP_CAT
            big -> Kind.BIGGEST
            Regex("""\bwhat\b""").containsMatchIn(r) -> Kind.TOP_CAT
            else -> Kind.TOP_PLACE
        }
    }

    private val filler = (
        "how much many often times time number count of total totals overall altogether combined all in on at for to from with by into " +
            "the a an my me i ive im we our us you your did do does done was were is are be been being have has had what whats which where who whom when " +
            "spend spent spending spends pay paid paying payment payments transaction transactions txn txns purchase purchases order orders money rs inr rupees " +
            "amount amounts cost costs expense expenses expenditure give show tell please can could would will so far till until now date since after between " +
            "during ever whole entire there that this these those it its get got per average avg mean typical typically usually daily weekly monthly " +
            "day days week weeks month months year years biggest largest highest most expensive costliest priciest heaviest max maximum single top " +
            "place places payee payees merchant merchants shop shops store stores app apps brand brands category categories " +
            "vs versus compared compare against than and or up went just only about roughly around exactly make made each every like"
        ).split(' ').toSet()

    private fun words(s: String) = s.split(' ').filter { it.isNotBlank() && it !in filler }

    private val synonyms: Map<String, Subject.Cats> = buildMap {
        Cat.entries.forEach { put(norm(it.label), Subject.Cats(it.label, setOf(it))) }
        fun add(c: Cat, vararg w: String) = w.forEach { put(it, Subject.Cats(c.label, setOf(c))) }
        add(Cat.GROCERIES, "grocery", "groceries", "kirana", "vegetables", "veggies", "fruits", "milk", "quick commerce")
        add(Cat.FOOD_DELIVERY, "delivery", "deliveries", "food order", "food orders", "ordering in", "ordered in", "takeaway", "takeout", "online food")
        add(Cat.EATING_OUT, "eating", "eat out", "dining", "dining out", "dine out", "restaurant", "restaurants", "cafe", "cafes", "coffee")
        add(Cat.COMMUTE, "cab", "cabs", "taxi", "taxis", "auto", "autos", "rickshaw", "ride", "rides", "transport", "transportation", "metro", "travel local")
        add(Cat.FUEL, "petrol", "diesel", "cng")
        add(Cat.SUBSCRIPTIONS, "subscription", "subs", "ott", "streaming", "membership", "memberships")
        add(Cat.SHOPPING, "clothes", "clothing", "online shopping", "gadgets", "electronics")
        add(Cat.BILLS, "bill", "utilities", "utility", "electricity", "power", "phone bill", "mobile bill", "recharge", "recharges", "internet", "wifi", "broadband", "insurance")
        add(Cat.MEDICAL, "medicine", "medicines", "meds", "pharmacy", "doctor", "doctors", "health", "healthcare", "hospital")
        add(Cat.MOVIES, "movie", "cinema", "film", "films")
        add(Cat.TRAVEL, "trip", "trips", "flight", "flights", "vacation", "holiday", "holidays")
        add(Cat.SALON, "haircut", "haircuts", "grooming", "parlour", "parlor", "spa")
        add(Cat.CASH, "atm", "withdrawal", "withdrawals", "cash withdrawal")
        add(Cat.PEOPLE, "friends", "transfers", "people")
        add(Cat.SMALL, "small", "small payment")
        add(Cat.FAMILY, "parents")
        add(Cat.INVESTING, "investment", "investments", "invest", "sip", "sips", "mutual fund", "mutual funds", "stocks", "shares")
        add(Cat.CARD_BILL, "card bills", "credit card", "credit card bill", "credit card bills", "cc bill")
        add(Cat.SALARY, "salaries", "paycheck", "wages")
        add(Cat.REFUND, "refunds", "cashback", "cash back", "reversal", "reversals")
        put("food", Subject.Cats("Food delivery and eating out", setOf(Cat.FOOD_DELIVERY, Cat.EATING_OUT)))
        put("ordering", get("delivery")!!)
    }

    private fun squash(s: String) = s.lowercase(L).filter { it.isLetterOrDigit() }

    private fun lev(a: String, b: String): Int {
        var prev = IntArray(b.length + 1) { it }
        for (i in a.indices) {
            val cur = IntArray(b.length + 1).also { it[0] = i + 1 }
            for (j in b.indices) cur[j + 1] = minOf(prev[j + 1] + 1, cur[j] + 1, prev[j] + if (a[i] == b[j]) 0 else 1)
            prev = cur
        }
        return prev[b.length]
    }

    private fun payee(phrase: String, payees: List<Pair<String, String>>): Subject.Payee? {
        val t = squash(phrase)
        if (t.length < 3) return null
        // The bank's part of a UPI handle would match everyone, so only the name side counts.
        var hit = payees.filter { (n, k) -> startsAWord(n, t) || startsAWord(k.substringBefore('@'), t) }
        if (hit.isEmpty() && t.length >= 4) {
            val room = if (t.length >= 7) 2 else 1
            hit = payees.filter { (n, _) -> lev(squash(n), t) <= room || n.split(' ').any { w -> squash(w).length >= 3 && lev(squash(w), t) <= room } }
        }
        if (hit.isEmpty()) return null
        val name = hit.map { it.first }.distinct().singleOrNull() ?: phrase.split(' ').joinToString(" ") { w -> w.replaceFirstChar { it.titlecase(L) } }
        return Subject.Payee(name, hit.map { it.second }.toSet())
    }

    /** Whether [t] begins at one of [name]'s words: "rent" isn't in "Torrent Power", "power" is. */
    private fun startsAWord(name: String, t: String): Boolean {
        val w = name.split(Regex("""[^\p{L}\p{N}]+""")).filter { it.isNotEmpty() }
        return w.indices.any { i -> squash(w.drop(i).joinToString("")).startsWith(t) }
    }

    private fun subject(phrase: String, payees: List<Pair<String, String>>): Subject? {
        if (phrase.isEmpty()) return Subject.All
        synonyms[phrase]?.let { return it }
        synonyms[phrase.removeSuffix("s")]?.let { return it }
        payee(phrase, payees)?.let { return it }
        // A known category inside a longer phrase, longest first: "food delivery stuff".
        return synonyms.keys.filter { Regex("""\b${Regex.escape(it)}\b""").containsMatchIn(phrase) }.maxByOrNull { it.length }?.let { synonyms[it] }
    }

    private fun unknown(phrase: String) = Parsed.Unclear("Couldn't match \"$phrase\" to a payee or category, so here's a plain search.")

    /** The same stretch just before [a]: last month for this month, last week for this week. */
    fun before(a: Span): Span {
        if (a.from.dayOfMonth == 1) {
            val n = ChronoUnit.MONTHS.between(YearMonth.from(a.from), YearMonth.from(a.to)) + 1
            val ym = YearMonth.from(a.from)
            return Span(ym.minusMonths(n).atDay(1), ym.minusMonths(1).atEndOfMonth())
        }
        if (a.from.dayOfWeek == DayOfWeek.MONDAY && a.days <= 7) return Span(a.from.minusWeeks(1), a.from.minusDays(1))
        return Span(a.from.minusDays(a.days), a.from.minusDays(1))
    }

    /**
     * [b] cut to as many days as [a] when [a] is still under way, so month-to-date
     * meets the same days of last month rather than all of it.
     */
    fun alike(a: Span, b: Span, today: LocalDate): Span =
        if (a.to == today && b.to.isBefore(a.from) && b.days > a.days) Span(b.from, b.from.plusDays(a.days - 1)) else b

    private val prevWords = setOf("last", "previous", "prior", "before", "usual")

    /** Null when the text doesn't read like a question, so plain search should handle it. */
    fun parse(text: String, today: LocalDate, payees: List<Pair<String, String>>): Parsed? {
        val s = norm(text)
        if (s.isBlank()) return null
        val (spans, blanked) = times(s, today)
        if (spans.isEmpty() && !cueRe.containsMatchIn(blanked)) return null
        // "after this month", "from august to june": nothing to count.
        if (spans.any { it.from.isAfter(it.to) }) return Parsed.Unclear("That time hasn't happened yet, so there's nothing to count.")
        val income = incomeRe.containsMatchIn(blanked)
        val rest = incomeRe.replace(blanked, " ")
        fun inc(sub: Subject) = income || sub is Subject.Cats && sub.cats.all { it.bucket == Bucket.INCOME }

        val cm = compareRe.find(rest)
        if (cm != null) {
            val left = rest.substring(0, cm.range.first)
            var right = rest.substring(cm.range.last + 1)
            if (spans.size >= 2) {
                val phrase = words("$left $right").joinToString(" ")
                val sub = subject(phrase, payees) ?: return unknown(phrase)
                return Parsed.Ok(Question(Kind.COMPARE, inc(sub), sub, spans[0], alike(spans[0], spans[1], today)))
            }
            var lw = words(left)
            if (lw.isEmpty() && Regex("""\band\b""").containsMatchIn(right)) {
                lw = words(right.substringBefore(" and "))
                right = right.substringAfter(" and ")
            }
            val rw = words(right)
            if (spans.size == 1 && (rw.isEmpty() || rw.all { it in prevWords })) {
                val phrase = lw.joinToString(" ")
                val sub = subject(phrase, payees) ?: return unknown(phrase)
                return Parsed.Ok(Question(Kind.COMPARE, inc(sub), sub, spans[0], alike(spans[0], before(spans[0]), today)))
            }
            if (lw.isNotEmpty() && rw.isNotEmpty()) {
                val a = subject(lw.joinToString(" "), payees) ?: return unknown(lw.joinToString(" "))
                val b = subject(rw.joinToString(" "), payees) ?: return unknown(rw.joinToString(" "))
                return Parsed.Ok(Question(Kind.COMPARE, inc(a), a, spans.firstOrNull(), vsSubject = b))
            }
            return Parsed.Unclear("Couldn't tell what to compare. Try \"this month vs last month\".")
        }

        val kind = kindOf(rest)
        val phrase = words(rest).joinToString(" ")
        val sub = subject(phrase, payees) ?: return unknown(phrase)
        if (kind == Kind.TOTAL && sub == Subject.All && spans.isEmpty() && !amountRe.containsMatchIn(s)) return null
        // Two loose times without "vs" or "to": cover both.
        val span = if (spans.isEmpty()) null else Span(spans.minOf { it.from }, spans.maxOf { it.to })
        return Parsed.Ok(Question(kind, inc(sub), sub, span))
    }

    fun norm(s: String) = s.lowercase(L).replace(Regex("""['’`]"""), "").replace(Regex("""[^a-z0-9]+"""), " ").trim()
}
