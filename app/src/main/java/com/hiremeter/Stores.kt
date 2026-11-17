package com.hiremeter

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** [icon] is an emoji picture of the vehicle; its name comes from [Strings.vehicleName]. */
data class Vehicle(val id: String, val icon: String, val rates: Rates)

// The three-wheeler emoji only exists on Android 10 and newer; older phones get a taxi.
private val TUK_ICON = if (Build.VERSION.SDK_INT >= 29) "🛺" else "🚖"

/**
 * Starting prices. Three-wheeler: Rs. 110 first km, Rs. 90 after (Western Province revision of
 * March 2026). The other vehicles and all waiting charges are estimates; the driver sets their
 * own on the prices screen.
 */
val DEFAULT_VEHICLES = listOf(
    Vehicle("tuk", TUK_ICON, Rates(110.0, 90.0, waitPerMin = 2.0)),
    Vehicle("bike", "🏍️", Rates(60.0, 45.0, waitPerMin = 1.0)),
    Vehicle("mini", "🚗", Rates(150.0, 100.0, waitPerMin = 3.0)),
    Vehicle("flex", "🚗", Rates(160.0, 110.0, waitPerMin = 3.0)),
    Vehicle("car", "🚗", Rates(180.0, 120.0, waitPerMin = 4.0)),
    Vehicle("minivan", "🚐", Rates(200.0, 130.0, waitPerMin = 5.0)),
    Vehicle("van", "🚐", Rates(220.0, 150.0, waitPerMin = 6.0)),
)
/** Choices on the settings screen that change how the meter works. */
object AppSettings {
    private fun prefs(ctx: Context) = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)

    /**
     * Shows the button that stops the meter while the driver goes on Uber / PickMe hires in the
     * middle of this one (an up-and-down hire). Off unless the driver turns it on, so it cannot be
     * pressed by mistake on an ordinary hire.
     */
    fun otherHiresButton(ctx: Context) = prefs(ctx).getBoolean("otherHires", false)
    fun setOtherHiresButton(ctx: Context, on: Boolean) = prefs(ctx).edit().putBoolean("otherHires", on).apply()
}

/** The largest change the "change all rates" box accepts in one press. */
const val MAX_ADJUST_PCT = 50.0

object RatesStore {
    private fun prefs(ctx: Context) = ctx.getSharedPreferences("rates", Context.MODE_PRIVATE)

    fun load(ctx: Context): List<Vehicle> {
        val saved = prefs(ctx).getString("vehicles", null) ?: return DEFAULT_VEHICLES
        return try {
            val byId = JSONObject(saved)
            DEFAULT_VEHICLES.map { v ->
                val o = byId.optJSONObject(v.id) ?: return@map v
                // "wait" (from older versions) and "perMin" (the per-minute trip charge of 1.3) are not
                // used any more; "pauseWait" is the waiting rate.
                v.copy(rates = Rates(o.getDouble("first"), o.getDouble("perKm"), o.optDouble("pauseWait", v.rates.waitPerMin), o.optDouble("min", 0.0)))
            }
        } catch (e: Exception) {
            DEFAULT_VEHICLES
        }
    }

    fun save(ctx: Context, vehicles: List<Vehicle>) {
        val byId = JSONObject()
        for (v in vehicles) {
            byId.put(
                v.id,
                JSONObject().put("first", v.rates.firstKm).put("perKm", v.rates.perKm)
                    .put("min", v.rates.minFare).put("pauseWait", v.rates.waitPerMin),
            )
        }
        prefs(ctx).edit().putString("vehicles", byId.toString()).apply()
    }


    /** The percentage last used to change all rates at once. */
    fun adjustPct(ctx: Context) = prefs(ctx).getFloat("adjustPct", 5f).toDouble()
    fun setAdjustPct(ctx: Context, v: Double) = prefs(ctx).edit().putFloat("adjustPct", v.toFloat()).apply()

