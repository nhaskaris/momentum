package com.eliteonetube.momentum.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.updateAll

/**
 * Call this immediately after any successful write to the food log or weight
 * table (insert, update, or delete). Glance's periodic updatePeriodMillis is
 * a floor of ~30 minutes on most devices, so without this the widget will
 * show stale calorie/streak data until the next OS-driven refresh or the
 * user manually reopens the app.
 *
 * Example call site, right after a DAO insert:
 *   foodDao.insert(entry)
 *   WidgetUpdater.refresh(context)
 */
object WidgetUpdater {
    suspend fun refresh(context: Context) {
        MomentumWidget().updateAll(context)
    }

    /** True if at least one instance of the widget is currently placed on a home screen. */
    suspend fun hasActiveInstances(context: Context): Boolean {
        return GlanceAppWidgetManager(context)
            .getGlanceIds(MomentumWidget::class.java)
            .isNotEmpty()
    }
}