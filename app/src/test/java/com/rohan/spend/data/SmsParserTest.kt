package com.rohan.spend.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SmsParserTest {
    private fun parse(body: String, sender: String = "VM-HDFCBK-S") = SmsParser.parse(1, sender, body, 0)

    @Test fun upiDebit() {
        val t = parse("Sent Rs.250.00\nFrom HDFC Bank A/C *1234\nTo ZEPTO MARKETPLACE PRIVATE LIMITED\nOn 12/10/26\nRef 123456789012\nNot You?")!!
        assertTrue(t.debit)
        assertEquals(250_00L, t.amount)
        assertEquals("Zepto", t.payee)
        assertEquals("1234", t.account)
        assertEquals(Cat.GROCERIES, Categorizer.auto(t))
    }

    @Test fun vpaDebitUsesHandle() {
        val t = parse("Rs.1,500.00 debited from a/c **1234 on 10-10-26 to VPA rahul.sharma99@okhdfcbank (UPI Ref No 1234). Not you?")!!
        assertTrue(t.debit)
        assertEquals(1_500_00L, t.amount)
        assertEquals("Rahul Sharma", t.payee)
        assertEquals(Channel.UPI, t.channel)
        assertEquals(Cat.PEOPLE, Categorizer.auto(t))
    }

    @Test fun cardSpend() {
        val t = parse("Spent Rs.649 On HDFC Bank Card 5678 At SWIGGY On 2026-10-11:20:15:01.Not You? To Block+Reissue Call")!!
        assertTrue(t.debit)
        assertEquals(Channel.CARD, t.channel)
        assertEquals("5678", t.account)
        assertEquals(Cat.FOOD_DELIVERY, Categorizer.auto(t))
    }

    @Test fun fuelIsItsOwnCategory() {
        val t = parse("Spent Rs.2000 On HDFC Bank Card 5678 At HPCL HARI OM PETROLEUM On 2026-10-11:09:15:01.Not You? To Block+Reissue Call")!!
        assertEquals(Cat.FUEL, Categorizer.auto(t))
        val ride = parse("Spent Rs.240 On HDFC Bank Card 5678 At UBER INDIA On 2026-10-11:09:15:01.Not You? To Block+Reissue Call")!!
        assertEquals(Cat.COMMUTE, Categorizer.auto(ride))
    }

    @Test fun salaryCredit() {
        val t = parse("Update! INR 1,20,966.00 deposited in HDFC Bank A/c XX1234 on 01-OCT-26 for NEFT Cr-CITI0000001-ACME SOFTWARE PVT LTD-ROHAN-CITIN26.Avl bal INR 2,10,000.50")!!
        assertFalse(t.debit)
        assertEquals(1_20_966_00L, t.amount)
        assertEquals("Acme Software", t.payee)
        assertEquals(2_10_000_50L, t.balance)
        assertEquals(Cat.SALARY, Categorizer.auto(t))
    }

    @Test fun atm() {
        val t = parse("Rs.5000.00 withdrawn from A/c XX1234 at ATM on 10-10-26. Avl Bal Rs.10,000")!!
        assertEquals(Channel.ATM, t.channel)
        assertEquals(Cat.CASH, Categorizer.auto(t))
    }

    @Test fun skipsNoise() {
        assertNull(parse("123456 is your OTP for txn of Rs.500 at AMAZON on card 5678"))
        assertNull(parse("Payment of Rs.15,000 received towards your HDFC Bank Credit Card ending 5678"))
        assertNull(parse("Your A/c XX1234 will be debited Rs.299 on 15-10-26 for mandate"))
        assertNull(parse("Sent Rs.250.00 To Zepto from a/c 1234", sender = "JD-FRIEND"))
    }

    @Test fun indianGrouping() {
        assertEquals("₹1,20,966", inr(1_20_966_00))
        assertEquals("−₹42,027", inr(-42_027_00))
        assertEquals("₹1.2L", inrShort(1_20_966_00))
        assertEquals("₹42k", inrShort(42_027_00))
    }
}
