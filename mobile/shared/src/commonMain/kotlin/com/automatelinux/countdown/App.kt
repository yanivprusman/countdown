package com.automatelinux.countdown

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.automatelinux.countdown.data.Countdown
import com.automatelinux.countdown.data.CountdownApi
import com.automatelinux.countdown.data.CountdownStore
import com.automatelinux.countdown.data.countOf
import com.automatelinux.countdown.data.formatTime
import com.automatelinux.countdown.data.singular
import com.automatelinux.countdown.data.today
import com.automatelinux.countdown.ui.DetailScreen
import com.automatelinux.countdown.ui.EditScreen
import com.automatelinux.countdown.ui.HomeScreen
import com.automatelinux.countdown.ui.components.ConfirmDialog
import com.automatelinux.countdown.ui.theme.AppTheme
import com.automatelinux.countdown.ui.theme.T
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone

/**
 * Every act in the app that changes a number goes through here so it can be taken
 * back: a count, a reset, a deleted countdown, a deleted tick.
 *
 * Only one offer stands at a time. Material queues snackbars, so three quick counts
 * would otherwise leave three Undo bars to sit through, each one taking back the wrong
 * thing — the newest offer replaces the one on screen, and its Undo is the last act.
 */
class Undoable(
    private val host: SnackbarHostState,
    private val store: CountdownStore,
    private val scope: CoroutineScope,
) {
    fun offerUndo(message: String) = scope.launch {
        host.currentSnackbarData?.dismiss()
        val result = host.showSnackbar(message = message, actionLabel = "Undo", duration = SnackbarDuration.Short)
        if (result == SnackbarResult.ActionPerformed) store.undoLast()
    }
}

/**
 * The tap the app exists for. Shared by the big button inside a countdown and the
 * small one on its card, so both count, buzz, offer Undo and ask before a second count
 * on the same day in exactly the same way.
 */
class Counter(
    private val store: CountdownStore,
    private val undoable: Undoable,
    private val haptics: HapticFeedback,
) {
    /** A countdown that was already counted today and is waiting for "count again?". */
    var askingFor: Countdown? by mutableStateOf(null)
        private set

    fun request(c: Countdown) {
        if (c.finished) return
        val zone = TimeZone.currentSystemDefault()
        if (c.oncePerDay && c.countedOn(today(zone), zone).isNotEmpty()) askingFor = c
        else count(c)
    }

    fun confirm() { askingFor?.let(::count); askingFor = null }

    fun cancel() { askingFor = null }

    private fun count(c: Countdown) {
        val left = store.count(c.id) ?: return
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        undoable.offerUndo(
            if (left == 0) "That was the last one — ${countOf(c.start, c.unit)} done"
            else "Counted · ${countOf(left, c.unit)} to go",
        )
    }
}

val LocalUndoable = staticCompositionLocalOf<Undoable> { error("No Undoable in scope") }
val LocalCounter = staticCompositionLocalOf<Counter> { error("No Counter in scope") }

@Composable
fun App(api: CountdownApi, nav: Navigator) {
    AppTheme {
        val scope = rememberCoroutineScope()
        val store = remember { CountdownStore(api, scope) }
        // Keeps each screen's saved state — in practice, where its list was scrolled to.
        // See Route.stateKey.
        val screenState = rememberSaveableStateHolder()
        val host = remember { SnackbarHostState() }
        val undoable = remember { Undoable(host, store, scope) }
        val haptics = LocalHapticFeedback.current
        val counter = remember { Counter(store, undoable, haptics) }

        CompositionLocalProvider(LocalUndoable provides undoable, LocalCounter provides counter) {
            Box(Modifier.fillMaxSize().background(T.bg)) {
                val forward = nav.forward
                AnimatedContent(
                    targetState = nav.current,
                    transitionSpec = {
                        val dir = if (forward) 1 else -1
                        (slideInHorizontally(tween(260)) { (it * 0.16f * dir).toInt() } + fadeIn(tween(190)))
                            .togetherWith(slideOutHorizontally(tween(260)) { (-it * 0.16f * dir).toInt() } + fadeOut(tween(140)))
                    },
                    label = "route",
                ) { route ->
                    screenState.SaveableStateProvider(route.stateKey) {
                        when (route) {
                            is Route.Home -> HomeScreen(store, nav)
                            is Route.Detail -> DetailScreen(store, nav, route.countdownId)
                            is Route.Edit -> EditScreen(store, nav, route.countdownId)
                        }
                    }
                }

                Column(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(12.dp)) {
                    store.error?.let { message -> ErrorBanner(message, onRetry = store::refresh, onHide = store::dismissError) }
                    SnackbarHost(hostState = host, modifier = Modifier.fillMaxWidth()) { data ->
                        UndoBar(data.visuals.message, data.visuals.actionLabel, data::performAction, data::dismiss)
                    }
                }
            }

            counter.askingFor?.let { c ->
                val zone = TimeZone.currentSystemDefault()
                val earlier = c.countedOn(today(zone), zone).maxOf { it.at }
                ConfirmDialog(
                    title = "Already counted today",
                    body = "${c.name} was counted at ${formatTime(earlier)}. Count another ${singular(c.unit)} today?",
                    confirmLabel = "Count again",
                    destructive = false,
                    onConfirm = counter::confirm,
                    onDismiss = counter::cancel,
                )
            }
        }
    }
}

@Composable
private fun ErrorBanner(message: String, onRetry: () -> Unit, onHide: () -> Unit) {
    Surface(shape = RoundedCornerShape(16.dp), color = T.dangerSoft, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = T.danger,
                modifier = Modifier.weight(1f).padding(vertical = 6.dp),
            )
            TextButton(onClick = onRetry) { Text("Retry", style = MaterialTheme.typography.labelLarge, color = T.danger) }
            TextButton(onClick = onHide) { Text("Hide", style = MaterialTheme.typography.labelLarge, color = T.textDim) }
        }
    }
    Spacer(Modifier.height(8.dp))
}

/**
 * The Undo bar, hand-built: the stock M3 Snackbar drew its action on top of its own
 * message (tally a4e86b5). Swipe it sideways to get it out of the way — it sits over
 * the bottom of the screen, where the buttons are (tally issue #8).
 */
@Composable
private fun UndoBar(message: String, actionLabel: String?, onAction: () -> Unit, onDismiss: () -> Unit) {
    var offsetX by remember { mutableStateOf(0f) }
    val alpha = 1f - (kotlin.math.abs(offsetX) / 400f).coerceIn(0f, 1f)
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = T.surfaceAlt,
        shadowElevation = 6.dp,
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { translationX = offsetX; this.alpha = alpha }
            .draggable(
                orientation = Orientation.Horizontal,
                state = rememberDraggableState { delta -> offsetX += delta },
                onDragStopped = { if (kotlin.math.abs(offsetX) > 150f) onDismiss() else offsetX = 0f },
            ),
    ) {
        Row(
            Modifier.padding(start = 18.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(message, style = MaterialTheme.typography.bodyLarge, color = T.text, modifier = Modifier.weight(1f).padding(vertical = 6.dp))
            actionLabel?.let { label ->
                TextButton(onClick = onAction) { Text(label, style = MaterialTheme.typography.labelLarge, color = T.brandInk) }
            }
        }
    }
}
