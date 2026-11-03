package com.cloudjex.jexplayer.data.update

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class ApkDownloadTest {

    @Test
    fun copiesEveryByteAndReportsMonotonicProgress() {
        val payload = ByteArray(20_000) { (it % 251).toByte() }
        val output = ByteArrayOutputStream()
        val reports = mutableListOf<Pair<Long, Long>>()

        val copied = copyWithProgress(
            input = ByteArrayInputStream(payload),
            output = output,
            totalBytes = payload.size.toLong()
        ) { downloaded, total ->
            reports += downloaded to total
        }

        assertEquals(payload.size.toLong(), copied)
        assertArrayEquals(payload, output.toByteArray())
        assertEquals(listOf(0L to payload.size.toLong()), reports.take(1))
        assertEquals(payload.size.toLong(), reports.last().first)
        assertEquals(payload.size.toLong(), reports.last().second)
        assertEquals(reports.map { it.first }.sorted(), reports.map { it.first })
    }

    @Test
    fun emptyStreamCopiesNothingButStillReportsZero() {
        val reports = mutableListOf<Long>()
        val copied = copyWithProgress(ByteArrayInputStream(ByteArray(0)), ByteArrayOutputStream(), 0L) { done, _ ->
            reports += done
        }

        assertEquals(0L, copied)
        assertEquals(listOf(0L), reports)
    }

    @Test
    fun formatsMegabytesWithOneDecimal() {
        assertEquals("0.0 MB", formatMegabytes(0))
        assertEquals("2.8 MB", formatMegabytes(2_936_013))
        assertEquals("81.7 MB", formatMegabytes(85_671_116))
    }
}
