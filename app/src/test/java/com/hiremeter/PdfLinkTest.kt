package com.hiremeter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfLinkTest {
    /** A one-page PDF laid out the way Android's PdfDocument writes it. */
    private fun androidStylePdf(): ByteArray {
        val sb = StringBuilder("%PDF-1.4\n")
        val offsets = IntArray(5)
        fun obj(n: Int, body: String) {
            offsets[n] = sb.length
            sb.append("$n 0 obj\n$body\nendobj\n")
        }
        obj(1, "<</Producer (Skia/PDF)>>")
        obj(2, "<</Type /Catalog\n/Pages 3 0 R>>")
        obj(3, "<</Type /Pages\n/Count 1\n/Kids [4 0 R]>>")
        obj(4, "<</Type /Page\n/Resources <</ProcSets [/PDF /Text]>>\n/MediaBox [0 0 420 550]\n/Parent 3 0 R>>")
        val xref = sb.length
        sb.append("xref\n0 5\n0000000000 65535 f \n")
        for (n in 1..4) sb.append(String.format("%010d 00000 n \n", offsets[n]))
        sb.append("trailer\n<</Size 5\n/Root 2 0 R\n/Info 1 0 R>>\nstartxref\n$xref\n%%EOF")
        return sb.toString().toByteArray(Charsets.ISO_8859_1)
    }

    @Test fun linkIsAddedAfterTheUntouchedOriginal() {
        val original = androidStylePdf()
        val url = "https://www.google.com/maps/dir/?api=1&origin=6.84120,79.96540&destination=6.84800,79.92650"
        val out = addPdfLink(original, 20f, 30f, 200f, 56f, url)
        assertTrue(out.copyOf(original.size).contentEquals(original))
        val tail = String(out, Charsets.ISO_8859_1).substring(original.size)
        assertTrue(tail, tail.contains("4 0 obj\n<</Annots [5 0 R]\n/Type /Page"))
        assertTrue(tail, tail.contains("/Subtype /Link /Rect [20.00 30.00 200.00 56.00]"))
        assertTrue(tail, tail.contains("/URI ($url)"))
        assertTrue(tail, tail.contains("<</Size 6 /Root 2 0 R /Info 1 0 R /Prev "))
    }

    @Test fun indexPointsAtTheNewObjects() {
        val out = String(addPdfLink(androidStylePdf(), 0f, 0f, 10f, 10f, "https://example.com"), Charsets.ISO_8859_1)
        val xref = Regex("""startxref\n(\d+)\n%%EOF\n$""").find(out)!!.groupValues[1].toInt()
        assertTrue(out.startsWith("xref\n", xref))
        val entries = Regex("""(\d+) 1\n(\d{10}) 00000 n \n""").findAll(out.substring(xref)).toList()
        assertEquals(listOf("4", "5"), entries.map { it.groupValues[1] })
        for (e in entries) {
            assertTrue(out.startsWith("${e.groupValues[1]} 0 obj", e.groupValues[2].toInt()))
        }
    }
}
