package com.hiremeter

import android.content.Context
import org.json.JSONArray
import kotlin.math.floor

/**
 * Traffic lights near which a stop is a red light, not waiting. Kept in a grid of small squares,
 * so asking about one place looks at a few lights, not all of them.
 */
class LightIndex(lights: List<LatLon>, val radiusM: Double = DEFAULT_RADIUS_M) {
    private val cells = HashMap<Long, MutableList<LatLon>>()
    val size: Int

    init {
        for (p in lights) cells.getOrPut(key(cell(p.lat), cell(p.lon))) { ArrayList() }.add(p)
        size = lights.size
    }

    /** There is a light within [radiusM] of [p]. */
    fun near(p: LatLon): Boolean {
        val cy = cell(p.lat)
        val cx = cell(p.lon)
        // A square is about 1.1 km, far more than the radius: the 9 around the place are enough.
        for (dy in -1..1) for (dx in -1..1) {
            val list = cells[key(cy + dy, cx + dx)] ?: continue
            if (list.any { haversineM(p.lat, p.lon, it.lat, it.lon) <= radiusM }) return true
        }
        return false
    }

    /** A light already this close to [p] means [p] adds nothing new. */
    fun closerThan(p: LatLon, metres: Double): Boolean {
        val cy = cell(p.lat)
        val cx = cell(p.lon)
        for (dy in -1..1) for (dx in -1..1) {
            if (cells[key(cy + dy, cx + dx)]?.any { haversineM(p.lat, p.lon, it.lat, it.lon) <= metres } == true) return true
        }
        return false
    }

    private fun cell(deg: Double) = floor(deg / CELL_DEG).toInt()
    private fun key(y: Int, x: Int) = (y.toLong() shl 32) or (x.toLong() and 0xffffffffL)

    companion object {
        private const val CELL_DEG = 0.01
        /** A queue at a red light reaches back along the road, so the place counts this far round. */
        const val DEFAULT_RADIUS_M = 100.0

        /** "lat,lon" lines; lines starting with # are notes. */
        fun parse(text: String): List<LatLon> = text.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .mapNotNull { line ->
                val (a, b) = line.split(',').takeIf { it.size >= 2 } ?: return@mapNotNull null
                val lat = a.trim().toDoubleOrNull() ?: return@mapNotNull null
                val lon = b.trim().toDoubleOrNull() ?: return@mapNotNull null
                LatLon(lat, lon)
            }.toList()
    }
}

/**
 * Where the traffic lights are: Sri Lanka's from OpenStreetMap, shipped with the app (they work
 * without internet), plus the ones the driver marked during trips.
 */
object TrafficLights {
    private const val ASSET = "traffic_lights_lk.csv"
    /** Two marks closer than this are the same light. */
    private const val SAME_LIGHT_M = 30.0
    @Volatile private var index = LightIndex(emptyList())

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("signals", Context.MODE_PRIVATE)

    fun load(ctx: Context) {
        val shipped = runCatching { ctx.assets.open(ASSET).bufferedReader().use { LightIndex.parse(it.readText()) } }.getOrDefault(emptyList())
        index = LightIndex(shipped + marked(ctx))
    }

    fun near(p: LatLon): Boolean = index.near(p)

    /** The lights the driver marked, kept with the rest of the app's data (and its backup). */
    fun marked(ctx: Context): List<LatLon> = runCatching {
        val a = JSONArray(prefs(ctx).getString("marked", "[]"))
        (0 until a.length() / 2).map { LatLon(a.getDouble(2 * it), a.getDouble(2 * it + 1)) }
    }.getOrDefault(emptyList())

    /** Remembers a light the driver marked, so the next stop there is known to be a light. */
    fun mark(ctx: Context, p: LatLon) {
        if (index.closerThan(p, SAME_LIGHT_M)) return
        val all = marked(ctx) + p
        val a = JSONArray()
        for (q in all) a.put(Math.round(q.lat * 1e5) / 1e5).put(Math.round(q.lon * 1e5) / 1e5)
        prefs(ctx).edit().putString("marked", a.toString()).apply()
        load(ctx)
    }
}
