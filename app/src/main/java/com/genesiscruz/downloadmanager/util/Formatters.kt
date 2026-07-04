package com.genesiscruz.downloadmanager.util

import java.util.Locale

object Formatters {

    fun bytes(value: Long): String {
        if (value < 0) return "unknown"
        if (value < 1024) return "$value B"
        val units = arrayOf("KB", "MB", "GB", "TB")
        var size = value.toDouble() / 1024
        var unit = 0
        while (size >= 1024 && unit < units.lastIndex) {
            size /= 1024
            unit++
        }
        return String.format(Locale.US, if (size >= 100) "%.0f %s" else "%.1f %s", size, units[unit])
    }

    fun speed(bytesPerSecond: Long): String =
        if (bytesPerSecond <= 0) "—" else "${bytes(bytesPerSecond)}/s"

    fun eta(seconds: Long): String {
        if (seconds < 0) return "—"
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return when {
            h > 0 -> String.format(Locale.US, "%dh %02dm", h, m)
            m > 0 -> String.format(Locale.US, "%dm %02ds", m, s)
            else -> "${s}s"
        }
    }

    fun percent(downloaded: Long, total: Long): Int =
        if (total <= 0) 0 else (downloaded * 100 / total).toInt().coerceIn(0, 100)
}
