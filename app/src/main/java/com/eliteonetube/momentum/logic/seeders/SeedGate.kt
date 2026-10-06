package com.eliteonetube.momentum.logic.seeders

import android.content.Context
import androidx.core.content.edit
import androidx.core.content.pm.PackageInfoCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Runs a seeder at most once per installed app version, off the main thread.
 *
 * The bundled JSON only changes when the app is updated, so once this version has been checked
 * there's nothing to do — previously every launch read and fully parsed the asset on the main
 * thread just to discover that.
 */
internal object SeedGate {
    private const val KEY_CHECKED_APP_VERSION = "checked_app_version"

    suspend fun runOncePerAppVersion(context: Context, prefsName: String, seed: suspend () -> Unit) =
        withContext(Dispatchers.IO) {
            val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
            val appVersion = PackageInfoCompat.getLongVersionCode(
                context.packageManager.getPackageInfo(context.packageName, 0)
            )
            if (prefs.getLong(KEY_CHECKED_APP_VERSION, -1L) == appVersion) return@withContext

            seed()
            // Only recorded after seeding succeeded, so a failed insert is retried next launch
            prefs.edit { putLong(KEY_CHECKED_APP_VERSION, appVersion) }
        }
}
