package com.rohan.spend.bg

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.rohan.spend.data.Prefs
import com.rohan.spend.data.SmsSource
import com.rohan.spend.data.TxnCache
import com.rohan.spend.data.sync
import com.rohan.spend.ui.AppState
import com.rohan.spend.ui.Stage
import com.rohan.spend.ui.withPrefs
import java.time.LocalDate
import kotlin.concurrent.thread

/**
 * The app's numbers without the app: the widget and alarms build the same [AppState] the screens use,
 * from the saved transactions and the user's edits, so every total matches what the app shows.
 */
object Headless {
    /**
     * Null when the app has never finished a first scan. With [read], new SMS are folded into the cache first,
     * but only when "Check for new messages" is on; a full inbox read is left for the app.
     */
    fun load(context: Context, read: Boolean = true): AppState? {
        val file = TxnCache.file(context)
        var snap = TxnCache.load(file) ?: return null
        val prefs = Prefs(context)
        if (read && prefs.autoRefresh && canRead(context)) {
            runCatching { SmsSource.sync(context, snap.maxId, snap.txns, wholeOk = false) }.getOrNull()?.let {
                runCatching { TxnCache.save(file, it) }
                snap = it
            }
        }
        return AppState(stage = Stage.READY, txns = snap.txns, foundCount = snap.txns.size, today = LocalDate.now()).withPrefs(prefs)
    }

    private fun canRead(context: Context) =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED
}

/** Runs [work] off the main thread and keeps the broadcast alive until it's done. */
fun BroadcastReceiver.inBackground(work: () -> Unit) {
    val pending = goAsync()
    thread(name = "spend-bg") {
        try { runCatching(work) } finally { pending.finish() }
    }
}
