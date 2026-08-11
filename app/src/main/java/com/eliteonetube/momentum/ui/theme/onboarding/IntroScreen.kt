package com.eliteonetube.momentum.ui.theme.onboarding

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
import com.eliteonetube.momentum.ui.screenSafePadding
import kotlinx.coroutines.launch

private data class IntroPage(val title: String, val body: String)

private val introPages = listOf(
    IntroPage(
        title = "Momentum",
        body = "Your personal fitness helper, built with privacy in mind. Everything is stored locally on your device — no cloud, no tracking, just your data and your progress."
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .screenSafePadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(
                onClick = onFinished,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            ) {
                Text("Skip", fontWeight = FontWeight.Medium)
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth().weight(4f)
        ) { page ->
            val introPage = introPages[page]
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = introPage.title,
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = introPage.body,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            introPages.indices.forEach { index ->
                val isSelected = pagerState.currentPage == index
                Box(
                    modifier = Modifier
                        .size(if (isSelected) 10.dp else 8.dp)
                        .clip(CircleShape)
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        val isLastPage = pagerState.currentPage == introPages.lastIndex

        Button(
            onClick = {
                if (isLastPage) {
                    onFinished()
                } else {
                    coroutineScope.launch {
                        pagerState.animateScrollToPage(pagerState.currentPage + 1)
                    }
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
