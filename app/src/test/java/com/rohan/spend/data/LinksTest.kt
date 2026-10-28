package com.rohan.spend.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LinksTest {
    private val day = 24L * 3600 * 1000
    private var next = 1L

    private fun t(amount: Long, debit: Boolean, payee: String, at: Long, account: String? = "1234", key: String = payee.lowercase(), channel: Channel = Channel.UPI) =
        Txn(next++, at, amount, debit, payee, key, account, channel, null, "", "VM-HDFCBK")

    @Test fun refundMatchesEarlierPaymentToSamePayee() {
        val zepto = t(500_00, true, "Zepto", 0)
        val swiggy = t(500_00, true, "Swiggy", 1 * day)
        val back = t(200_00, false, "Zepto Marketplace", 5 * day, key = "zepto marketplace")
        val m = Refunds.match(listOf(zepto, swiggy, back)) { it === back }
        assertEquals(mapOf(back.id to zepto.id), m)
    }

    @Test fun refundSkipsTooOldTooSmallOrLater() {
        val old = t(500_00, true, "Amazon", 0)
        val small = t(100_00, true, "Amazon", 70 * day)
        val later = t(900_00, true, "Amazon", 80 * day)
        val back = t(300_00, false, "Amazon", 75 * day)
        assertTrue(Refunds.match(listOf(old, small, later, back)) { it === back }.isEmpty())
    }

    @Test fun refundsDontOverfillOnePayment() {
        val a = t(500_00, true, "Myntra", 0)
        val b = t(500_00, true, "Myntra", 1 * day)
        val r1 = t(400_00, false, "Myntra", 3 * day)
        val r2 = t(400_00, false, "Myntra", 4 * day)
        val m = Refunds.match(listOf(a, b, r1, r2)) { !it.debit }
        assertEquals(setOf(a.id, b.id), m.values.toSet())
    }

    @Test fun namelessRefundNeedsOneSameAmountPaymentOnSameAccount() {
        val pay = t(649_00, true, "Swiggy", 0, account = "5678", channel = Channel.CARD)
        val other = t(649_00, true, "Zomato", 0, account = "1234")
        val back = t(649_00, false, "Money in", 2 * day, account = "5678", channel = Channel.CARD)
        assertEquals(mapOf(back.id to pay.id), Refunds.match(listOf(pay, other, back)) { it === back })
        val twin = t(649_00, true, "Uber", 1 * day, account = "5678", channel = Channel.CARD)
        assertTrue(Refunds.match(listOf(pay, twin, back)) { it === back }.isEmpty())
    }

    @Test fun transferPairsDebitWithCreditOnAnotherAccount() {
        val hour = 3600_000L
        val out = t(20_000_00, true, "Rohan Ahire", 0, account = "1234")
        val inn = t(20_000_00, false, "Rohan Ahire", hour, account = "9876")
        val same = t(20_000_00, false, "Friend", 0, account = "1234")
        val late = t(20_000_00, false, "Friend", 3 * hour, account = "5555")
        val m = Transfers.find(listOf(out, inn, same, late))
        assertEquals(mapOf(out.id to inn.id, inn.id to out.id), m)
    }

    @Test fun transferIgnoresCardsCashAndSkippedAndPairsOnce() {
        val card = t(5_000_00, true, "Amazon", 0, account = "5678", channel = Channel.CARD)
        val atm = t(5_000_00, true, "Cash withdrawal", 0, account = "1234", channel = Channel.ATM)
        val refund = t(5_000_00, false, "Amazon", 60_000, account = "9876")
        assertTrue(Transfers.find(listOf(card, atm, refund)) { it === refund }.isEmpty())
        assertTrue(Transfers.find(listOf(card, atm, refund)).isEmpty())

        val a = t(1_000_00, true, "Me", 0, account = "1111")
        val b = t(1_000_00, true, "Me", 1000, account = "1111")
        val c = t(1_000_00, false, "Me", 2000, account = "2222")
        assertEquals(2, Transfers.find(listOf(a, b, c)).size)
    }

    @Test fun netScalesShareByRefund() {
        assertEquals(1000_00L, Refunds.net(1000_00, null, 0))
        assertEquals(600_00L, Refunds.net(1000_00, null, 400_00))
        assertEquals(0L, Refunds.net(1000_00, null, 1500_00))
        assertEquals(150_00L, Refunds.net(1000_00, 250_00, 400_00))
    }
}
