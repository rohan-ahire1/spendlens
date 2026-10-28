package com.rohan.spend.bg

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.rohan.spend.data.Prefs
import com.rohan.spend.data.RENEWALS_HOUR
import com.rohan.spend.data.SUMMARY_HOUR
import com.rohan.spend.data.nextAt
import java.time.LocalTime
import java.time.ZonedDateTime

/**
 * Every timed job, as inexact alarms so no exact-alarm permission is needed. Each one sets the next when
 * it fires; boot, clock changes and opening the app set them all again.
 */
object Alarms {
    const val MIDNIGHT = "com.rohan.spend.MIDNIGHT"
    const val SUMMARY = "com.rohan.spend.SUMMARY"
    const val RENEWALS = "com.rohan.spend.RENEWALS"
    /** A few seconds in, so the date has surely turned over. */
    private val midnight = LocalTime.of(0, 0, 5)

    fun schedule(context: Context) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val now = ZonedDateTime.now()
        // RTC, not RTC_WAKEUP: a widget nobody is looking at can wait for the screen to come on.
        if (Widget.ids(context).isNotEmpty()) am.set(AlarmManager.RTC, ms(nextAt(now, midnight)), intent(context, MIDNIGHT))
        else am.cancel(intent(context, MIDNIGHT))
        val prefs = Prefs(context)
        // Allowed while idle so Doze doesn't hold it until morning; inexact, so it may come a few minutes late.
        if (prefs.eveningSummary) am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ms(nextAt(now, LocalTime.of(SUMMARY_HOUR, 0))), intent(context, SUMMARY))
        else am.cancel(intent(context, SUMMARY))
        if (prefs.renewalAlerts) am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ms(nextAt(now, LocalTime.of(RENEWALS_HOUR, 0))), intent(context, RENEWALS))
        else am.cancel(intent(context, RENEWALS))
    }

    private fun ms(t: ZonedDateTime) = t.toInstant().toEpochMilli()

    private fun intent(context: Context, action: String): PendingIntent = PendingIntent.getBroadcast(
        context, action.hashCode(), Intent(context, Ticks::class.java).setAction(action),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}

/** Our own alarms. Not exported, so only this app can set them off. */
class Ticks : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Alarms.schedule(context)
        when (intent.action) {
            Alarms.MIDNIGHT -> inBackground { Widget.refresh(context) }
            Alarms.SUMMARY -> inBackground { Notify.summary(context) }
            Alarms.RENEWALS -> inBackground { Notify.renewals(context) }
        }
    }
}

/** Boot, app updates and clock or time zone changes: set the alarms again and redraw for the new date. */
class Restart : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in actions) return
        Alarms.schedule(context)
        inBackground { Widget.refresh(context) }
    }

    private val actions = setOf(
        Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED,
        Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED,
    )
}
