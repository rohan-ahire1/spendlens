package com.rohan.spend.data

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.abs

private val zone: ZoneId = ZoneId.systemDefault()

fun Txn.date(): LocalDate = Instant.ofEpochMilli(time).atZone(zone).toLocalDate()
fun Txn.month(): YearMonth = YearMonth.from(date())

/** User choices that change how totals are counted. */
data class Counting(
    val excludeFamily: Boolean = true,
    val countCardBills: Boolean = false,
)

data class CatTotal(val cat: Cat, val total: Long, val count: Int)

data class Change(val cat: Cat, val now: Long, val pct: Int)

data class PlaceTotal(val payee: String, val cat: Cat, val total: Long, val visits: Int)

/** Spending habits over a recent window, for Insights. */
data class Habits(
    /** Average spend on each weekday, Monday first. */
    val weekday: List<Long>,
    /** Share of spend in the morning, afternoon, evening and night. */
    val daypart: List<Long>,
    val smallCount: Int,
    val smallTotal: Long,
)

enum class Period(val label: String) { MONTH("Month"), WEEK("Week"), YEAR("Year") }

/** All the numbers the screens need, computed once per data change. */
class Stats(
    private val txns: List<Txn>,
    private val catOf: (Txn) -> Cat,
    private val counting: Counting,
    val today: LocalDate = LocalDate.now(),
    private val net: (Txn) -> Long = { it.amount },
) {
    /** What a payment counts for in every spending total: the user's share, less refunds. */
    fun spend(t: Txn): Long = net(t)

    fun counts(t: Txn): Boolean {
        if (!t.debit) return false
        return when (catOf(t).bucket) {
            Bucket.LIVING -> true
            Bucket.FAMILY -> !counting.excludeFamily
            Bucket.MOVE -> counting.countCardBills
            else -> false
        }
    }

    val thisMonth: YearMonth = YearMonth.from(today)

    fun range(p: Period): Pair<LocalDate, LocalDate> = when (p) {
        Period.MONTH -> thisMonth.atDay(1) to today
        Period.WEEK -> today.minusDays(6) to today
        Period.YEAR -> LocalDate.of(today.year, 1, 1) to today
    }

    fun inRange(p: Period): List<Txn> {
        val (a, b) = range(p)
        return txns.filter { val d = it.date(); !d.isBefore(a) && !d.isAfter(b) }
    }

    fun spent(p: Period): Long = inRange(p).filter(::counts).sumOf { spend(it) }

    /** Living cost per month, oldest first, ending with the current month. */
    fun monthly(n: Int = 12, filter: (Txn) -> Boolean = ::counts): List<Pair<YearMonth, Long>> {
        val months = (n - 1 downTo 0).map { thisMonth.minusMonths(it.toLong()) }
        val sums = txns.filter(filter).groupBy { it.month() }.mapValues { e -> e.value.sumOf { spend(it) } }
        return months.map { it to (sums[it] ?: 0L) }
    }

    /** Drops chart months from before the SMS history starts, keeping at least six bars. */
    fun sinceHistory(months: List<Pair<YearMonth, Long>>): List<Pair<YearMonth, Long>> {
        val first = txns.minOfOrNull { it.month() } ?: return months
        return months.dropWhile { it.first < first }.takeIf { it.size >= 6 } ?: months.takeLast(6)
    }

    /** Median of the last 6 full months that had any spending. */
    val usualMonth: Long by lazy {
        val full = monthly(7).dropLast(1).map { it.second }.filter { it > 0 }.sorted()
        if (full.isEmpty()) 0 else full[full.size / 2]
    }

    fun usual(p: Period): Long = when (p) {
        Period.MONTH -> usualMonth
        Period.WEEK -> usualMonth * 7 / 30
        Period.YEAR -> usualMonth * today.monthValue
    }

    val daysLeft: Int get() = thisMonth.lengthOfMonth() - today.dayOfMonth

    fun byCat(p: Period): List<CatTotal> = inRange(p).filter(::counts)
        .groupBy(catOf)
        .map { (c, l) -> CatTotal(c, l.sumOf { spend(it) }, l.size) }
        .sortedByDescending { it.total }

    fun recent(limit: Int = 6): List<Txn> {
        val todays = txns.filter { it.date() == today }
        return (todays.ifEmpty { txns.take(limit) }).take(limit)
    }

    /** Counted spend per day of the current month. */
    val daily: Map<LocalDate, Long> by lazy { dailyIn(thisMonth) }

    /** Counted spend per day of any month. */
    fun dailyIn(ym: YearMonth): Map<LocalDate, Long> =
        txns.filter { counts(it) && it.month() == ym }.groupBy { it.date() }.mapValues { e -> e.value.sumOf { spend(it) } }

    fun byCatIn(ym: YearMonth): List<CatTotal> = txns.filter { counts(it) && it.month() == ym }
        .groupBy(catOf)
        .map { (c, l) -> CatTotal(c, l.sumOf { spend(it) }, l.size) }
        .sortedByDescending { it.total }

    /** The oldest month the SMS history reaches. */
    val firstMonth: YearMonth by lazy { txns.minOfOrNull { it.month() } ?: thisMonth }

    /** Counted spend for each of the last [n] days, oldest first. */
    fun lastDays(n: Int): List<Pair<LocalDate, Long>> {
        val from = today.minusDays(n - 1L)
        val sums = txns.filter { counts(it) && !it.date().isBefore(from) }.groupBy { it.date() }.mapValues { e -> e.value.sumOf { spend(it) } }
        return (0 until n).map { from.plusDays(it.toLong()).let { d -> d to (sums[d] ?: 0L) } }
    }

    fun onDay(d: LocalDate): List<Txn> = txns.filter { it.date() == d }

    val hasToday: Boolean get() = txns.any { it.date() == today }

    /** Salary per month averaged over months that had one, last 12 months. */
    val income: Long by lazy {
        val from = thisMonth.minusMonths(12)
        val sal = txns.filter { !it.debit && catOf(it) == Cat.SALARY && it.month().isAfter(from) }
        val months = sal.map { it.month() }.distinct().size
        if (months == 0) 0 else sal.sumOf { it.amount } / months
    }

    /** Average monthly spend per bucket over the last 12 full months that had data. */
    fun avgMonthly(filter: (Txn) -> Boolean): Long {
        val data = monthly(13, filter).dropLast(1).map { it.second }
        val active = monthly(13) { true }.dropLast(1).count { it.second > 0 }
        return if (active == 0) 0 else data.sum() / active
    }

    val avgLiving: Long by lazy { avgMonthly(::counts) }

    private fun counted(days: Long) = txns.filter { counts(it) && !it.date().isBefore(today.minusDays(days - 1)) }

    /** What a month in each category costs on average, over the last 3 full months. */
    val avgByCat: List<CatTotal> by lazy {
        val months = (1..3).map { thisMonth.minusMonths(it.toLong()) }.filter { it >= (txns.minOfOrNull { t -> t.month() } ?: thisMonth) }
        if (months.isEmpty()) return@lazy emptyList()
        txns.filter { counts(it) && it.month() in months }.groupBy(catOf)
            .map { (c, l) -> CatTotal(c, l.sumOf { spend(it) } / months.size, l.size) }
            .sortedByDescending { it.total }
    }

    /** Where money went most over the last [days] days. */
    fun topPlaces(days: Long = 90, n: Int = 5): List<PlaceTotal> = counted(days)
        .filter { catOf(it) != Cat.PEOPLE && catOf(it) != Cat.FAMILY }
        .groupBy { it.payeeKey }
        .map { (_, l) -> PlaceTotal(l.first().payee, catOf(l.first()), l.sumOf { spend(it) }, l.size) }
        .sortedByDescending { it.total }
        .take(n)

    fun habits(days: Long = 90): Habits {
        val l = counted(days)
        val from = today.minusDays(days - 1)
        val dowCount = IntArray(7).also { c -> (0 until days).forEach { c[from.plusDays(it).dayOfWeek.value - 1]++ } }
        val dow = LongArray(7).also { a -> l.forEach { a[it.date().dayOfWeek.value - 1] += spend(it) } }
        val part = LongArray(4).also { a ->
            l.forEach {
                val h = Instant.ofEpochMilli(it.time).atZone(zone).hour
                a[when (h) { in 5..11 -> 0; in 12..16 -> 1; in 17..21 -> 2; else -> 3 }] += spend(it)
            }
        }
        val small = counted(30).filter { it.amount < 200_00 }
        return Habits(
            dow.mapIndexed { i, v -> if (dowCount[i] == 0) 0 else v / dowCount[i] },
            part.toList(), small.size, small.sumOf { spend(it) },
        )
    }

    /** Days this month, up to today, with nothing counted spent. */
    val noSpendDays: Int by lazy { (1..today.dayOfMonth).count { (daily[thisMonth.atDay(it)] ?: 0L) == 0L } }
    val avgFamily: Long by lazy { avgMonthly { it.debit && catOf(it).bucket == Bucket.FAMILY } }

    /** Biggest moves this month vs the average of the 3 months before, pace-adjusted. */
    fun changes(limit: Int = 3): List<Change> {
        val pace = today.dayOfMonth.toDouble() / thisMonth.lengthOfMonth()
        val now = txns.filter { counts(it) && it.month() == thisMonth }.groupBy(catOf).mapValues { e -> e.value.sumOf { spend(it) } }
        val prev = (1..3).map { thisMonth.minusMonths(it.toLong()) }.toSet()
        val before = txns.filter { counts(it) && it.month() in prev }.groupBy(catOf).mapValues { e -> e.value.sumOf { spend(it) } / 3.0 * pace }
        return (now.keys + before.keys).mapNotNull { c ->
            val n = now[c] ?: 0L
            val b = before[c] ?: 0.0
            if (b < 1_000_00 || n == 0L) null else Change(c, n, ((n - b) / b * 100).toInt())
        }.sortedByDescending { abs(it.pct) }.take(limit)
    }

    fun inCat(cat: Cat): List<Txn> = txns.filter { it.debit && catOf(it) == cat }

    fun catMonthly(cat: Cat): List<Pair<YearMonth, Long>> = monthly(12) { it.debit && catOf(it) == cat }

    fun payeesThisMonth(cat: Cat): List<Triple<String, Long, Int>> =
        inCat(cat).filter { it.month() == thisMonth }.groupBy { it.payee }
            .map { (p, l) -> Triple(p, l.sumOf { spend(it) }, l.size) }
            .sortedByDescending { it.second }

    /** Recurring charges by payee, with cadence, price rises and ones that stopped. See [detectSubs]. */
    val subs: List<Subscription> by lazy { detectSubs(inCat(Cat.SUBSCRIPTIONS), today) }

    /** Live subscriptions a month, yearly ones spread over twelve. */
    val subsMonthly: Long get() = subs.perMonth()

    fun atPayeeThisMonth(t: Txn): Pair<Long, Int> {
        val l = txns.filter { it.debit && it.payeeKey == t.payeeKey && it.month() == thisMonth }
        return l.sumOf { spend(it) } to l.size
    }

    /** Per-account summary for the Accounts screen. */
    data class Account(val id: String, val isCard: Boolean, val balance: Long?, val monthSpend: Long, val sender: String, val balanceTime: Long? = null)

    val accounts: List<Account> by lazy {
        txns.filter { it.account != null }.groupBy { it.account!! }.map { (id, l) ->
            val isCard = l.count { it.channel == Channel.CARD } > l.size / 2
            val withBalance = l.filter { it.balance != null }.maxByOrNull { it.time }
            Account(
                id = id,
                isCard = isCard,
                balance = withBalance?.balance,
                monthSpend = l.filter { it.debit && it.month() == thisMonth }.sumOf { it.amount },
                sender = l.first().sender,
                balanceTime = withBalance?.time,
            )
        }.sortedByDescending { l -> txns.count { it.account == l.id } }
    }
}
