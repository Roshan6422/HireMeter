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

    @SuppressLint("MissingPermission") // MainActivity only starts the service after the permission is granted
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0
        ServiceCompat.startForeground(this, 1, notification(), type)
        TripRepo.start(System.currentTimeMillis())
        val lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, this, Looper.getMainLooper())
        // The network position works indoors, where GPS often finds nothing. It only marks the place on the map.
        listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
            .firstNotNullOfOrNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            ?.let { TripRepo.onRoughPlace(LatLon(it.latitude, it.longitude)) }
        runCatching {
            lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 5000L, 0f, roughListener, Looper.getMainLooper())
        }
        return START_NOT_STICKY
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

    private fun notification(): Notification {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Trip meter", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Builder(this, CHANNEL)
            .setContentTitle("HireMeter")
            .setContentText(Tr.s.notifText)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(open)
            .setOngoing(true)
            .build()
    }

    private companion object {
        const val CHANNEL = "meter"
    }
}
