package com.rohan.spend.ui

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rohan.spend.bg.Alarms
import com.rohan.spend.bg.Widget
import com.rohan.spend.bg.monthLeft
import com.rohan.spend.data.Cat
import com.rohan.spend.data.Categorizer
import com.rohan.spend.data.Counting
import com.rohan.spend.data.Prefs
import com.rohan.spend.data.Refunds
import com.rohan.spend.data.Transfers
import com.rohan.spend.data.SmsSource
import com.rohan.spend.data.Split
import com.rohan.spend.data.Splits
import com.rohan.spend.data.TxnCache
import com.rohan.spend.data.sync
import com.rohan.spend.data.Stats
import com.rohan.spend.data.Txn
import com.rohan.spend.data.date
import com.rohan.spend.data.groupIndian
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.LocalDate
import java.time.ZonedDateTime

enum class Stage { NEEDS_PERMISSION, SCANNING, READY }

/** How far a scan has got. Kept out of [AppState] so ticking it doesn't rebuild every screen's numbers. */
data class ScanProgress(val done: Int = 0, val total: Int = 0, val justFound: List<Txn> = emptyList())

data class AppState(
    val stage: Stage = Stage.NEEDS_PERMISSION,
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
    val recapsClosed: Set<String> = emptySet(),
    val rescanning: Boolean = false,
    /** The date every screen treats as today. The VM moves it on at midnight and on resume. */
    val today: LocalDate = LocalDate.now(),
    val splits: Map<Long, Split> = emptyMap(),
    /** Credit id → the split payment it paid back. */
    val paybacks: Map<Long, Long> = emptyMap(),
    val notPaybacks: Set<String> = emptySet(),
    val eveningSummary: Boolean = false,
    val renewalAlerts: Boolean = false,
) {
    // Every screen asks for categories many times over; run the regexes once per transaction.
    private val catMemo = java.util.concurrent.ConcurrentHashMap<Long, Cat>()
    fun catOf(t: Txn): Cat = catMemo.getOrPut(t.id) {
        txnCats[t.id] ?: payeeRules[t.payeeKey] ?: Cat.TRANSFER.takeIf { t.id in transfers } ?: Categorizer.auto(t)
    }
    val visible: List<Txn> by lazy { txns.filter { it.id !in hidden } }
    val stats: Stats by lazy { Stats(visible, ::catOf, Counting(excludeFamily, countCardBills), today, ::net) }
    /** Visible payments with each debit cut to what you actually paid, after refunds and splits. */
    val netted: List<Txn> by lazy { visible.map { t -> if (t.debit) stats.spend(t).let { if (it != t.amount) t.copy(amount = it) else t } else t } }
    fun usual(): Long = if (target > 0) target else stats.usualMonth

    val byId: Map<Long, Txn> by lazy { txns.associateBy { it.id } }
    /** Refund id → the payment it gives money back for. */
    val refundOf: Map<Long, Long> by lazy { Refunds.match(visible) { catOf(it) == Cat.REFUND } }
    /** Payment id → its refunds, oldest first. */
    val refundsOf: Map<Long, List<Txn>> by lazy {
        refundOf.entries.groupBy({ it.value }, { byId.getValue(it.key) }).mapValues { e -> e.value.sortedBy { it.time } }
    }
    /** What a payment counts for in totals. Credits are left as they are. */
    fun net(t: Txn): Long = if (!t.debit) t.amount else Refunds.net(t.amount, splits[t.id]?.mine, refunded(t))
    private fun refunded(t: Txn): Long = refundsOf[t.id]?.sumOf { it.amount } ?: 0
    /** Each side of a move between the user's own accounts → the other side. */
    // Only visible payments pair up, and one the user filed elsewhere frees its other side to be categorised as usual.
    val transfers: Map<Long, Long> by lazy {
        Transfers.find(visible) { t -> Categorizer.auto(t) == Cat.REFUND || (txnCats[t.id] ?: payeeRules[t.payeeKey]).let { it != null && it != Cat.TRANSFER } }
    }

    /** Split payment id → the paybacks confirmed for it, oldest first. */
    val paidBack: Map<Long, List<Txn>> by lazy {
        paybacks.entries.mapNotNull { (c, p) -> byId[c]?.let { p to it } }.groupBy({ it.first }, { it.second })
            .mapValues { e -> e.value.sortedBy { it.time } }
    }
    fun owed(t: Txn): Long = splits[t.id]?.let { Splits.owed(t.amount, it, refunded(t), paidBack[t.id]?.sumOf { c -> c.amount } ?: 0) } ?: 0
    /** Money in that looks like someone paying back their share of [t]. */
    fun paybackHints(t: Txn): List<Txn> {
        val sp = splits[t.id] ?: return emptyList()
        val pool = visible.filter { !it.debit && catOf(it) == Cat.RECEIVED && it.id !in paybacks && "${it.id}:${t.id}" !in notPaybacks }
        return Splits.candidates(t, sp, owed(t), pool)
    }
    /** Split payments someone still owes on, newest first, with what's owed. */
    val owedToYou: List<Pair<Txn, Long>> by lazy {
        splits.keys.mapNotNull { byId[it] }.filter { it.id !in hidden }.map { it to owed(it) }.filter { it.second > 0 }
            .sortedByDescending { it.first.time }
    }
}

