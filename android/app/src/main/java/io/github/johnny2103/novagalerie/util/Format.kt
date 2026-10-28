package io.github.johnny2103.novagalerie.util

import java.text.DecimalFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

object Format {

    fun fileSize(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val units = arrayOf("KB", "MB", "GB", "TB")
        var value = bytes.toDouble()
        var unit = -1
        while (value >= 1024 && unit < units.lastIndex) {
            value /= 1024
            unit++
        }
        return "${decimal(value, if (value >= 100) 0 else 1)} ${units[unit]}"
    }

    fun duration(ms: Long): String {
        val totalSeconds = ms / 1000
        val h = totalSeconds / 3600
        val m = (totalSeconds % 3600) / 60
        val s = totalSeconds % 60
        return if (h > 0) String.format(Locale.ROOT, "%d:%02d:%02d", h, m, s) else String.format(Locale.ROOT, "%d:%02d", m, s)
    }

    fun megapixels(width: Int, height: Int): String {
        val mp = width.toLong() * height.toLong() / 1_000_000.0
        return "${decimal(mp, 1)} MP"
    }

    fun decimal(value: Double, digits: Int = 1): String {
        val pattern = if (digits <= 0) "0" else "0." + "#".repeat(digits)
        return DecimalFormat(pattern).format(value)
    }

    fun exposure(seconds: Double): String =
        if (seconds >= 1) "${decimal(seconds)} s" else "1/${Math.round(1 / seconds)} s"

    fun dateTime(epochMs: Long): String {
        if (epochMs <= 0) return "—"
        val formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withLocale(Locale.getDefault())
        return Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()).format(formatter)
    }

    fun date(epochMs: Long): String {
        if (epochMs <= 0) return "—"
        val formatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(Locale.getDefault())
        return Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()).format(formatter)
    }

    fun localDate(epochMs: Long): LocalDate =
        Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()).toLocalDate()
}
