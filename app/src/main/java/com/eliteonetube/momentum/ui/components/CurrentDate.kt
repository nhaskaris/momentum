package com.eliteonetube.momentum.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Today's date that stays correct while the app is open: refreshed when the app returns to the
 * foreground and when midnight passes. Use this instead of `remember { LocalDate.now() }`, which
 * freezes the date for as long as the screen lives.
 */
@Composable
fun rememberCurrentDate(): State<LocalDate> {
    val date = remember { mutableStateOf(LocalDate.now()) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) date.value = LocalDate.now()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(date.value) {
        val untilMidnight = Duration.between(LocalDateTime.now(), date.value.plusDays(1).atStartOfDay())
        delay(untilMidnight.toMillis().coerceAtLeast(0) + 1_000)
        date.value = LocalDate.now()
    }

    return date
}
