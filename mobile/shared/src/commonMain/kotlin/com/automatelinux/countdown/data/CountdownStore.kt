package com.automatelinux.countdown.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import kotlin.random.Random

/**
 * The app state, backed by the countdown database rather than by the phone.
 *
 * The device holds a view, not the only copy: sixty days into a hundred-day goal is
 * exactly what a wipe, a reinstall or a lost phone must not cost.
 *
 * Deleting, though, deletes. The database protects against *accidents*; a delete the
 * user chose is not one, and a row kept behind a flag is not a delete. Undo works
 * because this class still holds what changed for as long as the snackbar is up.
 *
 * Mutations apply locally first and are sent in the background, so a tap feels
 * instant on a slow link. They are sent **one at a time, in order**: a count and its
 * undo are two requests, and if the undo's DELETE overtook the count's INSERT the
 * tick would come back from the dead. On failure the error is shown and the local
 * state is put back to whatever the server actually holds.
 */
class CountdownStore(private val api: CountdownApi, private val scope: CoroutineScope) {

    var countdowns: List<Countdown> by mutableStateOf(emptyList())
        private set

    /** True until the first answer arrives, so an empty list is never mistaken for "none yet". */
    var loading: Boolean by mutableStateOf(true)
        private set

    /**
     * True once the server has answered with the list at least once. Unlike `loading`,
     * a failed first load leaves this false — so a countdown the app reopened into, but
     * has not been able to fetch yet, is still waiting rather than taken for deleted.
     */
    var loaded: Boolean by mutableStateOf(false)
        private set

    var error: String? by mutableStateOf(null)
        private set

    /** What the snackbar's Undo would take back. Held only in memory. */
    private sealed interface Undo {
        /** A count: take that one tick away again. */
        data class Uncount(val countdownId: String, val tickId: String) : Undo

        /**
         * A reset or a delete: write back exactly what disappeared — not the whole
         * countdown, which would overwrite anything the delete never touched.
         */
        data class Restore(val before: List<Countdown>, val countdown: Countdown, val removed: List<Tick>) : Undo
    }

    private var undo: Undo? = null
    private val writes = Mutex()

    init { refresh() }

    fun refresh() {
        scope.launch {
            val r = api.list()
            if (r.ok) {
                countdowns = r.countdowns
                loaded = true
                error = null
            } else {
                error = r.error ?: "Could not load your countdowns"
            }
            loading = false
        }
    }

    fun dismissError() { error = null }

    fun countdown(id: String?): Countdown? = countdowns.firstOrNull { it.id == id }

    // ---- countdowns -------------------------------------------------------------

    fun createCountdown(name: String, start: Int, unit: String, accent: Int, oncePerDay: Boolean, askJob: Boolean): String {
        val c = Countdown(
            id = newId(), name = name.trim(), start = start, unit = unit.trim(),
            accent = accent, oncePerDay = oncePerDay, askJob = askJob, createdAt = now(),
        )
        countdowns = countdowns + c
        push { api.saveCountdown(c) }
        return c.id
    }

    fun updateCountdown(id: String, name: String, start: Int, unit: String, accent: Int, oncePerDay: Boolean, askJob: Boolean) {
        val current = countdown(id) ?: return
        val updated = current.copy(
            name = name.trim(), start = start, unit = unit.trim(), accent = accent, oncePerDay = oncePerDay, askJob = askJob,
        )
        countdowns = countdowns.map { if (it.id == id) updated else it }
        push { api.updateCountdown(updated) }
    }

    /**
     * A copy, ticks and all — the same what-if fork tally offers. Ids are new (no two
     * rows may share one) but every `at` is the original, so the copy's history reads
     * line for line against what it came from. Returns the new id, or null if gone.
     */
    fun duplicateCountdown(id: String): String? {
        val source = countdown(id) ?: return null
        val copy = source.copy(
            id = newId(),
            name = copyName(source.name),
            createdAt = now(),
            // The copy is a what-if: the money was earned once, by the original, so
            // the copy's days carry no pay — otherwise tazrim would count it twice.
            ticks = source.ticks.map { it.copy(id = newId(), amount = null, vat = "", tazrimError = "") },
        )
        countdowns = countdowns + copy
        push { api.saveCountdown(copy) }
        return copy.id
    }

    /** Back to the starting number. The countdown stays; its ticks go. */
    fun resetCountdown(id: String) {
        val before = countdowns
        val c = countdown(id) ?: return
        countdowns = countdowns.map { if (it.id == id) it.copy(ticks = emptyList()) else it }
        destructive(Undo.Restore(before, c, c.ticks)) { api.resetCountdown(id) }
    }

    fun deleteCountdown(id: String) {
        val before = countdowns
        val c = countdown(id) ?: return
        countdowns = countdowns.filterNot { it.id == id }
        destructive(Undo.Restore(before, c, c.ticks)) { api.deleteCountdown(id) }
    }

    // ---- ticks ------------------------------------------------------------------

    /** What a count did: what is left, for the snackbar, and the new tick, to describe. */
    data class Counted(val left: Int, val tickId: String)

