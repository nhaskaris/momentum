package com.eliteonetube.momentum.widget

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.*
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.*
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.eliteonetube.momentum.MainActivity
import com.eliteonetube.momentum.data.WeightDatabase
import com.eliteonetube.momentum.logic.algorithm.StreakCalculator
import com.eliteonetube.momentum.logic.utils.Units
import com.eliteonetube.momentum.ui.diaryFor
import com.eliteonetube.momentum.ui.screens.nutrition.MacroTargets
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import kotlin.math.roundToInt

private const val TAG = "MomentumWidget"

/** Everything the widget shows, loaded once per update. */
private data class WidgetData(
    val target: Int,
    val eaten: Int,
    val protein: Int,
    val carbs: Int,
    val fat: Int,
    val macroTargets: MacroTargets?,
    val streak: Int,
    /** Today's weigh-in, already formatted ("82.4 kg"), or null if not logged yet. */
    val todayWeight: String?
) {
    val remaining get() = target - eaten
    val isOver get() = eaten > target
    val progress get() = if (target > 0) (eaten.toFloat() / target).coerceIn(0f, 1f) else 0f
}

private sealed class WidgetState {
    data class Loaded(val data: WidgetData) : WidgetState()
    object NeedsOnboarding : WidgetState()
    object Error : WidgetState()
}

class MomentumWidget : GlanceAppWidget() {

    // Layouts by home-screen footprint (roughly 2×1, 2×2, 4×2 and 4×4 cells). The large layout
    // needs ~260dp to fit macro bars and the buttons; a 4×3 widget gets the 4×2 layout instead.
    private companion object {
        val TINY = DpSize(110.dp, 48.dp)
        val SMALL = DpSize(110.dp, 110.dp)
        val WIDE = DpSize(250.dp, 110.dp)
        val LARGE = DpSize(250.dp, 260.dp)
    }

