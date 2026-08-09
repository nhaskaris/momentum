package com.eliteonetube.momentum.logic

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.*
import androidx.core.app.NotificationCompat
import com.eliteonetube.momentum.MainActivity
import com.eliteonetube.momentum.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class RestTimerService : Service() {

    companion object {
        private const val NOTIFICATION_ID = 2001
        private const val CHANNEL_ID = "rest_timer_channel"

        private val _timeLeft = MutableStateFlow(0L)
        val timeLeft = _timeLeft.asStateFlow()

        private val _isActive = MutableStateFlow(false)
        val isActive = _isActive.asStateFlow()

        fun startTimer(context: Context, seconds: Long) {
            val intent = Intent(context, RestTimerService::class.java).apply {
                action = "START"
                putExtra("SECONDS", seconds)
            }
            context.startForegroundService(intent)
        }

        fun stopTimer(context: Context) {
            val intent = Intent(context, RestTimerService::class.java).apply {
                action = "STOP"
            }
            context.startService(intent)
        }

        fun adjustTimer(context: Context, deltaSeconds: Long) {
            val intent = Intent(context, RestTimerService::class.java).apply {
                action = "ADJUST"
                putExtra("DELTA", deltaSeconds)
            }
            context.startService(intent)
        }
    }

    private var timer: CountDownTimer? = null
    private var currentSecondsRemaining = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "START" -> {
                val seconds = intent.getLongExtra("SECONDS", 120L)
                startNewTimer(seconds)
            }
            "STOP" -> {
                stopSelf()
            }
            "ADJUST" -> {
                val delta = intent.getLongExtra("DELTA", 0L)
                val newTime = (currentSecondsRemaining + delta).coerceAtLeast(0L)
                startNewTimer(newTime)
            }
        }
        return START_NOT_STICKY
    }

    private fun startNewTimer(seconds: Long) {
        timer?.cancel()
        currentSecondsRemaining = seconds
        _isActive.value = true
        
        startForeground(NOTIFICATION_ID, createNotification(seconds))

        timer = object : CountDownTimer(seconds * 1000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                currentSecondsRemaining = millisUntilFinished / 1000
                _timeLeft.value = currentSecondsRemaining
                updateNotification(currentSecondsRemaining)
            }

            override fun onFinish() {
                currentSecondsRemaining = 0
                _timeLeft.value = 0
                
                // Update persistent notification to show completion
                updateNotification(0, true)
                
                // Show high-priority "Get back to your workout" notification
                NotificationHelper.showRestFinishedNotification(this@RestTimerService)
                
                vibrate()
                
                // Keep the service alive for 5 more seconds so UI shows "Finished"
                object : CountDownTimer(5000, 5000) {
                    override fun onTick(millisUntilFinished: Long) {}
                    override fun onFinish() {
                        _isActive.value = false
                        stopSelf()
                    }
                }.start()
            }
        }.start()
    }

    private fun createNotification(seconds: Long, finished: Boolean = false): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Workout Rest Timer",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)

        val intent = Intent(this, MainActivity::class.java).apply {
            action = MainActivity.ACTION_OPEN_ACTIVE_WORKOUT
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val timeStr = if (finished) "Rest Finished!" else formatTime(seconds)
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Rest Timer")
            .setContentText(timeStr)
            .setOngoing(!finished)
            .setContentIntent(pendingIntent)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun updateNotification(seconds: Long, finished: Boolean = false) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, createNotification(seconds, finished))
    }

    private fun formatTime(seconds: Long): String {
        val mins = seconds / 60
        val secs = seconds % 60
        return "%d:%02d remaining".format(mins, secs)
    }

    private fun vibrate() {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(VIBRATOR_SERVICE) as Vibrator
        }

        vibrator.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    override fun onDestroy() {
        timer?.cancel()
        _isActive.value = false
        _timeLeft.value = 0
        super.onDestroy()
    }
}
