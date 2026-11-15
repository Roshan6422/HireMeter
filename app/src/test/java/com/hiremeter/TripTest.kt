package com.hiremeter

import org.junit.Assert.assertEquals
import org.junit.Test

/** Whole trips, one GPS reading a second, checking the distance and the fare PickMe / Uber style. */
class TripTest {
    /** Drives a [TripTracker] along a straight road, [m] metres from the start. */
    private class Sim {
        val t = TripTracker()
        var m = 0.0
        var s = 0

        private fun fix(metres: Double, speed: Double?, accuracy: Float = 5f) =
            Fix(6.85 + metres / 111_195.0, 79.95, accuracy, speed, s * 1000L)

        init { t.onFix(fix(0.0, 0.0)) }

        fun drive(seconds: Int, mps: Double = 10.0) = repeat(seconds) { s++; m += mps; t.onFix(fix(m, mps)) }

        /** Parked: the GPS says speed 0 while the position wanders a few metres either way. */
        fun stop(seconds: Int) = repeat(seconds) { s++; t.onFix(fix(m + if (s % 2 == 0) 4.0 else -4.0, 0.0)) }

        /** No readings at all: under a roof, in a tunnel. [mps] is how fast the vehicle really went meanwhile. */
        fun noGps(seconds: Int, mps: Double = 0.0) { s += seconds; m += mps * seconds }

        /** Readings too inaccurate to use, while parked. */
        fun badReadings(seconds: Int) = repeat(seconds) { s++; t.onFix(fix(m + 60.0, 0.0, accuracy = 80f)) }

        val tripMs get() = s * 1000L
    }

    private val tuk = Rates(firstKm = 110.0, perKm = 90.0, perMin = 2.0)

    @Test fun redLightAddsNoDistanceButItsTimeIsCharged() {
        val sim = Sim()
        sim.drive(60); sim.stop(120); sim.drive(60)
        assertEquals(1200.0, sim.t.distanceM, 1.0)
        // 1.2 km = 110 + 18; 4 minutes = 8.
        assertEquals(FareBreakdown(128, 8), calculateFare(sim.t.distanceM, sim.tripMs, tuk))
    }

    @Test fun slowTrafficIsChargedForItsDistanceAndTimeOnce() {
        val sim = Sim()
        sim.drive(30); sim.drive(300, mps = 0.8); sim.drive(30)
        // The 240 m crawled count once, when the vehicle moves on.
        assertEquals(300.0 + 240.0 + 300.0, sim.t.distanceM, 1.0)
        // 6 minutes at Rs. 2, nothing more for being slow.
        assertEquals(12, calculateFare(sim.t.distanceM, sim.tripMs, tuk).timeFare)
    }

    @Test fun lostOrPoorGpsWhileParkedAddsNoDistance() {
        val sim = Sim()
        sim.drive(30); sim.stop(5); sim.noGps(90); sim.badReadings(60); sim.stop(5); sim.drive(30)
        assertEquals(600.0, sim.t.distanceM, 1.0)
    }

    @Test fun tunnelDistanceIsCounted() {
        val sim = Sim()
        sim.drive(30); sim.noGps(60, mps = 10.0); sim.drive(30)
        assertEquals(1200.0, sim.t.distanceM, 1.0)
    }

    @Test fun timeIsChargedEvenWithoutGps() {
        // Time comes from the clock, not the GPS: 10 minutes parked with no signal still cost 10 minutes.
        val sim = Sim()
        sim.noGps(600)
        assertEquals(FareBreakdown(110, 20), calculateFare(sim.t.distanceM, sim.tripMs, tuk))
    }
}
