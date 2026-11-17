package com.hiremeter

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Writes the bill as a one-page PDF: the route map (when there is one), the trip details and the
 * fare, and a link that opens the [route] in Google Maps. It is a receipt, so it carries no
 * payment details. Returns the file.
 */
fun writeBillPdf(ctx: Context, t: Trip, map: Bitmap?, route: List<LatLon>): File {
    val s = Tr.s
    val pageW = 420
    val pageH = 550
    val left = 24f
    val right = pageW - 24f
    val green = Color.rgb(11, 110, 79)

    val title = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 20f; color = green; typeface = Typeface.DEFAULT_BOLD }
    val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 11f; color = Color.DKGRAY }
    val value = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 11f; color = Color.BLACK; typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.RIGHT
    }
    val totalLabel = Paint(label).apply { textSize = 13f; color = Color.BLACK; typeface = Typeface.DEFAULT_BOLD }
    val totalValue = Paint(value).apply { textSize = 22f; color = green }
    val rule = Paint().apply { color = Color.LTGRAY; strokeWidth = 1f }

    val doc = PdfDocument()
    val page = doc.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, 1).create())
    val c = page.canvas
    var y = 40f
    c.drawText("HireMeter · ${s.billTitle}", left, y, title)
    y += 14f

    if (map != null) {
        val h = (right - left) * map.height / map.width
        c.drawBitmap(map, null, Rect(left.toInt(), y.toInt(), right.toInt(), (y + h).toInt()), Paint(Paint.FILTER_BITMAP_FLAG))
        y += h
    }
    y += 22f

    fun row(name: String, text: String) {
        c.drawText(name, left, y, label)
        c.drawText(text, right, y, value)
        y += 17f
    }
    fun line() {
        c.drawLine(left, y - 8f, right, y - 8f, rule)
        y += 6f
    }
    row(s.vehicleLabel, t.vehicle)
    row(s.date, SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(t.endedAtMs)))
    row(s.distance, km(t.distanceM))
    row(s.elapsed, clock(t.durationMs))
    // The customer's copy never mentions pausing; charged pause time is part of the waiting time.
    if (t.waitingFare > 0) row(s.waitedLabel, clock(t.waitedMs))
    line()
    row(s.distanceFare, rs(t.distanceFare))
    if (t.timeFare > 0) row(s.timeFare, rs(t.timeFare))
    if (t.waitingFare > 0) row(s.waitingFareLabel, rs(t.waitingFare))
    if (t.minimumTopUp > 0) row(s.minFareTopUp, rs(t.minimumTopUp))
    line()
    y += 8f
    c.drawText(s.total, left, y, totalLabel)
    c.drawText(rs(t.total), right, y, totalValue)

    // "View on Google Maps", which opens the route when tapped (the tap area is added after the page is written).
    val link = googleMapsLink(route)
    var linkArea: RectF? = null
    if (link != null) {
        y += 32f
        val linkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 13f; color = Color.rgb(21, 101, 192); isUnderlineText = true
        }
        c.drawText(s.viewOnGoogle, left, y, linkPaint)
        linkArea = RectF(left - 4f, y - 18f, left + linkPaint.measureText(s.viewOnGoogle) + 4f, y + 8f)
    }

    doc.finishPage(page)
    val bytes = ByteArrayOutputStream().also { doc.writeTo(it) }.toByteArray()
    doc.close()
    val withLink = if (link != null && linkArea != null) {
        // PDF measures up from the bottom of the page; the canvas measures down from the top.
        runCatching {
            addPdfLink(bytes, linkArea.left, pageH - linkArea.bottom, linkArea.right, pageH - linkArea.top, link)
        }.getOrDefault(bytes)
    } else {
        bytes
    }

    val dir = File(ctx.cacheDir, "bills").apply { mkdirs() }
    val file = File(dir, "HireMeter-bill-" + SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date(t.endedAtMs)) + ".pdf")
    file.writeBytes(withLink)
    return file
}

/**
 * Opens the share sheet (WhatsApp etc.) with the bill as a PDF, plus the same bill as text with a
 * Google Maps link to the [route].
 */
fun shareBill(ctx: Context, t: Trip, map: Bitmap?, route: List<LatLon>) {
    val file = writeBillPdf(ctx, t, map, route)
    val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", file)
    val send = Intent(Intent.ACTION_SEND)
        .setType("application/pdf")
        .putExtra(Intent.EXTRA_STREAM, uri)
        .putExtra(Intent.EXTRA_TEXT, billText(t, route))
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    send.clipData = ClipData.newRawUri("", uri) // lets the share sheet itself read the file
    ctx.startActivity(Intent.createChooser(send, Tr.s.sendBill))
}
