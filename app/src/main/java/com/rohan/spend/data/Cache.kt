package com.rohan.spend.data

import android.content.Context
import android.provider.Telephony
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream

/**
 * What we've read so far: the transactions, the highest SMS _ID already looked at and the newest SMS date.
 * The date matters because the inbox can hand a deleted message's _ID to the next one that arrives.
 */
data class Snapshot(val maxId: Long, val lastDate: Long, val txns: List<Txn>)

/** Parsed transactions kept on the phone so a cold start doesn't reread 18 months of SMS. */
object TxnCache {
    /** Bump whenever SmsParser or Txn changes, so old caches are thrown away and the inbox is read again. */
    const val VERSION = 2
    private const val MAGIC = 0x5350454E // "SPEN"

    fun write(out: OutputStream, s: Snapshot) {
        val d = DataOutputStream(out.buffered())
        d.writeInt(MAGIC)
        d.writeInt(VERSION)
        d.writeLong(s.maxId)
        d.writeLong(s.lastDate)
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
        val lastDate = d.readLong()
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
        Snapshot(maxId, lastDate, l)
    }.getOrNull()

    /** The one cache file, shared by the app and the widget and alarms. */
    fun file(context: Context) = File(context.filesDir, "txns.bin")

    fun load(f: File): Snapshot? = synchronized(this) {
        if (!f.exists()) return null
        return f.inputStream().use(::read) ?: run { f.delete(); null }
    }

    /** Writes beside the old file then swaps, so a crash mid-write never leaves half a cache. */
    fun save(f: File, s: Snapshot) = synchronized(this) {
        val tmp = File(f.path + ".tmp")
        tmp.outputStream().use { write(it, s) }
        if (!tmp.renameTo(f)) { f.delete(); tmp.renameTo(f) }
    }

    /** Messages this close before the newest one seen are read again, in case the clock or the inbox shuffled. */
    const val SLACK = 10 * 60 * 1000L

    /**
     * Cached transactions whose SMS still exist, plus the newly read ones, newest first.
     * [alive] is every SMS _ID in the scan window with its date, so deleted or aged-out messages drop off,
     * and so does a cached one whose _ID now belongs to a different, later message.
     */
    fun merge(cached: List<Txn>, alive: Map<Long, Long>, fresh: List<Txn>): List<Txn> {
        val f = fresh.distinctBy { it.id }
        val ids = f.mapTo(HashSet()) { it.id }
        return (f + cached.filter { alive[it.id] == it.time && it.id !in ids }).sortedByDescending { it.time }
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

/**
 * Reads SMS newer than [known] or dated from [lastDate] on into [cached]. When there's no cache ([known] < 0) or the inbox was
 * rebuilt (IDs went backwards) the whole inbox has to be read; with [wholeOk] false that returns null instead.
 */
fun SmsSource.sync(
    context: Context, known: Long, lastDate: Long, cached: List<Txn>, wholeOk: Boolean = true,
    onProgress: (done: Int, total: Int, found: List<Txn>) -> Unit = { _, _, _ -> },
): Snapshot? {
    // Taken before reading, so a message that lands mid-scan is picked up next time rather than skipped.
    val alive = ids(context)
    val top = alive.keys.maxOrNull() ?: 0L
    val topDate = alive.values.maxOrNull() ?: 0L
    val whole = known < 0 || top < known
    if (whole && !wholeOk) return null
    // A reused _ID isn't above [known], but its message is newer than anything read before.
    val read = read(context, afterId = if (whole) 0 else known, fromDate = if (whole) Long.MAX_VALUE else lastDate - TxnCache.SLACK, onProgress = onProgress)
    val txns = if (whole) read.distinctBy { it.id } else TxnCache.merge(cached, alive, read)
    return Snapshot(if (whole) top else maxOf(top, known), topDate, txns)
}

/** Every SMS _ID in the inbox from the last [monthsBack] months, with its date. Cheap: no bodies are read. */
fun SmsSource.ids(context: Context, monthsBack: Long = 18): Map<Long, Long> {
    val since = System.currentTimeMillis() - monthsBack * 31L * 24 * 3600 * 1000
    val out = HashMap<Long, Long>()
    context.contentResolver.query(
        Telephony.Sms.Inbox.CONTENT_URI, arrayOf(Telephony.Sms._ID, Telephony.Sms.DATE),
        "${Telephony.Sms.DATE} > ?", arrayOf(since.toString()), null,
    )?.use { c -> while (c.moveToNext()) out[c.getLong(0)] = c.getLong(1) }
    return out
}
