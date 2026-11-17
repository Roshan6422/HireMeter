package com.hiremeter

import org.junit.Assert.assertEquals
import org.junit.Test

private val tuk = Rates(firstKm = 110.0, perKm = 90.0, waitPerMin = 2.0)

/** Whole trips, one GPS reading a second, checking the distance, the waiting and the fare. */
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

        fun fare() = calculateFare(t.distanceM, t.waitedMs, tuk)
    }

    @Test fun aRedLightIsFree() {
        val sim = Sim()
        sim.drive(60); sim.stop(90); sim.drive(60)
        assertEquals(1200.0, sim.t.distanceM, 1.0)
        // 1.2 km = 110 + 18; the 90 second stop is not waiting.
        assertEquals(FareBreakdown(128, 0), sim.fare())
    }

    @Test fun aLongStopIsWaitingFromItsStart() {
        val sim = Sim()
        sim.drive(80); sim.stop(300); sim.drive(60)
        assertEquals(1400.0, sim.t.distanceM, 1.0)
        // All 5 minutes at Rs. 2, not only the 3 after the free 2. 1.4 km = 110 + 36.
        assertEquals(300_000L, sim.t.waitedMs)
        assertEquals(FareBreakdown(146, 10), sim.fare())
    }

    @Test fun noWaitingBeforeTheFirst700Metres() {
        val sim = Sim()
        // Standing at the start while the customer gets in, then a long stop 300 m on.
        sim.stop(300); sim.drive(30); sim.stop(300)
        assertEquals(0L, sim.t.waitedMs)
        // Past 700 m, the same stop is waiting.
        sim.drive(50); sim.stop(300); sim.drive(10)
        assertEquals(300_000L, sim.t.waitedMs)
    }

    @Test fun waitingShowsWhileTheStopGoesOn() {
        val sim = Sim()
        sim.drive(80); sim.stop(100)
        assertEquals(100_000L, sim.t.currentStopMs)
        assertEquals(0L, sim.t.waitedMs)
        sim.stop(50)
        assertEquals(150_000L, sim.t.waitedMs)
        sim.drive(10)
        assertEquals(0L, sim.t.currentStopMs)
        assertEquals(150_000L, sim.t.waitedMs)
    }

    @Test fun shortStopsDoNotAddUp() {
        val sim = Sim()
        repeat(5) { sim.drive(30); sim.stop(100) }
        sim.drive(30)
        assertEquals(0L, sim.t.waitedMs)
    }

    @Test fun slowTrafficIsChargedForItsDistanceOnceAndAsWaitingWhenLong() {
        val sim = Sim()
        sim.drive(80); sim.drive(300, mps = 0.8); sim.drive(30)
        // The 240 m crawled count once, when the vehicle moves on.
        assertEquals(800.0 + 240.0 + 300.0, sim.t.distanceM, 1.0)
        // Slower than walking for 5 minutes: that is waiting, 5 minutes at Rs. 2.
        assertEquals(10, sim.fare().waitingFare)
    }

    @Test fun theFreeStopCanBeSetLonger() {
        val t = TripTracker(freeStopMs = 5 * 60_000L)
        fun fix(s: Int, speed: Double) = Fix(6.85 + (if (s < 30) s * 10.0 else 300.0) / 111_195.0, 79.95, 5f, speed, s * 1000L)
        for (s in 0..30) t.onFix(fix(s, 10.0))
        for (s in 31..270) t.onFix(fix(s, 0.0)) // 4 minutes stopped
        assertEquals(0L, t.waitedMs)
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
        // 20 km = 110 + 19 x 90 = 1820; it never stood still, and the pause is not charged.
        assertEquals(FareBreakdown(1820, 0), calculateFare(s.distanceM, s.waitedMs(end), tuk))
        // The map starts the second piece where the meter was resumed, 25 km out.
        val route = TripRepo.routeSnapshot()
        val b = TripRepo.routeBreaksSnapshot().single()
        assertEquals(6.85 + 25_000.0 * m, route[b].lat, 20 * m)
    }

    @Test fun pausedTimeIsChargedAsWaitingOnlyWhenTheDriverChooses() {
        val r = tuk.copy(waitPerMin = 3.0)
        // 20 km, 10 minutes of long stops, 3 hours paused.
        val trip = TripState(running = true, startedAtMs = 0, pausedTotalMs = 3 * 3600_000L, stopWaitMs = 600_000)
        val end = 4 * 3600_000L
        val notCharged = calculateFare(20_000.0, trip.waitedMs(end), r)
        assertEquals(FareBreakdown(1820, 30), notCharged)
        val charged = calculateFare(20_000.0, trip.copy(chargePaused = true).waitedMs(end), r)
        // 180 minutes x Rs. 3 = 540 more.
        assertEquals(FareBreakdown(1820, 570), charged)
        assertEquals(2390, charged.total)
    }

    @Test fun waitingCountsTowardsTheMinimumFare() {
        val r = tuk.copy(minFare = 200.0, waitPerMin = 3.0)
        // 110 + 30 minutes x 3 = 200: nothing to add.
        assertEquals(FareBreakdown(110, 90, 0), calculateFare(500.0, 1800_000, r))
    }

    @Test fun pausingEndsAStopAndTheStopsBeforeItAreKept() {
        TripRepo.start(0)
        for (i in 0..30) TripRepo.onFix(at(i * 30.0, i * 1000L))
        for (i in 31..210) TripRepo.onFix(Fix(6.85 + 900 * metre, 79.95, 5f, 0.0, i * 1000L)) // 3 minutes still
        assertEquals(180_000L, TripRepo.state.value.stopWaitMs)
        assertEquals(180_000L, TripRepo.state.value.stoppedMs)
        TripRepo.pause(211_000)
        TripRepo.resume(400_000)
        // Standing still for a minute after resuming is a new, short stop.
        for (i in 0..60) TripRepo.onFix(Fix(6.85 + 900 * metre, 79.95, 5f, 0.0, 500_000 + i * 1000L))
        val s = TripRepo.state.value
        TripRepo.stop()
        assertEquals(180_000L, s.stopWaitMs)
        assertEquals(180_000L, s.waitedMs(600_000))
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
        assertEquals(before.stopWaitMs, after.stopWaitMs)

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

    @Test fun waitingSurvivesTheAppBeingKilled() {
        val box = Box()
        TripRepo.init(box)
        TripRepo.start(0)
        for (i in 0..10) TripRepo.onFix(at(i * 100.0, i * 1000L))
        for (i in 11..250) TripRepo.onFix(Fix(6.85 + 1000 * metre, 79.95, 5f, 0.0, i * 1000L)) // 4 minutes still
        for (i in 251..260) TripRepo.onFix(at(1000.0 + (i - 250) * 100.0, i * 1000L))
        TripRepo.stop()
        TripRepo.restoreIfNeeded()
        val waited = TripRepo.state.value.stopWaitMs
        TripRepo.finish()
        assertEquals(240_000L, waited)
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

    @Test fun waitingWithoutGpsCountsWhenTheSignalComesBackStill() {
        // Parked under a roof for 10 minutes: no readings, then still in the same place.
        val sim = Sim()
        sim.drive(80); sim.noGps(600); sim.stop(5); sim.drive(30)
        assertEquals(605_000L, sim.t.waitedMs)
    }
}
