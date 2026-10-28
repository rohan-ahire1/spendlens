package com.rohan.spend.bg

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.SizeF
import android.view.View
import android.widget.RemoteViews
import com.rohan.spend.MainActivity
import com.rohan.spend.R
import com.rohan.spend.data.MonthLeft
import com.rohan.spend.data.inr
import com.rohan.spend.ui.AppState

/** What the widget and the evening summary show, from the same numbers as Home's headline. */
fun AppState.monthLeft(): MonthLeft = stats.daily.let { MonthLeft(it.values.sum(), usual(), it[today] ?: 0L) }

class SpendWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, mgr: AppWidgetManager, ids: IntArray) = inBackground {
        Widget.draw(context, Headless.load(context)?.monthLeft(), ids)
    }

    override fun onAppWidgetOptionsChanged(context: Context, mgr: AppWidgetManager, id: Int, options: Bundle) = inBackground {
        Widget.draw(context, Headless.load(context, read = false)?.monthLeft(), intArrayOf(id))
    }

    override fun onEnabled(context: Context) = Alarms.schedule(context)
    override fun onDisabled(context: Context) = Alarms.schedule(context)
}

object Widget {
    /** Below this width the widget drops today's spend. */
    private const val WIDE_DP = 200f

    fun ids(context: Context): IntArray =
        AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, SpendWidget::class.java))

    /** From the app, with the numbers it's showing. */
    fun push(context: Context, m: MonthLeft) {
        val ids = ids(context)
        if (ids.isNotEmpty()) draw(context, m, ids)
    }

    /** Reloads from the cache, reading new SMS first. Background threads only. */
    fun refresh(context: Context) {
        val ids = ids(context)
        if (ids.isNotEmpty()) draw(context, Headless.load(context)?.monthLeft(), ids)
    }

    /** Null [m] means there's no cache yet, so the widget asks for the app to be opened. */
    fun draw(context: Context, m: MonthLeft?, ids: IntArray) {
        val mgr = AppWidgetManager.getInstance(context)
        val small = views(context, R.layout.widget_small, m)
        val wide = views(context, R.layout.widget_wide, m)
        for (id in ids) {
            val v = if (Build.VERSION.SDK_INT >= 31) RemoteViews(mapOf(SizeF(110f, 40f) to small, SizeF(WIDE_DP, 40f) to wide))
            else if (mgr.getAppWidgetOptions(id).getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH) >= WIDE_DP) wide else small
            mgr.updateAppWidget(id, v)
        }
    }

    private fun views(context: Context, layout: Int, m: MonthLeft?) = RemoteViews(context.packageName, layout).apply {
        val open = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        setOnClickPendingIntent(android.R.id.background,
            PendingIntent.getActivity(context, 0, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        val wide = layout == R.layout.widget_wide
        if (m == null) {
            setTextViewText(R.id.amount, "Spend Lens")
            setTextViewText(R.id.label, "Open Spend Lens to set up")
            setViewVisibility(R.id.bar, View.GONE)
            setViewVisibility(R.id.barOver, View.GONE)
            if (wide) setViewVisibility(R.id.todayBox, View.GONE)
            return@apply
        }
        setTextViewText(R.id.amount, m.amount)
        setTextViewText(R.id.label, m.label)
        val shown = if (m.over) R.id.barOver else R.id.bar
        setViewVisibility(R.id.bar, if (m.usual > 0 && !m.over) View.VISIBLE else View.GONE)
        setViewVisibility(R.id.barOver, if (m.over) View.VISIBLE else View.GONE)
        setProgressBar(shown, 1000, (m.fraction * 1000).toInt(), false)
        if (wide) {
            setViewVisibility(R.id.todayBox, View.VISIBLE)
            setTextViewText(R.id.today, inr(m.today))
        }
    }
}
