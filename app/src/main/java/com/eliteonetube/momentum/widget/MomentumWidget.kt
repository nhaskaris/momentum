package com.eliteonetube.momentum.widget

import android.content.Context
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.*
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.cornerRadius
import androidx.glance.layout.*
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.appwidget.SizeMode
import com.eliteonetube.momentum.MainActivity
import com.eliteonetube.momentum.data.WeightDatabase
import com.eliteonetube.momentum.logic.StreakCalculator
import kotlinx.coroutines.flow.first
import java.time.LocalDate

private const val TAG = "MomentumWidget"

private sealed class WidgetState {
    data class Loaded(val caloriesRemaining: Int, val streak: Int) : WidgetState()
    object NeedsOnboarding : WidgetState()
    object Error : WidgetState()
}

class MomentumWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val state = loadState(context)

        provideContent {
            val size = LocalSize.current
            // Adjusted breakpoint for more flexible layouts
            val isCompact = size.width < 160.dp || size.height < 100.dp

            when (state) {
                is WidgetState.Loaded -> WidgetContent(
                    caloriesRemaining = state.caloriesRemaining,
                    streak = state.streak,
                    compact = isCompact
                )
                WidgetState.NeedsOnboarding -> MessageContent(
                    message = "Set up profile to track."
                )
                WidgetState.Error -> MessageContent(
                    message = "Tap to refresh data."
                )
            }
        }
    }

    private suspend fun loadState(context: Context): WidgetState {
        return try {
            val database = WeightDatabase.getInstance(context)
            val weightDao = database.weightDao()
            val foodDao = database.foodDao()

            val profile = weightDao.getUserProfile().first()
                ?: return WidgetState.NeedsOnboarding
            val calorieTarget = profile.currentCalorieTarget

            val today = LocalDate.now().toString()
            val todayLogs = foodDao.getFoodLogsForDate(today).first()
            val caloriesConsumed = todayLogs.sumOf { it.calories * it.quantity }.toInt()
            val caloriesRemaining = calorieTarget - caloriesConsumed

            val allWeightDates = weightDao.getAllWeightDates().first()
            val streak = StreakCalculator.currentStreak(allWeightDates)

            WidgetState.Loaded(caloriesRemaining, streak)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load widget data", e)
            WidgetState.Error
        }
    }

    @Composable
    private fun MessageContent(message: String) {
        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(solidColor(MomentumColors.dark))
                .cornerRadius(28.dp)
                .clickable(actionStartActivity<MainActivity>())
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = message,
                style = TextStyle(
                    color = solidColor(MomentumColors.textSecondary),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = androidx.glance.text.TextAlign.Center
                )
            )
        }
    }

    @Composable
    private fun WidgetContent(caloriesRemaining: Int, streak: Int, compact: Boolean) {
        val a11yLabel = if (streak > 0) {
            "$caloriesRemaining kcal left, $streak day streak. Tap to open Momentum."
        } else {
            "$caloriesRemaining kcal left. Tap to open Momentum."
        }

        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(solidColor(MomentumColors.dark))
                .cornerRadius(28.dp)
                .clickable(actionStartActivity<MainActivity>())
                .semantics { contentDescription = a11yLabel }
        ) {
            if (compact) {
                CompactLayout(caloriesRemaining, streak)
            } else {
                FullLayout(caloriesRemaining, streak)
            }
        }
    }

    @Composable
    private fun CompactLayout(caloriesRemaining: Int, streak: Int) {
        Row(
            modifier = GlanceModifier.fillMaxSize().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(
                    text = "$caloriesRemaining",
                    style = TextStyle(
                        color = solidColor(MomentumColors.textPrimary),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
                Text(
                    text = "KCAL REMAINING",
                    style = TextStyle(
                        color = solidColor(MomentumColors.textSecondary),
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Medium
                    )
                )
            }
            if (streak > 0) {
                StreakPill(streak, compact = true)
            }
        }
    }

    @Composable
    private fun FullLayout(caloriesRemaining: Int, streak: Int) {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .padding(16.dp),
            verticalAlignment = Alignment.Top,
            horizontalAlignment = Alignment.Start
        ) {
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SparkMascot()

                Spacer(modifier = GlanceModifier.defaultWeight())

                if (streak > 0) {
                    StreakPill(streak, compact = false)
                }
            }

            Spacer(modifier = GlanceModifier.defaultWeight())

            Column(modifier = GlanceModifier.padding(horizontal = 2.dp)) {
                Text(
                    text = "$caloriesRemaining",
                    style = TextStyle(
                        color = solidColor(MomentumColors.textPrimary),
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
                Text(
                    text = "KCAL REMAINING",
                    style = TextStyle(
                        color = solidColor(MomentumColors.textSecondary),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Medium
                    )
                )
            }
        }
    }

    @Composable
    private fun StreakPill(streak: Int, compact: Boolean) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = GlanceModifier
                .background(solidColor(MomentumColors.blue.copy(alpha = 0.15f)))
                .cornerRadius(12.dp)
                .padding(horizontal = if (compact) 8.dp else 10.dp, vertical = if (compact) 2.dp else 4.dp)
        ) {
            Text(
                text = "🔥 $streak",
                style = TextStyle(
                    color = solidColor(MomentumColors.blue),
                    fontSize = if (compact) 11.sp else 13.sp,
                    fontWeight = FontWeight.Bold
                )
            )
        }
    }

    @Composable
    private fun SparkMascot() {
        Box(
            modifier = GlanceModifier.size(32.dp),
            contentAlignment = Alignment.Center
        ) {
            // Glow layer - scaled down
            Box(
                modifier = GlanceModifier
                    .size(28.dp)
                    .background(solidColor(MomentumColors.blue.copy(alpha = 0.15f)))
                    .cornerRadius(10.dp)
            ) {}
            
            // Core Body - scaled down
            Box(
                modifier = GlanceModifier
                    .size(20.dp)
                    .background(solidColor(MomentumColors.blue))
                    .cornerRadius(7.dp)
            ) {
                Row(
                    modifier = GlanceModifier.fillMaxSize().padding(horizontal = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = GlanceModifier.size(2.dp).background(solidColor(MomentumColors.textPrimary)).cornerRadius(1.dp)) {}
                    Spacer(modifier = GlanceModifier.width(3.dp))
                    Box(modifier = GlanceModifier.size(2.dp).background(solidColor(MomentumColors.textPrimary)).cornerRadius(1.dp)) {}
                }
            }
        }
    }
}
