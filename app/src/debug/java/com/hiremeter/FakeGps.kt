package com.hiremeter

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.location.Criteria
import android.location.Location
import android.location.LocationManager
import android.os.SystemClock
import android.util.Log
import java.util.concurrent.Executors
import java.util.concurrent.Future

/**
 * Debug builds only: plays a made-up drive into the phone's GPS, so the meter, the speedometer and
 * everything after them can be tried at a desk. Android must first allow it:
 *
 *     adb shell appops set com.hiremeter.debug android:mock_location allow
 *
 * Then a script, one step a second:
 *
 *     adb shell am broadcast -n com.hiremeter.debug/com.hiremeter.FakeGps -a com.hiremeter.FAKE_GPS --es script "'drive 60 30; stop 150; tunnel 40 20; jump 5000; drive 40 10'"
 *
 * Steps: `goto <lat> <lon>`, `drive <km/h> <seconds>`, `stop <seconds>` (parked, with GPS wander), `tunnel <km/h> <seconds>`
 * (moving with no readings), `jump <metres>` (moved with no readings, e.g. while paused), `off`
 * (gives the real GPS back). A new script replaces one still playing.
 */
class FakeGps : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val script = intent.getStringExtra("script") ?: return
        play(context.applicationContext, script)
    }

    companion object {
        private const val TAG = "FakeGps"
        private const val PROVIDER = LocationManager.GPS_PROVIDER
        private val worker = Executors.newSingleThreadExecutor()
        private var playing: Future<*>? = null

        /**
         * Where the made-up drive is: it starts at Colombo Fort, heading north. Saved, so it carries on
         * from the same place after the app is killed, as a real vehicle would.
         */
        @Volatile private var lat = Double.NaN
        @Volatile private var lon = 79.8428
        private const val M_PER_DEG = 111_195.0

        fun play(ctx: Context, script: String) {
            playing?.cancel(true)
            playing = worker.submit {
                try {
                    run(ctx, script)
                } catch (e: InterruptedException) {
                    Log.i(TAG, "replaced by a new script")
                } catch (e: Exception) {
                    Log.e(TAG, "failed", e)
                }
            }
        }

        private fun run(ctx: Context, script: String) {
            val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val prefs = ctx.getSharedPreferences("fake_gps", Context.MODE_PRIVATE)
            if (lat.isNaN()) {
                lat = prefs.getFloat("lat", 6.9344f).toDouble()
                lon = prefs.getFloat("lon", 79.8428f).toDouble()
            }
            for (raw in script.split(';').map { it.trim() }.filter { it.isNotEmpty() }) {
                val p = raw.split(Regex("\\s+"))
                Log.i(TAG, "step: $raw")
                when (p[0]) {
                    "off" -> {
                        runCatching { lm.removeTestProvider(PROVIDER) }
                        Log.i(TAG, "real GPS back")
                        return
                    }
                    "drive" -> {
                        val mps = p[1].toDouble() / 3.6
                        repeat(p[2].toInt()) {
                            lat += mps / M_PER_DEG
                            send(lm, mps)
                            prefs.edit().putFloat("lat", lat.toFloat()).commit()
                            tick()
                        }
                    }
                    "stop" -> repeat(p[1].toInt()) { i ->
                        // A parked phone's GPS wanders a few metres either way, at speed 0.
                        val wobble = (if (i % 2 == 0) 4.0 else -4.0) / M_PER_DEG
                        send(lm, 0.0, latShift = wobble)
                        tick()
                    }
                    "tunnel" -> {
                        val mps = p[1].toDouble() / 3.6
                        repeat(p[2].toInt()) { lat += mps / M_PER_DEG; tick() }
                    }
                    "jump" -> { lat += p[1].toDouble() / M_PER_DEG; prefs.edit().putFloat("lat", lat.toFloat()).commit() }
                    "goto" -> {
                        lat = p[1].toDouble()
                        lon = p[2].toDouble()
                        prefs.edit().putFloat("lat", lat.toFloat()).putFloat("lon", lon.toFloat()).commit()
                    }
                    "home" -> { lat = 6.9344; prefs.edit().putFloat("lat", lat.toFloat()).commit() }
                    else -> Log.w(TAG, "unknown step: $raw")
                }
            }
            Log.i(TAG, "script done at $lat,$lon")
        }

        private fun tick() = Thread.sleep(1000)

        private fun send(lm: LocationManager, mps: Double, latShift: Double = 0.0) {
            ensureProvider(lm)
            val loc = Location(PROVIDER).apply {
                latitude = lat + latShift
                longitude = lon
                accuracy = 5f
                altitude = 5.0
                speed = mps.toFloat()
                bearing = 0f
                time = System.currentTimeMillis()
                elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
            }
            lm.setTestProviderLocation(PROVIDER, loc)
        }

        @Suppress("DEPRECATION")
        private fun ensureProvider(lm: LocationManager) {
            if (runCatching { lm.isProviderEnabled(PROVIDER) && lm.allProviders.contains(PROVIDER) }.getOrDefault(false) && added) return
            runCatching { lm.removeTestProvider(PROVIDER) }
            lm.addTestProvider(
                PROVIDER, false, false, false, false, true, true, true,
                Criteria.POWER_LOW, Criteria.ACCURACY_FINE,
            )
            lm.setTestProviderEnabled(PROVIDER, true)
            added = true
        }

        @Volatile private var added = false
    }
}
