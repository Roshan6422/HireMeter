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

    @Test fun upAndDownHireWithAPauseInBetween() {
        // To Colombo (10 km, 30 min), meter paused 3 hours while the driver does other hires,
        // resumed 25 km away at the pick-up, back again (10 km, 30 min). One bill for both legs.
        val m = 1.0 / 111_195.0
        fun fix(metres: Double, s: Long) = Fix(6.85 + metres * m, 79.95, 5f, 10.0, s * 1000L)
        val start = 1_000_000L
        TripRepo.start(start)
        for (i in 0..1800) TripRepo.onFix(fix(i * 10_000.0 / 1800, i.toLong()))
        TripRepo.pause(start + 1800_000)
        // Readings while paused (if any arrive) are ignored.
        for (i in 1801..1900) TripRepo.onFix(fix(10_000.0 + (i - 1800) * 10, i.toLong()))
        TripRepo.resume(start + 1800_000 + 3 * 3600_000)
        val back = 1800L + 3 * 3600
        for (i in 0..1800) TripRepo.onFix(fix(25_000.0 - i * 10_000.0 / 1800, back + i))
        val end = start + 3600_000 + 3 * 3600_000
        val s = TripRepo.state.value
        TripRepo.stop()

        // The 15 km jump from where it paused to where it resumed is not counted.
        assertEquals(20_000.0, s.distanceM, 5.0)
        assertEquals(3600_000L, s.activeMs(end))
        assertEquals(3 * 3600_000L, s.pausedMs(end))
        // 20 km = 110 + 19 x 90 = 1820; 60 minutes x Rs. 2 = 120.
        assertEquals(FareBreakdown(1820, 120), calculateFare(s.distanceM, s.activeMs(end), tuk))
        // The map starts the second piece where the meter was resumed, 25 km out.
        val route = TripRepo.routeSnapshot()
        val b = TripRepo.routeBreaksSnapshot().single()
        assertEquals(6.85 + 25_000.0 * m, route[b].lat, 20 * m)
    }

    @Test fun pausedTimeIsChargedAsWaitingOnlyWhenTheDriverChooses() {
        val r = tuk.copy(waitPerMin = 3.0)
        // 20 km, 60 minutes driving, 3 hours paused.
        val notCharged = calculateFare(20_000.0, 3600_000, r)
        assertEquals(FareBreakdown(1820, 120), notCharged)
        val charged = calculateFare(20_000.0, 3600_000, r, chargedPauseMs = 3 * 3600_000)
        // 180 minutes x Rs. 3 = 540 more.
        assertEquals(FareBreakdown(1820, 120, 0, 540), charged)
        assertEquals(2480, charged.total)
    }

    @Test fun waitingCountsTowardsTheMinimumFare() {
        val r = tuk.copy(minFare = 200.0, waitPerMin = 3.0)
        // 110 + 0 + 30 minutes paused x 3 = 200: nothing to add.
        assertEquals(FareBreakdown(110, 0, 0, 90), calculateFare(500.0, 0, r, chargedPauseMs = 1800_000))
    }

    @Test fun switchingWaitingOnIsKeptOnTheTrip() {
        TripRepo.start(0)
        TripRepo.pause(1000)
        assertEquals(false, TripRepo.state.value.chargePaused)
        TripRepo.setChargePaused(true)
        TripRepo.resume(61_000)
        val s = TripRepo.state.value
        TripRepo.stop()
        assertEquals(true, s.chargePaused)
        assertEquals(60_000L, s.pausedMs(100_000))
    }

    private class Box : TripStorage {
        var json: String? = null
        override fun read() = json
        override fun write(json: String?) { this.json = json }
    }

    private val metre = 1.0 / 111_195.0
    private fun at(metres: Double, timeMs: Long) = Fix(6.85 + metres * metre, 79.95, 5f, 10.0, timeMs)

    @Test fun aKilledPausedTripIsCarriedOnWhereItWas() {
        val box = Box()
        TripRepo.init(box)
        TripRepo.start(1000)
        for (i in 0 until 10) TripRepo.onFix(at(i * 100.0, i * 1000L))
        TripRepo.pause(70_000)
        TripRepo.setChargePaused(true)
        val before = TripRepo.state.value
        TripRepo.stop() // the app is killed

        assertEquals(true, TripRepo.restoreIfNeeded())
        val after = TripRepo.state.value
        assertEquals(before.startedAtMs, after.startedAtMs)
        assertEquals(before.distanceM, after.distanceM, 0.01)
        assertEquals(before.pausedAtMs, after.pausedAtMs)
        assertEquals(true, after.chargePaused)
        assertEquals(10, TripRepo.routeSnapshot().size)

        // Resumed after the phone restarted: its GPS clock starts again from zero.
        TripRepo.resume(100_000)
        for (i in 0..10) TripRepo.onFix(at(5000.0 + i * 100, i * 1000L))
        assertEquals(before.distanceM + 1000, TripRepo.state.value.distanceM, 5.0)

        TripRepo.finish()
        assertEquals(null, box.json)
        assertEquals(false, TripRepo.restoreIfNeeded())
    }

    @Test fun afterARestartDistanceCarriesOnFromTheLastPlace() {
        val box = Box()
        TripRepo.init(box)
        TripRepo.start(0)
        for (i in 0 until 10) TripRepo.onFix(at(i * 100.0, 500_000 + i * 1000L))
        TripRepo.stop()
        TripRepo.restoreIfNeeded()
        // First reading after the restart (clock back near zero), 600 m further on and moving.
        TripRepo.onFix(at(1500.0, 2000))
        val d = TripRepo.state.value.distanceM
        TripRepo.finish()
        assertEquals(1500.0, d, 5.0)
    }

    @Test fun nothingIsRestoredWhenATripIsAlreadyRunning() {
        val box = Box()
        TripRepo.init(box)
        TripRepo.start(0)
        assertEquals(false, TripRepo.restoreIfNeeded())
        TripRepo.finish()
    }

    @Test fun pausedTimeKeepsGrowingUntilResumed() {
        val s = TripState(running = true, startedAtMs = 0, pausedAtMs = 600_000, pausedTotalMs = 120_000)
        assertEquals(120_000L + 300_000, s.pausedMs(900_000))
        assertEquals(900_000L - 420_000, s.activeMs(900_000))
    }

    @Test fun breaksFollowTheRouteWhenALongTripIsThinned() {
        val t = TripTracker()
        fun fix(i: Int) = Fix(6.0 + i * 0.0001, 80.0, 5f, 11.0, i * 1000L)
        for (i in 0 until 1001) t.onFix(fix(i))
        t.newPiece()
        for (i in 1001 until 3001) t.onFix(fix(i))
        val b = t.routeBreaks.single()
        // The piece after the pause still starts with the first reading taken after it.
        assertEquals(6.0 + 1001 * 0.0001, t.route[b].lat, 0.00011)
    }

    @Test fun timeIsChargedEvenWithoutGps() {
        // Time comes from the clock, not the GPS: 10 minutes parked with no signal still cost 10 minutes.
        val sim = Sim()
        sim.noGps(600)
        assertEquals(FareBreakdown(110, 20), calculateFare(sim.t.distanceM, sim.tripMs, tuk))
    }
}
