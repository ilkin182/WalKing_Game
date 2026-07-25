package com.example.location

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.data.local.LocationTrackingState
import com.example.data.mapper.toDomain
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority

/**
 * Foreground service that keeps requesting GPS updates while the app is backgrounded or the
 * screen is locked. Being an active foreground service (type="location") exempts the app from
 * Android's background-location throttling, so hex-stomping keeps registering the areas the user
 * walks through without needing the separate ACCESS_BACKGROUND_LOCATION permission - mirrors the
 * approach already used by StepCounterService for step counting.
 */
class LocationTrackingService : Service() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var locationCallback: LocationCallback? = null

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        startForeground(NOTIFICATION_ID, buildNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val intervalMs = intent?.getLongExtra(EXTRA_INTERVAL_MS, DEFAULT_INTERVAL_MS) ?: DEFAULT_INTERVAL_MS
        if (locationCallback == null) {
            registerLocationUpdates(intervalMs)
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    @SuppressLint("MissingPermission")
    private fun registerLocationUpdates(intervalMs: Long) {
        val hasFinePermission = ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCoarsePermission = ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

        if (!hasFinePermission && !hasCoarsePermission) {
            LocationTrackingState.updateError("Məkan icazəsi verilmədi. GPS izlənməsi mümkün deyil.")
            stopSelf()
            return
        }

        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, intervalMs)
            .setMinUpdateIntervalMillis(intervalMs / 2)
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { LocationTrackingState.updateLocation(it.toDomain()) }
            }
        }

        try {
            LocationTrackingState.updateError(null)
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback!!, Looper.getMainLooper())
                .addOnFailureListener { e ->
                    LocationTrackingState.updateError("Məkan xidmətinə qoşulmaq mümkün olmadı: ${e.message}")
                    locationCallback = null
                }
        } catch (e: Exception) {
            LocationTrackingState.updateError("Məkan xidmətinə qoşulmaq mümkün olmadı: ${e.message}")
            locationCallback = null
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // App swiped away from recents: treat as "fully closed" and stop background tracking.
        super.onTaskRemoved(rootIntent)
        stopSelf()
    }

    override fun onDestroy() {
        locationCallback?.let { fusedLocationClient.removeLocationUpdates(it) }
        locationCallback = null
        super.onDestroy()
    }

    private fun buildNotification(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Məkan İzləmə",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Arxa planda məkan izlənməsi"
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
            .setContentTitle("Stomped ərazinizi izləyir")
            .setContentText("Arxa planda məkan izlənməsi aktivdir")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setContentIntent(contentIntent)
            .build()
    }

    companion object {
        private const val NOTIFICATION_ID = 43
        private const val CHANNEL_ID = "location_tracking_channel"
        private const val EXTRA_INTERVAL_MS = "interval_ms"
        private const val DEFAULT_INTERVAL_MS = 3000L

        fun start(context: Context, intervalMs: Long) {
            val intent = Intent(context, LocationTrackingService::class.java)
                .putExtra(EXTRA_INTERVAL_MS, intervalMs)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, LocationTrackingService::class.java))
        }
    }
}