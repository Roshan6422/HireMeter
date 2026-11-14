package com.hiremeter

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** Prices for one vehicle type, in rupees. [minFare] 0 means there is no minimum fare. */
data class Rates(
    val firstKm: Double,
    val perKm: Double,
    val waitPerMin: Double,
    val minFare: Double = 0.0,
)

/** [minimumTopUp] is what is added so a short trip still costs the minimum fare. */
data class FareBreakdown(val distanceFare: Int, val waitingFare: Int, val minimumTopUp: Int = 0) {
    val total: Int get() = distanceFare + waitingFare + minimumTopUp
}

/**
 * The first kilometre (or any part of it) costs [Rates.firstKm]; every kilometre after it costs
 * [Rates.perKm], charged by the metre. Waiting is charged by the second after [freeWaitMin] free minutes.
 * However short the trip, the total is never less than [Rates.minFare].
 */
fun calculateFare(distanceM: Double, waitingMs: Long, rates: Rates, freeWaitMin: Double): FareBreakdown {
    val km = distanceM / 1000.0
    val distanceFare = rates.firstKm + max(0.0, km - 1.0) * rates.perKm
    val chargedWaitMin = max(0.0, waitingMs / 60000.0 - freeWaitMin)
    val distance = distanceFare.roundToInt()
    val waiting = (chargedWaitMin * rates.waitPerMin).roundToInt()
    val topUp = max(0, rates.minFare.roundToInt() - distance - waiting)
    return FareBreakdown(distance, waiting, topUp)
}

/**
 * Every price in [rates] raised or lowered by [percent] (5 = 5% more, -5 = 5% less), kept to round
 * amounts: whole rupees for prices of Rs. 20 or more, half rupees below that. A price always moves
 * by at least one step, so a small waiting charge still changes when the button is pressed.
 */
fun adjustRates(rates: Rates, percent: Double): Rates {
    fun one(v: Double): Double {
        if (v <= 0.0) return v
        val step = if (v >= 20.0) 1.0 else 0.5
        var n = Math.round(v * (1 + percent / 100) / step) * step
        if (n == v) n = if (percent > 0) v + step else v - step
        return max(0.0, n)
    }
    return Rates(one(rates.firstKm), one(rates.perKm), one(rates.waitPerMin), one(rates.minFare))
}

/** One GPS reading. [timeMs] only has to increase steadily (it is not a clock time). */
data class Fix(
    val lat: Double,
    val lon: Double,
    val accuracyM: Float,
    val speedMps: Double?,
    val timeMs: Long,
)

data class LatLon(val lat: Double, val lon: Double)

/** Web-map ("slippy map") arithmetic: where a place falls, in pixels, on the world map at a zoom level. */
object MapMath {
    const val TILE = 256
    const val MAX_ZOOM = 17

    fun worldSize(zoom: Int) = TILE * (1 shl zoom).toDouble()

    fun worldX(lon: Double, zoom: Int) = (lon + 180.0) / 360.0 * worldSize(zoom)

    fun worldY(lat: Double, zoom: Int): Double {
        val r = Math.toRadians(lat)
        return (1.0 - Math.log(Math.tan(r) + 1.0 / cos(r)) / Math.PI) / 2.0 * worldSize(zoom)
    }

    /** The closest zoom at which the whole route fits in the picture with a margin around it. */
    fun chooseZoom(route: List<LatLon>, widthPx: Int, heightPx: Int): Int {
        for (z in MAX_ZOOM downTo 1) {
            val xs = route.map { worldX(it.lon, z) }
            val ys = route.map { worldY(it.lat, z) }
            if (xs.max() - xs.min() <= widthPx * 0.8 && ys.max() - ys.min() <= heightPx * 0.8) return z
        }
        return 1
    }
}

/** At most [max] points, evenly picked, always keeping the first and the last. */
fun thinRoute(route: List<LatLon>, max: Int): List<LatLon> {
    if (route.size <= max) return route
    return (0 until max).map { i -> route[(i.toLong() * (route.size - 1) / (max - 1)).toInt()] }
}

/**
 * A web address that opens the route in the Google Maps app (or site): from the first point to the
 * last, through a few points in between so Google follows the roads actually driven. Free, no key.
 * With a single point (the vehicle never moved) it shows that place. Null when there is no point.
 */
fun googleMapsLink(route: List<LatLon>): String? {
    fun at(p: LatLon) = String.format(java.util.Locale.US, "%.5f,%.5f", p.lat, p.lon)
    if (route.isEmpty()) return null
    if (route.size == 1) return "https://www.google.com/maps/search/?api=1&query=" + at(route[0])
    // Google's links take at most 9 stops between the two ends.
    val stops = thinRoute(route, 10)
    val between = stops.subList(1, stops.size - 1)
    return "https://www.google.com/maps/dir/?api=1&travelmode=driving" +
        "&origin=" + at(stops.first()) +
        "&destination=" + at(stops.last()) +
        if (between.isEmpty()) "" else "&waypoints=" + between.joinToString("%7C") { at(it) }
}

fun haversineM(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val r = 6371000.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2) * sin(dLat / 2) +
        cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2) * sin(dLon / 2)
    return 2 * r * asin(sqrt(a))
}

/**
 * Turns GPS readings into distance driven and time spent waiting, like a taxi meter: while the
 * vehicle is slower than [WAIT_SPEED_MPS] the time counts as waiting, otherwise the distance counts.
 */
class TripTracker {
    var distanceM = 0.0
        private set
    var waitingMs = 0L
        private set
    private var last: Fix? = null
    private val points = ArrayList<LatLon>()

    /** The path driven so far, for drawing on the bill's map. */
    val route: List<LatLon> get() = points

    private fun addPoint(fix: Fix) {
        points.add(LatLon(fix.lat, fix.lon))
        // A very long trip keeps every second point, so the list never grows without limit.
        if (points.size > MAX_ROUTE_POINTS) {
            val kept = points.filterIndexed { i, _ -> i % 2 == 0 }
            points.clear()
            points.addAll(kept)
        }
    }

    fun onFix(fix: Fix) {
        if (fix.accuracyM > MAX_ACCURACY_M) return
        val prev = last
        if (prev == null) {
            last = fix
            addPoint(fix)
            return
        }
        val dt = fix.timeMs - prev.timeMs
        if (dt <= 0) return
        val d = haversineM(prev.lat, prev.lon, fix.lat, fix.lon)
        val speed = fix.speedMps ?: (d / (dt / 1000.0))
        if (speed < WAIT_SPEED_MPS) {
            // Standing still or crawling. The position is kept, so GPS drift adds no distance and
            // the few metres crawled are counted once the vehicle moves off again.
            waitingMs += min(dt, MAX_GAP_MS)
            last = prev.copy(timeMs = fix.timeMs)
        } else {
            distanceM += d
            last = fix
            addPoint(fix)
        }
    }

    companion object {
        const val MAX_ROUTE_POINTS = 3000
        /** About 5 km/h. */
        const val WAIT_SPEED_MPS = 1.4
        const val MAX_ACCURACY_M = 35f
        /** After a GPS gap (tunnel, phone asleep) at most this much is counted as waiting. */
        const val MAX_GAP_MS = 15000L
    }
}
