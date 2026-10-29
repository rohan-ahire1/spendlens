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
import java.time.Duration
import java.time.LocalTime
import java.time.ZonedDateTime

/**
 * Every timed job, as inexact alarms so no exact-alarm permission is needed. Each one sets the next when
 * it fires; boot, clock changes and opening the app set them all again.
 *
 * An inexact alarm set a day ahead may come hours late, so the 9 pm and 9 am jobs go in two steps: a bounded
 * [AlarmManager.setWindow] wake half an hour before, which then sets the job itself while-idle for the hour,
 * where Android's slack is a fraction of the half hour left. setWindow alone can be held by Doze; while-idle
 * alone, set a day ahead, can't be bounded.
 */
object Alarms {
    const val MIDNIGHT = "com.rohan.spend.MIDNIGHT"
    const val SUMMARY = "com.rohan.spend.SUMMARY"
    const val RENEWALS = "com.rohan.spend.RENEWALS"
    const val SUMMARY_SOON = "com.rohan.spend.SUMMARY_SOON"
    const val RENEWALS_SOON = "com.rohan.spend.RENEWALS_SOON"
    /** A few seconds in, so the date has surely turned over. */
    private val midnight = LocalTime.of(0, 0, 5)
    private val LEAD = Duration.ofMinutes(30)
    private const val WINDOW = 15 * 60 * 1000L

    fun schedule(context: Context) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val now = ZonedDateTime.now()
        // RTC, not RTC_WAKEUP: a widget nobody is looking at can wait for the screen to come on. The window keeps
        // it within minutes of midnight when someone is.
        if (Widget.ids(context).isNotEmpty()) am.setWindow(AlarmManager.RTC, ms(nextAt(now, midnight)), WINDOW, intent(context, MIDNIGHT))
        else am.cancel(intent(context, MIDNIGHT))
        val prefs = Prefs(context)
        arm(context, am, now, prefs.eveningSummary, LocalTime.of(SUMMARY_HOUR, 0), SUMMARY, SUMMARY_SOON)
        arm(context, am, now, prefs.renewalAlerts, LocalTime.of(RENEWALS_HOUR, 0), RENEWALS, RENEWALS_SOON)
    }

    private fun arm(context: Context, am: AlarmManager, now: ZonedDateTime, on: Boolean, at: LocalTime, job: String, soon: String) {
        if (!on) {
            am.cancel(intent(context, job))
            am.cancel(intent(context, soon))
            return
        }
        val t = nextAt(now, at)
        if (Duration.between(now, t) <= LEAD) am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ms(t), intent(context, job))
        else am.setWindow(AlarmManager.RTC_WAKEUP, ms(t.minus(LEAD)), WINDOW, intent(context, soon))
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
        // Also sets the job itself when this is the half-hour-early wake.
        Alarms.schedule(context)
        when (intent.action) {
            Alarms.MIDNIGHT -> inBackground { Widget.refresh(context) }
            // The early wake does the job itself only when Doze held it past the hour. Both check the time.
            Alarms.SUMMARY, Alarms.SUMMARY_SOON -> inBackground { Notify.summary(context) }
            Alarms.RENEWALS -> inBackground { Notify.renewals(context) }
            Alarms.RENEWALS_SOON -> if (LocalTime.now().hour >= RENEWALS_HOUR) inBackground { Notify.renewals(context) }
        }
    }
}

/** Boot, app updates and clock or time zone changes: set the alarms again and redraw for the new date. */
class Restart : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in actions) return
        Alarms.schedule(context)
        // Back on after 9 pm: tonight's summary still comes.
        inBackground { Widget.refresh(context); Notify.summary(context) }
    }

    private val actions = setOf(
        Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED,
        Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED,
    )
}
