package com.hiremeter

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Paint
import android.graphics.Typeface
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** Slower than this (km/h) is GPS drift, shown as 0. */
private const val DRIFT_KMH = 2.0
/** No usable reading for this long: the speed shown is no longer true. */
private const val STALE_MS = 5000L

/** Arc of the dial: starts bottom-left, 240 degrees round to bottom-right. */
private const val START_DEG = 150f
private const val SWEEP_DEG = 240f

fun hasFineLocation(ctx: Context) =
    ctx.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

/** The vehicle's speed from the GPS, as the screen showing it last heard. */
class LiveSpeed {
    var kmh by mutableDoubleStateOf(0.0)
    var topKmh by mutableDoubleStateOf(0.0)
    var lastFixAt by mutableLongStateOf(0L)
    var now by mutableLongStateOf(System.currentTimeMillis())
    var gpsOn by mutableStateOf(true)

    /** A reading came in the last few seconds, so [kmh] is true now. */
    val live: Boolean get() = lastFixAt > 0 && now - lastFixAt < STALE_MS
    val shownKmh: Double get() = if (live) kmh else 0.0
}

/**
 * Listens to the GPS for as long as the calling screen is shown (and [allowed]), with or without a
 * trip running. It only shows the speed; the meter's distance and fare never come from here.
 */
@Composable
fun rememberLiveSpeed(allowed: Boolean): LiveSpeed {
    val ctx = LocalContext.current
    val speed = remember { LiveSpeed() }
    val lm = remember { ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager }
    speed.gpsOn = lm.isProviderEnabled(LocationManager.GPS_PROVIDER)
    if (allowed) {
        DisposableEffect(Unit) {
            var prev: Location? = null
            val listener = object : LocationListener {
                override fun onLocationChanged(loc: Location) {
                    if (!loc.hasAccuracy() || loc.accuracy > TripTracker.MAX_ACCURACY_M) return
                    // Most phones give the speed with the reading; otherwise it is worked out from the last one.
                    val p = prev
                    val mps = when {
                        loc.hasSpeed() -> loc.speed.toDouble()
                        p != null && loc.time > p.time -> p.distanceTo(loc) / ((loc.time - p.time) / 1000.0)
                        else -> null
                    }
                    prev = loc
                    mps ?: return
                    speed.kmh = (mps * 3.6).let { if (it < DRIFT_KMH) 0.0 else it }
                    if (speed.kmh > speed.topKmh) speed.topKmh = speed.kmh
                    speed.lastFixAt = System.currentTimeMillis()
                    speed.now = speed.lastFixAt
                }
                override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                override fun onProviderEnabled(provider: String) { speed.gpsOn = true }
                override fun onProviderDisabled(provider: String) { speed.gpsOn = false }
            }
            try {
                lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, listener, Looper.getMainLooper())
            } catch (e: SecurityException) {
                // The permission was taken away; nothing is shown.
            }
            onDispose { lm.removeUpdates(listener) }
        }
    }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            speed.now = System.currentTimeMillis()
        }
    }
    return speed
}

/** The full-screen speedometer tab. */
@Composable
fun SpeedScreen() {
    val ctx = LocalContext.current
    val s = Tr.s
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    var allowed by remember { mutableStateOf(hasFineLocation(ctx)) }
    val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed = it }
    val speed = rememberLiveSpeed(allowed)

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("🏎️ ${s.speedTitle}", fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth())
        SpeedDial(speed, compact = false, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))

        when {
            !allowed -> {
                Text(s.noPermission, color = Red, textAlign = TextAlign.Center)
                Button(onClick = { askPermission.launch(Manifest.permission.ACCESS_FINE_LOCATION) }, modifier = Modifier.padding(top = 8.dp)) {
                    Text(s.allowLocation)
                }
            }
            !speed.gpsOn -> {
                Text(s.gpsOff, color = Red, textAlign = TextAlign.Center)
                Button(
                    onClick = { ctx.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) },
                    modifier = Modifier.padding(top = 8.dp),
                ) { Text(s.gpsOff) }
            }
            !speed.live -> Text(s.gpsSearching, color = Red, textAlign = TextAlign.Center)
        }

        Card(Modifier.fillMaxWidth().padding(top = 16.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(s.topSpeed, fontSize = 12.sp, color = Color.Gray)
                    Text("${speed.topKmh.roundToInt()} km/h", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                }
                TextButton(onClick = { speed.topKmh = 0.0 }) { Text(s.resetTopSpeed) }
            }
        }
        Text(s.speedNote, fontSize = 12.sp, color = Color.Gray, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 12.dp))
    }
}

