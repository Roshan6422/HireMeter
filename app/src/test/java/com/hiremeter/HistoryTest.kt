package com.hiremeter

import org.junit.Assert.assertEquals
import org.junit.Test

class HistoryTest {
    private val day = 24 * 3600_000L
    private fun trip(endedAtMs: Long) = Trip(endedAtMs, "tuk", 1000.0, 60_000, 110)

    @Test fun tripsOlderThan40DaysAreDropped() {
        val now = 100 * day
        val trips = listOf(trip(now - day), trip(now - 39 * day), trip(now - 40 * day), trip(now - 90 * day))
        assertEquals(listOf(trip(now - day), trip(now - 39 * day)), recentTrips(trips, now))
    }

    @Test fun aShortRouteKeepsBothEndsAndAtMost25Points() {
        val route = (0 until 1000).map { LatLon(it.toDouble(), 0.0) }
        val short = shortRoute(route)
        assertEquals(25, short.size)
        assertEquals(route.first(), short.first())
        assertEquals(route.last(), short.last())
        // A route that is already short stays as it is.
        assertEquals(route.take(10), shortRoute(route.take(10)))
    }
}