    /** Counts one off. */
    fun count(countdownId: String): Counted? {
        val c = countdown(countdownId) ?: return null
        if (c.finished) return null
        val t = Tick(id = newId(), at = now())
        countdowns = countdowns.map { if (it.id == countdownId) it.copy(ticks = it.ticks + t) else it }
        val u = Undo.Uncount(countdownId, t.id)
        undo = u
        scope.launch {
            val e = writes.withLock { api.addTick(countdownId, t) }
            if (e != null) {
                if (undo == u) undo = null
                error = e
                refresh()
            }
        }
        return Counted(c.left - 1, t.id)
    }

    /**
     * What the day was and what it paid. Sent after the count rather than with it, so
     * the count itself is never held up by typing — and so the same call edits it later.
     */
    fun describeTick(countdownId: String, tickId: String, job: String, amount: Double?, vat: String) {
        val cleanJob = job.trim().take(80)
        val paid = amount?.takeIf { it > 0 }
        val cleanVat = if (paid == null) "" else vat
        countdowns = countdowns.map { c ->
            if (c.id != countdownId) c
            else c.copy(ticks = c.ticks.map {
                if (it.id == tickId) it.copy(job = cleanJob, amount = paid, vat = cleanVat, tazrimError = "") else it
            })
        }
        scope.launch {
            val e = writes.withLock { api.describeTick(tickId, cleanJob, paid, cleanVat) }
            // Re-read either way: the server says whether tazrim took the income.
            if (e != null) error = e
            refresh()
        }
    }

    fun updateNote(countdownId: String, tickId: String, note: String) {
        val clean = note.trim()
        countdowns = countdowns.map { c ->
            if (c.id != countdownId) c
            else c.copy(ticks = c.ticks.map { if (it.id == tickId) it.copy(note = clean) else it })
        }
        push { api.updateNote(tickId, clean) }
    }

    fun deleteTick(countdownId: String, tickId: String) {
        val before = countdowns
        val c = countdown(countdownId) ?: return
        val gone = c.ticks.filter { it.id == tickId }
        countdowns = countdowns.map { x ->
            if (x.id != countdownId) x else x.copy(ticks = x.ticks.filterNot { it.id == tickId })
        }
        destructive(Undo.Restore(before, c, gone)) { api.deleteTick(tickId) }
    }

    /** Takes back the last count, reset or delete. */
    fun undoLast() {
        when (val u = undo ?: return) {
            is Undo.Uncount -> {
                undo = null
                countdowns = countdowns.map { c ->
                    if (c.id != u.countdownId) c else c.copy(ticks = c.ticks.filterNot { it.id == u.tickId })
                }
                push { api.deleteTick(u.tickId) }
            }
            is Undo.Restore -> {
                undo = null
                // Put the removed rows back into whatever the list is now, rather than
                // rewinding the whole list to `before` — a count made on another
                // countdown since the delete must survive its undo.
                val restored = u.countdown.copy(ticks = u.removed)
                countdowns = if (countdowns.any { it.id == u.countdown.id }) {
                    countdowns.map { c ->
                        if (c.id != u.countdown.id) c
                        else c.copy(ticks = (c.ticks + u.removed).distinctBy { it.id })
                    }
                } else {
                    val index = u.before.indexOfFirst { it.id == u.countdown.id }.coerceAtLeast(0)
                    countdowns.toMutableList().apply { add(index.coerceAtMost(size), u.countdown) }
                }
                push { api.saveCountdown(restored) }
            }
        }
    }

    // ---- plumbing ---------------------------------------------------------------

    /** A change with nothing to undo: on failure, resync with the server. */
    private fun push(call: suspend () -> String?) {
        scope.launch {
            val e = writes.withLock { call() }
            if (e != null) {
                error = e
                refresh()
            }
        }
    }

    /**
     * A change the user can take back. The undo is armed straight away — the snackbar
     * is on screen before the server has answered, and an Undo tapped in that moment
     * must still work. On failure the local state goes back and the undo is disarmed.
     */
    private fun destructive(u: Undo.Restore, call: suspend () -> String?) {
        undo = u
        scope.launch {
            val e = writes.withLock { call() }
            if (e != null) {
                if (undo == u) undo = null
                error = e
                countdowns = u.before
            }
        }
    }

    /** "Run" -> "Run copy" -> "Run copy 2"; forking a fork replaces the suffix. */
    private fun copyName(source: String): String = nextCopyName(source, countdowns.map { it.name }.toSet())

    private var seq = 0

    private fun now() = Clock.System.now().toEpochMilliseconds()

    /**
     * Unique by construction inside a run, not by luck: a duplicate mints one id per
     * tick inside a single millisecond, and two that collided would be folded into
     * one row by the upsert — a copy silently a day short.
     */
    private fun newId() =
        now().toString(36) + "-" + (seq++).toString(36) + Random.nextInt(0, 1 shl 20).toString(36)
}

const val MaxNameLength = 40
private val CopySuffix = Regex(" copy( \\d+)?$")

/** The name field holds 40 characters, so a long name loses its tail, never its suffix. */
fun nextCopyName(source: String, taken: Set<String>): String {
    val base = source.trim().replace(CopySuffix, "").trim().ifBlank { source.trim() }
    var n = 1
    while (true) {
        val suffix = if (n == 1) "copy" else "copy $n"
        val room = MaxNameLength - suffix.length - 1
        val candidate = (if (base.length <= room) base else base.take(room).trimEnd()) + " " + suffix
        if (candidate !in taken) return candidate
        n++
    }
}