    /** How long a stop may last before it is waiting (a traffic light, a jam), in minutes. */
    fun freeStopMin(ctx: Context) = prefs(ctx).getFloat("freeStopMin", (TripTracker.DEFAULT_FREE_STOP_MS / 60_000.0).toFloat()).toDouble()
    fun setFreeStopMin(ctx: Context, v: Double) = prefs(ctx).edit().putFloat("freeStopMin", v.toFloat()).apply()
    fun freeStopMs(ctx: Context) = (freeStopMin(ctx) * 60_000).toLong()

    /** How long a stop at a traffic light may last before it is waiting, in minutes. */
    fun signalFreeMin(ctx: Context) = prefs(ctx).getFloat("signalFreeMin", (TripTracker.DEFAULT_SIGNAL_FREE_MS / 60_000.0).toFloat()).toDouble()
    fun setSignalFreeMin(ctx: Context, v: Double) = prefs(ctx).edit().putFloat("signalFreeMin", v.toFloat()).apply()
    fun signalFreeMs(ctx: Context) = (signalFreeMin(ctx) * 60_000).toLong()

    fun selectedId(ctx: Context) = prefs(ctx).getString("selected", "tuk") ?: "tuk"
    fun setSelectedId(ctx: Context, id: String) = prefs(ctx).edit().putString("selected", id).apply()
}

/**
 * How customers pay the driver without cash: a picture of the driver's own payment QR (the one their
 * bank or payment app gave them) and a few lines of account details. The app only shows these; the
 * money moves in the customer's banking app.
 */
object PayStore {
    private fun prefs(ctx: Context) = ctx.getSharedPreferences("pay", Context.MODE_PRIVATE)
    private fun qrFile(ctx: Context) = File(ctx.filesDir, "pay_qr")

    fun details(ctx: Context) = prefs(ctx).getString("details", "") ?: ""
    fun setDetails(ctx: Context, v: String) = prefs(ctx).edit().putString("details", v).apply()

    fun hasQr(ctx: Context) = qrFile(ctx).exists()
    fun removeQr(ctx: Context) { qrFile(ctx).delete() }

    /** Copies the picked picture into the app. False if it could not be read as a picture. */
    fun saveQr(ctx: Context, uri: Uri): Boolean {
        return try {
            ctx.contentResolver.openInputStream(uri)!!.use { input ->
                qrFile(ctx).outputStream().use { input.copyTo(it) }
            }
            if (loadQr(ctx) == null) { removeQr(ctx); false } else true
        } catch (e: Exception) {
            removeQr(ctx)
            false
        }
    }

    /** The QR picture, shrunk so a full-size camera photo does not fill the memory. */
    fun loadQr(ctx: Context): Bitmap? {
        val path = qrFile(ctx).takeIf { it.exists() }?.path ?: return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 1600) sample *= 2
        return BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
    }
}

data class Trip(
    val endedAtMs: Long,
    val vehicle: String,
    val distanceM: Double,
    val durationMs: Long,
    val distanceFare: Int,
    /** Added so a short trip still cost the minimum fare. */
    val minimumTopUp: Int = 0,
    /** Time the meter was paused (the driver away on other hires). */
    val pausedMs: Long = 0,
    /** Charged for waiting: long stops, and the paused time when the driver chose to charge it. */
    val waitingFare: Int = 0,
    /** The waiting that was charged. */
    val waitedMs: Long = 0,
    /** The paused time was charged as waiting. */
    val pauseCharged: Boolean = false,
    /** Charged per minute of trip time; only trips from version 1.3 have it. */
    val timeFare: Int = 0,
    /** A few points of the path driven, enough for a Google Maps link (see [shortRoute]). */
    val route: List<LatLon> = emptyList(),
) {
    val total: Int get() = distanceFare + timeFare + minimumTopUp + waitingFare
}

/** Trips are kept this long, then deleted by themselves. */
const val KEEP_TRIP_DAYS = 40
private const val KEEP_TRIP_MS = KEEP_TRIP_DAYS * 24 * 3600_000L

/** The trips not older than [KEEP_TRIP_DAYS] at [nowMs]. */
fun recentTrips(trips: List<Trip>, nowMs: Long): List<Trip> = trips.filter { nowMs - it.endedAtMs < KEEP_TRIP_MS }

/**
 * At most [max] points of [route], evenly spread and always with both ends, so a trip's path can
 * be kept in the trip list without filling the phone.
 */
