package com.rohan.spend.ui

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rohan.spend.data.Cat
import com.rohan.spend.data.Categorizer
import com.rohan.spend.data.Counting
import com.rohan.spend.data.Prefs
import com.rohan.spend.data.SmsSource
import com.rohan.spend.data.Snapshot
import com.rohan.spend.data.TxnCache
import com.rohan.spend.data.ids
import com.rohan.spend.data.Stats
import com.rohan.spend.data.Txn
import com.rohan.spend.data.date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class Stage { NEEDS_PERMISSION, SCANNING, READY }

data class AppState(
    val stage: Stage = Stage.NEEDS_PERMISSION,
    val done: Int = 0,
    val total: Int = 0,
    val justFound: List<Txn> = emptyList(),
    val foundCount: Int = 0,
    val txns: List<Txn> = emptyList(),
    val payeeRules: Map<String, Cat> = emptyMap(),
    val txnCats: Map<Long, Cat> = emptyMap(),
    val notes: Map<Long, String> = emptyMap(),
    val hidden: Set<Long> = emptySet(),
    val excludeFamily: Boolean = true,
    val countCardBills: Boolean = false,
    val autoRefresh: Boolean = true,
    val target: Long = 0,
    val refreshing: Boolean = false,
    val rescanning: Boolean = false,
) {
    // Every screen asks for categories many times over; run the regexes once per transaction.
    private val catMemo = java.util.concurrent.ConcurrentHashMap<Long, Cat>()
    fun catOf(t: Txn): Cat = catMemo.getOrPut(t.id) { txnCats[t.id] ?: payeeRules[t.payeeKey] ?: Categorizer.auto(t) }
    val visible: List<Txn> by lazy { txns.filter { it.id !in hidden } }
    val stats: Stats by lazy { Stats(visible, ::catOf, Counting(excludeFamily, countCardBills)) }
    fun usual(): Long = if (target > 0) target else stats.usualMonth
}

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = Prefs(app)
    private val _state = MutableStateFlow(fromPrefs(AppState()))
    val state: StateFlow<AppState> = _state
    private var scanJob: Job? = null
    private val cacheFile = File(app.filesDir, "txns.bin")
    /** Highest SMS _ID already read, or -1 when there's no cache and everything must be read. */
    private var maxSmsId = -1L

    init {
        if (hasPermission()) start()
    }

    private fun fromPrefs(s: AppState) = s.copy(
        payeeRules = prefs.payeeRules,
        txnCats = prefs.txnCats,
        notes = prefs.notes,
        hidden = prefs.hidden,
        excludeFamily = prefs.excludeFamily,
        countCardBills = prefs.countCardBills,
        autoRefresh = prefs.autoRefresh,
        target = prefs.target,
    )

    fun hasPermission() = ContextCompat.checkSelfPermission(getApplication(), Manifest.permission.READ_SMS) ==
        PackageManager.PERMISSION_GRANTED

    fun onPermission(granted: Boolean) {
        if (granted) start()
    }

    fun onResume() {
        val s = _state.value
        if (s.stage == Stage.NEEDS_PERMISSION && hasPermission()) start()
        else if (s.stage == Stage.READY && s.autoRefresh) scan(firstTime = false)
    }

    fun refresh() = scan(firstTime = false)

    /** Shows the saved transactions straight away and only reads what's new; the scan screen is for the very first run. */
    private fun start() {
        if (maxSmsId < 0) {
            // A small file read on the main thread, so the first frame is the real app, not a flash of the scan screen.
            TxnCache.load(cacheFile)?.let { c ->
                maxSmsId = c.maxId
                _state.update { it.copy(stage = Stage.READY, txns = c.txns, foundCount = c.txns.size) }
            }
        }
        if (maxSmsId < 0) scan(firstTime = true) else if (_state.value.autoRefresh) scan(firstTime = false)
    }

    private fun scan(firstTime: Boolean, full: Boolean = false) {
        if (scanJob?.isActive == true) return
        val showScreen = firstTime && _state.value.txns.isEmpty()
        val known = if (full) -1L else maxSmsId
        _state.update { it.copy(stage = if (showScreen) Stage.SCANNING else it.stage, refreshing = !showScreen, rescanning = full) }
        scanJob = viewModelScope.launch {
            val started = System.currentTimeMillis()
            val app = getApplication<Application>()
            val snap = withContext(Dispatchers.IO) {
                // Taken before reading, so a message that lands mid-scan is picked up next time rather than skipped.
                val alive = SmsSource.ids(app)
                val top = alive.maxOrNull() ?: 0L
                // IDs going backwards means the inbox was rebuilt (restore, new phone), so read it all again.
                val whole = known < 0 || top < known
                val read = SmsSource.read(app, afterId = if (whole) 0 else known) { done, total, found ->
                    _state.update { it.copy(done = done, total = total, justFound = found.reversed()) }
                }
                val txns = if (whole) read else TxnCache.merge(_state.value.txns, alive, read)
                Snapshot(if (whole) top else maxOf(top, known), txns).also { runCatching { TxnCache.save(cacheFile, it) } }
            }
            maxSmsId = snap.maxId
            _state.update { it.copy(foundCount = snap.txns.size) }
            // Let the scan animation land rather than flashing past.
            if (showScreen) delay((1800 - (System.currentTimeMillis() - started)).coerceAtLeast(0))
            _state.update { it.copy(stage = Stage.READY, txns = snap.txns, refreshing = false, rescanning = false) }
        }
    }

    fun setCategory(t: Txn, cat: Cat, always: Boolean) {
        val s = _state.value
        val rules = s.payeeRules.toMutableMap()
        val one = s.txnCats.toMutableMap()
        if (always) {
            rules[t.payeeKey] = cat
            one.remove(t.id)
        } else {
            one[t.id] = cat
        }
        prefs.payeeRules = rules
        prefs.txnCats = one
        _state.update { it.copy(payeeRules = rules, txnCats = one) }
    }

    fun setAlways(t: Txn, always: Boolean) {
        val s = _state.value
        val cat = s.catOf(t)
        val rules = s.payeeRules.toMutableMap()
        if (always) rules[t.payeeKey] = cat else rules.remove(t.payeeKey)
        prefs.payeeRules = rules
        _state.update { it.copy(payeeRules = rules) }
    }

    fun removeRule(key: String) {
        val rules = _state.value.payeeRules - key
        prefs.payeeRules = rules
        _state.update { it.copy(payeeRules = rules) }
    }

    fun setNote(t: Txn, note: String) {
        val notes = _state.value.notes.toMutableMap()
        if (note.isBlank()) notes.remove(t.id) else notes[t.id] = note.trim()
        prefs.notes = notes
        _state.update { it.copy(notes = notes) }
    }

    fun toggleHidden(t: Txn) {
        val h = _state.value.hidden.let { if (t.id in it) it - t.id else it + t.id }
        prefs.hidden = h
        _state.update { it.copy(hidden = h) }
    }

    fun setExcludeFamily(v: Boolean) { prefs.excludeFamily = v; _state.update { it.copy(excludeFamily = v) } }
    fun setCountCardBills(v: Boolean) { prefs.countCardBills = v; _state.update { it.copy(countCardBills = v) } }
    fun setAutoRefresh(v: Boolean) { prefs.autoRefresh = v; _state.update { it.copy(autoRefresh = v) } }
    fun setTarget(paise: Long) { prefs.target = paise; _state.update { it.copy(target = paise) } }

    fun resetEdits() {
        prefs.clear()
        _state.update { fromPrefs(it) }
    }

    fun exportCsv(uri: Uri) {
        val s = _state.value
        viewModelScope.launch(Dispatchers.IO) {
            getApplication<Application>().contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { w ->
                w.write("date,payee,amount,direction,category,channel,account,note\n")
                for (t in s.visible) {
                    val cells = listOf(
                        t.date().toString(), t.payee, "%.2f".format(t.amount / 100.0),
                        if (t.debit) "out" else "in", s.catOf(t).label, t.channel.label,
                        t.account ?: "", s.notes[t.id] ?: "",
                    )
                    w.write(cells.joinToString(",") { "\"" + it.replace("\"", "\"\"") + "\"" } + "\n")
                }
            }
        }
    }

    /** Reads the whole inbox again rather than only what's new. Wired to "Rescan all messages". */
    fun fullRescan() {
        viewModelScope.launch {
            scanJob?.join()
            scan(firstTime = false, full = true)
        }
    }
}
