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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.automatelinux.countdown.data.Countdown
import com.automatelinux.countdown.data.CountdownApi
import com.automatelinux.countdown.data.CountdownStore
import com.automatelinux.countdown.data.countOf
import com.automatelinux.countdown.data.formatTime
import com.automatelinux.countdown.data.singular
import com.automatelinux.countdown.data.today
import com.automatelinux.countdown.ui.DayDialog
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
 * Long, not Short: four seconds was gone before a thumb reached Undo on the emulator.
 */
class Undoable(
    private val host: SnackbarHostState,
    private val store: CountdownStore,
    private val scope: CoroutineScope,
) {
    fun offerUndo(message: String) = scope.launch {
        host.currentSnackbarData?.dismiss()
        val result = host.showSnackbar(message = message, actionLabel = "Undo", duration = SnackbarDuration.Long)
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

    /** The day just counted, waiting to be told what it was and what it paid. */
    data class Describing(val countdownId: String, val tickId: String)

    var describing: Describing? by mutableStateOf(null)
        private set

    fun doneDescribing() { describing = null }

    fun request(c: Countdown) {
        if (c.finished) return
        val zone = TimeZone.currentSystemDefault()
        if (c.oncePerDay && c.countedOn(today(zone), zone).isNotEmpty()) askingFor = c
        else count(c)
    }

    fun confirm() { askingFor?.let(::count); askingFor = null }

    fun cancel() { askingFor = null }

    private fun count(c: Countdown) {
        val (left, tickId) = store.count(c.id) ?: return
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        // Asked straight away, while the day is fresh — not left to fill in by hand later.
        if (c.askJob) describing = Describing(c.id, tickId)
        undoable.offerUndo(
            if (left == 0) "That was the last one — ${countOf(c.start, c.unit)} done"
            else "Counted · ${countOf(left, c.unit)} to go",
        )
    }
}

/**
 * How tall the current screen's bottom action is — the count button, the save button,
 * the New FAB. The Undo bar and the error banner sit *above* it, never over it: in tally
 * the bar covered the FAB (issue #8) and the fix there was to make it swipeable; here a
 * second count tapped straight into the bar. A screen reports its own height with
 * [reportsBottomAction], so the bar is placed by measurement, not by a guessed margin.
 */
class BottomAction {
    private var owner: Any? = null
    var height: Dp by mutableStateOf(0.dp)
        private set

    fun report(from: Any, h: Dp) { owner = from; height = h }

    /** Screens cross-fade, so the leaving screen is disposed *after* the arriving one has
     *  reported — it may only clear a height that is still its own. */
    fun clear(from: Any) { if (owner === from) { owner = null; height = 0.dp } }
}

val LocalBottomAction = staticCompositionLocalOf<BottomAction> { error("No BottomAction in scope") }

/** Marks this element as the screen's bottom action; cleared when the screen goes away. */
@Composable
fun Modifier.reportsBottomAction(): Modifier {
    val action = LocalBottomAction.current
    val density = LocalDensity.current
    val me = remember { Any() }
    DisposableEffect(Unit) { onDispose { action.clear(me) } }
    return this.onSizeChanged { action.report(me, with(density) { it.height.toDp() }) }
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
        val bottomAction = remember { BottomAction() }

        CompositionLocalProvider(
            LocalUndoable provides undoable,
            LocalCounter provides counter,
            LocalBottomAction provides bottomAction,
        ) {
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

                Column(
                    Modifier.align(Alignment.BottomCenter).navigationBarsPadding()
                        .padding(bottom = bottomAction.height).padding(12.dp),
                ) {
                    store.error?.let { message -> ErrorBanner(message, onRetry = store::refresh, onHide = store::dismissError) }
                    SnackbarHost(hostState = host, modifier = Modifier.fillMaxWidth()) { data ->
                        UndoBar(data.visuals.message, data.visuals.actionLabel, data::performAction, data::dismiss)
                    }
                }
            }

            counter.describing?.let { d ->
                val c = store.countdown(d.countdownId)
                val tick = c?.ticks?.firstOrNull { it.id == d.tickId }
                if (c == null || tick == null) {
                    // Undone (or deleted) while the question was up: nothing left to describe.
                    counter.doneDescribing()
                } else {
                    DayDialog(
                        title = "${singular(c.unit).replaceFirstChar { it.uppercaseChar() }} ${c.chronological.indexOfFirst { it.id == tick.id } + 1}",
                        countdown = c,
                        tick = tick,
                        isNew = true,
                        onSave = { job, amount, vat ->
                            store.describeTick(c.id, tick.id, job, amount, vat)
                            counter.doneDescribing()
                        },
                        onDismiss = counter::doneDescribing,
                    )
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
