package com.rohan.spend.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class AskTest {
    private val today = LocalDate.of(2026, 10, 29) // a Thursday
    private var nextId = 0L

    private fun tx(date: String, rupees: Long, payee: String, debit: Boolean = true, key: String = payee.lowercase(), body: String = "") = Txn(
        id = nextId++,
        time = LocalDate.parse(date).atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
        amount = rupees * 100, debit = debit, payee = payee, payeeKey = key, account = "1234",
        channel = Channel.UPI, balance = null, body = body, sender = "HDFCBK",
    )

    private val txns = listOf(
        tx("2026-10-29", 150, "Blinkit"),
        tx("2026-10-28", 5000, "Rahul Sharma", key = "rahul.sharma99@okhdfcbank"),
        tx("2026-10-28", 350, "Starbucks"),
        tx("2026-10-27", 200, "Uber"),
        tx("2026-10-20", 150, "Uber"),
        tx("2026-10-15", 600, "Zomato"),
        tx("2026-10-10", 2000, "HPCL Petrol"),
        tx("2026-10-05", 100, "Uber"),
        tx("2026-10-05", 10000, "Zerodha"),
        tx("2026-10-02", 500, "Swiggy"),
        tx("2026-10-01", 300, "Amazon", debit = false, body = "Refund of Rs 300"),
        tx("2026-09-30", 100000, "Acme Corp", debit = false, body = "Salary credited"),
        tx("2026-09-12", 700, "Zomato"),
        tx("2026-09-01", 900, "Zepto"),
        tx("2026-07-10", 400, "Swiggy"),
        tx("2026-06-05", 300, "Swiggy"),
        tx("2026-05-20", 250, "Swiggy"),
        tx("2026-03-20", 1200, "Zepto"),
        tx("2026-03-14", 25000, "Croma"),
        tx("2026-03-03", 800, "Zepto"),
        tx("2025-12-10", 450, "Swiggy"),
        tx("2025-10-15", 90000, "Acme Corp", debit = false, body = "Salary credited"),
    )
    private val catOf: (Txn) -> Cat = Categorizer::auto
    private val counts = Stats(txns, catOf, Counting(), today)::counts
    private val payees = txns.distinctBy { it.payeeKey }.map { it.payee to it.payeeKey }

    private fun q(s: String, on: LocalDate = today): Question = (AskParser.parse(s, on, payees) as Parsed.Ok).q
    private fun ask(s: String, on: LocalDate = today): Answer = (Ask.reply(s, on, txns, catOf, counts) as Reply.Done).a
    private fun span(a: String, b: String) = Span(LocalDate.parse(a), LocalDate.parse(b))
    private fun rs(r: Long) = r * 100

    // ---------- Not a question ----------

    @Test fun plainSearchIsNotAQuestion() {
        assertNull(AskParser.parse("swiggy", today, payees))
        assertNull(AskParser.parse("", today, payees))
        assertNull(AskParser.parse("   ", today, payees))
    }

    @Test fun barePromptWordsWaitForMore() {
        assertNull(AskParser.parse("how", today, payees))
        assertNull(AskParser.parse("what", today, payees))
    }

    @Test fun unknownSubjectIsUnclear() {
        val r = Ask.reply("how much on xyzzy last month", today, txns, catOf, counts)
        assertTrue(r is Reply.Unclear)
        assertTrue((r as Reply.Unclear).why.contains("xyzzy"))
    }

    // ---------- Time phrases ----------

    @Test fun today() = assertEquals(span("2026-10-29", "2026-10-29"), q("spent today").span)
    @Test fun yesterday() = assertEquals(span("2026-10-28", "2026-10-28"), q("spent yesterday").span)
    @Test fun thisWeekStartsMonday() = assertEquals(span("2026-10-26", "2026-10-29"), q("uber this week").span)
    @Test fun lastWeekIsTheFullWeekBefore() = assertEquals(span("2026-10-19", "2026-10-25"), q("uber last week").span)
    @Test fun thisMonth() = assertEquals(span("2026-10-01", "2026-10-29"), q("spent this month").span)
    @Test fun lastMonth() = assertEquals(span("2026-09-01", "2026-09-30"), q("spent last month").span)
    @Test fun thisYear() = assertEquals(span("2026-01-01", "2026-10-29"), q("spent this year").span)
    @Test fun lastYear() = assertEquals(span("2025-01-01", "2025-12-31"), q("spent last year").span)
    @Test fun pastMonths() = assertEquals(span("2026-07-30", "2026-10-29"), q("spent in the last 3 months").span)
    @Test fun pastDaysInWords() = assertEquals(span("2026-10-23", "2026-10-29"), q("spent last seven days").span)
    @Test fun monthWithoutYearIsTheLatest() = assertEquals(span("2026-03-01", "2026-03-31"), q("groceries in march").span)
    @Test fun laterMonthWithoutYearIsLastYear() = assertEquals(span("2025-12-01", "2025-12-31"), q("spent in december").span)
    @Test fun currentMonthStopsToday() = assertEquals(span("2026-10-01", "2026-10-29"), q("spent in october").span)
    @Test fun monthWithYear() = assertEquals(span("2025-03-01", "2025-03-31"), q("spent in march 2025").span)
    @Test fun sinceMonth() = assertEquals(span("2026-06-01", "2026-10-29"), q("how much on swiggy since june").span)
    @Test fun yearStopsToday() = assertEquals(span("2026-01-01", "2026-10-29"), q("total spent in 2026").span)
    @Test fun singleDay() = assertEquals(span("2026-03-14", "2026-03-14"), q("spent on 14 march").span)
    @Test fun fromTo() = assertEquals(span("2026-06-01", "2026-08-31"), q("spent from june to august").span)

    @Test fun lastMonthInJanuaryIsDecember() =
        assertEquals(span("2026-12-01", "2026-12-31"), q("spent last month", LocalDate.of(2027, 1, 15)).span)

    @Test fun monthNameInJanuary() {
        val jan = LocalDate.of(2027, 1, 15)
        assertEquals(span("2026-03-01", "2026-03-31"), q("groceries in march", jan).span)
        assertEquals(span("2027-01-01", "2027-01-15"), q("spent in january", jan).span)
        assertEquals(span("2026-12-01", "2026-12-31"), q("spent in dec", jan).span)
    }

    @Test fun lastWeekAcrossYearEnd() =
        assertEquals(span("2026-12-28", "2027-01-03"), q("spent last week", LocalDate.of(2027, 1, 6)).span)

    // ---------- Subjects ----------

    @Test fun categoryByLabel() = assertEquals(setOf(Cat.FOOD_DELIVERY), (q("how much did I spend on food delivery last month").subject as Subject.Cats).cats)
    @Test fun categorySynonyms() {
        assertEquals(setOf(Cat.FUEL), (q("petrol this month").subject as Subject.Cats).cats)
        assertEquals(setOf(Cat.COMMUTE), (q("how much on cabs this year").subject as Subject.Cats).cats)
        assertEquals(setOf(Cat.EATING_OUT), (q("eating out last month").subject as Subject.Cats).cats)
        assertEquals(setOf(Cat.FOOD_DELIVERY, Cat.EATING_OUT), (q("food this month").subject as Subject.Cats).cats)
    }

    @Test fun payeeIgnoresCase() = assertEquals("Swiggy", (q("how much on swiggy since june").subject as Subject.Payee).name)
    @Test fun payeeTypo() = assertEquals("Zomato", (q("how much on zomatto this month").subject as Subject.Payee).name)
    @Test fun payeePartialName() = assertEquals("HPCL Petrol", (q("how much at hpcl this month").subject as Subject.Payee).name)
    @Test fun upiBankHandleDoesNotMatch() = assertTrue(Ask.reply("how much on okhdfcbank", today, txns, catOf, counts) is Reply.Unclear)
    @Test fun nothingNamedMeansEverything() = assertEquals(Subject.All, q("total spent in 2025").subject)

    // ---------- Amounts ----------

    @Test fun payeeSinceMonth() {
        val a = ask("how much on swiggy since june")
        assertEquals(rs(1200), a.value)
        assertEquals("Swiggy, 1 Jun – today, 3 payments.", a.sentence)
        assertEquals(3, a.txns.size)
        assertEquals(5, a.matches.size) // every Swiggy payment, for the list before tapping
        assertEquals(5, a.bars.size) // Jun to Oct
    }

    @Test fun categoryLastMonth() {
        val a = ask("how much did I spend on food delivery last month")
        assertEquals(rs(700), a.value)
        assertEquals("Food delivery, September, 1 payment.", a.sentence)
    }

    @Test fun payeeThisWeek() = assertEquals(rs(200), ask("uber this week").value)
    @Test fun groceriesInMarch() = assertEquals(rs(2000), ask("groceries in march").value)
    @Test fun totalInYear() = assertEquals(rs(450), ask("total spent in 2025").value)

    @Test fun spentYesterdayFollowsCountingRules() {
        // Rahul is a person, which still counts as living cost; nothing is left out here.
        assertEquals(rs(5350), ask("spent yesterday").value)
    }

    @Test fun everythingSkipsWhatTheAppDoesNotCount() {
        // Zerodha is investing and isn't spending; the refund is money in.
        val a = ask("spent this month")
        assertEquals(rs(150 + 5000 + 350 + 200 + 150 + 600 + 2000 + 100 + 500), a.value)
        assertTrue(a.txns.none { it.payee == "Zerodha" || !it.debit })
    }

    @Test fun categoryLeftOutOfTotalsStillAnswers() = assertEquals(rs(10000), ask("investing this month").value)

    // ---------- Counts ----------

    @Test fun countThisMonth() {
        val a = ask("how many times uber this month")
        assertTrue(a.isCount)
        assertEquals(3L, a.value)
    }

    @Test fun howOftenAllTime() {
        val a = ask("how often zomato")
        assertEquals(2L, a.value)
        assertTrue(a.sentence.startsWith("Zomato, since"))
    }

    // ---------- Extremes ----------

    @Test fun biggestPayment() {
        val a = ask("biggest payment in march")
        assertEquals(Kind.BIGGEST, q("biggest payment in march").kind)
        assertEquals(rs(25000), a.value)
        assertTrue(a.sentence.contains("Croma"))
        assertEquals("Croma", a.bars.first().label)
    }

    @Test fun mostExpensiveDay() {
        val qq = q("most expensive day last month")
        assertEquals(Kind.TOP_DAY, qq.kind)
        val a = ask("most expensive day this month")
        assertEquals(rs(5350), a.value)
        assertEquals(2, a.txns.size)
    }

    @Test fun whereMost() {
        assertEquals(Kind.TOP_PLACE, q("where did I spend most this year").kind)
        val a = ask("where did I spend most this year")
        // Croma beats Rahul because people aren't places.
        assertEquals(rs(25000), a.value)
        assertEquals("Croma", a.bars.first().label)
    }

    @Test fun whatCategoryMost() {
        assertEquals(Kind.TOP_CAT, q("what did I spend most on this year").kind)
        assertEquals("Shopping", ask("what did I spend most on this year").bars.first().label)
    }

    @Test fun biggestMonth() = assertEquals(Kind.TOP_MONTH, q("most expensive month this year").kind)

    // ---------- Averages ----------

    @Test fun averagePerMonth() {
        val qq = q("average groceries per month")
        assertEquals(Kind.AVG_MONTH, qq.kind)
        val a = ask("average groceries per month")
        // History starts Oct 2025, so the window is Oct 2025 to Sep 2026: 12 full months.
        assertEquals(rs(2900) / 12, a.value)
        assertTrue(a.sentence.contains("12 months"))
    }

    @Test fun averageMonthDropsTheMonthUnderWay() {
        val a = ask("average groceries per month this year")
        assertEquals(rs(2900) / 9, a.value)
    }

    @Test fun averageDaily() {
        assertEquals(Kind.AVG_DAY, q("average daily spend").kind)
        val a = ask("average daily spend")
        val last30 = txns.filter { counts(it) && !it.date().isBefore(today.minusDays(29)) }.sumOf { it.amount }
        assertEquals(last30 / 30, a.value)
    }

    @Test fun averageWeekly() = assertEquals(Kind.AVG_WEEK, q("how much do I spend per week").kind)
    @Test fun averageOrder() = assertEquals(Kind.AVG_PAYMENT, q("average swiggy order").kind)

    // ---------- Comparisons ----------

    @Test fun compareMonths() {
        val qq = q("food delivery this month vs last month")
        assertEquals(Kind.COMPARE, qq.kind)
        // Month to date meets the same 29 days of September, not all of it.
        assertEquals(span("2026-09-01", "2026-09-29"), qq.vs)
        val a = ask("food delivery this month vs last month")
        assertEquals(rs(1100), a.value)
        assertEquals("Food delivery: ₹1,100 1–29 Oct vs ₹700 1–29 Sep. 57% more.", a.sentence)
        assertEquals(3, a.txns.size)
    }

    @Test fun compareWithBareLast() = assertEquals(span("2026-09-01", "2026-09-29"), q("uber this month vs last").vs)
    @Test fun compareWeekWithWeekBefore() = assertEquals(span("2026-10-19", "2026-10-22"), q("spent this week compared to last").vs)
    @Test fun finishedMonthsCompareWhole() =
        assertEquals(span("2026-08-01", "2026-08-31"), q("spent september vs august").vs)
    @Test fun compareLastMonthFirst() {
        val qq = q("spent last month vs this month")
        assertEquals(span("2026-09-01", "2026-09-29"), qq.span)
        assertEquals(span("2026-10-01", "2026-10-29"), qq.vs)
    }

    @Test fun compareNamedMonthsWhileTheLaterIsUnderWay() {
        val qq = q("spent september vs october")
        assertEquals(span("2026-09-01", "2026-09-29"), qq.span)
        assertEquals(span("2026-10-01", "2026-10-29"), qq.vs)
        val rev = q("spent october vs september")
        assertEquals(span("2026-10-01", "2026-10-29"), rev.span)
        assertEquals(span("2026-09-01", "2026-09-29"), rev.vs)
    }

    @Test fun compareEarlyInTheMonth() {
        val on = LocalDate.of(2026, 10, 3)
        assertEquals(span("2026-09-01", "2026-09-03"), q("spent this month vs last month", on).vs)
        assertTrue(ask("spent this month vs last month", on).sentence.contains("1–3 Oct vs ₹900 1–3 Sep"))
    }

    // ---------- Edge cases ----------

    @Test fun afterSomethingNotOverIsUnclear() {
        assertTrue(AskParser.parse("average daily spend after this month", today, payees) is Parsed.Unclear)
        assertTrue(AskParser.parse("spent after today", today, payees) is Parsed.Unclear)
    }

    @Test fun mostInADayIsTheTopDay() {
        assertEquals(Kind.TOP_DAY, q("what is the most i spent in a day").kind)
        assertEquals(Kind.TOP_DAY, q("highest spend in a day this month").kind)
        assertEquals(Kind.AVG_DAY, q("how much do i spend a day").kind)
    }

    @Test fun payeesMatchOnWordStarts() {
        val l = listOf(tx("2026-10-10", 900, "Torrent Power"), tx("2026-10-11", 1500, "The Steakhouse"), tx("2026-10-12", 20000, "Rent Landlord"))
        val p = l.map { it.payee to it.payeeKey }
        fun sub(s: String) = ((AskParser.parse(s, today, p) as Parsed.Ok).q.subject as Subject.Payee).keys
        assertEquals(setOf("rent landlord"), sub("how much on rent"))
        assertEquals(setOf("torrent power"), sub("how much on torrent"))
        // A typo in a whole word still finds it.
        assertEquals(setOf("the steakhouse"), sub("how much on steakhose"))
        assertEquals(setOf("the steakhouse"), sub("how much on house"))
        assertTrue(AskParser.parse("how much on tea", today, p) !is Parsed.Ok ||
            (AskParser.parse("how much on tea", today, p) as Parsed.Ok).q.subject !is Subject.Payee)
    }

    @Test fun longerTermsMatchMidWord() {
        val l = listOf(tx("2026-10-10", 900, "BigBasket"), tx("2026-10-11", 1500, "Torrent Power"))
        val p = l.map { it.payee to it.payeeKey }
        fun sub(s: String) = ((AskParser.parse(s, today, p) as Parsed.Ok).q.subject as Subject.Payee).keys
        assertEquals(setOf("bigbasket"), sub("how much on basket"))
        // Four letters still have to start a word.
        assertTrue(AskParser.parse("how much on rent", today, p).let { it !is Parsed.Ok || it.q.subject !is Subject.Payee })
    }

    @Test fun comparePayees() {
        val qq = q("swiggy vs zomato")
        assertEquals("Zomato", (qq.vsSubject as Subject.Payee).name)
        val a = ask("swiggy vs zomato")
        assertEquals(rs(1900), a.value)
        assertEquals(rs(1300), a.bars[1].value)
    }

    @Test fun compareNothing() = assertTrue(Ask.reply("this vs that", today, txns, catOf, counts) is Reply.Unclear)

    // ---------- Income ----------

    @Test fun receivedInMonth() {
        val qq = q("how much did I receive in october")
        assertTrue(qq.income)
        // October's only credit is a refund, which is money back rather than money in.
        assertEquals(0L, ask("how much did I receive in october").value)
        assertEquals(rs(100000), ask("how much did I receive in september").value)
        assertEquals(rs(300), ask("refunds in october").value)
    }

    @Test fun moneyInLeavesOutOwnTransfers() {
        val l = listOf(
            tx("2026-10-10", 5000, "Self", debit = false, body = "Rs 5000 credited"),
            tx("2026-10-12", 2000, "Asha", debit = false, body = "Rs 2000 credited"),
        )
        val own: (Txn) -> Cat = { if (it.payee == "Self") Cat.TRANSFER else Cat.RECEIVED }
        val a = (Ask.reply("how much did I receive in october", today, l, own) { true } as Reply.Done).a
        assertEquals(rs(2000), a.value)
    }

    @Test fun fullyRefundedPaymentIsNotCounted() {
        // A netted copy of a payment that came back in full has nothing left.
        val l = listOf(tx("2026-10-06", 500, "Myntra").copy(amount = 0), tx("2026-10-07", 300, "Myntra"))
        val a = (Ask.reply("how many times myntra this month", today, l, catOf, Stats(l, catOf, Counting(), today)::counts) as Reply.Done).a
        assertEquals(1L, a.value)
    }

    @Test fun salaryLastMonth() {
        val qq = q("salary last month")
        assertTrue(qq.income)
        assertEquals(setOf(Cat.SALARY), (qq.subject as Subject.Cats).cats)
        assertEquals(rs(100000), ask("salary last month").value)
    }

    @Test fun incomeQuestionsSayCredits() = assertTrue(ask("salary last month").sentence.endsWith("1 credit."))

    // ---------- Wording ----------

    @Test fun rangeText() {
        assertEquals("September", Ask.rangeText(span("2026-09-01", "2026-09-30"), today))
        assertEquals("March 2025", Ask.rangeText(span("2025-03-01", "2025-03-31"), today))
        assertEquals("2025", Ask.rangeText(span("2025-01-01", "2025-12-31"), today))
        assertEquals("1 Jun – today", Ask.rangeText(span("2026-06-01", "2026-10-29"), today))
        assertEquals("yesterday", Ask.rangeText(span("2026-10-28", "2026-10-28"), today))
    }

    @Test fun punctuationAndCaseDoNotMatter() =
        assertEquals(q("how much on swiggy since june"), q("How much on Swiggy since June?"))

    @Test fun examplesUseTopPayee() {
        assertEquals("How much on Swiggy this month", Ask.examples("Swiggy").first())
        assertTrue(Ask.examples(null).all { AskParser.parse(it, today, payees) is Parsed.Ok })
    }
}
