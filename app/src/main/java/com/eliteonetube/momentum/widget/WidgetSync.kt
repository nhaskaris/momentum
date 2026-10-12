package com.eliteonetube.momentum.widget

import android.content.Context
import android.util.Log
import com.eliteonetube.momentum.data.WeightDatabase
import com.eliteonetube.momentum.ui.diaryFor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Keeps the home-screen widget in step with the database for as long as the app process lives,
 * whether or not a screen is open. It watches exactly what the widget shows — today's diary, the
 * calorie target and units, recent weights and the streak dates — and redraws once shortly after
 * any of it changes (and at midnight, when "today" moves on).
 *
 * This replaces a watcher in the UI, which stalled when the app went to the background right after
 * logging something.
 */
object WidgetSync {
    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    fun start(context: Context, scope: CoroutineScope) {
        val appContext = context.applicationContext
        scope.launch {
            val database = WeightDatabase.getInstance(appContext)
            val weightDao = database.weightDao()
            val foodDao = database.foodDao()

            val todaysDiary = currentDate().flatMapLatest { date -> foodDao.diaryFor(date.toString()) }
            val target = weightDao.getUserProfile().map { profile -> profile?.currentCalorieTarget to profile?.unitSystem }

            combine(todaysDiary, target, weightDao.getLastTwoWeeks(), weightDao.getAllWeightDates()) { diary, profile, weights, dates ->
                listOf(diary, profile, weights.firstOrNull(), dates.size, dates.firstOrNull())
            }
                .distinctUntilChanged()
                .debounce(500)
                .collect {
                    runCatching { WidgetUpdater.refresh(appContext) }
                        .onFailure { Log.e("WidgetSync", "Widget refresh failed", it) }
                }
        }
    }

    /** Today's date, emitted again just after each midnight. */
    private fun currentDate(): Flow<LocalDate> = flow {
        while (true) {
            val today = LocalDate.now()
            emit(today)
            val untilMidnight = Duration.between(LocalDateTime.now(), today.plusDays(1).atStartOfDay())
            delay(untilMidnight.toMillis().coerceAtLeast(0) + 1_000)
        }
    }
}
