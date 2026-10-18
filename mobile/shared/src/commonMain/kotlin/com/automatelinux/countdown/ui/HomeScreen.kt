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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.automatelinux.countdown.LocalCounter
import com.automatelinux.countdown.LocalUndoable
import com.automatelinux.countdown.Navigator
import com.automatelinux.countdown.Route
import com.automatelinux.countdown.reportsBottomAction
import com.automatelinux.countdown.data.Countdown
import com.automatelinux.countdown.data.CountdownStore
import com.automatelinux.countdown.data.countOf
import com.automatelinux.countdown.data.formatCount
import com.automatelinux.countdown.data.formatRelative
import com.automatelinux.countdown.ui.components.ConfirmDialog
import com.automatelinux.countdown.ui.components.CalendarArt
import com.automatelinux.countdown.ui.components.MenuRow
import com.automatelinux.countdown.ui.components.ProgressLine
import com.automatelinux.countdown.ui.components.RoundIconButton
import com.automatelinux.countdown.ui.components.ScreenHeader
import com.automatelinux.countdown.ui.components.rememberToday
import com.automatelinux.countdown.ui.theme.Num
import com.automatelinux.countdown.ui.theme.OnAccent
import com.automatelinux.countdown.ui.theme.T
import com.automatelinux.countdown.ui.theme.accentAt
import kotlinx.datetime.TimeZone

@Composable
fun HomeScreen(store: CountdownStore, nav: Navigator) {
    // Unfinished first, then by when each was last touched: the one you count every day
    // stays at the top, and a goal you have reached drifts down out of the way.
    val countdowns = store.countdowns.sortedWith(compareBy<Countdown> { it.finished }.thenByDescending { it.lastActivity })
    var confirmReset by remember { mutableStateOf<Countdown?>(null) }
    var confirmDelete by remember { mutableStateOf<Countdown?>(null) }
    val undoable = LocalUndoable.current
    val counter = LocalCounter.current
    val listState = rememberLazyListState()

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            ScreenHeader(
                title = "Countdown",
                subtitle = when {
                    store.loading -> "Loading…"
                    countdowns.isEmpty() -> "Nothing counting down yet"
                    else -> {
                        val running = countdowns.count { !it.finished }
                        when (running) {
                            0 -> "Every goal reached"
                            1 -> "1 goal on the go"
                            else -> "$running goals on the go"
                        }
                    }
                },
                large = true,
            )
            if (store.loading) {
                // An empty list before the first answer is not "no countdowns" — saying so
                // would invite the user to start a duplicate of one they already have.
                Box(Modifier.fillMaxSize().padding(bottom = 80.dp), contentAlignment = Alignment.Center) {
                    CalendarArt(T.surfaceAlt, dim = true)
                }
            } else if (countdowns.isEmpty()) {
                EmptyHome()
            } else {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 110.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(countdowns, key = { it.id }) { c ->
                        CountdownCard(
                            countdown = c,
                            onOpen = { nav.go(Route.Detail(c.id)) },
                            onCount = { counter.request(c) },
                            onEdit = { nav.go(Route.Edit(c.id)) },
                            onDuplicate = { store.duplicateCountdown(c.id)?.let { nav.go(Route.Detail(it)) } },
                            onReset = { confirmReset = c },
                            onDelete = { confirmDelete = c },
                        )
                    }
                }
            }
        }

        if (!store.loading) {
            ExtendedFloatingActionButton(
                onClick = { nav.go(Route.Edit(null)) },
                containerColor = T.brand,
                contentColor = T.onBrand,
                icon = { Icon(Icons.Rounded.Add, null) },
                text = { Text("New countdown", style = MaterialTheme.typography.labelLarge) },
                modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().reportsBottomAction().padding(20.dp),
            )
        }
    }

    confirmReset?.let { c ->
        ResetDialog(c, onConfirm = {
            store.resetCountdown(c.id); confirmReset = null
            undoable.offerUndo("${c.name} is back at ${formatCount(c.start)}")
        }, onDismiss = { confirmReset = null })
    }
    confirmDelete?.let { c ->
        DeleteDialog(c, onConfirm = {
            store.deleteCountdown(c.id); confirmDelete = null
            undoable.offerUndo("${c.name} deleted")
        }, onDismiss = { confirmDelete = null })
    }
}

@Composable
fun ResetDialog(c: Countdown, onConfirm: () -> Unit, onDismiss: () -> Unit) = ConfirmDialog(
    title = "Start ${c.name} over?",
    body = "Clears the ${countOf(c.done, c.unit)} counted so far and puts it back at ${formatCount(c.start)}. The countdown itself stays.",
    confirmLabel = "Start over",
    onConfirm = onConfirm,
    onDismiss = onDismiss,
)

@Composable
fun DeleteDialog(c: Countdown, onConfirm: () -> Unit, onDismiss: () -> Unit) = ConfirmDialog(
    title = "Delete ${c.name}?",
    body = if (c.done == 0) "Removes the countdown." else "Removes the countdown and the ${countOf(c.done, c.unit)} counted in it.",
    confirmLabel = "Delete",
    onConfirm = onConfirm,
    onDismiss = onDismiss,
)