/**
 * The round speedometer with the speed in figures in its open bottom. [compact] is the small one
 * on the meter screen; [modifier] sets its width, the height follows.
 */
@Composable
fun SpeedDial(speed: LiveSpeed, compact: Boolean, modifier: Modifier = Modifier) {
    // The dial goes to 120, or 160 once the vehicle has gone faster than that.
    val maxKmh = if (speed.topKmh > 115) 160 else 120
    val fraction by animateFloatAsState(
        (speed.shownKmh / maxKmh).coerceIn(0.0, 1.0).toFloat(), tween(800), label = "needle",
    )
    Box(modifier.aspectRatio(1f), contentAlignment = Alignment.Center) {
        Dial(fraction, maxKmh, Modifier.fillMaxSize())
        // In the open bottom of the dial, clear of the needle.
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = if (compact) 0.dp else 8.dp),
        ) {
            Text(
                if (speed.live) speed.kmh.roundToInt().toString() else "--",
                fontSize = if (compact) 34.sp else 84.sp, fontWeight = FontWeight.Bold,
                color = if (speed.live) Color.Black else Color.Gray,
                lineHeight = if (compact) 36.sp else 88.sp,
            )
            Text("km/h", fontSize = if (compact) 12.sp else 20.sp, color = Color.Gray)
        }
    }
}

/** The dial itself: the track, how far it is filled, ticks with numbers, and the needle. [fraction] is 0..1. */
@Composable
private fun Dial(fraction: Float, maxKmh: Int, modifier: Modifier) {
    val labelPaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.Gray.toArgb()
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
        }
    }
    Canvas(modifier) {
        val stroke = size.minDimension * 0.06f
        // The numbers go outside the track, where the needle never covers them.
        val radius = size.minDimension / 2 - stroke * 2.6f
        val center = Offset(size.width / 2, size.height / 2)
        val topLeft = Offset(center.x - radius, center.y - radius)
        val arcSize = Size(radius * 2, radius * 2)

        drawArc(Color(0xFFE3E7E5), START_DEG, SWEEP_DEG, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
        if (fraction > 0f) {
            drawArc(Green, START_DEG, SWEEP_DEG * fraction, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
        }

        fun at(deg: Float, r: Float): Offset {
            val rad = Math.toRadians(deg.toDouble())
            return Offset(center.x + r * cos(rad).toFloat(), center.y + r * sin(rad).toFloat())
        }
        labelPaint.textSize = size.minDimension * 0.07f
        for (v in 0..maxKmh step 10) {
            val deg = START_DEG + SWEEP_DEG * v / maxKmh
            val major = v % 20 == 0
            val inner = radius - stroke * (if (major) 1.9f else 1.4f)
            drawLine(Color.Gray, at(deg, radius - stroke), at(deg, inner), strokeWidth = stroke * (if (major) 0.25f else 0.12f))
            if (major) {
                val p = at(deg, radius + stroke * 1.7f)
                drawContext.canvas.nativeCanvas.drawText(v.toString(), p.x, p.y + labelPaint.textSize / 3, labelPaint)
            }
        }

        val needleDeg = START_DEG + SWEEP_DEG * fraction
        drawLine(Red, center, at(needleDeg, radius - stroke * 1.6f), strokeWidth = stroke * 0.35f, cap = StrokeCap.Round)
        drawCircle(Red, stroke * 0.6f, center)
    }
}
