package com.rohan.spend.data

import android.content.Context
import android.provider.Telephony

/** Small key-value store for the user's own edits. Everything stays on the phone. */
class Prefs(context: Context) {
    private val p = context.getSharedPreferences("spend", Context.MODE_PRIVATE)

    var payeeRules: Map<String, Cat>
        get() = readMap("payeeRules").mapNotNull { (k, v) -> runCatching { k to Cat.valueOf(v) }.getOrNull() }.toMap()
        set(v) = writeMap("payeeRules", v.mapValues { it.value.name })

    var txnCats: Map<Long, Cat>
        get() = readMap("txnCats").mapNotNull { (k, v) -> runCatching { k.toLong() to Cat.valueOf(v) }.getOrNull() }.toMap()
        set(v) = writeMap("txnCats", v.mapKeys { it.key.toString() }.mapValues { it.value.name })

    var notes: Map<Long, String>
        get() = readMap("notes").mapNotNull { (k, v) -> k.toLongOrNull()?.let { it to v } }.toMap()
        set(v) = writeMap("notes", v.mapKeys { it.key.toString() })

    var hidden: Set<Long>
        get() = p.getStringSet("hidden", emptySet())!!.mapNotNull { it.toLongOrNull() }.toSet()
        set(v) = p.edit().putStringSet("hidden", v.map { it.toString() }.toSet()).apply()

    var excludeFamily: Boolean
        get() = p.getBoolean("excludeFamily", true)
        set(v) = p.edit().putBoolean("excludeFamily", v).apply()

    var countCardBills: Boolean
        get() = p.getBoolean("countCardBills", false)
        set(v) = p.edit().putBoolean("countCardBills", v).apply()

    var autoRefresh: Boolean
        get() = p.getBoolean("autoRefresh", true)
        set(v) = p.edit().putBoolean("autoRefresh", v).apply()

    /** 0 means "work it out from my history". */
    var target: Long
        get() = p.getLong("target", 0)
        set(v) = p.edit().putLong("target", v).apply()

    fun clear() = p.edit().clear().apply()

    // Tab and newline never appear in keys we write, so a flat string is enough.
    private fun readMap(key: String): Map<String, String> =
        p.getString(key, "")!!.split('\n').filter { '\t' in it }.associate { it.substringBefore('\t') to it.substringAfter('\t') }

    private fun writeMap(key: String, m: Map<String, String>) =
        p.edit().putString(key, m.entries.joinToString("\n") { "${it.key.replace('\t', ' ')}\t${it.value.replace('\n', ' ')}" }).apply()

    /** Monthly budget per category, in paise. A category without one isn't in the map. */
    var budgets: Map<Cat, Long>
        get() = readMap("budgets").mapNotNull { (k, v) -> runCatching { Cat.valueOf(k) to v.toLong() }.getOrNull() }.toMap()
        set(v) = writeMap("budgets", v.mapKeys { it.key.name }.mapValues { it.value.toString() })
}

object SmsSource {
    /** Reads the inbox newest first and reports progress as it goes. */
    fun read(context: Context, monthsBack: Long = 18, onProgress: (done: Int, total: Int, found: List<Txn>) -> Unit): List<Txn> {
        val since = System.currentTimeMillis() - monthsBack * 31L * 24 * 3600 * 1000
        val out = ArrayList<Txn>()
        context.contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI,
            arrayOf(Telephony.Sms._ID, Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE),
            "${Telephony.Sms.DATE} > ?",
            arrayOf(since.toString()),
            "${Telephony.Sms.DATE} DESC",
        )?.use { c ->
            val total = c.count
            val iId = c.getColumnIndexOrThrow(Telephony.Sms._ID)
            val iAddr = c.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
            val iBody = c.getColumnIndexOrThrow(Telephony.Sms.BODY)
            val iDate = c.getColumnIndexOrThrow(Telephony.Sms.DATE)
            var i = 0
            while (c.moveToNext()) {
                val body = c.getString(iBody) ?: ""
                val addr = c.getString(iAddr) ?: ""
                SmsParser.parse(c.getLong(iId), addr, body, c.getLong(iDate))?.let(out::add)
                i++
                if (i % 40 == 0 || i == total) onProgress(i, total, out.takeLast(3))
            }
            if (total == 0) onProgress(0, 0, emptyList())
        }
        return out
    }
}