/** The user's saved choices laid over [this]. The app and the background loader both build state this way. */
fun AppState.withPrefs(prefs: Prefs) = copy(
    payeeRules = prefs.payeeRules,
    txnCats = prefs.txnCats,
    notes = prefs.notes,
    hidden = prefs.hidden,
    excludeFamily = prefs.excludeFamily,
    countCardBills = prefs.countCardBills,
    autoRefresh = prefs.autoRefresh,
    target = prefs.target,
    budgets = prefs.budgets,
    recapsClosed = prefs.recapsClosed,
    splits = prefs.splits,
    paybacks = prefs.paybacks,
    notPaybacks = prefs.notPaybacks,
    eveningSummary = prefs.eveningSummary,
    renewalAlerts = prefs.renewalAlerts,
)

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = Prefs(app)
    private val _state = MutableStateFlow(AppState().withPrefs(prefs))
    val state: StateFlow<AppState> = _state
    private val _progress = MutableStateFlow(ScanProgress())
    val progress: StateFlow<ScanProgress> = _progress
    private var scanJob: Job? = null
    private val cacheFile = TxnCache.file(app)
    /** Highest SMS _ID already read, or -1 when there's no cache and everything must be read. */
    private var maxSmsId = -1L
    private var lastSmsDate = 0L

    init {
        if (hasPermission()) start()
        // Keeps the home-screen widget on the numbers this screen shows, after scans and after edits.
        viewModelScope.launch(Dispatchers.Default) {
            state.filter { it.stage == Stage.READY && !it.refreshing }.map { it.monthLeft() }.distinctUntilChanged()
                .collectLatest { runCatching { Widget.push(getApplication(), it) } }
        }
        viewModelScope.launch {
            while (true) {
                delay(msToMidnight())
                rollDate()
            }
        }
    }

    fun hasPermission() = ContextCompat.checkSelfPermission(getApplication(), Manifest.permission.READ_SMS) ==
        PackageManager.PERMISSION_GRANTED

    fun onPermission(granted: Boolean) {
        if (granted) start()
    }

    fun onResume() {
        rollDate()
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
                lastSmsDate = c.lastDate
                _state.update { it.copy(stage = Stage.READY, txns = c.txns, foundCount = c.txns.size) }
            }
        }
        if (maxSmsId < 0) scan(firstTime = true) else if (_state.value.autoRefresh) scan(firstTime = false)
    }

    private fun scan(firstTime: Boolean, full: Boolean = false) {
        if (scanJob?.isActive == true) return
        val showScreen = firstTime && _state.value.txns.isEmpty()
        val known = if (full) -1L else maxSmsId
        _progress.value = ScanProgress()
        _state.update { it.copy(stage = if (showScreen) Stage.SCANNING else it.stage, refreshing = !showScreen, rescanning = full) }
        scanJob = viewModelScope.launch {
            val started = System.currentTimeMillis()
            val app = getApplication<Application>()
            val snap = withContext(Dispatchers.IO) {
                synchronized(TxnCache.syncLock) {
                    // Background work may have read newer messages since; start from those.
                    val disk = if (full) null else TxnCache.load(cacheFile)?.takeIf { it.maxId > known || it.lastDate > lastSmsDate }
                    SmsSource.sync(app, disk?.maxId ?: known, disk?.lastDate ?: lastSmsDate, disk?.txns ?: _state.value.txns) { done, total, found ->
                        _progress.value = ScanProgress(done, total, found.reversed())
                    }!!.also { runCatching { TxnCache.save(cacheFile, it) } }
                }
            }
            maxSmsId = snap.maxId
            lastSmsDate = snap.lastDate
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
            // "All of them" means all: one-off picks for this payee give way to the rule.
            s.txns.forEach { if (it.payeeKey == t.payeeKey) one.remove(it.id) }
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
    fun setEveningSummary(v: Boolean) { prefs.eveningSummary = v; _state.update { it.copy(eveningSummary = v) }; Alarms.schedule(getApplication()) }
    fun setRenewalAlerts(v: Boolean) { prefs.renewalAlerts = v; _state.update { it.copy(renewalAlerts = v) }; Alarms.schedule(getApplication()) }
    fun setTarget(paise: Long) { prefs.target = paise; _state.update { it.copy(target = paise) } }

    fun resetEdits() {
        prefs.clear()
        _state.update { it.withPrefs(prefs) }
        Alarms.schedule(getApplication())
    }

    fun exportCsv(uri: Uri) {
        val s = _state.value
        viewModelScope.launch {
            // A full disk, a revoked folder or a provider that won't open all land here.
            val ok = runCatching {
                withContext(Dispatchers.IO) {
                    val out = getApplication<Application>().contentResolver.openOutputStream(uri) ?: error("No file to write to")
                    out.bufferedWriter().use { w ->
                        w.write("date,payee,amount,my_share,direction,category,channel,account,note\n")
                        for (t in s.visible) {
                            val cells = listOf(
                                t.date().toString(), t.payee, "%.2f".format(t.amount / 100.0),
                                "%.2f".format((if (t.debit) s.stats.spend(t) else t.amount) / 100.0),
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

    fun closeRecap(month: java.time.YearMonth) {
        val v = _state.value.recapsClosed + month.toString()
        prefs.recapsClosed = v
        _state.update { it.copy(recapsClosed = v) }
    }

    /** Reads the whole inbox again rather than only what's new. Wired to "Rescan all messages". */
    fun fullRescan() {
        viewModelScope.launch {
            scanJob?.join()
            scan(firstTime = false, full = true)
        }
    }

    /** Moves "today" on when the date has changed, which rebuilds Stats for the new day and month. */
    private fun rollDate() {
        val now = LocalDate.now()
        if (now != _state.value.today) _state.update { it.copy(today = now) }
    }

    // A second past midnight, so LocalDate.now() has surely turned over. Doze can delay it; onResume catches that.
    private fun msToMidnight(): Long {
        val now = ZonedDateTime.now()
        return Duration.between(now, now.toLocalDate().plusDays(1).atStartOfDay(now.zone)).toMillis() + 1000
    }

    /** Null removes the split; its paybacks go with it. */
    fun setSplit(t: Txn, split: Split?) {
        val s = _state.value
        val splits = if (split == null) s.splits - t.id else s.splits + (t.id to split)
        val paybacks = if (split == null) s.paybacks.filterValues { it != t.id } else s.paybacks
        prefs.splits = splits
        prefs.paybacks = paybacks
        _state.update { it.copy(splits = splits, paybacks = paybacks) }
    }

    fun confirmPayback(credit: Txn, payment: Txn) {
        val p = _state.value.paybacks + (credit.id to payment.id)
        prefs.paybacks = p
        _state.update { it.copy(paybacks = p) }
    }

    fun rejectPayback(credit: Txn, payment: Txn) {
        val n = _state.value.notPaybacks + "${credit.id}:${payment.id}"
        prefs.notPaybacks = n
        _state.update { it.copy(notPaybacks = n) }
    }

    fun unlinkPayback(credit: Txn) {
        val s = _state.value
        val p = s.paybacks - credit.id
        val n = s.paybacks[credit.id]?.let { s.notPaybacks + "${credit.id}:$it" } ?: s.notPaybacks
        prefs.paybacks = p
        prefs.notPaybacks = n
        _state.update { it.copy(paybacks = p, notPaybacks = n) }
    }
}
