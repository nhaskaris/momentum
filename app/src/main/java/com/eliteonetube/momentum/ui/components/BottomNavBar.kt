package com.eliteonetube.momentum.ui.components

import androidx.compose.ui.unit.Dp
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class AppTab(val label: String, val icon: ImageVector) {
    DASHBOARD("Home", Icons.Default.Home),
    STATISTICS("Stats", Icons.Default.BarChart),
    NUTRITION("Eat", Icons.Default.Restaurant),
    WORKOUTS("Train", Icons.Default.FitnessCenter),
    PROFILE("Me", Icons.Default.AccountCircle)
}

@Composable
fun BottomNavBar(
    currentTab: AppTab,
    onTabSelected: (AppTab) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 48.dp, vertical = NavBarVerticalPadding),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
            shape = RoundedCornerShape(24.dp),
            shadowElevation = 6.dp,
            border = BorderStroke(
                width = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
            ),
            modifier = Modifier
                .height(NavBarHeight)
                .fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppTab.entries.forEach { tab ->
                    val isSelected = currentTab == tab
                    val contentColor by animateColorAsState(
                        targetValue = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                        animationSpec = spring(stiffness = 500f),
                        label = "tabColor"
                    )

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .bounceClick { onTabSelected(tab) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = tab.icon,
                            contentDescription = tab.label,
                            tint = contentColor,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = tab.label,
                            color = contentColor,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 9.sp,
                                letterSpacing = 0.4.sp
                            )
                        )
                    }
                }
            }
        }
    }
}

private val NavBarHeight = 64.dp
private val NavBarVerticalPadding = 16.dp

/**
 * Bottom padding a tab's scrolling content needs so its last item can scroll clear of the
 * floating nav bar: the bar, its padding, the system navigation area (much taller with 3-button
 * navigation than with gestures) and a little breathing room. A fixed 120dp hid the end of every
 * page behind the bar on 3-button phones.
 */
@Composable
fun bottomNavClearance(): Dp =
    NavBarHeight + NavBarVerticalPadding * 2 +
        WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 16.dp