@Composable
private fun CountdownCard(
    countdown: Countdown,
    onOpen: () -> Unit,
    onCount: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onReset: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val accent = accentAt(countdown.accent)
    val today = rememberToday()
    val zone = TimeZone.currentSystemDefault()
    val countedToday = countdown.countedOn(today, zone).isNotEmpty()

    Surface(onClick = onOpen, shape = RoundedCornerShape(22.dp), color = T.surface, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 18.dp, end = 8.dp, top = 14.dp, bottom = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(accent))
                Spacer(Modifier.width(10.dp))
                Text(
                    countdown.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = T.text,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                Box {
                    RoundIconButton(Icons.Rounded.MoreVert, "More", { menuOpen = true })
                    CountdownMenu(
                        expanded = menuOpen,
                        onDismiss = { menuOpen = false },
                        onEdit = { menuOpen = false; onEdit() },
                        onDuplicate = { menuOpen = false; onDuplicate() },
                        onReset = { menuOpen = false; onReset() },
                        onDelete = { menuOpen = false; onDelete() },
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 10.dp)) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        AnimatedContent(
                            targetState = countdown.left,
                            transitionSpec = {
                                (slideInVertically(tween(260)) { -it / 2 } + fadeIn(tween(200)))
                                    .togetherWith(slideOutVertically(tween(260)) { it / 2 } + fadeOut(tween(160)))
                            },
                            label = "left",
                        ) { left ->
                            Text(formatCount(left), style = Num.large, color = if (countdown.finished) T.done else T.text)
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (countdown.finished) "done" else "to go",
                            style = MaterialTheme.typography.bodyLarge,
                            color = T.textDim,
                            modifier = Modifier.padding(bottom = 4.dp),
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "of ${countOf(countdown.start, countdown.unit)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = T.textFaint,
                    )
                }
                CardCountButton(
                    accent = accent,
                    finished = countdown.finished,
                    countedToday = countedToday,
                    onClick = onCount,
                )
            }
            Spacer(Modifier.height(14.dp))
            ProgressLine(1f - countdown.progress, accent, Modifier.padding(end = 10.dp))
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 10.dp)) {
                if (countedToday && !countdown.finished) {
                    Icon(Icons.Rounded.Check, null, Modifier.size(14.dp), tint = T.done)
                    Spacer(Modifier.width(4.dp))
                    Text("Counted today", style = MaterialTheme.typography.labelMedium, color = T.done)
                } else {
                    Text(
                        "${formatCount(countdown.done)} done",
                        style = MaterialTheme.typography.labelMedium,
                        color = T.textFaint,
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    if (countdown.ticks.isEmpty()) "not started" else "last ${formatRelative(countdown.lastActivity)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = T.textFaint,
                )
            }
        }
    }
}

/**
 * The card's own count button, so a goal can be counted from the list without opening
 * it. Once today is counted it turns into a check — still tappable, because some goals
 * do take two in a day, but it asks first (see Counter).
 */
@Composable
private fun CardCountButton(accent: Color, finished: Boolean, countedToday: Boolean, onClick: () -> Unit) {
    if (finished) {
        Box(Modifier.size(56.dp).clip(CircleShape).background(T.doneSoft), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Check, "Goal reached", Modifier.size(26.dp), tint = T.done)
        }
        return
    }
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (countedToday) T.surfaceAlt else accent,
        modifier = Modifier.size(56.dp),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (countedToday) {
                Icon(Icons.Rounded.Check, "Counted today — count again", Modifier.size(24.dp), tint = T.done)
            } else {
                Text("−1", style = Num.medium, color = OnAccent)
            }
        }
    }
}

@Composable
fun CountdownMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onReset: () -> Unit,
    onDelete: () -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        containerColor = T.surfaceAlt,
        shape = RoundedCornerShape(18.dp),
    ) {
        MenuRow("Edit countdown", Icons.Rounded.Tune, T.text, onEdit)
        MenuRow("Duplicate", Icons.Rounded.ContentCopy, T.text, onDuplicate)
        MenuRow("Start over", Icons.Rounded.Restore, T.text, onReset)
        MenuRow("Delete", Icons.Rounded.DeleteOutline, T.danger, onDelete)
    }
}

@Composable
private fun EmptyHome() {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 40.dp).padding(bottom = 80.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CalendarArt(T.brand)
        Spacer(Modifier.height(28.dp))
        Text("Set a goal, count it down", style = MaterialTheme.typography.headlineMedium, color = T.text, textAlign = TextAlign.Center)
        Spacer(Modifier.height(10.dp))
        Text(
            "100 days of work, 30 runs, 12 books. The number you start from stays on screen — each tap takes one off it.",
            style = MaterialTheme.typography.bodyLarge,
            color = T.textDim,
            textAlign = TextAlign.Center,
        )
    }
}
