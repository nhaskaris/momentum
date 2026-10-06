package com.eliteonetube.momentum.ui.screens.learn

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.eliteonetube.momentum.ui.theme.CarbGreen
import com.eliteonetube.momentum.ui.theme.FatYellow
import com.eliteonetube.momentum.ui.theme.ProteinBlue

enum class LearnCategory(val label: String) {
    NUTRITION("NUTRITION"),
    COACHING("COACHING"),
    TRAINING("TRAINING")
}

data class LearnTopic(
    val id: String,
    val category: LearnCategory,
    val title: String,
    val summary: String,
    val body: String,
    val icon: ImageVector,
    /** Accent for the topic's icon; null uses the theme's primary colour. */
    val accent: Color? = null
)

/**
 * The in-app knowledge base. Keep these explanations in sync with the logic they describe
 * (MacroTargets, CoachAlgorithm, the active workout screen) when that logic changes.
 */
object LearnContent {
    val topics: List<LearnTopic> = listOf(
        // Nutrition
        LearnTopic(
            id = "protein",
            category = LearnCategory.NUTRITION,
            title = "Protein: the builder",
            summary = "Repairs and builds muscle. Target: 2.0 g per kg.",
            body = "Protein is what your body uses to repair and build muscle tissue, and it keeps you fuller than " +
                "carbs or fat. Momentum sets your target at 2.0 g per kg of your most recent logged weight — " +
                "towards the top of the 1.6–2.2 g/kg range that research supports for people who train. " +
                "Spread it across your meals rather than saving it for one sitting.",
            icon = Icons.Default.FitnessCenter,
            accent = ProteinBlue
        ),
        LearnTopic(
            id = "fat",
            category = LearnCategory.NUTRITION,
            title = "Fats: the regulator",
            summary = "Essential for hormones and brain health. Target: 0.8 g per kg.",
            body = "Dietary fat supports hormone production, brain health and the absorption of vitamins A, D, E and K. " +
                "Momentum sets your target at 0.8 g per kg of body weight. Fat has 9 kcal per gram — more than " +
                "double protein or carbs — so small portions of oils, nuts and butter add up quickly.",
            icon = Icons.Default.WaterDrop,
            accent = FatYellow
        ),
        LearnTopic(
            id = "carbs",
            category = LearnCategory.NUTRITION,
            title = "Carbs: the fuel",
            summary = "Your main energy source for hard training.",
            body = "Carbohydrates are the body's preferred fuel for intense exercise. Momentum doesn't give carbs a " +
                "fixed per-kg number: once protein (4 kcal/g) and fat (9 kcal/g) are set, carbs (4 kcal/g) fill " +
                "whatever calories are left in your daily target. That's why your carb target moves when your " +
                "calorie target changes.",
            icon = Icons.Default.Bolt,
            accent = CarbGreen
        ),
        LearnTopic(
            id = "calorie-target",
            category = LearnCategory.NUTRITION,
            title = "How your calorie target is set",
            summary = "Your estimated burn, then adjusted for your goal.",
            body = "Momentum first estimates your resting burn: with the Mifflin-St Jeor formula from your weight, " +
                "height, age and sex, or with Katch-McArdle if you've entered a body fat %. It multiplies that by " +
                "1.15 for daily life and adds about 30 kcal per 1,000 average daily steps. That's your estimated " +
                "maintenance. Your goal then sets the starting target — for example a deficit for a cut or a " +
                "surplus for a bulk — and the weekly check-in fine-tunes it from your real results.",
            icon = Icons.Default.LocalFireDepartment
        ),

        // Coaching
        LearnTopic(
            id = "weekly-check-in",
            category = LearnCategory.COACHING,
            title = "The weekly check-in",
            summary = "Compares this week's average weight with last week's.",
            body = "Single weigh-ins swing with water, salt and food in your gut, so Momentum looks at averages. " +
                "Each week it compares your average weight over the last 7 days with the 7 days before. It needs " +
                "at least 3 weigh-ins in each week to make a call — with fewer, it keeps your target as it is.",
            icon = Icons.Default.EventRepeat
        ),
        LearnTopic(
            id = "calorie-adjustments",
            category = LearnCategory.COACHING,
            title = "How calories are adjusted",
            summary = "Small, goal-specific nudges based on your weekly trend.",
            body = "Cut: if you lost less than 0.2 kg, calories drop by 150; if you lost more than 1.2 kg, they go up " +
                "by 150 to keep it sustainable.\n\n" +
                "Bulk: if you gained less than 0.1 kg, calories go up by 150; more than 0.6 kg, they drop by 100 to " +
                "limit fat gain.\n\n" +
                "Maintain: a change of more than 0.4 kg either way moves calories by 100 in the other direction.\n\n" +
                "Reverse: calories rise by 100 a week towards your estimated maintenance, pausing if you gain more " +
                "than 0.5 kg in a week.",
            icon = Icons.AutoMirrored.Filled.TrendingUp
        ),
        LearnTopic(
            id = "weighing-in",
            category = LearnCategory.COACHING,
            title = "Weighing in well",
            summary = "Same time, same conditions, every day you can.",
            body = "For the most useful trend, weigh yourself first thing in the morning, after the bathroom and before " +
                "eating or drinking. Daily ups and downs of a kilo or more are normal and mostly water — the weekly " +
                "average is what counts. More weigh-ins make the average more reliable.",
            icon = Icons.Default.MonitorWeight
        ),
        LearnTopic(
            id = "body-fat",
            category = LearnCategory.COACHING,
            title = "Logging body fat",
            summary = "Optional, but it sharpens your maintenance estimate.",
            body = "If you know your body fat % — from a smart scale, DEXA scan or calipers — add it at your weekly " +
                "check-in. Momentum then estimates your burn from your lean mass (Katch-McArdle), which is more " +
                "accurate for people with more or less muscle than average. Use the same method each time, since " +
                "different methods can disagree by several percent.",
            icon = Icons.Default.Percent
        ),

        // Training
        LearnTopic(
            id = "placeholders",
            category = LearnCategory.TRAINING,
            title = "The grey numbers in a workout",
            summary = "Your routine's targets, or what you did last time.",
            body = "The grey placeholder in each weight and reps box comes from your routine's target for that set, " +
                "or from your most recent performance if the routine doesn't have one. If you leave a set blank, " +
                "it's saved with exactly the numbers shown — so you only need to type when you do something different.",
            icon = Icons.Default.EditNote
        ),
        LearnTopic(
            id = "routine-updates",
            category = LearnCategory.TRAINING,
            title = "How routines update",
            summary = "Your numbers carry forward to next time.",
            body = "When you finish a routine with the same exercises and set counts, today's weights and reps " +
                "become the routine's targets for next time, so it progresses with you. If you added, removed or " +
                "reordered exercises or sets, Momentum asks whether to save those changes to the routine or keep " +
                "it as it was.",
            icon = Icons.Default.Sync
        ),
        LearnTopic(
            id = "volume",
            category = LearnCategory.TRAINING,
            title = "What training volume means",
            summary = "Weight × reps, added up across every set.",
            body = "Volume is the total weight you moved: for each set, weight multiplied by reps, summed across the " +
                "workout. It's a rough measure of how much work you did, and a rising trend over weeks usually means " +
                "you're getting stronger. Cardio and bodyweight sets with no weight don't add to it.",
            icon = Icons.Default.BarChart
        )
    )
}
