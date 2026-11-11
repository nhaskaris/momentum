package com.eliteonetube.momentum.logic.services

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.edit
import androidx.core.net.toUri
import com.eliteonetube.momentum.logic.algorithm.WeeklyReviewResult
import com.google.android.play.core.review.ReviewManagerFactory
import com.google.android.play.core.review.testing.FakeReviewManager
import com.google.android.play.core.ktx.launchReview
import com.google.android.play.core.ktx.requestReview
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** When Momentum may ask for a Play Store review. No Android types, so it can be unit-tested. */
object ReviewPolicy {
    const val MIN_DAYS_BETWEEN_ASKS = 120L
    /** Google never says whether someone rated or declined, so a lifetime cap stands in for "not interested". */
    const val MAX_ASKS = 3
    const val STREAK_MILESTONE = 7

    fun canAsk(lastAsked: LocalDate?, askCount: Int, today: LocalDate): Boolean =
        askCount < MAX_ASKS && (lastAsked == null || ChronoUnit.DAYS.between(lastAsked, today) >= MIN_DAYS_BETWEEN_ASKS)

    /** Good news worth asking after: enough data to judge, calories up or held, and not a floor verdict. */
    fun isGoodCheckIn(review: WeeklyReviewResult, previousTarget: Int): Boolean =
        review.hadEnoughData && review.newTarget >= previousTarget && !review.hitCutFloor

    /** True when a weigh-in took the streak to exactly the milestone; longer streaks don't count again. */
    fun reachedStreakMilestone(streakBefore: Int, streakAfter: Int): Boolean =
        streakBefore < STREAK_MILESTONE && streakAfter == STREAK_MILESTONE
}

/** Shows Google's in-app review dialog when [ReviewPolicy] allows it, and remembers each ask. */
class ReviewPrompter(private val activity: Activity) {
    private val prefs = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // One at a time, so a burst of triggers (e.g. a weight import) reads the updated count
    suspend fun maybeAsk(today: LocalDate = LocalDate.now()) = askLock.withLock {
        val lastAsked = prefs.getString(KEY_LAST_ASKED, null)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        val askCount = prefs.getInt(KEY_ASK_COUNT, 0)
        if (!ReviewPolicy.canAsk(lastAsked, askCount, today)) return@withLock

        // The sandbox build isn't on Play, so it uses Google's fake manager to exercise the flow
        val isSandbox = activity.packageName.endsWith(".sandbox")
        val manager = if (isSandbox) FakeReviewManager(activity) else ReviewManagerFactory.create(activity)
        // Fails when Play can't show it (e.g. not installed from Play); that doesn't use up an ask
        val info = runCatching { manager.requestReview() }.getOrNull() ?: return@withLock
        prefs.edit {
            putString(KEY_LAST_ASKED, today.toString())
            putInt(KEY_ASK_COUNT, askCount + 1)
        }
        runCatching { manager.launchReview(activity, info) }
        if (isSandbox) Log.d(TAG, "Review prompt shown (ask ${askCount + 1} of ${ReviewPolicy.MAX_ASKS})")
    }

    companion object {
        private val askLock = Mutex()
        private const val TAG = "ReviewPrompter"
        private const val PREFS = "review_prompt"
        private const val KEY_LAST_ASKED = "last_asked"
        private const val KEY_ASK_COUNT = "ask_count"
        /** The real listing, also for the sandbox build, which has its own app ID. */
        private const val PLAY_PACKAGE = "com.eliteonetube.momentum"

        /** Opens Momentum's Play Store page; doesn't count as an automatic ask. */
        fun openStoreListing(context: Context) {
            val market = Intent(Intent.ACTION_VIEW, "market://details?id=$PLAY_PACKAGE".toUri())
            try {
                context.startActivity(market)
            } catch (e: ActivityNotFoundException) {
                context.startActivity(Intent(Intent.ACTION_VIEW, "https://play.google.com/store/apps/details?id=$PLAY_PACKAGE".toUri()))
            }
        }
    }
}
