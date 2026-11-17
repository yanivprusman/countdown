package com.automatelinux.countdown.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.automatelinux.countdown.LocalCounter
import com.automatelinux.countdown.LocalUndoable
import com.automatelinux.countdown.Navigator
import com.automatelinux.countdown.Route
import com.automatelinux.countdown.reportsBottomAction
import com.automatelinux.countdown.data.Countdown
import com.automatelinux.countdown.data.CountdownStore
import com.automatelinux.countdown.data.Tick
import com.automatelinux.countdown.data.countOf
import com.automatelinux.countdown.data.dateOf
import com.automatelinux.countdown.data.formatCount
import com.automatelinux.countdown.data.formatDayHeader
import com.automatelinux.countdown.data.formatShortDate
import com.automatelinux.countdown.data.formatTime
import com.automatelinux.countdown.data.singular
import com.automatelinux.countdown.ui.components.CalendarArt
import com.automatelinux.countdown.ui.components.ProgressRing
import com.automatelinux.countdown.ui.components.RoundIconButton
import com.automatelinux.countdown.ui.components.ScreenHeader
import com.automatelinux.countdown.ui.components.SectionLabel
import com.automatelinux.countdown.ui.components.SwipeToDelete
import com.automatelinux.countdown.ui.components.rememberToday
import com.automatelinux.countdown.ui.theme.Num
import com.automatelinux.countdown.ui.theme.OnAccent
import com.automatelinux.countdown.ui.theme.T
import com.automatelinux.countdown.ui.theme.accentAt
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

@Composable
fun DetailScreen(store: CountdownStore, nav: Navigator, countdownId: String) {
    val countdown = store.countdown(countdownId)
    if (countdown == null && !store.loaded) {
        // Reopened into this countdown before the list has arrived — or off the VPN,
        // where it has not arrived at all. Missing is not the same as deleted: wait, and
        // let the error banner's Retry bring it in (tally issue #14).
        Waiting(store.loading) { nav.back() }
        return
    }
    if (countdown == null) {
        // Deleted under us (the undo of a create, another device) — go back, don't crash.
        LaunchedEffect(Unit) { nav.popToHome() }
        return
    }
    var menuOpen by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var editingNote by remember { mutableStateOf<Tick?>(null) }
    val undoable = LocalUndoable.current
    val counter = LocalCounter.current
    val accent = accentAt(countdown.accent)
    val today = rememberToday()
    val zone = TimeZone.currentSystemDefault()
    val listState = rememberLazyListState()

    // Position = number: the first tick ever counted is "Day 1".
    val numbered = countdown.chronological.mapIndexed { i, t -> i + 1 to t }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(
            title = countdown.name,
            subtitle = "From ${countOf(countdown.start, countdown.unit)} · started ${formatShortDate(countdown.createdAt)}",
            onBack = { nav.back() },
        ) {
            Box {
                RoundIconButton(Icons.Rounded.MoreVert, "More", { menuOpen = true })
                CountdownMenu(
                    expanded = menuOpen,
                    onDismiss = { menuOpen = false },
                    onEdit = { menuOpen = false; nav.go(Route.Edit(countdown.id)) },
                    // Pushed, not replaced: back returns to the original, so the two can
                    // be read one after the other.
                    onDuplicate = { menuOpen = false; store.duplicateCountdown(countdown.id)?.let { nav.go(Route.Detail(it)) } },
                    onReset = { menuOpen = false; confirmReset = true },
                    onDelete = { menuOpen = false; confirmDelete = true },
                )
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
        ) {
            item(key = "hero") {
                Hero(countdown, accent)
                Spacer(Modifier.height(20.dp))
                Stats(countdown, today, zone)
                Spacer(Modifier.height(26.dp))
            }

            if (numbered.isEmpty()) {
                item(key = "empty") { NothingYet(countdown) }
            } else {
                item(key = "history") { SectionLabel("History", Modifier.padding(start = 4.dp, bottom = 4.dp)) }
                val byDay = numbered.asReversed().groupBy { dateOf(it.second.at, zone) }
                byDay.forEach { (day, ticks) ->
                    item(key = "h$day") { DayHeader(formatDayHeader(ticks.first().second.at, today), ticks.size, countdown.unit) }
                    items(ticks, key = { it.second.id }) { (number, tick) ->
                        Box(Modifier.padding(bottom = 8.dp)) {
                            SwipeToDelete(onDelete = {
                                store.deleteTick(countdown.id, tick.id)
                                undoable.offerUndo("${ordinal(countdown.unit, number)} removed")
                            }) {
                                TickRow(
                                    label = ordinal(countdown.unit, number),
                                    left = countdown.start - number,
                                    tick = tick,
                                    accent = accent,
                                    onClick = { editingNote = tick },
                                )
                            }
                        }
                    }
                }
                item(key = "hint") {
                    Text(
                        if (countdown.askJob) "Tap one to change what it was or paid · swipe left to remove it"
                        else "Tap one to add a note · swipe left to remove it",
                        style = MaterialTheme.typography.labelMedium,
                        color = T.textFaint,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                }
            }
        }

        CountBar(countdown, accent, today, zone) { counter.request(countdown) }
    }

    if (confirmReset) {
        ResetDialog(countdown, onConfirm = {
            confirmReset = false
            store.resetCountdown(countdown.id)
            undoable.offerUndo("${countdown.name} is back at ${formatCount(countdown.start)}")
        }, onDismiss = { confirmReset = false })
    }
    if (confirmDelete) {
        DeleteDialog(countdown, onConfirm = {
            confirmDelete = false
            store.deleteCountdown(countdown.id)
            undoable.offerUndo("${countdown.name} deleted")
            nav.popToHome()
        }, onDismiss = { confirmDelete = false })
    }
    editingNote?.takeIf { countdown.askJob }?.let { tick ->
        DayDialog(
            title = ordinal(countdown.unit, numbered.first { it.second.id == tick.id }.first),
            countdown = countdown,
            tick = tick,
            isNew = false,
            onSave = { job, amount, vat -> store.describeTick(countdown.id, tick.id, job, amount, vat); editingNote = null },
            onDismiss = { editingNote = null },
        )
    }
    editingNote?.takeIf { !countdown.askJob }?.let { tick ->
        NoteDialog(
            title = ordinal(countdown.unit, numbered.first { it.second.id == tick.id }.first),
            initial = tick.note,
            onSave = { store.updateNote(countdown.id, tick.id, it); editingNote = null },
            onDismiss = { editingNote = null },
        )
    }
}

/** "Day 27", "Run 3", "Session 12". */
private fun ordinal(unit: String, n: Int): String = singular(unit).replaceFirstChar { it.uppercaseChar() } + " " + formatCount(n)

@Composable
private fun Hero(c: Countdown, accent: Color) {
    Box(Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.size(264.dp), contentAlignment = Alignment.Center) {
            ProgressRing(
                left = if (c.start <= 0) 0f else c.left.toFloat() / c.start,
                color = accent,
                modifier = Modifier.fillMaxSize(),
                stroke = 16.dp,
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (c.finished) {
                    Box(Modifier.size(64.dp).clip(CircleShape).background(T.doneSoft), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Check, null, Modifier.size(36.dp), tint = T.done)
                    }
                    Spacer(Modifier.height(12.dp))
                    Text("Goal reached", style = MaterialTheme.typography.titleLarge, color = T.text)
                    Spacer(Modifier.height(4.dp))
                    Text("all ${countOf(c.start, c.unit)}", style = MaterialTheme.typography.bodyMedium, color = T.textDim)
                } else {
                    // The number rolls down as it changes, the way a countdown should.
                    AnimatedContent(
                        targetState = c.left,
                        transitionSpec = {
                            val down = targetState < initialState
                            (slideInVertically(tween(320)) { if (down) -it / 2 else it / 2 } + fadeIn(tween(240)))
                                .togetherWith(slideOutVertically(tween(320)) { if (down) it / 2 else -it / 2 } + fadeOut(tween(180)))
                        },
                        label = "left",
                    ) { left ->
                        Text(formatCount(left), style = Num.hero, color = T.text)
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "${if (c.left == 1) singular(c.unit) else c.unit} to go",
                        style = MaterialTheme.typography.titleMedium,
                        color = T.textDim,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text("of ${formatCount(c.start)}", style = MaterialTheme.typography.bodyMedium, color = T.textFaint)
                }
            }
        }
    }
}

