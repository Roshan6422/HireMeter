package com.hiremeter

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

internal val Green = Color(0xFF0B6E4F)
internal val Red = Color(0xFFC62828)
internal val Amber = Color(0xFFB26A00)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Tr.load(this)
        TripRepo.init(this)
        TrafficLights.load(this)
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = Green)) { App() }
        }
    }

    override fun onStop() {
        super.onStop()
        // Going to another app or locking the phone is a safe moment to back up to Drive.
        DriveBackup.saveLater(this)
    }

    override fun onResume() {
        super.onResume()
        // The app was killed or the phone restarted during a trip: carry on the saved one.
        if (TripRepo.restoreIfNeeded()) {
            ContextCompat.startForegroundService(this, Intent(this, MeterService::class.java))
            Toast.makeText(this, Tr.s.tripRecovered, Toast.LENGTH_LONG).show()
        }
    }
}

fun rs(v: Int) = Tr.s.currency + " " + String.format(Locale.US, "%,d", v)
/** A price as set on the rates screen, which may have a half rupee: "Rs. 110", "Rs. 5.5". */
fun rsRate(v: Double) = if (v == Math.floor(v)) rs(v.toInt()) else Tr.s.currency + " " + plain(v)
fun km(m: Double) = String.format(Locale.US, "%.2f km", m / 1000.0)
fun clock(ms: Long): String {
    val s = ms / 1000
    return if (s >= 3600) String.format(Locale.US, "%d:%02d:%02d", s / 3600, s % 3600 / 60, s % 60)
    else String.format(Locale.US, "%02d:%02d", s / 60, s % 60)
}
/** "110" for whole rupees, "112.5" otherwise. */
fun plain(v: Double) = if (v == Math.floor(v)) v.toLong().toString() else v.toString()

fun billText(t: Trip, route: List<LatLon>) = buildString {
    val s = Tr.s
    appendLine("🚕 ${s.billTitle} (HireMeter)")
    appendLine(SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(t.endedAtMs)))
    appendLine("${s.vehicleLabel}: ${t.vehicle}")
    appendLine("${s.distance}: ${km(t.distanceM)}  →  ${rs(t.distanceFare)}")
    appendLine("${s.elapsed}: ${clock(t.durationMs)}" + if (t.timeFare > 0) "  →  ${rs(t.timeFare)}" else "")
    // The customer's copy never mentions pausing; charged pause time is part of the waiting time.
    if (t.waitingFare > 0) appendLine("${s.waitedLabel}: ${clock(t.waitedMs)}  →  ${rs(t.waitingFare)}")
    if (t.minimumTopUp > 0) appendLine("${s.minFareTopUp}: ${rs(t.minimumTopUp)}")
    append("${s.total}: ${rs(t.total)}")
    googleMapsLink(route)?.let { append("\n\n${s.routeLink}\n$it") }
}

