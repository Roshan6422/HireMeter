package com.hiremeter

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.floor

/**
 * The picture of a trip's route on a free OpenStreetMap street map, for the bill. Needs internet.
 * When the vehicle never moved there is only one point, and the map shows that place with a pin.
 * Returns null only when the GPS gave no usable reading at all.
 */
suspend fun renderRouteMap(route: List<LatLon>, widthPx: Int, heightPx: Int, breaks: List<Int> = emptyList()): Bitmap? {
    if (route.isEmpty()) return null
    return renderOsmMap(route, widthPx, heightPx, breaks.toSet())
}

/**
 * The route on an OpenStreetMap map. The map squares are downloaded from openstreetmap.org; a
 * square that cannot be downloaded stays grey and the route is still drawn.
 */
private suspend fun renderOsmMap(route: List<LatLon>, widthPx: Int, heightPx: Int, breaks: Set<Int>): Bitmap {
    return withContext(Dispatchers.IO) {
        val zoom = MapMath.chooseZoom(route, widthPx, heightPx)
        val xs = route.map { MapMath.worldX(it.lon, zoom) }
        val ys = route.map { MapMath.worldY(it.lat, zoom) }
        val originX = (xs.min() + xs.max()) / 2 - widthPx / 2.0
        val originY = (ys.min() + ys.max()) / 2 - heightPx / 2.0

        val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.rgb(232, 232, 232))

        val lastTile = (1 shl zoom) - 1
        val tile = MapMath.TILE
        for (ty in floor(originY / tile).toInt()..floor((originY + heightPx) / tile).toInt()) {
            for (tx in floor(originX / tile).toInt()..floor((originX + widthPx) / tile).toInt()) {
                if (tx !in 0..lastTile || ty !in 0..lastTile) continue
                val square = downloadTile(zoom, tx, ty) ?: continue
                canvas.drawBitmap(square, (tx * tile - originX).toFloat(), (ty * tile - originY).toFloat(), null)
            }
        }

        // Fade the street map so the route is the clearest thing in the picture.
        canvas.drawColor(Color.argb(115, 255, 255, 255))

        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        val path = Path()
        for (i in route.indices) {
            val x = (xs[i] - originX).toFloat()
            val y = (ys[i] - originY).toFloat()
            // A new piece after a pause starts fresh, with no line across the gap.
            if (i == 0 || i in breaks) path.moveTo(x, y) else path.lineTo(x, y)
        }
        // A white edge first, then the blue route on top of it.
        canvas.drawPath(path, line.apply { color = Color.WHITE; strokeWidth = 17f })
        canvas.drawPath(path, line.apply { color = Color.rgb(21, 101, 192); strokeWidth = 10f })

        fun dot(i: Int, color: Int) {
            val x = (xs[i] - originX).toFloat()
            val y = (ys[i] - originY).toFloat()
            canvas.drawCircle(x, y, 18f, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = Color.WHITE })
            canvas.drawCircle(x, y, 12f, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color })
        }
        dot(0, Color.rgb(11, 110, 79)) // start: green
        // With a single point the start pin alone marks the place.
        if (route.size > 1) dot(route.lastIndex, Color.rgb(198, 40, 40)) // end: red

        // OpenStreetMap asks for this credit wherever its map is shown.
        val credit = "© OpenStreetMap"
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.DKGRAY; textSize = 20f }
        val w = text.measureText(credit)
        canvas.drawRect(widthPx - w - 12, heightPx - 30f, widthPx.toFloat(), heightPx.toFloat(),
            Paint().apply { color = Color.argb(190, 255, 255, 255) })
        canvas.drawText(credit, widthPx - w - 6, heightPx - 9f, text)
        bitmap
    }
}

private fun downloadTile(zoom: Int, x: Int, y: Int): Bitmap? {
    return try {
        val conn = URL("https://tile.openstreetmap.org/$zoom/$x/$y.png").openConnection() as HttpURLConnection
        conn.connectTimeout = 6000
        conn.readTimeout = 6000
        // OpenStreetMap refuses requests that do not say which app is asking.
        conn.setRequestProperty("User-Agent", "HireMeter/1.0 (Android trip meter app)")
        try {
            if (conn.responseCode == 200) conn.inputStream.use { BitmapFactory.decodeStream(it) } else null
        } finally {
            conn.disconnect()
        }
    } catch (e: Exception) {
        null
    }
}
