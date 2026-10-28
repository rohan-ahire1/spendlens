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
import com.rohan.spend.data.Stats
import com.rohan.spend.data.Txn
import com.rohan.spend.data.date
import com.rohan.spend.data.groupIndian
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    val budgets: Map<Cat, Long> = emptyMap(),
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

    init {
        if (hasPermission()) scan(firstTime = true)
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
        budgets = prefs.budgets,
    )

    fun hasPermission() = ContextCompat.checkSelfPermission(getApplication(), Manifest.permission.READ_SMS) ==
        PackageManager.PERMISSION_GRANTED

    fun onPermission(granted: Boolean) {
        if (granted) scan(firstTime = true)
    }

    fun onResume() {
        val s = _state.value
        if (s.stage == Stage.NEEDS_PERMISSION && hasPermission()) scan(firstTime = true)
        else if (s.stage == Stage.READY && s.autoRefresh) scan(firstTime = false)
    }

    fun refresh() = scan(firstTime = false)

    private fun scan(firstTime: Boolean) {
        if (scanJob?.isActive == true) return
        val showScreen = firstTime && _state.value.txns.isEmpty()
        _state.update { it.copy(stage = if (showScreen) Stage.SCANNING else it.stage, refreshing = !showScreen) }
        scanJob = viewModelScope.launch {
            val started = System.currentTimeMillis()
            val txns = withContext(Dispatchers.IO) {
                SmsSource.read(getApplication()) { done, total, found ->
                    _state.update { it.copy(done = done, total = total, justFound = found.reversed()) }
                }
            }
            _state.update { it.copy(foundCount = txns.size) }
            // Let the scan animation land rather than flashing past.
            if (showScreen) delay((1800 - (System.currentTimeMillis() - started)).coerceAtLeast(0))
            _state.update { it.copy(stage = Stage.READY, txns = txns, refreshing = false) }
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
        viewModelScope.launch {
            // A full disk, a revoked folder or a provider that won't open all land here.
            val ok = runCatching {
                withContext(Dispatchers.IO) {
                    val out = getApplication<Application>().contentResolver.openOutputStream(uri) ?: error("No file to write to")
                    out.bufferedWriter().use { w ->
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
            }.isSuccess
            val n = s.visible.size
            say(if (!ok) "Couldn't save the file. Try another folder." else if (n == 1) "Saved 1 payment" else "Saved ${groupIndian(n.toLong())} payments")
        }
    }

    private val _snack = MutableStateFlow<Snack?>(null)
    val snack: StateFlow<Snack?> = _snack
    private var undoFn: (() -> Unit)? = null
    private var snackJob: Job? = null
    private var snackId = 0L

    /** Shows [text] at the bottom for a few seconds, with Undo when [undo] is given. Main thread only. */
    fun say(text: String, undo: (() -> Unit)? = null) {
        val s = Snack(++snackId, text, undo != null)
        undoFn = undo
        _snack.value = s
        snackJob?.cancel()
        snackJob = viewModelScope.launch {
            delay(if (undo != null) 5000 else 3500)
            if (_snack.value?.id == s.id) { _snack.value = null; undoFn = null }
        }
    }

    fun undo() {
        val f = undoFn ?: return
        undoFn = null
        snackJob?.cancel()
        _snack.value = null
        f()
    }

    /** Runs [change], then offers Undo that puts back exactly the edits it made, in prefs too. */
    fun undoable(text: String, change: () -> Unit) {
        val before = Edits.of(_state.value)
        change()
        val after = Edits.of(_state.value)
        if (before == after) return
        say(text) { putEdits(Edits.of(_state.value).revert(before, after)) }
    }

    private fun putEdits(e: Edits) {
        prefs.payeeRules = e.rules
        prefs.txnCats = e.txnCats
        prefs.hidden = e.hidden
        _state.update { it.copy(payeeRules = e.rules, txnCats = e.txnCats, hidden = e.hidden) }
    }

    /** 0 or less clears the budget. */
    fun setBudget(cat: Cat, paise: Long) {
        val b = if (paise > 0) _state.value.budgets + (cat to paise) else _state.value.budgets - cat
        prefs.budgets = b
        _state.update { it.copy(budgets = b) }
    }
}
