package com.eliteonetube.momentum.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Standard full-screen padding for screens that build their own layout outside
 * Scaffold (onboarding, intro pager, loading screen). Reserves space for the
 * status bar and system nav bar so content and buttons never sit under them,
 * then applies the app's default 24dp content margin.
 *
 * Screens hosted inside HomeScreen's Scaffold (Dashboard, Profile) don't need
 * this — Scaffold already handles insets via its innerPadding.
 */
@Composable
fun Modifier.screenSafePadding(): Modifier =
    this
        .windowInsetsPadding(WindowInsets.statusBars.union(WindowInsets.navigationBars))
        .padding(24.dp)