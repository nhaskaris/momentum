package com.eliteonetube.momentum.ui.screens.onboarding

import com.eliteonetube.momentum.ui.screens.dashboard.MomentumMascot
import com.eliteonetube.momentum.ui.screens.dashboard.MascotMood
import com.eliteonetube.momentum.ui.components.PrimaryButton
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.eliteonetube.momentum.ui.components.screenSafePadding
import kotlinx.coroutines.launch

private data class IntroPage(val title: String, val body: String)

private val introPages = listOf(
    IntroPage(
        title = "Momentum",
        body = "Your personal fitness helper, built with privacy in mind. Your data stays on your device — no account, no cloud, no tracking. Online food lookup is optional — you choose during setup."
    ),
    IntroPage(
        title = "Cut",
        body = "Eat in a calorie deficit to lose fat while holding onto muscle. Momentum watches your weekly trend and trims calories only when your loss actually stalls."
    ),
    IntroPage(
        title = "Bulk",
        body = "Eat in a surplus to build muscle over time. Momentum keeps the surplus lean, easing calories up or down so gains stay steady rather than rushed."
    ),
    IntroPage(
        title = "Maintain",
        body = "Hold your current weight steady. Momentum nudges calories in either direction the moment your trend starts drifting, so you stay level without guessing."
    ),
    IntroPage(
        title = "Reverse",
        body = "Coming off a cut? Reverse dieting gradually increases your calories back toward maintenance, so your metabolism rebuilds without rapid fat regain."
    )
)

@Composable
fun IntroScreen(onFinished: () -> Unit) {
    val pagerState = rememberPagerState(pageCount = { introPages.size })
    val coroutineScope = rememberCoroutineScope()
    val isDark = isSystemInDarkTheme()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.primary.copy(alpha = if (isDark) 0.15f else 0.12f),
                        MaterialTheme.colorScheme.background
                    ),
                    endY = 1200f
                )
            )
            .screenSafePadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(
                onClick = onFinished,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
            ) {
                Text("Skip")
            }
        }

        Spacer(modifier = Modifier.weight(0.6f))
        MomentumMascot(mood = MascotMood.HAPPY, modifier = Modifier.size(96.dp))
        Spacer(modifier = Modifier.height(24.dp))

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth().weight(2f)
        ) { page ->
            val introPage = introPages[page]
            Column(
                modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = if (page == 0) "WELCOME" else "GOAL ${page} OF ${introPages.lastIndex}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = introPage.title,
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = introPage.body,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            introPages.indices.forEach { index ->
                val isSelected = pagerState.currentPage == index
                Box(
                    modifier = Modifier
                        .height(8.dp)
                        .width(if (isSelected) 24.dp else 8.dp)
                        .clip(CircleShape)
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                        )
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        val isLastPage = pagerState.currentPage == introPages.lastIndex
        PrimaryButton(
            onClick = {
                if (isLastPage) {
                    onFinished()
                } else {
                    coroutineScope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (isLastPage) "Get Started" else "Next")
            if (!isLastPage) {
                Spacer(modifier = Modifier.width(8.dp))
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
            }
        }
    }
}
