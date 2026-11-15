package com.hiremeter

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Prices for one vehicle type, in rupees. [perMin] is charged for every minute of the trip, driving
 * or not, like PickMe and Uber. [minFare] 0 means there is no minimum fare. [waitPerMin] is for
 * time the meter was paused (customers away, e.g. on an up-and-down hire), only when the driver
 * chooses to charge it.
 */
data class Rates(
    val firstKm: Double,
    val perKm: Double,
    val perMin: Double,
    val minFare: Double = 0.0,
    val waitPerMin: Double = 0.0,
)

/**
 * [minimumTopUp] is what is added so a short trip still costs the minimum fare; [waitingFare] is
 * for paused time the driver chose to charge.
 */
data class FareBreakdown(val distanceFare: Int, val timeFare: Int, val minimumTopUp: Int = 0, val waitingFare: Int = 0) {
    val total: Int get() = distanceFare + timeFare + minimumTopUp + waitingFare
}

/**
 * The fare the way PickMe and Uber work it out: distance plus time, both always.
 * The first kilometre (or any part of it) costs [Rates.firstKm]; every kilometre after it costs
 * [Rates.perKm], charged by the metre. Every minute of the trip ([tripMs], from start to end)
 * costs [Rates.perMin], charged by the second. Paused time the driver chose to charge
 * ([chargedPauseMs]) costs [Rates.waitPerMin]. However short the trip, the total is never less
 * than [Rates.minFare].
 */
fun calculateFare(distanceM: Double, tripMs: Long, rates: Rates, chargedPauseMs: Long = 0): FareBreakdown {
    val km = distanceM / 1000.0
    val distance = (rates.firstKm + max(0.0, km - 1.0) * rates.perKm).roundToInt()
    val time = (max(0L, tripMs) / 60000.0 * rates.perMin).roundToInt()
    val waiting = (max(0L, chargedPauseMs) / 60000.0 * rates.waitPerMin).roundToInt()
    val topUp = max(0, rates.minFare.roundToInt() - distance - time - waiting)
    return FareBreakdown(distance, time, topUp, waiting)
}

/**
 * Every price in [rates] raised or lowered by [percent] (5 = 5% more, -5 = 5% less), kept to round
 * amounts: whole rupees for prices of Rs. 20 or more, half rupees below that. A price always moves
 * by at least one step, so a small per-minute charge still changes when the button is pressed.
 */
fun adjustRates(rates: Rates, percent: Double): Rates {
    fun one(v: Double): Double {
        if (v <= 0.0) return v
        val step = if (v >= 20.0) 1.0 else 0.5
        var n = Math.round(v * (1 + percent / 100) / step) * step
        if (n == v) n = if (percent > 0) v + step else v - step
        return max(0.0, n)
    }
    return Rates(one(rates.firstKm), one(rates.perKm), one(rates.perMin), one(rates.minFare), one(rates.waitPerMin))
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
 * Turns GPS readings into the distance driven. While the vehicle is slower than [STILL_SPEED_MPS]
 * its position is held, so GPS drift while parked adds no distance; the few metres crawled in slow
 * traffic are counted once it moves on.
 */
class TripTracker {
    var distanceM = 0.0
        private set
    private var last: Fix? = null
    private val points = ArrayList<LatLon>()
    private val breaks = ArrayList<Int>()

    /** The path driven so far, for drawing on the bill's map. */
    val route: List<LatLon> get() = points

    /** Where in [route] a new piece starts after a pause; the map draws no line across the gap. */
    val routeBreaks: List<Int> get() = breaks

    /**
     * The meter was paused and is running again, maybe somewhere else entirely. The next reading
     * starts a new piece of route and adds no distance from where the last piece ended.
     */
    fun newPiece() {
        last = null
        if (points.isNotEmpty() && breaks.lastOrNull() != points.size) breaks.add(points.size)
    }

    private fun addPoint(fix: Fix) {
        points.add(LatLon(fix.lat, fix.lon))
        // A very long trip keeps every second point, so the list never grows without limit.
        if (points.size > MAX_ROUTE_POINTS) {
            val kept = points.filterIndexed { i, _ -> i % 2 == 0 }
            points.clear()
            points.addAll(kept)
            // A piece starting at an odd index now starts at the next kept point.
            val moved = breaks.map { (it + 1) / 2 }.distinct()
            breaks.clear()
            breaks.addAll(moved)
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
        if (speed < STILL_SPEED_MPS) {
            last = prev.copy(timeMs = fix.timeMs)
        } else {
            distanceM += d
            last = fix
            addPoint(fix)
        }
    }

    companion object {
        const val MAX_ROUTE_POINTS = 3000
        /** About 5 km/h: slower than this the GPS position is mostly drift. */
        const val STILL_SPEED_MPS = 1.4
        const val MAX_ACCURACY_M = 35f
    }
}
