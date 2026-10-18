package com.automatelinux.countdown

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.russhwolf.settings.Settings

sealed interface Route {
    data object Home : Route
    data class Detail(val countdownId: String) : Route
    data class Edit(val countdownId: String?) : Route
}

/**
 * What makes a screen *this* screen rather than another of the same kind, so its saved
 * UI state can be handed back when it is composed again.
 *
 * Screens are swapped by AnimatedContent, which disposes the one being left — and with
 * it everything remembered inside. That is right for a screen's own working state (a
 * half-typed name must not come back) but wrong for where a list was scrolled to
 * (tally issue #12). Only `rememberSaveable` values travel through this.
 */
val Route.stateKey: String
    get() = when (this) {
        is Route.Home -> "home"
        is Route.Detail -> "detail:$countdownId"
        is Route.Edit -> "edit:" + (countdownId ?: "new")
    }

/**
 * A back stack, not a navigation library. Three screens do not need one, and owning
 * the stack here is what lets the Android back button and the on-screen back arrow be
 * the same single code path.
 *
 * It also remembers which countdown you are in, so the app opens back inside it: a
 * countdown is something you come back to every day to tap once, and landing on the
 * list each time would double the cost of that tap (tally issue #14).
 *
 * That memory is on the phone, unlike everything the countdowns hold. It is not a
 * record — it is where this phone was — and losing it costs exactly one tap. On the
 * server it would put a network write, and an error banner whenever the VPN is down,
 * behind every tap into a countdown.
 */
class Navigator(private val settings: Settings) {
    // Home stays underneath, so back from the reopened countdown goes to the list
    // rather than out of the app.
    private val stack = mutableStateListOf<Route>(Route.Home).apply {
        settings.getStringOrNull(OpenCountdownKey)?.let { add(Route.Detail(it)) }
    }

    val current: Route get() = stack.last()

    /** Which way the last transition went, so screens can animate in from the right side. */
    var forward by mutableStateOf(true)
        private set

    fun go(route: Route) {
        forward = true
        stack.add(route)
        rememberOpenCountdown()
    }

    /** Replaces the top of the stack — used when a new countdown opens straight away. */
    fun replaceTop(route: Route) {
        forward = true
        stack[stack.lastIndex] = route
        rememberOpenCountdown()
    }

    /** True if it handled the gesture; false means "there is nothing left, leave the app". */
    fun back(): Boolean {
        if (stack.size <= 1) return false
        forward = false
        stack.removeAt(stack.lastIndex)
        rememberOpenCountdown()
        return true
    }

    fun popToHome() {
        forward = false
        while (stack.size > 1) stack.removeAt(stack.lastIndex)
        rememberOpenCountdown()
    }

    /**
     * The innermost countdown on the stack, not the top screen: editing happens on a
     * screen above it, and you are still in that countdown throughout. Editing from a
     * card on the list never passed through a countdown, so it is not one.
     */
    private fun rememberOpenCountdown() {
        when (val open = stack.lastOrNull { it is Route.Detail }) {
            is Route.Detail -> settings.putString(OpenCountdownKey, open.countdownId)
            else -> settings.remove(OpenCountdownKey)
        }
    }

    private companion object {
        const val OpenCountdownKey = "openCountdown"
    }
}