@Composable
fun App() {
    val ctx = LocalContext.current
    val s = Tr.s
    var tab by remember { mutableStateOf(0) }
    var vehicles by remember { mutableStateOf(RatesStore.load(ctx)) }
    var selectedId by remember { mutableStateOf(RatesStore.selectedId(ctx)) }
    var history by remember { mutableStateOf(HistoryStore.load(ctx)) }
    val trip by TripRepo.state.collectAsState()

    Scaffold(
        bottomBar = {
            NavigationBar {
                listOf("🚕" to s.tabMeter, "🏎️" to s.tabSpeed, "💰" to s.tabRates, "📒" to s.tabTrips, "⚙️" to s.tabSettings).forEachIndexed { i, (icon, label) ->
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = { tab = i },
                        icon = { Text(icon, fontSize = 20.sp) },
                        label = { Text(label, fontSize = 11.sp, maxLines = 1) },
                    )
                }
            }
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            when (tab) {
                0 -> MeterScreen(
                    trip = trip,
                    vehicles = vehicles,
                    selectedId = selectedId,
                    onSelect = { selectedId = it; RatesStore.setSelectedId(ctx, it) },
                    onTripSaved = { history = HistoryStore.load(ctx); DriveBackup.saveLater(ctx) },
                )
                1 -> SpeedScreen()
                2 -> RatesScreen(
                    vehicles = vehicles,
                    locked = trip.running,
                    onVehicle = { v ->
                        vehicles = vehicles.map { if (it.id == v.id) v else it }
                        RatesStore.save(ctx, vehicles)
                    },
                    onAdjustAll = { percent ->
                        vehicles = vehicles.map { it.copy(rates = adjustRates(it.rates, percent)) }
                        RatesStore.save(ctx, vehicles)
                    },
                )
                3 -> HistoryScreen(
                    history,
                    onClear = { HistoryStore.clear(ctx); history = emptyList() },
                    onDelete = { HistoryStore.delete(ctx, it); history = HistoryStore.load(ctx) },
                )
                else -> SettingsScreen()
            }
        }
    }
}

private fun hasLocationPermission(ctx: Context) =
    ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

private fun startTrip(ctx: Context) {
    val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    if (!lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
        Toast.makeText(ctx, Tr.s.gpsOff, Toast.LENGTH_LONG).show()
        ctx.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return
    }
    ContextCompat.startForegroundService(ctx, Intent(ctx, MeterService::class.java).setAction(MeterService.ACTION_START))
}