@Composable
private fun Stats(c: Countdown, today: LocalDate, zone: TimeZone) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Stat("Done", formatCount(c.done), "${(c.progress * 100).toInt()}%", Modifier.weight(1f))
        val streak = c.streak(today, zone)
        Stat("Streak", formatCount(streak), if (streak == 1) "day" else "days", Modifier.weight(1f))
        val days = (today.toEpochDays() - dateOf(c.createdAt, zone).toEpochDays()) + 1
        Stat("Running", formatCount(days), if (days == 1) "day" else "days", Modifier.weight(1f))
    }
}

@Composable
private fun Stat(label: String, value: String, suffix: String, modifier: Modifier) {
    Surface(shape = RoundedCornerShape(18.dp), color = T.surface, modifier = modifier) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = T.textFaint)
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(value, style = Num.medium, color = T.text)
                Spacer(Modifier.width(4.dp))
                Text(suffix, style = MaterialTheme.typography.labelMedium, color = T.textDim, modifier = Modifier.padding(bottom = 3.dp))
            }
        }
    }
}

@Composable
private fun DayHeader(label: String, count: Int, unit: String) {
    Row(
        Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 10.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium, color = T.text, modifier = Modifier.weight(1f))
        // Only worth saying when a day holds more than one — the usual day holds exactly one.
        if (count > 1) Text("$count counted", style = MaterialTheme.typography.labelMedium, color = T.textFaint)
    }
}

