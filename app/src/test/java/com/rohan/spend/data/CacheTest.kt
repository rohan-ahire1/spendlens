package com.rohan.spend.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class CacheTest {
    private fun txn(id: Long, time: Long, account: String? = "1234", balance: Long? = null, body: String = "Sent Rs.10") =
        Txn(id, time, 10_00, true, "Zepto", "zepto", account, Channel.UPI, balance, body, "VM-HDFCBK")

    private fun bytes(s: Snapshot) = ByteArrayOutputStream().also { TxnCache.write(it, s) }.toByteArray()

    @Test fun roundTrip() {
        val s = Snapshot(
            42, 2000,
            listOf(
                txn(2, 2000, balance = 1_20_966_50, body = "Sent ₹250 to चाय\nwala\tRef 1"),
                txn(1, 1000, account = null),
            ),
        )
        assertEquals(s, TxnCache.read(ByteArrayInputStream(bytes(s))))
        assertEquals(Snapshot(0, 0, emptyList()), TxnCache.read(ByteArrayInputStream(bytes(Snapshot(0, 0, emptyList())))))
    }

    @Test fun rejectsDamagedOrOld() {
        val b = bytes(Snapshot(5, 1000, listOf(txn(1, 1000))))
        assertNull(TxnCache.read(ByteArrayInputStream(b.copyOf(b.size - 3))))
        assertNull(TxnCache.read(ByteArrayInputStream(b + 0)))
        assertNull(TxnCache.read(ByteArrayInputStream(ByteArray(0))))
        val old = b.copyOf().also { it[7] = (TxnCache.VERSION + 1).toByte() }
        assertNull(TxnCache.read(ByteArrayInputStream(old)))
    }

    @Test fun readsAVersionOneCache() {
        // Version 1 had no newest-SMS date after the highest _ID.
        val b = bytes(Snapshot(5, 7777, listOf(txn(2, 2000), txn(1, 1000))))
        val v1 = b.copyOfRange(0, 16) + b.copyOfRange(24, b.size)
        v1[7] = 1
        // The newest payment stands in for the date, so the next sync rereads from there.
        assertEquals(Snapshot(5, 2000, listOf(txn(2, 2000), txn(1, 1000))), TxnCache.read(ByteArrayInputStream(v1)))
        val empty = bytes(Snapshot(0, 0, emptyList())).let { it.copyOfRange(0, 16) + it.copyOfRange(24, it.size) }.also { it[7] = 1 }
        assertEquals(Snapshot(0, 0, emptyList()), TxnCache.read(ByteArrayInputStream(empty)))
    }

    @Test fun mergeDropsDeletedAndKeepsNewestFirst() {
        val cached = listOf(txn(3, 3000), txn(2, 2000), txn(1, 1000))
        val fresh = listOf(txn(5, 5000), txn(3, 3000))
        val out = TxnCache.merge(cached, alive = mapOf(1L to 1000L, 3L to 3000L, 5L to 5000L), fresh = fresh)
        assertEquals(listOf(5L, 3L, 1L), out.map { it.id })
    }

    @Test fun reusedIdReplacesTheOldPayment() {
        // SMS 3 was deleted and the next message got _ID 3 too.
        val cached = listOf(txn(3, 3000), txn(1, 1000))
        val fresh = listOf(txn(3, 9000).copy(amount = 99_00))
        val out = TxnCache.merge(cached, alive = mapOf(1L to 1000L, 3L to 9000L), fresh = fresh)
        assertEquals(listOf(3L to 99_00L, 1L to 10_00L), out.map { it.id to it.amount })
    }

    @Test fun reusedIdByANonPaymentDropsTheOldPayment() {
        val cached = listOf(txn(3, 3000), txn(1, 1000))
        val out = TxnCache.merge(cached, alive = mapOf(1L to 1000L, 3L to 9000L), fresh = emptyList())
        assertEquals(listOf(1L), out.map { it.id })
    }

    @Test fun rereadMessagesAreNotDoubled() {
        val cached = listOf(txn(2, 2000))
        val out = TxnCache.merge(cached, alive = mapOf(2L to 2000L), fresh = listOf(txn(2, 2000), txn(2, 2000)))
        assertEquals(listOf(2L), out.map { it.id })
    }
}
