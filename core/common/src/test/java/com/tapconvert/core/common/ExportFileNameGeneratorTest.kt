package com.tapconvert.core.common

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ExportFileNameGeneratorTest {

    private val fixedTimestampMs = 1724338200000L
    private val expectedTimestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date(fixedTimestampMs))

    @Test
    fun `generate creates standard branded filename for simple single file`() {
        val fileName = ExportFileNameGenerator.generate(
            originalName = "invoice.pdf",
            extension = "pdf",
            timestampMs = fixedTimestampMs
        )

        assertThat(fileName).isEqualTo("TapConvert_invoice_$expectedTimestamp.pdf")
    }

    @Test
    fun `generate sanitizes special characters spaces and punctuation`() {
        val fileName = ExportFileNameGenerator.generate(
            originalName = "My Vacation (Hawaii) #2024 & fun!.jpg",
            extension = "jpg",
            timestampMs = fixedTimestampMs
        )

        assertThat(fileName).isEqualTo("TapConvert_My_Vacation_Hawaii_2024_fun_$expectedTimestamp.jpg")
    }

    @Test
    fun `generate handles null or empty original filename with default fallback`() {
        val nullName = ExportFileNameGenerator.generate(
            originalName = null,
            extension = "png",
            timestampMs = fixedTimestampMs
        )
        assertThat(nullName).isEqualTo("TapConvert_File_$expectedTimestamp.png")

        val emptyName = ExportFileNameGenerator.generate(
            originalName = "   ",
            extension = "mp4",
            timestampMs = fixedTimestampMs
        )
        assertThat(emptyName).isEqualTo("TapConvert_File_$expectedTimestamp.mp4")
    }

    @Test
    fun `generate includes batch index when provided`() {
        val fileName = ExportFileNameGenerator.generate(
            originalName = "receipt.jpg",
            extension = "pdf",
            timestampMs = fixedTimestampMs,
            batchIndex = 3
        )

        assertThat(fileName).isEqualTo("TapConvert_receipt_3_$expectedTimestamp.pdf")
    }

    @Test
    fun `generate truncates excessively long file names to prevent OS errors`() {
        val veryLongName = "a".repeat(100) + ".png"
        val fileName = ExportFileNameGenerator.generate(
            originalName = veryLongName,
            extension = "png",
            timestampMs = fixedTimestampMs
        )

        assertThat(fileName.startsWith("TapConvert_")).isTrue()
        assertThat(fileName.endsWith("_$expectedTimestamp.png")).isTrue()
        val basePart = fileName.removePrefix("TapConvert_").removeSuffix("_$expectedTimestamp.png")
        assertThat(basePart.length).isAtMost(30)
    }

    @Test
    fun `generate strips leading dots from extension`() {
        val fileName = ExportFileNameGenerator.generate(
            originalName = "recording.m4a",
            extension = ".mp3",
            timestampMs = fixedTimestampMs
        )

        assertThat(fileName).isEqualTo("TapConvert_recording_$expectedTimestamp.mp3")
    }

    @Test
    fun `generate handles path string with directories by taking basename only`() {
        val fileName = ExportFileNameGenerator.generate(
            originalName = "/storage/emulated/0/Download/presentation_final.pdf",
            extension = "pdf",
            timestampMs = fixedTimestampMs
        )

        assertThat(fileName).isEqualTo("TapConvert_presentation_final_$expectedTimestamp.pdf")
    }

    @Test
    fun `generate strips intake staging timestamp and hash prefix from staged filenames`() {
        val stagedName = "1787583442306_4059ce_queen-amina-story.mp4"
        val fileName = ExportFileNameGenerator.generate(
            originalName = stagedName,
            extension = "mp4",
            timestampMs = fixedTimestampMs
        )

        assertThat(fileName).isEqualTo("TapConvert_queen-amina-story_$expectedTimestamp.mp4")
    }

    @Test
    fun `generate strips share-sheet 4-char hash staging prefix`() {
        val stagedName = "1790153858907_b4c4_queen-mor.mp4"
        val fileName = ExportFileNameGenerator.generate(
            originalName = stagedName,
            extension = "mp4",
            timestampMs = fixedTimestampMs
        )

        assertThat(fileName).isEqualTo("TapConvert_queen-mor_$expectedTimestamp.mp4")
    }
}