@Composable
private fun TickRow(label: String, left: Int, tick: Tick, accent: Color, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(16.dp), color = T.surface, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(accent))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(label, style = MaterialTheme.typography.titleMedium, color = T.text)
                    Spacer(Modifier.width(8.dp))
                    Text("${formatCount(left.coerceAtLeast(0))} left", style = MaterialTheme.typography.labelMedium, color = T.textFaint, modifier = Modifier.padding(bottom = 1.dp))
                }
                if (tick.title.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(tick.title, style = MaterialTheme.typography.bodyMedium, color = T.textDim, maxLines = 2)
                }
                // A note that says more than the job (the reconstructed days carry one).
                if (tick.job.isNotBlank() && tick.note.isNotBlank() && tick.note != tick.job) {
                    Text(tick.note, style = MaterialTheme.typography.labelMedium, color = T.textFaint, maxLines = 2)
                }
                if (tick.place.isNotBlank() && tick.place != tick.title) {
                    Text(tick.place, style = MaterialTheme.typography.labelMedium, color = T.textFaint, maxLines = 1)
                }
                val pay = payLabel(tick)
                if (pay.isNotEmpty()) {
                    Spacer(Modifier.height(2.dp))
                    Text(pay, style = MaterialTheme.typography.labelLarge, color = T.text)
                }
                // Out loud: a day missing from the forecast must never look like it is in it.
                if (tick.tazrimError.isNotBlank()) {
                    Text("Not in tazrim yet — ${tick.tazrimError}", style = MaterialTheme.typography.labelMedium, color = T.danger, maxLines = 3)
                }
            }
            Spacer(Modifier.width(10.dp))
            Text(formatTime(tick.at), style = MaterialTheme.typography.labelMedium, color = T.textFaint)
        }
    }
}

/**
 * The button the whole app exists for, pinned under the thumb rather than scrolled
 * away with the history. Three states, and each says which it is in words.
 */
@Composable
private fun CountBar(c: Countdown, accent: Color, today: LocalDate, zone: TimeZone, onCount: () -> Unit) {
    val todays = c.countedOn(today, zone)
    val modifier = Modifier.fillMaxWidth().navigationBarsPadding().reportsBottomAction()
        .padding(horizontal = 16.dp, vertical = 10.dp).height(64.dp)
    when {
        c.finished -> Surface(shape = RoundedCornerShape(22.dp), color = T.doneSoft, modifier = modifier) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Check, null, Modifier.size(22.dp), tint = T.done)
                Spacer(Modifier.width(8.dp))
                Text("All ${countOf(c.start, c.unit)} counted", style = MaterialTheme.typography.labelLarge, color = T.done)
            }
        }
        c.oncePerDay && todays.isNotEmpty() -> Surface(onClick = onCount, shape = RoundedCornerShape(22.dp), color = T.surface, modifier = modifier) {
            Row(Modifier.fillMaxSize().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(32.dp).clip(CircleShape).background(T.doneSoft), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Check, null, Modifier.size(18.dp), tint = T.done)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Counted today", style = MaterialTheme.typography.labelLarge, color = T.text)
                    Text("at ${formatTime(todays.maxOf { it.at })} · see you tomorrow", style = MaterialTheme.typography.labelMedium, color = T.textDim)
                }
                Text("Again", style = MaterialTheme.typography.labelLarge, color = T.textFaint)
            }
        }
        else -> Surface(onClick = onCount, shape = RoundedCornerShape(22.dp), color = accent, modifier = modifier) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Text("−1", style = Num.medium, color = OnAccent)
                Spacer(Modifier.width(10.dp))
                Text("Count a ${singular(c.unit)}", style = MaterialTheme.typography.labelLarge, color = OnAccent)
            }
        }
    }
}

@Composable
private fun NothingYet(c: Countdown) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "Nothing counted yet. Tap the button below when the first ${singular(c.unit)} is done — " +
                "it takes one off ${formatCount(c.start)}.",
            style = MaterialTheme.typography.bodyLarge,
            color = T.textDim,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun Waiting(loading: Boolean, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(title = "", onBack = onBack)
        Column(
            Modifier.fillMaxSize().padding(horizontal = 40.dp).padding(bottom = 80.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            CalendarArt(T.surfaceAlt, dim = true)
            Spacer(Modifier.height(20.dp))
            Text(
                if (loading) "Fetching your countdown…" else "Waiting for the server to answer",
                style = MaterialTheme.typography.bodyLarge,
                color = T.textDim,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** What a tick was for — "the long shift", "rained, did it anyway". Optional, always editable. */
@Composable
private fun NoteDialog(title: String, initial: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(initial) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = T.surface,
        titleContentColor = T.text,
        shape = RoundedCornerShape(24.dp),
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = {
            Surface(shape = RoundedCornerShape(14.dp), color = T.surfaceAlt, modifier = Modifier.fillMaxWidth()) {
                Box(Modifier.padding(horizontal = 14.dp, vertical = 14.dp)) {
                    if (text.isEmpty()) Text("Add a note", style = MaterialTheme.typography.bodyLarge, color = T.textFaint)
                    BasicTextField(
                        value = text,
                        onValueChange = { text = it.take(255) },
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = T.text),
                        cursorBrush = SolidColor(T.brandInk),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { onSave(text) }),
                        modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(text) }) { Text("Save", color = T.brandInk, style = MaterialTheme.typography.labelLarge) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = T.textDim, style = MaterialTheme.typography.labelLarge) }
        },
    )
}
