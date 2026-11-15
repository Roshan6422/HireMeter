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
 * March 2026). The other vehicles and all per-minute charges are estimates (PickMe has charged
 * about Rs. 4 a minute for a Mini); the driver sets their own on the prices screen.
 */
val DEFAULT_VEHICLES = listOf(
    Vehicle("tuk", TUK_ICON, Rates(110.0, 90.0, 2.0)),
    Vehicle("bike", "🏍️", Rates(60.0, 45.0, 1.0)),
    Vehicle("mini", "🚗", Rates(150.0, 100.0, 3.0)),
    Vehicle("flex", "🚗", Rates(160.0, 110.0, 3.0)),
    Vehicle("car", "🚗", Rates(180.0, 120.0, 4.0)),
    Vehicle("minivan", "🚐", Rates(200.0, 130.0, 5.0)),
    Vehicle("van", "🚐", Rates(220.0, 150.0, 6.0)),
)
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
                // "wait" (a waiting-only charge in older versions) means something else now, so it is not reused.
                v.copy(rates = Rates(o.getDouble("first"), o.getDouble("perKm"), o.optDouble("perMin", v.rates.perMin), o.optDouble("min", 0.0)))
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
                JSONObject().put("first", v.rates.firstKm).put("perKm", v.rates.perKm).put("perMin", v.rates.perMin)
                    .put("min", v.rates.minFare),
            )
        }
        prefs(ctx).edit().putString("vehicles", byId.toString()).apply()
    }


    /** The percentage last used to change all rates at once. */
    fun adjustPct(ctx: Context) = prefs(ctx).getFloat("adjustPct", 5f).toDouble()
    fun setAdjustPct(ctx: Context, v: Double) = prefs(ctx).edit().putFloat("adjustPct", v.toFloat()).apply()

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
    /** Charged for the trip time, per minute. */
    val timeFare: Int,
    /** Added so a short trip still cost the minimum fare. */
    val minimumTopUp: Int = 0,
    /** Time the meter was paused (the driver away on other hires). Not charged. */
    val pausedMs: Long = 0,
) {
    val total: Int get() = distanceFare + timeFare + minimumTopUp
}

object HistoryStore {
    private const val MAX_TRIPS = 300
    private fun prefs(ctx: Context) = ctx.getSharedPreferences("history", Context.MODE_PRIVATE)

    /** Newest first. */
    fun load(ctx: Context): List<Trip> {
        return try {
            val arr = JSONArray(prefs(ctx).getString("trips", "[]"))
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Trip(
                    o.getLong("t"), o.getString("v"), o.getDouble("d"),
                    o.getLong("dur"), o.getInt("df"), o.getInt("wf"), o.optInt("mf", 0), o.optLong("p", 0L),
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun add(ctx: Context, trip: Trip) {
        val arr = JSONArray()
        for (t in (listOf(trip) + load(ctx)).take(MAX_TRIPS)) {
            arr.put(
                JSONObject().put("t", t.endedAtMs).put("v", t.vehicle).put("d", t.distanceM)
                    .put("dur", t.durationMs).put("df", t.distanceFare).put("wf", t.timeFare).put("mf", t.minimumTopUp).put("p", t.pausedMs),
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
) {
    val paused: Boolean get() = pausedAtMs != null

    /** Time paused so far, including a pause still going on. */
    fun pausedMs(nowMs: Long): Long = pausedTotalMs + (pausedAtMs?.let { maxOf(0L, nowMs - it) } ?: 0L)

    /** Time the meter has been running: what is charged. */
    fun activeMs(nowMs: Long): Long = maxOf(0L, nowMs - startedAtMs - pausedMs(nowMs))
}

/** The trip in progress. Filled by [MeterService], shown by the screen. */
object TripRepo {
    private val _state = MutableStateFlow(TripState())
    val state: StateFlow<TripState> = _state
    private var tracker = TripTracker()
    /** Where the phone roughly is, from WiFi / mobile network. Only for the bill's map, never for the fare. */
    @Volatile private var roughPlace: LatLon? = null

    fun start(nowMs: Long) {
        tracker = TripTracker()
        roughPlace = null
        _state.value = TripState(running = true, startedAtMs = nowMs)
    }

    /** Stops counting distance and time until [resume], e.g. while the driver does other hires. */
    fun pause(nowMs: Long) {
        val s = _state.value
        if (!s.running || s.paused) return
        _state.value = s.copy(pausedAtMs = nowMs)
    }

    fun resume(nowMs: Long) {
        val s = _state.value
        val at = s.pausedAtMs ?: return
        tracker.newPiece()
        _state.value = s.copy(pausedAtMs = null, pausedTotalMs = s.pausedTotalMs + maxOf(0L, nowMs - at), hasGps = false)
    }

    fun onFix(fix: Fix) {
        if (!_state.value.running || _state.value.paused) return
        tracker.onFix(fix)
        _state.value = _state.value.copy(
            distanceM = tracker.distanceM,
            hasGps = _state.value.hasGps || fix.accuracyM <= TripTracker.MAX_ACCURACY_M,
        )
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

    /** Keeps the totals so the screen can still show the finished trip. */
    fun stop() {
        _state.value = _state.value.copy(running = false)
    }
}
