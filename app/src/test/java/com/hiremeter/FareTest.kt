package com.hiremeter

import org.junit.Assert.assertEquals
import org.junit.Test

class FareTest {
    private val tuk = Rates(firstKm = 110.0, perKm = 90.0, perMin = 2.0)

    @Test fun shortTripCostsFirstKm() {
        assertEquals(FareBreakdown(110, 0), calculateFare(400.0, 0, tuk))
    }

    @Test fun laterKmsAreChargedByTheMetre() {
        // 1 km + 4.5 km
        assertEquals(110 + 405, calculateFare(5500.0, 0, tuk).distanceFare)
    }

    @Test fun everyMinuteOfTheTripIsCharged() {
        // No free minutes: 10 minutes at Rs. 2, 90 seconds at Rs. 2.
        assertEquals(20, calculateFare(0.0, 10 * 60_000L, tuk).timeFare)
        assertEquals(3, calculateFare(0.0, 90_000L, tuk).timeFare)
    }

    @Test fun distanceAndTimeAreBothCharged() {
        // 5.5 km in 20 minutes: 515 for the distance + 40 for the time.
        assertEquals(FareBreakdown(515, 40), calculateFare(5500.0, 20 * 60_000L, tuk))
        assertEquals(555, calculateFare(5500.0, 20 * 60_000L, tuk).total)
    }

    @Test fun shortTripIsRaisedToTheMinimumFare() {
        val withMin = tuk.copy(minFare = 150.0)
        assertEquals(FareBreakdown(110, 0, 40), calculateFare(400.0, 0, withMin))
        assertEquals(150, calculateFare(400.0, 0, withMin).total)
    }

    @Test fun minimumFareAddsNothingOnceTheTripCostsMore() {
        val withMin = tuk.copy(minFare = 150.0)
        assertEquals(FareBreakdown(515, 0, 0), calculateFare(5500.0, 0, withMin))
        // Time counts towards the minimum too: 110 + 30 = 140, so 10 is added.
        assertEquals(FareBreakdown(110, 30, 10), calculateFare(400.0, 15 * 60_000L, withMin))
    }

    @Test fun allRatesMoveByFivePercentToRoundAmounts() {
        val r = Rates(firstKm = 200.0, perKm = 100.0, perMin = 5.0)
        assertEquals(Rates(210.0, 105.0, 5.5), adjustRates(r, 5.0))
        assertEquals(Rates(190.0, 95.0, 4.5), adjustRates(r, -5.0))
    }

    @Test fun anyPercentageWorks() {
        val r = Rates(firstKm = 200.0, perKm = 100.0, perMin = 5.0, minFare = 300.0)
        assertEquals(Rates(204.0, 102.0, 5.5, 306.0), adjustRates(r, 2.0))
        assertEquals(Rates(194.0, 97.0, 4.5, 291.0), adjustRates(r, -3.0))
    }

    @Test fun smallRatesStillMoveAndNeverGoBelowZero() {
        assertEquals(1.0, adjustRates(Rates(0.0, 0.0, 0.5), 5.0).perMin, 0.0)
        assertEquals(0.0, adjustRates(Rates(0.0, 0.0, 0.5), -5.0).perMin, 0.0)
        assertEquals(Rates(0.0, 0.0, 0.0), adjustRates(Rates(0.0, 0.0, 0.0), 5.0))
    }

    // About 11.1 m of latitude per step.
    private fun fix(step: Int, timeS: Int, speed: Double?, accuracy: Float = 5f) =
        Fix(6.85 + step * 0.0001, 79.95, accuracy, speed, timeS * 1000L)

    @Test fun movingAddsDistance() {
        val t = TripTracker()
        for (i in 0..10) t.onFix(fix(i, i, 11.0))
        assertEquals(111.2, t.distanceM, 1.0)
    }

    @Test fun standingStillIgnoresDrift() {
        val t = TripTracker()
        t.onFix(fix(0, 0, 0.0))
        // The position jitters by 11 m while the speed says the vehicle is stopped.
        for (i in 1..60) t.onFix(fix(i % 2, i, 0.0))
        assertEquals(0.0, t.distanceM, 0.001)
    }

    @Test fun badAccuracyIsIgnored() {
        val t = TripTracker()
        t.onFix(fix(0, 0, 11.0))
        t.onFix(fix(50, 1, 11.0, accuracy = 80f))
        t.onFix(fix(1, 2, 11.0))
        assertEquals(11.1, t.distanceM, 0.5)
    }

    @Test fun speedIsWorkedOutWhenTheGpsGivesNone() {
        val t = TripTracker()
        t.onFix(fix(0, 0, null))
        t.onFix(fix(1, 1, null)) // 11 m in 1 s: moving
        t.onFix(fix(1, 11, null)) // same place 10 s later: parked
        assertEquals(11.1, t.distanceM, 0.5)
    }

    @Test fun routeKeepsMovingPointsOnly() {
        val t = TripTracker()
        for (i in 0..5) t.onFix(fix(i, i, 11.0))
        for (i in 6..20) t.onFix(fix(5, i, 0.0)) // parked
        assertEquals(6, t.route.size)
        assertEquals(6.85, t.route.first().lat, 1e-9)
    }

    @Test fun mapZoomFitsTheRouteAsCloselyAsPossible() {
        // Roughly 5 km apart, north-south.
        val route = listOf(LatLon(6.85, 79.95), LatLon(6.895, 79.95))
        val z = MapMath.chooseZoom(route, 800, 480)
        fun span(zoom: Int) = MapMath.worldY(route[0].lat, zoom) - MapMath.worldY(route[1].lat, zoom)
        assert(span(z) <= 480 * 0.8) { "does not fit at $z" }
        assert(span(z + 1) > 480 * 0.8) { "could have zoomed closer than $z" }
    }

    @Test fun mapPutsColomboInTheRightPlace() {
        // At zoom 0 the world is one 256 px square; Sri Lanka is right of centre, just above the middle.
        assertEquals(184.8, MapMath.worldX(79.86, 0), 0.5)
        assertEquals(123.1, MapMath.worldY(6.93, 0), 0.5)
    }

    @Test fun thinningKeepsBothEnds() {
        val route = (0..999).map { LatLon(6.0 + it * 0.001, 80.0) }
        val thin = thinRoute(route, 300)
        assertEquals(300, thin.size)
        assertEquals(route.first(), thin.first())
        assertEquals(route.last(), thin.last())
        assertEquals(route, thinRoute(route, 5000))
    }

    @Test fun googleMapsLinkGoesFromStartToEndThroughTheMiddle() {
        assertEquals(null, googleMapsLink(emptyList()))
        assertEquals(
            "https://www.google.com/maps/search/?api=1&query=6.84000,79.96000",
            googleMapsLink(listOf(LatLon(6.84, 79.96))),
        )
        assertEquals(
            "https://www.google.com/maps/dir/?api=1&travelmode=driving&origin=6.84120,79.96540&destination=6.84800,79.92650",
            googleMapsLink(listOf(LatLon(6.8412, 79.9654), LatLon(6.848, 79.9265))),
        )
        val long = (0..999).map { LatLon(6.0 + it * 0.001, 80.0) }
        val link = googleMapsLink(long)!!
        assert(link.contains("origin=6.00000,80.00000") && link.contains("destination=6.99900,80.00000")) { link }
        assertEquals(8, link.substringAfter("waypoints=").split("%7C").size)
    }

}
