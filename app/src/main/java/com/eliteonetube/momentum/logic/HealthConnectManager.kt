package com.eliteonetube.momentum.logic

import android.content.Context
import androidx.activity.result.contract.ActivityResultContract
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.health.connect.client.aggregate.AggregateMetric
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

class HealthConnectManager(private val context: Context) {
    val healthConnectClient by lazy { HealthConnectClient.getOrCreate(context) }

    val permissions = setOf(
        HealthPermission.getReadPermission(StepsRecord::class)
    )

    fun getAvailabilityStatus(): Int {
        return HealthConnectClient.getSdkStatus(context)
    }

    suspend fun revokeAllPermissions() {
        healthConnectClient.permissionController.revokeAllPermissions()
    }

    fun isAvailable(): Boolean {
        return getAvailabilityStatus() == HealthConnectClient.SDK_AVAILABLE
    }

    suspend fun hasAllPermissions(): Boolean {
        val granted = healthConnectClient.permissionController.getGrantedPermissions()
        return granted.containsAll(permissions)
    }

    fun requestPermissionsContract(): ActivityResultContract<Set<String>, Set<String>> {
        return PermissionController.createRequestPermissionResultContract()
    }

    suspend fun fetchAverageStepsLast7Days(): Int? {
        if (!isAvailable() || !hasAllPermissions()) return null

        try {
            val now = Instant.now()
            val startTime = now.minus(7, ChronoUnit.DAYS)

            val metrics = setOf(StepsRecord.COUNT_TOTAL)
            val response = healthConnectClient.aggregate(
                AggregateRequest(
                    metrics = metrics,
                    timeRangeFilter = TimeRangeFilter.between(startTime, now)
                )
            )

            val totalSteps = response[StepsRecord.COUNT_TOTAL] ?: return 0
            return (totalSteps / 7).toInt()
        } catch (e: Exception) {
            return null
        }
    }

    companion object {
        const val ACTION_MANAGE_PERMISSIONS = "androidx.health.connect.action.MANAGE_HEALTH_PERMISSIONS"
    }
}