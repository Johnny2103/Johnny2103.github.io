package io.github.johnny2103.novagalerie.util

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Date helpers for month / year grouping headers. */
object FormatDates {
    private val monthYear: DateTimeFormatter = DateTimeFormatter.ofPattern("LLLL yyyy", Locale.getDefault())
    private val monthShort: DateTimeFormatter = DateTimeFormatter.ofPattern("LLL yyyy", Locale.getDefault())
    private val dayShort: DateTimeFormatter = DateTimeFormatter.ofPattern("d. LLL yyyy", Locale.getDefault())

    fun monthYear(epochMs: Long): String = Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()).format(monthYear)
    fun monthShort(epochMs: Long): String = Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()).format(monthShort)
    fun dayShort(epochMs: Long): String = Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()).format(dayShort)
    fun year(epochMs: Long): String = Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()).year.toString()
}
