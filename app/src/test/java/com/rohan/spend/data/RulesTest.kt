package com.rohan.spend.data

import org.junit.Assert.assertEquals
import org.junit.Test

class RulesTest {
    private fun txn(id: Long, key: String, payee: String, time: Long) =
        Txn(id, time, 100_00, true, payee, key, "1234", Channel.UPI, null, "", "VM-HDFCBK-S")

    @Test fun namesFromNewestPaymentAndCounts() {
        val txns = listOf(
            txn(1, "swiggy@icici", "Swiggy Ltd", 1),
            txn(2, "swiggy@icici", "Swiggy", 5),
            txn(3, "zepto@ybl", "Zepto", 3),
        )
        val l = ruleInfos(mapOf("swiggy@icici" to Cat.FOOD_DELIVERY, "zepto@ybl" to Cat.GROCERIES), txns)
        assertEquals(listOf("Swiggy", "Zepto"), l.map { it.name })
        assertEquals(listOf(2, 1), l.map { it.count })
        assertEquals(Cat.FOOD_DELIVERY, l[0].cat)
    }

    @Test fun fallsBackToKey() {
        val l = ruleInfos(mapOf("old@upi" to Cat.SHOPPING), emptyList())
        assertEquals("old@upi", l.single().name)
        assertEquals(0, l.single().count)
    }
}