    override val sizeMode: SizeMode = SizeMode.Responsive(setOf(TINY, SMALL, WIDE, LARGE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val state = loadState(context)

        provideContent {
            val size = LocalSize.current
            val openEat = openApp(context, MainActivity.ACTION_OPEN_NUTRITION)

            Box(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(MomentumColors.background)
                    .cornerRadius(24.dp)
                    .clickable(openEat)
            ) {
                when (state) {
                    is WidgetState.Loaded -> when {
                        size.height < SMALL.height -> TinyLayout(state.data)
                        size.width >= WIDE.width && size.height >= LARGE.height -> LargeLayout(context, state.data)
                        size.width >= WIDE.width -> WideLayout(context, state.data)
                        else -> SmallLayout(state.data)
                    }
                    WidgetState.NeedsOnboarding -> Message("Open Momentum to set up your profile.")
                    WidgetState.Error -> Message("Tap to open Momentum.")
                }
            }
        }
    }

    private suspend fun loadState(context: Context): WidgetState = try {
        val database = WeightDatabase.getInstance(context)
        val weightDao = database.weightDao()
        val profile = weightDao.getUserProfile().first() ?: return WidgetState.NeedsOnboarding

        val today = LocalDate.now().toString()
        // Foods and whole meals — meals used to be left out of the total
        val diary = database.foodDao().diaryFor(today).first()
        val recentWeights = weightDao.getLastTwoWeeks().first()
        val todayEntry = recentWeights.firstOrNull { it.date == today }

        WidgetState.Loaded(
            WidgetData(
                target = profile.currentCalorieTarget,
                eaten = diary.sumOf { it.calories * it.quantity }.roundToInt(),
                protein = diary.sumOf { it.protein * it.quantity }.roundToInt(),
                carbs = diary.sumOf { it.carbs * it.quantity }.roundToInt(),
                fat = diary.sumOf { it.fat * it.quantity }.roundToInt(),
                macroTargets = MacroTargets.from(profile.currentCalorieTarget, recentWeights.firstOrNull()?.weight),
                streak = StreakCalculator.currentStreak(weightDao.getAllWeightDates().first()),
                todayWeight = todayEntry?.let { Units.displayWeight(it.weight, profile.unitSystem) }
            )
        )
    } catch (e: Exception) {
        Log.e(TAG, "Failed to load widget data", e)
        WidgetState.Error
    }

    // --- Layouts ---

    @Composable
    private fun TinyLayout(data: WidgetData) {
        Column(
            modifier = GlanceModifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 8.dp)
                .semantics { contentDescription = summary(data) },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                BigNumber(data, 22)
                Spacer(GlanceModifier.width(6.dp))
                Caption(if (data.isOver) "kcal over" else "kcal left")
            }
            Spacer(GlanceModifier.height(4.dp))
            CalorieBar(data)
        }
    }

    @Composable
    private fun SmallLayout(data: WidgetData) {
        Column(
            modifier = GlanceModifier.fillMaxSize().padding(14.dp)
                .semantics { contentDescription = summary(data) },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Label("TODAY")
            Spacer(GlanceModifier.height(4.dp))
            BigNumber(data, 32)
            Caption(if (data.isOver) "kcal over" else "kcal left")
            Spacer(GlanceModifier.height(8.dp))
            CalorieBar(data)
            Spacer(GlanceModifier.height(6.dp))
            Caption("%,d / %,d".format(data.eaten, data.target))
        }
    }

    @Composable
    private fun WideLayout(context: Context, data: WidgetData) {
        Column(modifier = GlanceModifier.fillMaxSize().padding(14.dp)) {
            Row(
                modifier = GlanceModifier.fillMaxWidth().defaultWeight()
                    .semantics { contentDescription = summary(data) },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = GlanceModifier.defaultWeight()) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        BigNumber(data, 28)
                        Spacer(GlanceModifier.width(6.dp))
                        Caption(if (data.isOver) "kcal over" else "kcal left")
                    }
                    Spacer(GlanceModifier.height(6.dp))
                    CalorieBar(data)
                }
                Spacer(GlanceModifier.width(16.dp))
                Column {
                    MacroLine("Protein", data.protein, data.macroTargets?.proteinGrams, MomentumColors.protein)
                    MacroLine("Carbs", data.carbs, data.macroTargets?.carbGrams, MomentumColors.carbs)
                    MacroLine("Fat", data.fat, data.macroTargets?.fatGrams, MomentumColors.fat)
                }
            }
            Spacer(GlanceModifier.height(10.dp))
            ActionRow(context, data)
        }
    }

    @Composable
    private fun LargeLayout(context: Context, data: WidgetData) {
        Column(modifier = GlanceModifier.fillMaxSize().padding(16.dp)) {
            Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Label("TODAY")
                Spacer(GlanceModifier.defaultWeight())
                if (data.streak > 0) {
                    Text(
                        "${data.streak}-day streak",
                        style = TextStyle(color = MomentumColors.accent, fontSize = 12.sp, fontWeight = FontWeight.Bold),
                        modifier = GlanceModifier.background(MomentumColors.accentSoft).cornerRadius(10.dp)
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
            Spacer(GlanceModifier.height(6.dp))
            Column(modifier = GlanceModifier.semantics { contentDescription = summary(data) }) {
                Row(verticalAlignment = Alignment.Bottom) {
                    BigNumber(data, 34)
                    Spacer(GlanceModifier.width(6.dp))
                    Caption(if (data.isOver) "kcal over" else "kcal left")
                }
                Spacer(GlanceModifier.height(6.dp))
                CalorieBar(data)
                Spacer(GlanceModifier.height(4.dp))
                Caption("%,d eaten · %,d target".format(data.eaten, data.target))
            }
            Spacer(GlanceModifier.defaultWeight())
            data.macroTargets?.let { targets ->
                MacroBarRow("Protein", data.protein, targets.proteinGrams, MomentumColors.protein)
                MacroBarRow("Carbs", data.carbs, targets.carbGrams, MomentumColors.carbs)
                MacroBarRow("Fat", data.fat, targets.fatGrams, MomentumColors.fat)
            }
            Spacer(GlanceModifier.height(10.dp))
            ActionRow(context, data)
        }
    }

    // --- Pieces ---

    @Composable
    private fun BigNumber(data: WidgetData, sizeSp: Int) {
        Text(
            "%,d".format(kotlin.math.abs(data.remaining)),
            style = TextStyle(
                color = if (data.isOver) MomentumColors.over else MomentumColors.textPrimary,
                fontSize = sizeSp.sp,
                fontWeight = FontWeight.Bold
            )
        )
    }

    @Composable
    private fun CalorieBar(data: WidgetData) {
        LinearProgressIndicator(
            progress = data.progress,
            modifier = GlanceModifier.fillMaxWidth().height(6.dp).cornerRadius(3.dp),
            color = if (data.isOver) MomentumColors.over else MomentumColors.accent,
            backgroundColor = MomentumColors.track
        )
    }

    @Composable
    private fun MacroLine(label: String, grams: Int, target: Int?, color: ColorProvider) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = GlanceModifier.padding(vertical = 1.dp)) {
            Box(modifier = GlanceModifier.size(7.dp).background(color).cornerRadius(4.dp)) {}
            Spacer(GlanceModifier.width(6.dp))
            Text(
                if (target != null) "$label ${grams}/${target}g" else "$label ${grams}g",
                style = TextStyle(color = MomentumColors.textSecondary, fontSize = 11.sp)
            )
        }
    }

    @Composable
    private fun MacroBarRow(label: String, grams: Int, target: Int, color: ColorProvider) {
        Column(modifier = GlanceModifier.fillMaxWidth().padding(vertical = 2.dp)) {
            Row(modifier = GlanceModifier.fillMaxWidth()) {
                Text(label, style = TextStyle(color = MomentumColors.textPrimary, fontSize = 11.sp, fontWeight = FontWeight.Medium))
                Spacer(GlanceModifier.defaultWeight())
                Text("$grams / ${target}g", style = TextStyle(color = MomentumColors.textSecondary, fontSize = 11.sp))
            }
            Spacer(GlanceModifier.height(2.dp))
            LinearProgressIndicator(
                progress = if (target > 0) (grams.toFloat() / target).coerceIn(0f, 1f) else 0f,
                modifier = GlanceModifier.fillMaxWidth().height(4.dp).cornerRadius(2.dp),
                color = color,
                backgroundColor = MomentumColors.track
            )
        }
    }

    @Composable
    private fun ActionRow(context: Context, data: WidgetData) {
        Row(modifier = GlanceModifier.fillMaxWidth()) {
            ActionChip(
                text = "+ Food",
                description = "Log food",
                filled = true,
                action = openApp(context, MainActivity.ACTION_ADD_FOOD),
                modifier = GlanceModifier.defaultWeight()
            )
            Spacer(GlanceModifier.width(8.dp))
            ActionChip(
                text = data.todayWeight ?: "Weigh in",
                description = if (data.todayWeight != null) "Today's weight ${data.todayWeight}" else "Log today's weight",
                filled = false,
                action = openApp(context, MainActivity.ACTION_OPEN_WEIGHT_ENTRY),
                modifier = GlanceModifier.defaultWeight()
            )
        }
    }

    @Composable
    private fun ActionChip(text: String, description: String, filled: Boolean, action: Action, modifier: GlanceModifier) {
        Box(
            modifier = modifier
                .height(36.dp)
                .background(if (filled) MomentumColors.accent else MomentumColors.accentSoft)
                .cornerRadius(12.dp)
                .clickable(action)
                .semantics { contentDescription = description },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text,
                style = TextStyle(
                    color = if (filled) MomentumColors.onAccent else MomentumColors.accent,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            )
        }
    }

    @Composable
    private fun Label(text: String) {
        Text(text, style = TextStyle(color = MomentumColors.accent, fontSize = 11.sp, fontWeight = FontWeight.Bold))
    }

    @Composable
    private fun Caption(text: String) {
        Text(text, style = TextStyle(color = MomentumColors.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium))
    }

    @Composable
    private fun Message(text: String) {
        Box(modifier = GlanceModifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
            Text(
                text,
                style = TextStyle(
                    color = MomentumColors.textSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = androidx.glance.text.TextAlign.Center
                )
            )
        }
    }

    private fun summary(data: WidgetData) =
        (if (data.isOver) "${-data.remaining} kilocalories over today's target" else "${data.remaining} kilocalories left today") +
            ", ${data.eaten} of ${data.target} eaten. Tap to open the food diary."

    private fun openApp(context: Context, action: String): Action = actionStartActivity(
        Intent(context, MainActivity::class.java).apply {
            this.action = action
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
    )
}
