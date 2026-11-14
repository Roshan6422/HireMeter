package com.hiremeter

import java.util.Locale

/**
 * Makes an area of a one-page PDF written by Android's PdfDocument open [url] when tapped.
 * PdfDocument cannot make links itself, so this appends a standard PDF "incremental update" to the
 * finished file: a link annotation, a new copy of the page that points at it, and the index for
 * the two. Everything already in the file stays as it was.
 *
 * The area is in PDF points measured from the page's bottom-left corner.
 */
fun addPdfLink(pdf: ByteArray, left: Float, bottom: Float, right: Float, top: Float, url: String): ByteArray {
    // Latin-1 keeps one character per byte, so string positions are byte positions.
    val text = String(pdf, Charsets.ISO_8859_1)
    val prevXref = Regex("""startxref\s+(\d+)\s+%%EOF\s*$""").find(text)?.groupValues?.get(1)
        ?: throw IllegalArgumentException("no startxref")
    val trailer = text.substring(text.lastIndexOf("trailer"))
    val size = Regex("""/Size\s+(\d+)""").find(trailer)?.groupValues?.get(1)?.toInt()
        ?: throw IllegalArgumentException("no /Size")
    val root = Regex("""/Root\s+(\d+\s+\d+\s+R)""").find(trailer)?.groupValues?.get(1)
        ?: throw IllegalArgumentException("no /Root")
    val info = Regex("""/Info\s+(\d+\s+\d+\s+R)""").find(trailer)?.groupValues?.get(1)

    // The page object (not the /Pages list): "<n> 0 obj <</Type /Page ... >> endobj".
    val page = Regex("""(\d+) 0 obj\s*<<\s*/Type\s*/Page(?![a-z])""").find(text)
        ?: throw IllegalArgumentException("no page")
    val pageNum = page.groupValues[1].toInt()
    val dictStart = text.indexOf("<<", page.range.first)
    val pageEnd = text.indexOf("endobj", dictStart)
    val pageDict = text.substring(dictStart + 2, pageEnd) // everything after "<<", up to and with the closing ">>"
    require(!pageDict.contains("/Annots")) { "page already has annotations" }

    val annotNum = size
    val rect = String.format(Locale.US, "%.2f %.2f %.2f %.2f", left, bottom, right, top)
    val safeUrl = url.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)")

    val add = StringBuilder("\n")
    val pageOffset = pdf.size + add.length
    add.append("$pageNum 0 obj\n<</Annots [$annotNum 0 R]\n").append(pageDict).append("endobj\n")
    val annotOffset = pdf.size + add.length
    add.append("$annotNum 0 obj\n<</Type /Annot /Subtype /Link /Rect [$rect] /Border [0 0 0]")
        .append(" /A <</S /URI /URI ($safeUrl)>>>>\nendobj\n")
    val xrefOffset = pdf.size + add.length
    fun entry(offset: Int) = String.format(Locale.US, "%010d 00000 n \n", offset)
    add.append("xref\n$pageNum 1\n").append(entry(pageOffset))
        .append("$annotNum 1\n").append(entry(annotOffset))
        .append("trailer\n<</Size ${annotNum + 1} /Root $root")
        .append(if (info != null) " /Info $info" else "")
        .append(" /Prev $prevXref>>\nstartxref\n$xrefOffset\n%%EOF\n")
    return pdf + add.toString().toByteArray(Charsets.ISO_8859_1)
}
