package com.hiremeter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class TrafficLightTest {
    private val metre = 1.0 / 111_195.0
    /** A light 1 km north of the start of the made-up road. */
    private val light = LatLon(6.85 + 1000 * metre, 79.95)

    @Test fun aPlaceNearALightIsFoundAndOneFarAwayIsNot() {
        val index = LightIndex(listOf(light))
        assertTrue(index.near(LatLon(light.lat - 80 * metre, light.lon)))
        assertFalse(index.near(LatLon(light.lat - 150 * metre, light.lon)))
    }

    @Test fun lightsAcrossASquaresEdgeAreFound() {
        // 6.99999 and 7.00001 fall in different squares of the grid.
        val index = LightIndex(listOf(LatLon(7.00001, 80.0)))
        assertTrue(index.near(LatLon(6.99999, 80.0)))
    }

    @Test fun theShippedFileReadsAndHasColomboLights() {
        val file = File("src/main/assets/traffic_lights_lk.csv")
        val lights = LightIndex.parse(file.readText())
        assertTrue(lights.size > 500)
        // Every light is in Sri Lanka.
        assertTrue(lights.all { it.lat in 5.8..10.0 && it.lon in 79.5..82.0 })
    }

    /** Drives north at 10 m/s, one reading a second, as in [TripTest]. */
    private class Sim(val t: TripTracker) {
        var m = 0.0
        var s = 0
        private val metre = 1.0 / 111_195.0
        private fun fix(metres: Double, speed: Double) = Fix(6.85 + metres * metre, 79.95, 5f, speed, s * 1000L)
        init { t.onFix(fix(0.0, 0.0)) }
        fun drive(seconds: Int) = repeat(seconds) { s++; m += 10.0; t.onFix(fix(m, 10.0)) }
        fun stop(seconds: Int) = repeat(seconds) { s++; t.onFix(fix(m + if (s % 2 == 0) 4.0 else -4.0, 0.0)) }
    }

    private fun tracker(lights: List<LatLon>) = LightIndex(lights).let { idx -> TripTracker(isTrafficLight = idx::near) }

    @Test fun aLongRedLightIsFreeUpTo5Minutes() {
        val sim = Sim(tracker(listOf(light)))
        // Stopped 3 minutes right at the light, 1 km on.
        sim.drive(100); sim.stop(180); sim.drive(10)
        assertEquals(0L, sim.t.waitedMs)
    }

    @Test fun theSameStopAwayFromALightIsWaiting() {
        val sim = Sim(tracker(emptyList()))
        sim.drive(100); sim.stop(180); sim.drive(10)
        assertEquals(180_000L, sim.t.waitedMs)
    }

    @Test fun moreThan5MinutesAtALightIsWaitingFromItsStart() {
        // Someone waiting for the customer near a junction still gets paid.
        val sim = Sim(tracker(listOf(light)))
        sim.drive(100); sim.stop(360); sim.drive(10)
        assertEquals(360_000L, sim.t.waitedMs)
    }

    @Test fun aStopTheDriverMarksAsALightIsNotCharged() {
        val sim = Sim(tracker(emptyList()))
        sim.drive(100); sim.stop(150)
        assertEquals(150_000L, sim.t.waitedMs)
        val place = sim.t.markStopAsLight()
        assertEquals(6.85 + 1000 * metre, place!!.lat, 2 * metre)
        assertEquals(0L, sim.t.waitedMs)
        sim.stop(200); sim.drive(10)
        assertEquals(0L, sim.t.waitedMs)
        // The next stop is an ordinary one again.
        sim.stop(150); sim.drive(10)
        assertEquals(150_000L, sim.t.waitedMs)
    }

    @Test fun markingWhileMovingDoesNothing() {
        val sim = Sim(tracker(emptyList()))
        sim.drive(100)
        assertEquals(null, sim.t.markStopAsLight())
    }
}