@Composable
fun MeterScreen(
    trip: TripState,
    vehicles: List<Vehicle>,
    selectedId: String,
    onSelect: (String) -> Unit,
    onTripSaved: () -> Unit,
) {
    val ctx = LocalContext.current
    val s = Tr.s
    val vehicle = vehicles.firstOrNull { it.id == selectedId } ?: vehicles.first()
    var finished by remember { mutableStateOf<Trip?>(null) }
    var finishedRoute by remember { mutableStateOf<List<LatLon>>(emptyList()) }
    var finishedBreaks by remember { mutableStateOf<List<Int>>(emptyList()) }
    var payFor by remember { mutableStateOf<Trip?>(null) }
    var confirmEnd by remember { mutableStateOf(false) }
    var confirmPause by remember { mutableStateOf(false) }
    val otherHiresButton = remember { AppSettings.otherHiresButton(ctx) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(trip.running) {
        while (trip.running) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    // While paused the driver is in other apps; the screen may sleep.
    LocalView.current.keepScreenOn = trip.running && !trip.paused

    val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (hasLocationPermission(ctx)) startTrip(ctx)
        else Toast.makeText(ctx, Tr.s.noPermission, Toast.LENGTH_LONG).show()
    }

    val elapsedMs = if (trip.running) trip.activeMs(now) else 0L
    val waitedMs = if (trip.running) trip.waitedMs(now) else 0L
    val fare = calculateFare(trip.distanceM, waitedMs, vehicle.rates)
    val showTrip = trip.running

    val endTrip = {
        val endMs = System.currentTimeMillis()
        val tripMs = trip.activeMs(endMs)
        val pausedMs = trip.pausedMs(endMs)
        val endWaitedMs = trip.waitedMs(endMs)
        val route = TripRepo.routeSnapshot()
        val endFare = calculateFare(trip.distanceM, endWaitedMs, vehicle.rates)
        val done = Trip(
            endedAtMs = endMs,
            vehicle = s.vehicleName(vehicle.id),
            distanceM = trip.distanceM,
            durationMs = tripMs,
            distanceFare = endFare.distanceFare,
            minimumTopUp = endFare.minimumTopUp,
            pausedMs = pausedMs,
            waitingFare = endFare.waitingFare,
            waitedMs = endWaitedMs,
            pauseCharged = trip.chargePaused && pausedMs > 0,
            route = shortRoute(route),
        )
        finishedRoute = route
        finishedBreaks = TripRepo.routeBreaksSnapshot()
        TripRepo.finish()
        ctx.stopService(Intent(ctx, MeterService::class.java))
        HistoryStore.add(ctx, done)
        onTripSaved()
        finished = done
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // The vehicle cannot change during a trip, so its choice makes room for the buttons then.
        if (!trip.running) Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (v in vehicles) {
                FilterChip(
                    selected = v.id == vehicle.id,
                    onClick = { onSelect(v.id) },
                    label = { Text("${v.icon} ${s.vehicleName(v.id)}") },
                )
            }
        }
        Text(
            s.rateLine.format(rsRate(vehicle.rates.firstKm), rsRate(vehicle.rates.perKm), rsRate(vehicle.rates.waitPerMin)),
            fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(top = 4.dp),
        )
        if (vehicle.rates.minFare > 0) {
            Text(s.minFareLine.format(rsRate(vehicle.rates.minFare)), fontSize = 12.sp, color = Color.Gray)
        }

        Spacer(Modifier.height(20.dp))
        Card(
            Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Green),
        ) {
            Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(vehicle.icon, fontSize = if (trip.running) 32.sp else 56.sp)
                Text("${s.vehicleName(vehicle.id)} · ${s.fare}", color = Color.White, fontSize = 16.sp)
                Text(
                    if (showTrip) rs(fare.total) else rs(0),
                    color = Color.White, fontSize = 56.sp, fontWeight = FontWeight.Bold,
                )
                if (showTrip) {
                    Text(
                        s.fareSplit.format(rs(fare.distanceFare), rs(fare.waitingFare)),
                        color = Color(0xFFD7F2E6), fontSize = 13.sp,
                    )
                    if (fare.minimumTopUp > 0) {
                        Text(s.minFareLine.format(rsRate(vehicle.rates.minFare)), color = Color(0xFFD7F2E6), fontSize = 13.sp)
                    }
                }
            }
        }

        // The speed beside the trip's figures, so the buttons below stay on the screen.
        Row(
            Modifier.fillMaxWidth().padding(top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Live speed, with or without a trip; it never changes the fare.
            if (hasLocationPermission(ctx)) {
                val speed = rememberLiveSpeed(true)
                SpeedDial(speed, compact = true, modifier = Modifier.width(170.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Stat(s.distance, if (showTrip) km(trip.distanceM) else "0.00 km")
                Stat(s.elapsed, clock(elapsedMs))
                Stat(s.waitedLabel, clock(waitedMs))
            }
        }

        if (trip.paused) {
            Text(
                s.pausedBanner.format(clock(trip.pausedMs(now))),
                color = Amber, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 12.dp), textAlign = TextAlign.Center,
            )
        } else if (trip.running && !trip.hasGps) {
            Text(s.gpsSearching, color = Red, modifier = Modifier.padding(top = 12.dp), textAlign = TextAlign.Center)
        } else if (trip.running && trip.stoppedMs > 0) {
            // One line, so the buttons below stay on the screen.
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                val freeMin = plain(trip.stopFreeMs / 60_000.0)
                val note = when {
                    trip.stopMarkedLight -> s.lightMarkedNote.format(clock(trip.stoppedMs))
                    // Standing still, but not long enough yet to be waiting: the driver sees when it will be.
                    trip.stoppedMs < trip.stopFreeMs ->
                        (if (trip.stopAtLight) s.lightNote else s.stoppedNote).format(clock(trip.stoppedMs), freeMin)
                    else -> ""
                }
                Text(
                    note, color = if (trip.stopMarkedLight) Green else Amber, fontSize = 14.sp,
                    modifier = Modifier.weight(1f),
                )
                if (!trip.stopMarkedLight) {
                    // A light the map does not know: the driver says so, and it is remembered for next time.
                    OutlinedButton(onClick = { TripRepo.markStopAsLight()?.let { TrafficLights.mark(ctx, it) } }) {
                        Text(s.markLight)
                    }
                }
            }
        }

        if (trip.running && (trip.paused || trip.pausedTotalMs > 0)) {
            // Up-and-down hires: the driver decides whether the time away is charged as waiting.
            Row(
                Modifier.fillMaxWidth().padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    s.chargePausedLabel.format(rsRate(vehicle.rates.waitPerMin)),
                    fontSize = 14.sp, modifier = Modifier.weight(1f),
                )
                Switch(checked = trip.chargePaused, onCheckedChange = { TripRepo.setChargePaused(it) })
            }
        }

        Spacer(Modifier.height(16.dp))
        if (!trip.running) {
            Button(
                onClick = {
                    if (hasLocationPermission(ctx)) startTrip(ctx)
                    else askPermission.launch(
                        buildList {
                            add(Manifest.permission.ACCESS_FINE_LOCATION)
                            add(Manifest.permission.ACCESS_COARSE_LOCATION)
                            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
                        }.toTypedArray(),
                    )
                },
                modifier = Modifier.fillMaxWidth().height(64.dp),
            ) { Text("▶  ${s.startTrip}", fontSize = 20.sp) }
        } else {
            // Turned on in Settings; while away on another hire it always shows, to come back.
            if (otherHiresButton || trip.paused) Button(
                onClick = {
                    // Pause takes the place of Start, so a double tap or a bump on the road could pause
                    // the meter while driving: it is asked once more. Resuming needs no question.
                    if (trip.paused) ctx.startService(Intent(ctx, MeterService::class.java).setAction(MeterService.ACTION_RESUME))
                    else confirmPause = true
                },
                colors = ButtonDefaults.buttonColors(containerColor = if (trip.paused) Green else Amber),
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) {
                Text(
                    if (trip.paused) "▶  ${s.resumeTrip}" else "🚗  ${s.pauseTrip}",
                    fontSize = 16.sp, textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = { confirmEnd = true },
                colors = ButtonDefaults.buttonColors(containerColor = Red),
                modifier = Modifier.fillMaxWidth().height(64.dp),
            ) { Text("■  ${s.endTrip}", fontSize = 20.sp) }
        }
        Text(
            s.meterNote,
            fontSize = 12.sp, color = Color.Gray, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 12.dp),
        )
    }

    if (confirmPause && trip.running && !trip.paused) {
        AlertDialog(
            onDismissRequest = { confirmPause = false },
            title = { Text(s.pauseConfirmTitle) },
            text = { Text(s.pauseConfirmText) },
            confirmButton = {
                TextButton(onClick = {
                    confirmPause = false
                    ctx.startService(Intent(ctx, MeterService::class.java).setAction(MeterService.ACTION_PAUSE))
                }) { Text(s.pauseConfirmYes, color = Amber) }
            },
            dismissButton = { TextButton(onClick = { confirmPause = false }) { Text(s.cancel) } },
        )
    }

    if (confirmEnd) {
        // Ending by mistake would lose the hire, so it is asked once more.
        AlertDialog(
            onDismissRequest = { confirmEnd = false },
            title = { Text(s.endConfirmTitle) },
            text = { Text(rs(fare.total), fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Green) },
            confirmButton = {
                TextButton(onClick = { confirmEnd = false; endTrip() }) { Text(s.endTrip, color = Red) }
            },
            dismissButton = { TextButton(onClick = { confirmEnd = false }) { Text(s.cancel) } },
        )
    }

    finished?.let { t ->
        val route = finishedRoute
        val breaks = finishedBreaks
        val map by produceState<Bitmap?>(null, t) { value = renderRouteMap(route, 800, 480, breaks) }
        AlertDialog(
            onDismissRequest = { finished = null },
            title = { Text("🚕 ${s.billTitle}") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    map?.let {
                        Image(
                            bitmap = it.asImageBitmap(),
                            contentDescription = "Route map",
                            contentScale = ContentScale.FillWidth,
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)),
                        )
                        Spacer(Modifier.height(10.dp))
                    }
                    TripBillRows(t)
                    googleMapsLink(route)?.let { link ->
                        TextButton(onClick = { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link))) }) {
                            Text(s.viewOnGoogle)
                        }
                    }
                    if (PayStore.hasQr(ctx) || PayStore.details(ctx).isNotBlank()) {
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = { payFor = t }, modifier = Modifier.fillMaxWidth()) { Text(s.payByQr) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { finished = null }) { Text(s.ok) } },
            dismissButton = {
                TextButton(onClick = { shareBill(ctx, t, map, route) }) { Text("PDF / WhatsApp") }
            },
        )
    }

    payFor?.let { t ->
        val qr: Bitmap? = remember(t) { PayStore.loadQr(ctx) }
        val details = remember(t) { PayStore.details(ctx) }
        AlertDialog(
            onDismissRequest = { payFor = null },
            title = { Text(s.payTitle) },
            text = {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(s.amountDue, fontSize = 13.sp, color = Color.Gray)
                    Text(rs(t.total), fontSize = 40.sp, fontWeight = FontWeight.Bold, color = Green)
                    if (qr != null) {
                        Image(
                            bitmap = qr.asImageBitmap(),
                            contentDescription = "Payment QR",
                            modifier = Modifier.fillMaxWidth().height(280.dp).padding(vertical = 8.dp),
                        )
                        Text(s.payHint, fontSize = 12.sp, color = Color.Gray, textAlign = TextAlign.Center)
                    }
                    if (details.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(details.trim(), textAlign = TextAlign.Center)
                    }
                }
            },
            confirmButton = { TextButton(onClick = { payFor = null }) { Text(s.ok) } },
        )
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier) {
        Column(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, fontSize = 12.sp, color = Color.Gray, textAlign = TextAlign.Center)
            Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun RatesScreen(
    vehicles: List<Vehicle>,
    locked: Boolean,
    onVehicle: (Vehicle) -> Unit,
    onAdjustAll: (Double) -> Unit,
) {
    val s = Tr.s
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text(s.ratesTitle, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(
            if (locked) s.ratesLocked else s.ratesHint,
            fontSize = 13.sp, color = if (locked) Red else Color.Gray,
        )
        Card(Modifier.fillMaxWidth().padding(top = 12.dp)) {
            Column(Modifier.padding(12.dp)) {
                Text(s.adjustAllTitle, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text(s.adjustAllHint, fontSize = 12.sp, color = Color.Gray)
                // Any percentage the driver types, e.g. 2 or 3.5; remembered for next time.
                val ctx = LocalContext.current
                var pctText by remember { mutableStateOf(plain(RatesStore.adjustPct(ctx))) }
                val pct = pctText.toDoubleOrNull()?.takeIf { it > 0 && it <= MAX_ADJUST_PCT }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    OutlinedTextField(
                        value = pctText,
                        onValueChange = { new ->
                            pctText = new
                            new.toDoubleOrNull()?.takeIf { it > 0 && it <= MAX_ADJUST_PCT }?.let { RatesStore.setAdjustPct(ctx, it) }
                        },
                        label = { Text("%", fontSize = 11.sp) },
                        enabled = !locked,
                        isError = pct == null,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(0.8f),
                    )
                    Button(
                        onClick = { pct?.let { onAdjustAll(-it) } },
                        enabled = !locked && pct != null,
                        colors = ButtonDefaults.buttonColors(containerColor = Red),
                        modifier = Modifier.weight(1f).height(56.dp),
                    ) { Text("−${plain(pct ?: 0.0)}%", fontSize = 17.sp, maxLines = 1) }
                    Button(
                        onClick = { pct?.let { onAdjustAll(it) } },
                        enabled = !locked && pct != null,
                        modifier = Modifier.weight(1f).height(56.dp),
                    ) { Text("+${plain(pct ?: 0.0)}%", fontSize = 17.sp, maxLines = 1) }
                }
            }
        }
        Card(Modifier.fillMaxWidth().padding(top = 12.dp)) {
            Column(Modifier.padding(12.dp)) {
                val ctx = LocalContext.current
                var freeStop by remember { mutableStateOf(RatesStore.freeStopMin(ctx)) }
                var signalFree by remember { mutableStateOf(RatesStore.signalFreeMin(ctx)) }
                Text(s.freeStopTitle, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text(s.freeStopHint, fontSize = 12.sp, color = Color.Gray)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                    NumField(s.freeStopLabel, freeStop, !locked, Modifier.weight(1f)) {
                        freeStop = it
                        RatesStore.setFreeStopMin(ctx, it)
                    }
                    NumField(s.signalFreeLabel, signalFree, !locked, Modifier.weight(1f)) {
                        signalFree = it
                        RatesStore.setSignalFreeMin(ctx, it)
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        for (v in vehicles) {
            Card(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text("${v.icon} ${s.vehicleName(v.id)}", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NumField(s.firstKm, v.rates.firstKm, !locked, Modifier.weight(1f)) {
                            onVehicle(v.copy(rates = v.rates.copy(firstKm = it)))
                        }
                        NumField(s.nextKm, v.rates.perKm, !locked, Modifier.weight(1f)) {
                            onVehicle(v.copy(rates = v.rates.copy(perKm = it)))
                        }
                        NumField(s.waitRateLabel, v.rates.waitPerMin, !locked, Modifier.weight(1f)) {
                            onVehicle(v.copy(rates = v.rates.copy(waitPerMin = it)))
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                        NumField(s.minFareLabel, v.rates.minFare, !locked, Modifier.weight(1f)) {
                            onVehicle(v.copy(rates = v.rates.copy(minFare = it)))
                        }
                    }
                }
            }
        }
    }
}

/** Language and the driver's own payment account. */
@Composable
fun SettingsScreen() {
    val ctx = LocalContext.current
    val s = Tr.s
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text("⚙️ ${s.tabSettings}", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Card(Modifier.fillMaxWidth().padding(top = 12.dp)) {
            Column(Modifier.padding(12.dp)) {
                Text("🌐 ${s.language}", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (l in Lang.values()) {
                        FilterChip(selected = Tr.lang == l, onClick = { Tr.set(ctx, l) }, label = { Text(l.label) })
                    }
                }
            }
        }
        OtherHiresSettings()
        BackupSettings()
        PaymentSettings()
        BatterySettings()
    }
}

/** Turns on the button for going on Uber / PickMe hires in the middle of a hire. */
@Composable
private fun OtherHiresSettings() {
    val ctx = LocalContext.current
    val s = Tr.s
    var on by remember { mutableStateOf(AppSettings.otherHiresButton(ctx)) }
    Card(Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(s.otherHiresTitle, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text(s.otherHiresHint, fontSize = 12.sp, color = Color.Gray)
            }
            Switch(
                checked = on,
                onCheckedChange = { on = it; AppSettings.setOtherHiresButton(ctx, it) },
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

/** Battery saving can stop the meter during a trip; this takes HireMeter off its limits. */
@Composable
private fun BatterySettings() {
    val ctx = LocalContext.current
    val s = Tr.s
    val pm = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
    var free by remember { mutableStateOf(pm.isIgnoringBatteryOptimizations(ctx.packageName)) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        free = pm.isIgnoringBatteryOptimizations(ctx.packageName)
    }
    Card(Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Column(Modifier.padding(12.dp)) {
            Text(s.batteryTitle, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(s.batteryHint, fontSize = 12.sp, color = Color.Gray)
            if (free) {
                Text(s.batteryDone, color = Green, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
            } else {
                Button(
                    onClick = {
                        try {
                            ask.launch(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + ctx.packageName)))
                        } catch (e: Exception) {
                            ask.launch(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                        }
                    },
                    modifier = Modifier.padding(top = 8.dp),
                ) { Text(s.batteryButton) }
            }
            if (Build.MANUFACTURER.lowercase() in listOf("xiaomi", "redmi", "poco")) {
                // MIUI blocks a stopped app from starting its meter again unless Autostart is on.
                Text(s.autostartHint, fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(top = 12.dp))
                Button(
                    onClick = {
                        val autostart = Intent().setClassName(
                            "com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity",
                        )
                        try {
                            ctx.startActivity(autostart)
                        } catch (e: Exception) {
                            ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + ctx.packageName)))
                        }
                    },
                    modifier = Modifier.padding(top = 8.dp),
                ) { Text(s.autostartButton) }
            }
        }
    }
}

/** A trip's bill, line by line, for the driver: as the trip ends and again from the trip list. */
@Composable
private fun TripBillRows(t: Trip) {
    val s = Tr.s
    BillRow(s.vehicleLabel, t.vehicle)
    BillRow(s.date, SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(t.endedAtMs)))
    BillRow(s.distance, km(t.distanceM))
    BillRow(s.elapsed, clock(t.durationMs))
    if (t.pausedMs > 0) BillRow(if (t.pauseCharged) s.pausedCharged else s.pausedTime, clock(t.pausedMs))
    if (t.waitingFare > 0) BillRow(s.waitedLabel, clock(t.waitedMs))
    HorizontalDivider(Modifier.padding(vertical = 6.dp))
    BillRow(s.distanceFare, rs(t.distanceFare))
    if (t.timeFare > 0) BillRow(s.timeFare, rs(t.timeFare))
    if (t.waitingFare > 0) BillRow(s.waitingFareLabel, rs(t.waitingFare))
    if (t.minimumTopUp > 0) BillRow(s.minFareTopUp, rs(t.minimumTopUp))
    HorizontalDivider(Modifier.padding(vertical = 6.dp))
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(s.total, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        Text(rs(t.total), fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Green)
    }
}

@Composable
private fun BillRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, color = Color.Gray, modifier = Modifier.weight(1f))
        Text(value, fontWeight = FontWeight.Medium)
    }
}

/** Where the driver adds their own payment QR picture and account details. */
@Composable
private fun PaymentSettings() {
    val ctx = LocalContext.current
    val s = Tr.s
    var qr by remember { mutableStateOf(PayStore.loadQr(ctx)) }
    var details by remember { mutableStateOf(PayStore.details(ctx)) }
    val pickQr = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            if (PayStore.saveQr(ctx, uri)) qr = PayStore.loadQr(ctx)
            else Toast.makeText(ctx, Tr.s.badImage, Toast.LENGTH_LONG).show()
        }
    }
    Card(Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Column(Modifier.padding(12.dp)) {
            Text(s.payCardTitle, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(s.payCardHint, fontSize = 12.sp, color = Color.Gray)
            qr?.let {
                Image(
                    bitmap = it.asImageBitmap(),
                    contentDescription = "Payment QR",
                    modifier = Modifier.padding(top = 8.dp).height(160.dp).fillMaxWidth(),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                Button(onClick = { pickQr.launch("image/*") }) { Text(if (qr == null) s.pickQr else s.changeQr) }
                if (qr != null) {
                    TextButton(onClick = { PayStore.removeQr(ctx); qr = null }) { Text(s.remove, color = Red) }
                }
            }
            OutlinedTextField(
                value = details,
                onValueChange = { details = it; PayStore.setDetails(ctx, it) },
                label = { Text(s.accountLabel) },
                minLines = 2,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
        }
    }
}

/**
 * A number box that reports every valid, non-negative number as it is typed, and shows the new
 * number when [value] is changed from outside (the -5% / +5% buttons).
 */
@Composable
private fun NumField(label: String, value: Double, enabled: Boolean, modifier: Modifier, onValue: (Double) -> Unit) {
    var text by remember { mutableStateOf(plain(value)) }
    var shown by remember { mutableStateOf(value) }
    if (value != shown) {
        text = plain(value)
        shown = value
    }
    val parsed = text.toDoubleOrNull()?.takeIf { it >= 0 }
    OutlinedTextField(
        value = text,
        onValueChange = { new ->
            text = new
            new.toDoubleOrNull()?.takeIf { it >= 0 }?.let { shown = it; onValue(it) }
        },
        label = { Text(label, fontSize = 11.sp) },
        enabled = enabled,
        isError = parsed == null,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier,
    )
}

@Composable
fun HistoryScreen(history: List<Trip>, onClear: () -> Unit, onDelete: (Trip) -> Unit) {
    val ctx = LocalContext.current
    val s = Tr.s
    var confirmClear by remember { mutableStateOf(false) }
    var open by remember { mutableStateOf<Trip?>(null) }
    var confirmDelete by remember { mutableStateOf<Trip?>(null) }
    val dayFormat = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US) }
    val today = dayFormat.format(Date())
    val todayTrips = history.filter { dayFormat.format(Date(it.endedAtMs)) == today }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text(s.tripsTitle, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Card(
            Modifier.fillMaxWidth().padding(vertical = 8.dp),
            colors = CardDefaults.cardColors(containerColor = Green),
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(s.todayIncome.format(todayTrips.size), color = Color.White)
                Text(rs(todayTrips.sumOf { it.total }), color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold)
            }
        }
        if (history.isEmpty()) {
            Text(s.noTrips, color = Color.Gray, modifier = Modifier.padding(top = 16.dp))
        } else {
            Text(s.tripsKeptNote.format(KEEP_TRIP_DAYS), fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(bottom = 4.dp))
        }
        LazyColumn(Modifier.weight(1f)) {
            items(history) { t ->
                Card(Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { open = t }) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(t.endedAtMs)) + " · " + t.vehicle,
                                fontSize = 12.sp, color = Color.Gray,
                            )
                            Text("${km(t.distanceM)} · ${clock(t.durationMs)}")
                        }
                        Text(rs(t.total), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text("  ›", fontSize = 20.sp, color = Color.Gray)
                    }
                }
            }
        }
        if (history.isNotEmpty()) {
            TextButton(onClick = { confirmClear = true }) { Text(s.clearTrips, color = Red) }
        }
    }
    open?.let { t ->
        AlertDialog(
            onDismissRequest = { open = null },
            title = { Text("🚕 ${s.billTitle}") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    TripBillRows(t)
                    googleMapsLink(t.route)?.let { link ->
                        TextButton(onClick = { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link))) }) {
                            Text(s.viewOnGoogle)
                        }
                    }
                    TextButton(onClick = { confirmDelete = t }) { Text("🗑  ${s.deleteTrip}", color = Red) }
                }
            },
            confirmButton = { TextButton(onClick = { open = null }) { Text(s.ok) } },
            dismissButton = {
                TextButton(onClick = { shareBill(ctx, t, null, t.route) }) { Text("PDF / WhatsApp") }
            },
        )
    }
    confirmDelete?.let { t ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text(s.deleteTripTitle) },
            text = { Text(s.clearConfirmText) },
            confirmButton = {
                TextButton(onClick = { confirmDelete = null; open = null; onDelete(t) }) { Text(s.delete, color = Red) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text(s.cancel) } },
        )
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(s.clearConfirmTitle) },
            text = { Text(s.clearConfirmText) },
            confirmButton = { TextButton(onClick = { confirmClear = false; onClear() }) { Text(s.delete, color = Red) } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text(s.cancel) } },
        )
    }
}
