package com.automatelinux.countdown.data

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Time formatting, hand-rolled because commonMain has no java.text and the output
 * here is deliberately terse. Every "which day is this" question is answered in the
 * phone's own zone: a day of work is a day where you are standing, not in UTC.
 */

private val MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
private val DAYS = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

fun dateOf(epochMillis: Long, zone: TimeZone = TimeZone.currentSystemDefault()): LocalDate =
    Instant.fromEpochMilliseconds(epochMillis).toLocalDateTime(zone).date

fun today(zone: TimeZone = TimeZone.currentSystemDefault()): LocalDate =
    Clock.System.now().toLocalDateTime(zone).date

/** "Today" / "Yesterday" / "Wed 3 Sep" — the header above a day's ticks. */
fun formatDayHeader(epochMillis: Long, today: LocalDate = today()): String {
    val d = dateOf(epochMillis)
    return when (today.toEpochDays() - d.toEpochDays()) {
        0 -> "Today"
        1 -> "Yesterday"
        else -> DAYS[d.dayOfWeek.ordinal] + " " + d.dayOfMonth + " " + MONTHS[d.monthNumber - 1] +
            (if (d.year != today.year) " " + d.year else "")
    }
}

/** "12 Oct" — when a countdown started. */
fun formatShortDate(epochMillis: Long): String {
    val d = dateOf(epochMillis)
    return "${d.dayOfMonth} ${MONTHS[d.monthNumber - 1]}" + (if (d.year != today().year) " ${d.year}" else "")
}

/** "14:05" */
fun formatTime(epochMillis: Long): String {
    val t = Instant.fromEpochMilliseconds(epochMillis).toLocalDateTime(TimeZone.currentSystemDefault())
    return t.hour.toString().padStart(2, '0') + ":" + t.minute.toString().padStart(2, '0')
}

/** "just now" / "3h ago" / "2 Sep" — the quiet line on a countdown card. */
fun formatRelative(epochMillis: Long): String {
    val minutes = (Clock.System.now().toEpochMilliseconds() - epochMillis) / 60_000
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "${minutes}m ago"
        minutes < 60 * 24 -> "${minutes / 60}h ago"
        minutes < 60 * 24 * 7 -> "${minutes / (60 * 24)}d ago"
        else -> formatShortDate(epochMillis)
    }
}

/** 1234 -> "1,234". */
fun formatCount(n: Int): String {
    val s = n.toString()
    if (s.length <= 3) return s
    val out = StringBuilder()
    for ((count, i) in (s.lastIndex downTo 0).withIndex()) {
        if (count > 0 && count % 3 == 0) out.append(',')
        out.append(s[i])
    }
    return out.reverse().toString()
}
