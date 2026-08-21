package com.tapconvert.core.testing

import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets

object PdfTestFactory {

    /**
     * Generates a structurally valid minimal PDF 1.4 document stream.
     */
    fun createMinimalPdfBytes(pageCount: Int = 1): ByteArray {
        val safePages = pageCount.coerceAtLeast(1)
        val sb = StringBuilder()

        sb.append("%PDF-1.4\n")
        sb.append("%\u00e2\u00e3\u00cf\u00d3\n")

        val offsets = mutableListOf<Int>()
        var currentOffset = sb.toString().toByteArray(StandardCharsets.US_ASCII).size

        // 1 0 obj - Catalog
        offsets.add(currentOffset)
        val catalog = "1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n"
        sb.append(catalog)
        currentOffset += catalog.toByteArray(StandardCharsets.US_ASCII).size

        // 2 0 obj - Pages
        offsets.add(currentOffset)
        val kids = (1..safePages).joinToString(" ") { "${it + 2} 0 R" }
        val pagesObj = "2 0 obj\n<< /Type /Pages /Kids [$kids] /Count $safePages >>\nendobj\n"
        sb.append(pagesObj)
        currentOffset += pagesObj.toByteArray(StandardCharsets.US_ASCII).size

        // Individual Page objects
        for (i in 1..safePages) {
            offsets.add(currentOffset)
            val pageObj = "${i + 2} 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] >>\nendobj\n"
            sb.append(pageObj)
            currentOffset += pageObj.toByteArray(StandardCharsets.US_ASCII).size
        }

        val totalObjects = safePages + 2

        // XRef table
        val startXref = currentOffset
        sb.append("xref\n")
        sb.append("0 ${totalObjects + 1}\n")
        sb.append("0000000000 65535 f \n")
        for (offset in offsets) {
            sb.append(String.format("%010d 00000 n \n", offset))
        }

        // Trailer
        sb.append("trailer\n")
        sb.append("<< /Size ${totalObjects + 1} /Root 1 0 R >>\n")
        sb.append("startxref\n")
        sb.append("$startXref\n")
        sb.append("%%EOF\n")

        return sb.toString().toByteArray(StandardCharsets.US_ASCII)
    }

    /**
     * Generates a synthetic PDF stream marked with an /Encrypt dictionary.
     */
    fun createEncryptedPdfBytes(): ByteArray {
        val stream = ByteArrayOutputStream()
        val content = """
            %PDF-1.4
            1 0 obj
            << /Type /Catalog /Pages 2 0 R >>
            endobj
            2 0 obj
            << /Type /Pages /Kids [3 0 R] /Count 1 >>
            endobj
            3 0 obj
            << /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] >>
            endobj
            4 0 obj
            << /Filter /Standard /V 2 /R 3 /P -4 >>
            endobj
            xref
            0 5
            0000000000 65535 f 
            0000000009 00000 n 
            0000000058 00000 n 
            0000000115 00000 n 
            0000000185 00000 n 
            trailer
            << /Size 5 /Root 1 0 R /Encrypt 4 0 R >>
            startxref
            245
            %%EOF
        """.trimIndent()
        stream.write(content.toByteArray(StandardCharsets.US_ASCII))
        return stream.toByteArray()
    }

    /**
     * Generates a corrupt PDF byte array missing required xref and EOF markers.
     */
    fun createCorruptPdfBytes(): ByteArray {
        return "%PDF-1.4\n1 0 obj\n<< /Type /Catalog\n[BROKEN_CONTENT_CORRUPTED_STREAM]".toByteArray(StandardCharsets.US_ASCII)
    }
}
