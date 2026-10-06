package com.eliteonetube.momentum.ui.screens.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.eliteonetube.momentum.ui.components.BigStat
import com.eliteonetube.momentum.ui.components.SectionLabel
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun StreakCard(
    currentStreak: Int,
    totalDaysLogged: Int,
    loggedDates: Set<LocalDate>
) {
    val today = LocalDate.now()
    val last7Days = (6 downTo 0).map { today.minusDays(it.toLong()) }

    SectionLabel("STREAK")
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.LocalFireDepartment,
                contentDescription = null,
                tint = if (currentStreak > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                BigStat("$currentStreak", "day streak")
            }
            Text(
                "$totalDaysLogged days\nlogged",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.End
            )
        }
        if (currentStreak == 0) {
            Text(
                "Log your weight today to start one.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            last7Days.forEach { date ->
                val isLogged = loggedDates.contains(date)
                val isToday = date == today

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        date.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (isToday) FontWeight.Black else FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(
                                if (isLogged) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
                            )
                            .then(
                                if (isToday && !isLogged) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                                else Modifier
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isLogged) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = "Logged",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        } else {
                            Text(
                                date.dayOfMonth.toString(),
                                style = MaterialTheme.typography.labelMedium,
                                color = if (isToday) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
            }
        }
    }
}
