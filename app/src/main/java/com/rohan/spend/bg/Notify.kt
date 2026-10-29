package com.rohan.spend.bg

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationChannelGroup
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.rohan.spend.MainActivity
import com.rohan.spend.R
import com.rohan.spend.data.Prefs
import com.rohan.spend.data.nudgeCopy
import com.rohan.spend.data.pruneSeen
import com.rohan.spend.data.renewalsDue
import com.rohan.spend.data.subAlerts
import com.rohan.spend.data.summaryDue
import java.time.LocalDateTime
import java.time.LocalTime

object Notify {
    const val SUMMARY = "summary"
    const val SUMMARY_ID = 1
    const val RENEWALS = "renewals"

    /** True when Android will show our notifications at all. */
    fun allowed(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED) return false
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    private fun channels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannelGroup(NotificationChannelGroup("alerts", "Spend Lens"))
        // Low: it comes every evening, so it sits in the shade quietly rather than making a sound.
        nm.createNotificationChannel(NotificationChannel(SUMMARY, "Evening summary", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Today's spend and what's left this month, at 9 pm"
            group = "alerts"
        })
        nm.createNotificationChannel(NotificationChannel(RENEWALS, "Renewals", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "A day or two before a subscription charges, and when one costs more"
            group = "alerts"
        })
    }

    fun post(context: Context, channel: String, id: Int, title: String, text: String) {
        channels(context)
        if (!allowed(context)) return
        val open = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val n = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat)
            .setColor(0xFF2D1FB3.toInt())
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(PendingIntent.getActivity(context, 0, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
            .setAutoCancel(true)
            .build()
        try { NotificationManagerCompat.from(context).notify(id, n) } catch (_: SecurityException) {}
    }

    /** The 9 pm summary. Also redraws the widget, since the numbers were just loaded. */
    fun summary(context: Context) {
        val prefs = Prefs(context)
        val now = LocalDateTime.now()
        if (!prefs.eveningSummary || !summaryDue(now, prefs.summarySent)) return
        val m = Headless.load(context)?.monthLeft() ?: return
        Widget.push(context, m)
        val (title, text) = nudgeCopy(m)
        post(context, SUMMARY, SUMMARY_ID, title, text)
        prefs.summarySent = now.toLocalDate().toString()
    }

    /** The morning check: renewals in the next two days and fresh price rises, each told once, by day only. */
    fun renewals(context: Context) {
        val prefs = Prefs(context)
        if (!prefs.renewalAlerts || !renewalsDue(LocalTime.now()) || !allowed(context)) return
        val s = Headless.load(context) ?: return
        val seen = pruneSeen(prefs.alertsSeen, s.today)
        val new = subAlerts(s.stats.subs, s.today, seen)
        new.forEach { post(context, RENEWALS, it.key.hashCode(), it.title, it.text) }
        prefs.alertsSeen = seen + new.map { it.key }
    }
}