fun shortRoute(route: List<LatLon>, max: Int = 25): List<LatLon> {
    if (route.size <= max) return route
    return (0 until max).map { route[(it.toLong() * (route.size - 1) / (max - 1)).toInt()] }
}

object HistoryStore {
    /** Only so a very busy driver cannot fill the phone; 40 days is the usual limit. */
    private const val MAX_TRIPS = 2000
    private fun prefs(ctx: Context) = ctx.getSharedPreferences("history", Context.MODE_PRIVATE)

    /** Newest first. Trips older than [KEEP_TRIP_DAYS] are deleted here. */
    fun load(ctx: Context, nowMs: Long = System.currentTimeMillis()): List<Trip> {
        val all = read(ctx)
        val kept = recentTrips(all, nowMs)
        if (kept.size != all.size) write(ctx, kept)
        return kept
    }

    private fun read(ctx: Context): List<Trip> {
        return try {
            val arr = JSONArray(prefs(ctx).getString("trips", "[]"))
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                val pausedMs = o.optLong("p", 0L)
                val waitingFare = o.optInt("pw", 0)
                Trip(
                    endedAtMs = o.getLong("t"), vehicle = o.getString("v"), distanceM = o.getDouble("d"),
                    durationMs = o.getLong("dur"), distanceFare = o.getInt("df"), minimumTopUp = o.optInt("mf", 0),
                    pausedMs = pausedMs, waitingFare = waitingFare,
                    // Before "w" was kept, the only waiting charged was the paused time.
                    waitedMs = o.optLong("w", if (waitingFare > 0) pausedMs else 0L),
                    pauseCharged = o.optBoolean("pc", waitingFare > 0 && pausedMs > 0),
                    timeFare = o.optInt("wf", 0),
                    route = o.optJSONArray("r")?.let { r -> (0 until r.length() / 2).map { LatLon(r.getDouble(2 * it), r.getDouble(2 * it + 1)) } }
                        ?: emptyList(),
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun add(ctx: Context, trip: Trip) = write(ctx, listOf(trip) + load(ctx))

    /** Deletes one trip; the driver chose it in the trip list. */
    fun delete(ctx: Context, trip: Trip) = write(ctx, load(ctx).filterNot { it.endedAtMs == trip.endedAtMs })

    private fun write(ctx: Context, trips: List<Trip>) {
        val arr = JSONArray()
        for (t in trips.take(MAX_TRIPS)) {
            val r = JSONArray()
            // About a metre is close enough for a map link.
            for (p in t.route) r.put(Math.round(p.lat * 1e5) / 1e5).put(Math.round(p.lon * 1e5) / 1e5)
            arr.put(
                JSONObject().put("t", t.endedAtMs).put("v", t.vehicle).put("d", t.distanceM)
                    .put("dur", t.durationMs).put("df", t.distanceFare).put("wf", t.timeFare).put("mf", t.minimumTopUp).put("p", t.pausedMs).put("pw", t.waitingFare)
                    .put("w", t.waitedMs).put("pc", t.pauseCharged).put("r", r),
            )
        }
        prefs(ctx).edit().putString("trips", arr.toString()).apply()
    }

    fun clear(ctx: Context) = prefs(ctx).edit().remove("trips").apply()
}

data class TripState(
    val running: Boolean = false,
    val startedAtMs: Long = 0,
    val distanceM: Double = 0.0,
    /** False until the first usable GPS reading of this trip (or since resuming) arrives. */
    val hasGps: Boolean = false,
    /** When the meter was paused, or null while it runs. */
    val pausedAtMs: Long? = null,
    /** All earlier pauses of this trip together. */
    val pausedTotalMs: Long = 0,
    /** The driver chose to charge the paused time as waiting. */
    val chargePaused: Boolean = false,
    /** Waiting counted from long stops while driving (not the paused time). */
    val stopWaitMs: Long = 0,
    /** How long the vehicle has been standing still now; 0 while it moves. */
    val stoppedMs: Long = 0,
    /** How long the stop going on now may last before it is waiting. */
    val stopFreeMs: Long = 0,
    /** The stop going on now is at a known traffic light. */
    val stopAtLight: Boolean = false,
    /** The driver said the stop going on now is at a traffic light: it is not charged. */
    val stopMarkedLight: Boolean = false,
) {
    val paused: Boolean get() = pausedAtMs != null

    /** Time paused so far, including a pause still going on. */
    fun pausedMs(nowMs: Long): Long = pausedTotalMs + (pausedAtMs?.let { maxOf(0L, nowMs - it) } ?: 0L)

    /** Time the meter has been running. */
    fun activeMs(nowMs: Long): Long = maxOf(0L, nowMs - startedAtMs - pausedMs(nowMs))

    /** All waiting to charge: long stops, and the paused time when the driver chose to charge it. */
    fun waitedMs(nowMs: Long): Long = stopWaitMs + if (chargePaused) pausedMs(nowMs) else 0L
}

/** Where the trip in progress is kept on the phone. Null clears it. */
interface TripStorage {
    fun read(): String?
    fun write(json: String?)
}

/**
 * The trip in progress. Filled by [MeterService], shown by the screen. It is saved to the phone as
 * it goes, so a trip survives the app being killed or the phone restarting: [restoreIfNeeded]
 * carries it on.
 */
object TripRepo {
    private val _state = MutableStateFlow(TripState())
    val state: StateFlow<TripState> = _state
    private var tracker = TripTracker()
    /** Where the phone roughly is, from WiFi / mobile network. Only for the bill's map, never for the fare. */
    @Volatile private var roughPlace: LatLon? = null
    private var storage: TripStorage? = null
    private var fixesSinceSave = 0

    fun init(storage: TripStorage) {
        this.storage = storage
    }

    fun init(ctx: Context) {
        if (storage != null) return
        val prefs = ctx.applicationContext.getSharedPreferences("trip", Context.MODE_PRIVATE)
        init(object : TripStorage {
            override fun read() = prefs.getString("trip", null)
            override fun write(json: String?) {
                prefs.edit().apply { if (json == null) remove("trip") else putString("trip", json) }.apply()
            }
        })
    }

    private fun save() {
        val st = storage ?: return
        val s = _state.value
        if (!s.running) return
        val route = JSONArray()
        for (p in tracker.route) route.put(p.lat).put(p.lon)
        val o = JSONObject()
            .put("start", s.startedAtMs).put("dist", s.distanceM)
            .put("pausedAt", s.pausedAtMs ?: -1L).put("pausedTotal", s.pausedTotalMs).put("charge", s.chargePaused)
            .put("waited", tracker.waitedMs).put("freeStop", tracker.freeStopMs).put("signalFree", tracker.signalFreeMs)
            .put("route", route).put("breaks", JSONArray(tracker.routeBreaks))
        tracker.lastPlace?.let { o.put("lastLat", it.lat).put("lastLon", it.lon) }
        st.write(o.toString())
        fixesSinceSave = 0
    }

    /**
     * When a saved trip is running but this one is not (the app was killed, the phone restarted),
     * carries the saved one on. True if it did; the caller then starts the meter service again.
     */
    fun restoreIfNeeded(): Boolean {
        if (_state.value.running) return false
        val json = storage?.read() ?: return false
        return try {
            val o = JSONObject(json)
            val flat = o.getJSONArray("route")
            val route = (0 until flat.length() / 2).map { LatLon(flat.getDouble(2 * it), flat.getDouble(2 * it + 1)) }
            val br = o.getJSONArray("breaks")
            val last = if (o.has("lastLat")) LatLon(o.getDouble("lastLat"), o.getDouble("lastLon")) else null
            val freeStop = o.optLong("freeStop", TripTracker.DEFAULT_FREE_STOP_MS)
            val signalFree = o.optLong("signalFree", TripTracker.DEFAULT_SIGNAL_FREE_MS)
            tracker = TripTracker(freeStop, signalFreeMs = signalFree, isTrafficLight = TrafficLights::near).apply {
                restore(o.getDouble("dist"), route, (0 until br.length()).map { br.getInt(it) }, last, o.optLong("waited", 0L))
            }
            roughPlace = null
            val pausedAt = o.getLong("pausedAt")
            _state.value = TripState(
                running = true,
                startedAtMs = o.getLong("start"),
                distanceM = o.getDouble("dist"),
                pausedAtMs = if (pausedAt >= 0) pausedAt else null,
                pausedTotalMs = o.getLong("pausedTotal"),
                chargePaused = o.getBoolean("charge"),
                stopWaitMs = tracker.waitedMs,
            )
            true
        } catch (e: Exception) {
            storage?.write(null)
            false
        }
    }

    /** [freeStopMs], [signalFreeMs]: stops shorter than these are not waiting (see [TripTracker]). */
    fun start(
        nowMs: Long,
        freeStopMs: Long = TripTracker.DEFAULT_FREE_STOP_MS,
        signalFreeMs: Long = TripTracker.DEFAULT_SIGNAL_FREE_MS,
    ) {
        tracker = TripTracker(freeStopMs, signalFreeMs = signalFreeMs, isTrafficLight = TrafficLights::near)
        roughPlace = null
        _state.value = TripState(running = true, startedAtMs = nowMs)
        save()
    }

    /** Stops counting distance and time until [resume], e.g. while the driver does other hires. */
    fun pause(nowMs: Long) {
        val s = _state.value
        if (!s.running || s.paused) return
        // A stop going on now ends here; the paused time is charged (or not) on its own.
        tracker.endStop()
        _state.value = s.copy(pausedAtMs = nowMs, stopWaitMs = tracker.waitedMs, stoppedMs = 0)
        save()
    }

    fun setChargePaused(on: Boolean) {
        _state.value = _state.value.copy(chargePaused = on)
        save()
    }

    fun resume(nowMs: Long) {
        val s = _state.value
        val at = s.pausedAtMs ?: return
        tracker.newPiece()
        _state.value = s.copy(
            pausedAtMs = null, pausedTotalMs = s.pausedTotalMs + maxOf(0L, nowMs - at), hasGps = false,
            stopWaitMs = tracker.waitedMs, stoppedMs = 0,
        )
        save()
    }

    fun onFix(fix: Fix) {
        if (!_state.value.running || _state.value.paused) return
        tracker.onFix(fix)
        _state.value = _state.value.copy(
            distanceM = tracker.distanceM,
            stopWaitMs = tracker.waitedMs,
            stoppedMs = tracker.currentStopMs,
            stopFreeMs = tracker.currentFreeMs,
            stopAtLight = tracker.stopAtLight,
            stopMarkedLight = tracker.stopMarkedLight,
            hasGps = _state.value.hasGps || fix.accuracyM <= TripTracker.MAX_ACCURACY_M,
        )
        // Saved every few readings: at most a few seconds are lost if the app is killed.
        if (++fixesSinceSave >= 5) save()
    }

    /**
     * The driver says the vehicle is stopped at a traffic light: this stop is not charged. Returns
     * where it is, to remember the light; null when the vehicle is not stopped.
     */
    fun markStopAsLight(): LatLon? {
        val place = tracker.markStopAsLight() ?: return null
        _state.value = _state.value.copy(
            stopWaitMs = tracker.waitedMs, stopFreeMs = tracker.currentFreeMs, stopMarkedLight = true,
        )
        save()
        return place
    }

    fun onRoughPlace(place: LatLon) {
        roughPlace = place
    }

    /**
     * A copy of the path driven so far. When the GPS has not found the phone yet (indoors, under a
     * roof) it is the rough place instead, so the bill can still show where the trip was.
     */
    fun routeSnapshot(): List<LatLon> = tracker.route.toList().ifEmpty { listOfNotNull(roughPlace) }

    /** Where the route snapshot's pieces start after each pause. */
    fun routeBreaksSnapshot(): List<Int> = tracker.routeBreaks.toList()

    /** The meter stopped (the service went away). The saved trip stays, to be carried on. */
    fun stop() {
        _state.value = _state.value.copy(running = false)
    }

    /** The driver ended the trip: nothing is left to carry on. */
    fun finish() {
        _state.value = _state.value.copy(running = false)
        storage?.write(null)
    }
}
