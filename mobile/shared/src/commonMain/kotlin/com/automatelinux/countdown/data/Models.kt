package com.automatelinux.countdown.data

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.serialization.Serializable

/** One step counted off — a day worked, a run done. Always exactly one. */
@Serializable
data class Tick(
    val id: String,
    val note: String = "",
    /** What the day was — "Dolphinarium, Tel Aviv, aluminium". */
    val job: String = "",
    // Where it was counted. Carried so an undo puts a removed day back exactly as it was.
    val lat: Double? = null,
    val lon: Double? = null,
    val place: String = "",
    /** What the day paid, as said: net with `vat` = "plus", or the whole sum. Null = not said. */
    val amount: Double? = null,
    /** "plus" | "included" | "none"; empty without an amount. */
    val vat: String = "",
    /** Why tazrim does not have this day's income yet; empty when it does. */
    val tazrimError: String = "",
    val at: Long,
) {
    /** What to call the day on screen: its job, else whatever note it carries. */
    val title: String get() = job.ifBlank { note }
}

/**
 * A goal being counted down: 100 days of work, 30 runs.
 *
 * `start` is where it began and is never decremented. What is left is always derived
 * from it, so the number you started from stays on screen for as long as the
 * countdown exists — that is the whole point of keeping it.
 */
@Serializable
data class Countdown(
    val id: String,
    val name: String,
    val start: Int,
    /** Plural, as it reads after a number: "days", "runs". */
    val unit: String = "days",
    val accent: Int = 0,
    /** A second count on the same day asks first. On by default: most goals are daily. */
    val oncePerDay: Boolean = true,
    /** After each count, ask what the day was and what it paid. The pay goes to tazrim. */
    val askJob: Boolean = false,
    val createdAt: Long,
    val ticks: List<Tick> = emptyList(),
) {
    val done: Int get() = ticks.size

    /** Never below zero: lowering the start under what is already done reads as finished. */
    val left: Int get() = (start - done).coerceAtLeast(0)

    val finished: Boolean get() = left == 0

    /** 0 at the start, 1 at the finish. */
    val progress: Float get() = if (start <= 0) 1f else (done.toFloat() / start).coerceIn(0f, 1f)

    val lastActivity: Long get() = ticks.maxOfOrNull { it.at } ?: createdAt

    /** Oldest first, so a tick's position is its number: the first day is #1. */
    val chronological: List<Tick> get() = ticks.sortedWith(compareBy({ it.at }, { it.id }))

    fun countedOn(day: LocalDate, zone: TimeZone): List<Tick> =
        ticks.filter { dateOf(it.at, zone) == day }

    /**
     * Consecutive days with at least one tick, ending today — or ending yesterday, since
     * a day not yet counted is not a broken streak until it is over.
     */
    fun streak(today: LocalDate, zone: TimeZone): Int {
        val days = ticks.map { dateOf(it.at, zone).toEpochDays() }.toSet()
        var cursor = today.toEpochDays()
        if (cursor !in days) cursor -= 1
        var n = 0
        while (cursor in days) { n++; cursor-- }
        return n
    }
}

/** The units offered on the edit screen; anything typed is used as written. */
val UnitSuggestions = listOf("days", "sessions", "workouts", "runs", "pages", "times")

/**
 * "days" -> "day". Only for the handful of shapes a unit is likely to take; a unit
 * the rule does not know reads correctly as-is in the plural, so it falls back to that.
 */
fun singular(unit: String): String {
    val u = unit.trim()
    return when {
        u.endsWith("ies", ignoreCase = true) && u.length > 3 -> u.dropLast(3) + "y"
        u.endsWith("sses", ignoreCase = true) -> u.dropLast(2)
        u.endsWith("ss", ignoreCase = true) -> u
        u.endsWith("s", ignoreCase = true) && u.length > 1 -> u.dropLast(1)
        else -> u
    }
}

/** "1 day", "73 days". */
fun countOf(n: Int, unit: String): String = "$n ${if (n == 1) singular(unit) else unit}"
