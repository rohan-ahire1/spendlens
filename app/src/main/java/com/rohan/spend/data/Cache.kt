package com.rohan.spend.data

import android.content.Context
import android.provider.Telephony
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream

/** What we've read so far: the transactions and the highest SMS _ID already looked at. */
data class Snapshot(val maxId: Long, val txns: List<Txn>)

/** Parsed transactions kept on the phone so a cold start doesn't reread 18 months of SMS. */
object TxnCache {
    /** Bump whenever SmsParser or Txn changes, so old caches are thrown away and the inbox is read again. */
    const val VERSION = 1
    private const val MAGIC = 0x5350454E // "SPEN"

    fun write(out: OutputStream, s: Snapshot) {
        val d = DataOutputStream(out.buffered())
        d.writeInt(MAGIC)
        d.writeInt(VERSION)
        d.writeLong(s.maxId)
        d.writeInt(s.txns.size)
        for (t in s.txns) {
            d.writeLong(t.id)
            d.writeLong(t.time)
            d.writeLong(t.amount)
            d.writeBoolean(t.debit)
            d.str(t.payee)
            d.str(t.payeeKey)
            d.str(t.account)
            d.str(t.channel.name)
            d.writeBoolean(t.balance != null)
            d.writeLong(t.balance ?: 0)
            d.str(t.body)
            d.str(t.sender)
        }
        d.flush()
    }

    /** Null when the data is from another version or damaged in any way. */
    fun read(inp: InputStream): Snapshot? = runCatching {
        val d = DataInputStream(inp.buffered())
        if (d.readInt() != MAGIC || d.readInt() != VERSION) return null
        val maxId = d.readLong()
        val n = d.readInt()
        if (n < 0) return null
        val l = ArrayList<Txn>(minOf(n, 100_000))
        repeat(n) {
            l += Txn(
                id = d.readLong(), time = d.readLong(), amount = d.readLong(), debit = d.readBoolean(),
                payee = d.str()!!, payeeKey = d.str()!!, account = d.str(), channel = Channel.valueOf(d.str()!!),
                balance = d.readBoolean().let { has -> d.readLong().takeIf { has } },
                body = d.str()!!, sender = d.str()!!,
            )
        }
        if (d.read() != -1) return null
        Snapshot(maxId, l)
    }.getOrNull()

    fun load(f: File): Snapshot? {
        if (!f.exists()) return null
        return f.inputStream().use(::read) ?: run { f.delete(); null }
    }

    /** Writes beside the old file then swaps, so a crash mid-write never leaves half a cache. */
    fun save(f: File, s: Snapshot) {
        val tmp = File(f.path + ".tmp")
        tmp.outputStream().use { write(it, s) }
        if (!tmp.renameTo(f)) { f.delete(); tmp.renameTo(f) }
    }

    /**
     * Cached transactions whose SMS still exist, plus the newly read ones, newest first.
     * [alive] is every SMS _ID in the scan window, so deleted or aged-out messages drop off.
     */
    fun merge(cached: List<Txn>, alive: Set<Long>, fresh: List<Txn>): List<Txn> {
        val ids = fresh.mapTo(HashSet()) { it.id }
        return (fresh + cached.filter { it.id in alive && it.id !in ids }).sortedByDescending { it.time }
    }

    private fun DataOutputStream.str(s: String?) {
        if (s == null) { writeInt(-1); return }
        val b = s.toByteArray(Charsets.UTF_8)
        writeInt(b.size)
        write(b)
    }

    private fun DataInputStream.str(): String? {
        val n = readInt()
        if (n < 0) return null
        if (n > 1_000_000) error("bad length")
        return ByteArray(n).also(::readFully).toString(Charsets.UTF_8)
    }
}

/** Every SMS _ID in the inbox from the last [monthsBack] months. Cheap: no bodies are read. */
fun SmsSource.ids(context: Context, monthsBack: Long = 18): Set<Long> {
    val since = System.currentTimeMillis() - monthsBack * 31L * 24 * 3600 * 1000
    val out = HashSet<Long>()
    context.contentResolver.query(
        Telephony.Sms.Inbox.CONTENT_URI, arrayOf(Telephony.Sms._ID),
        "${Telephony.Sms.DATE} > ?", arrayOf(since.toString()), null,
    )?.use { c -> while (c.moveToNext()) out += c.getLong(0) }
    return out
}
