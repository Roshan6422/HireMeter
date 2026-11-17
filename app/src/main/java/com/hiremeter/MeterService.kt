package com.hiremeter

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.Looper
import androidx.core.app.ServiceCompat

/** Keeps reading the GPS while a trip is running, also with the screen off or another app open. */
class MeterService : Service(), LocationListener {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        TripRepo.init(this)
        TrafficLights.load(this)
    }

    @SuppressLint("MissingPermission") // MainActivity only starts the service after the permission is granted
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        when (intent?.action) {
            ACTION_PAUSE -> {
                // The driver is away on other hires: the GPS goes off, nothing is counted.
                TripRepo.pause(System.currentTimeMillis())
                lm.removeUpdates(this)
                lm.removeUpdates(roughListener)
                showNotification(paused = true)
            }
            ACTION_RESUME -> {
                TripRepo.resume(System.currentTimeMillis())
                listen(lm)
                showNotification(paused = false)
            }
            ACTION_START -> {
                if (!goForeground(paused = false)) return START_NOT_STICKY
                TripRepo.start(System.currentTimeMillis(), RatesStore.freeStopMs(this), RatesStore.signalFreeMs(this))
                listen(lm)
            }
            else -> {
                // Started again by Android after the app was killed, or by the app after the phone
                // restarted: carry on the saved trip.
                TripRepo.restoreIfNeeded()
                val s = TripRepo.state.value
                if (!s.running || !goForeground(s.paused)) {
                    stopSelf()
                    return START_NOT_STICKY
                }
                if (!s.paused) listen(lm)
            }
        }
        // If Android kills the app during a trip, it starts this service again.
        return START_STICKY
    }

    /** False if Android does not allow it right now; the saved trip is carried on when the app opens. */
    private fun goForeground(paused: Boolean): Boolean {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0
        return try {
            ServiceCompat.startForeground(this, 1, notification(paused), type)
            true
        } catch (e: Exception) {
            false
        }
    }

    @SuppressLint("MissingPermission") // MainActivity only starts the service after the permission is granted
    private fun listen(lm: LocationManager) {
        // The permission can have been taken away while the app was stopped.
        try {
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, this, Looper.getMainLooper())
        } catch (e: SecurityException) {
            return
        }
        // The network position works indoors, where GPS often finds nothing. It only marks the place on the map.
        listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
            .firstNotNullOfOrNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            ?.let { TripRepo.onRoughPlace(LatLon(it.latitude, it.longitude)) }
        runCatching {
            lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 5000L, 0f, roughListener, Looper.getMainLooper())
        }
    }

    private fun showNotification(paused: Boolean) {
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(1, notification(paused))
    }

    private val roughListener = object : LocationListener {
        override fun onLocationChanged(loc: Location) = TripRepo.onRoughPlace(LatLon(loc.latitude, loc.longitude))
        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
    }

    override fun onLocationChanged(loc: Location) {
        TripRepo.onFix(
            Fix(
                lat = loc.latitude,
                lon = loc.longitude,
                accuracyM = if (loc.hasAccuracy()) loc.accuracy else Float.MAX_VALUE,
                speedMps = if (loc.hasSpeed()) loc.speed.toDouble() else null,
                timeMs = loc.elapsedRealtimeNanos / 1_000_000,
            ),
        )
    }

    // Abstract on Android 8 and 9, so they must exist even though they do nothing.
    @Deprecated("Deprecated in Java")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    override fun onProviderEnabled(provider: String) {}
    override fun onProviderDisabled(provider: String) {}

    override fun onDestroy() {
        val lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        lm.removeUpdates(this)
        lm.removeUpdates(roughListener)
        TripRepo.stop()
        super.onDestroy()
    }

    private fun notification(paused: Boolean): Notification {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Trip meter", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Builder(this, CHANNEL)
            .setContentTitle("HireMeter")
            .setContentText(if (paused) Tr.s.notifPaused else Tr.s.notifText)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(open)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val CHANNEL = "meter"
        const val ACTION_PAUSE = "com.hiremeter.PAUSE"
        const val ACTION_RESUME = "com.hiremeter.RESUME"
        const val ACTION_START = "com.hiremeter.START"
    }
}
