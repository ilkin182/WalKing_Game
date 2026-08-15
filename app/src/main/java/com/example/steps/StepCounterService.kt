package com.example.steps

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.data.local.StepCounterState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Foreground service that keeps counting steps via the hardware step-counter sensor while the
 * app is backgrounded. TYPE_STEP_COUNTER reports a cumulative total since the last device boot,
 * so the running app step count is derived as (rawSensorTotal - baseline), where baseline is
 * persisted so the count survives process death and is re-based on device reboot (the sensor
 * resets to 0 then).
 */
class StepCounterService : Service(), SensorEventListener {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Default + serviceJob)
    private val stepMutex = Mutex()

    private lateinit var sensorManager: SensorManager
    private var stepCounterSensor: Sensor? = null
    private lateinit var prefs: SharedPreferences


    override fun onCreate() {
        super.onCreate()
        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        stepCounterSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)

        // Publish the last known count immediately so the UI isn't stuck at 0 while we wait
        // for the sensor to deliver its first (possibly batched/delayed) reading.
        StepCounterState.updateStepCount(prefs.getInt(KEY_STEP_COUNT, 0))

        startForeground(NOTIFICATION_ID, buildNotification())

        stepCounterSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
        StepCounterState.setTracking(true)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onSensorChanged(event: SensorEvent?) {
        val rawTotalSteps = event?.values?.getOrNull(0) ?: return
        serviceScope.launch {
            stepMutex.withLock {
                processStepReading(rawTotalSteps)
            }
        }
    }

    private fun processStepReading(rawTotalSteps: Float) {
        val savedBaseline = prefs.getFloat(KEY_RAW_BASELINE, -1f)
        val savedStepCount = prefs.getInt(KEY_STEP_COUNT, 0)

        val baseline = when {
            savedBaseline < 0f -> rawTotalSteps // first ever reading on this device
            rawTotalSteps < savedBaseline -> rawTotalSteps - savedStepCount // device rebooted
            else -> savedBaseline
        }

        val newStepCount = (rawTotalSteps - baseline).toInt().coerceAtLeast(0)

        prefs.edit()
            .putFloat(KEY_RAW_BASELINE, baseline)
            .putInt(KEY_STEP_COUNT, newStepCount)
            .apply()

        StepCounterState.updateStepCount(newStepCount)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onTaskRemoved(rootIntent: Intent?) {
        // App swiped away from recents: treat as "fully closed" and stop background counting.
        super.onTaskRemoved(rootIntent)
        stopSelf()
    }

    override fun onDestroy() {
        sensorManager.unregisterListener(this)
        StepCounterState.setTracking(false)
        serviceJob.cancel()
        super.onDestroy()
    }

    private fun buildNotification(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Addım Sayğacı",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Arxa planda addım izlənməsi"
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Stomped addımlarınızı sayır")
            .setContentText("Arxa planda addım sayğacı aktivdir")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setContentIntent(contentIntent)
            .build()
    }

    companion object {
        private const val NOTIFICATION_ID = 42
        private const val CHANNEL_ID = "step_counter_channel"
        private const val PREFS_NAME = "step_counter_prefs"
        private const val KEY_RAW_BASELINE = "raw_baseline"
        private const val KEY_STEP_COUNT = "step_count"

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, StepCounterService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, StepCounterService::class.java))
        }
    }
}
