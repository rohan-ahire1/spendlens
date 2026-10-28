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
            42,
            listOf(
                txn(2, 2000, balance = 1_20_966_50, body = "Sent ₹250 to चाय\nwala\tRef 1"),
                txn(1, 1000, account = null),
            ),
        )
        assertEquals(s, TxnCache.read(ByteArrayInputStream(bytes(s))))
        assertEquals(Snapshot(0, emptyList()), TxnCache.read(ByteArrayInputStream(bytes(Snapshot(0, emptyList())))))
    }

    @Test fun rejectsDamagedOrOld() {
        val b = bytes(Snapshot(5, listOf(txn(1, 1000))))
        assertNull(TxnCache.read(ByteArrayInputStream(b.copyOf(b.size - 3))))
        assertNull(TxnCache.read(ByteArrayInputStream(b + 0)))
        assertNull(TxnCache.read(ByteArrayInputStream(ByteArray(0))))
        val old = b.copyOf().also { it[7] = (TxnCache.VERSION + 1).toByte() }
        assertNull(TxnCache.read(ByteArrayInputStream(old)))
    }

    @Test fun mergeDropsDeletedAndKeepsNewestFirst() {
        val cached = listOf(txn(3, 3000), txn(2, 2000), txn(1, 1000))
        val fresh = listOf(txn(5, 5000), txn(3, 3000))
        val out = TxnCache.merge(cached, alive = setOf(1, 3, 5), fresh = fresh)
        assertEquals(listOf(5L, 3L, 1L), out.map { it.id })
    }
}
