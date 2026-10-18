package com.automatelinux.countdown.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.automatelinux.countdown.Navigator
import com.automatelinux.countdown.Route
import com.automatelinux.countdown.reportsBottomAction
import com.automatelinux.countdown.data.CountdownStore
import com.automatelinux.countdown.data.MaxNameLength
import com.automatelinux.countdown.data.UnitSuggestions
import com.automatelinux.countdown.data.countOf
import com.automatelinux.countdown.data.formatCount
import com.automatelinux.countdown.ui.components.ScreenHeader
import com.automatelinux.countdown.ui.components.SectionLabel
import com.automatelinux.countdown.ui.theme.AccentPalette
import com.automatelinux.countdown.ui.theme.Num
import com.automatelinux.countdown.ui.theme.OnAccent
import com.automatelinux.countdown.ui.theme.T
import com.automatelinux.countdown.ui.theme.accentAt

private val StartPresets = listOf(7, 30, 66, 100, 365)

/** A suggestion is a whole goal, not just a name: tapping "Read 12 books" sets 12 and books too. */
private data class Suggestion(val name: String, val start: Int, val unit: String)

private val Suggestions = listOf(
    Suggestion("100 days of work", 100, "days"),
    Suggestion("30 days of running", 30, "days"),
    Suggestion("Read 12 books", 12, "books"),
    Suggestion("66 days of practice", 66, "days"),
)

/** The same backstop as the server's, so the screen never offers what the server refuses. */
private const val MaxStart = 100_000

/**
 * New and edit are one screen. The starting number is the heart of it, so it is the
 * biggest thing here, with the usual goal lengths one tap away.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditScreen(store: CountdownStore, nav: Navigator, countdownId: String?) {
    val existing = store.countdown(countdownId)
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var startText by remember { mutableStateOf(existing?.start?.toString() ?: "100") }
    var unit by remember { mutableStateOf(existing?.unit ?: "days") }
    var accent by remember { mutableStateOf(existing?.accent ?: (store.countdowns.size % AccentPalette.size)) }
    var oncePerDay by remember { mutableStateOf(existing?.oncePerDay ?: true) }
    val nameFocus = remember { FocusRequester() }
    val focus = LocalFocusManager.current

    LaunchedEffect(Unit) { if (existing == null) nameFocus.requestFocus() }

    val start = startText.toIntOrNull()
    val startValid = start != null && start in 1..MaxStart
    val canSave = name.isNotBlank() && startValid && unit.isNotBlank()

    fun save() {
        if (!canSave || start == null) return
        if (existing == null) {
            val id = store.createCountdown(name, start, unit, accent, oncePerDay)
            nav.replaceTop(Route.Detail(id))
        } else {
            store.updateCountdown(existing.id, name, start, unit, accent, oncePerDay)
            nav.back()
        }
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        ScreenHeader(
            title = if (existing == null) "New countdown" else "Edit countdown",
            subtitle = if (existing == null) "What are you counting down?" else existing.name,
            onBack = { nav.back() },
        )

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            Field {
                if (name.isEmpty()) Text("Name it", style = Num.medium, color = T.textFaint)
                BasicTextField(
                    value = name,
                    onValueChange = { name = it.take(MaxNameLength) },
                    singleLine = true,
                    textStyle = Num.medium.copy(color = T.text),
                    cursorBrush = SolidColor(accentAt(accent)),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { focus.clearFocus() }),
                    modifier = Modifier.fillMaxWidth().focusRequester(nameFocus),
                )
            }
            if (existing == null && name.isBlank()) {
                Spacer(Modifier.height(12.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Suggestions.forEach { s ->
                        Chip(s.name, selected = false) {
                            name = s.name; startText = s.start.toString(); unit = s.unit
                            focus.clearFocus()
                        }
                    }
                }
            }

            Spacer(Modifier.height(26.dp))
            SectionLabel("Count down from")
            Spacer(Modifier.height(10.dp))
            Field {
                Row(verticalAlignment = Alignment.Bottom) {
                    BasicTextField(
                        value = startText,
                        onValueChange = { v -> startText = v.filter { it.isDigit() }.take(6) },
                        singleLine = true,
                        textStyle = Num.large.copy(color = if (startValid || startText.isEmpty()) T.text else T.danger),
                        cursorBrush = SolidColor(accentAt(accent)),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
                        modifier = Modifier.weight(1f),
                    )
                    Text(unit.ifBlank { "…" }, style = Num.medium, color = T.textDim, modifier = Modifier.padding(bottom = 4.dp))
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StartPresets.forEach { n ->
                    Chip(formatCount(n), selected = start == n, modifier = Modifier.weight(1f)) {
                        startText = n.toString(); focus.clearFocus()
                    }
                }
            }
            if (!startValid && startText.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text("Pick a number from 1 to ${formatCount(MaxStart)}", style = MaterialTheme.typography.labelMedium, color = T.danger)
            }
            // Lowering the start under what is already done is allowed — it simply reads
            // as finished — but it should not come as a surprise.
            if (existing != null && start != null && startValid && start <= existing.done) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "${countOf(existing.done, existing.unit)} already counted — this will show as reached.",
                    style = MaterialTheme.typography.labelMedium,
                    color = T.textDim,
                )
            }

            Spacer(Modifier.height(26.dp))
            SectionLabel("Counting")
            Spacer(Modifier.height(10.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                UnitSuggestions.forEach { u -> Chip(u, selected = unit == u) { unit = u; focus.clearFocus() } }
            }
            Spacer(Modifier.height(10.dp))
            Field(compact = true) {
                if (unit.isEmpty() || unit in UnitSuggestions) {
                    Text(if (unit.isEmpty()) "or type your own — books, km, chapters" else "or type your own", style = MaterialTheme.typography.bodyLarge, color = T.textFaint)
                }
                BasicTextField(
                    value = if (unit in UnitSuggestions) "" else unit,
                    onValueChange = { unit = it.lowercase().take(24) },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = T.text),
                    cursorBrush = SolidColor(accentAt(accent)),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(Modifier.height(26.dp))
            SectionLabel("Colour")
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AccentPalette.forEachIndexed { index, color ->
                    val selected = index == accent
                    Surface(
                        onClick = { accent = index },
                        color = color,
                        shape = CircleShape,
                        modifier = Modifier
                            .size(44.dp)
                            .border(if (selected) 3.dp else 0.dp, if (selected) T.text else Color.Transparent, CircleShape),
                    ) {
                        if (selected) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Icon(Icons.Rounded.Check, null, Modifier.size(20.dp), tint = OnAccent)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(26.dp))
            Surface(
                onClick = { oncePerDay = !oncePerDay },
                shape = RoundedCornerShape(18.dp),
                color = T.surface,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(Modifier.padding(start = 18.dp, end = 14.dp, top = 14.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Once a day", style = MaterialTheme.typography.titleMedium, color = T.text)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "A second count on the same day asks first, so a double tap can't cost you a day.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = T.textDim,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Switch(
                        checked = oncePerDay,
                        onCheckedChange = { oncePerDay = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = T.onBrand, checkedTrackColor = T.brand,
                            uncheckedThumbColor = T.textFaint, uncheckedTrackColor = T.surfaceAlt, uncheckedBorderColor = T.line,
                        ),
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }

        Surface(
            onClick = ::save,
            enabled = canSave,
            shape = RoundedCornerShape(20.dp),
            color = if (canSave) T.brand else T.surfaceAlt,
            modifier = Modifier.fillMaxWidth().navigationBarsPadding().reportsBottomAction()
                .padding(horizontal = 16.dp, vertical = 10.dp).height(58.dp),
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    when {
                        existing != null -> "Save changes"
                        start != null && startValid && unit.isNotBlank() -> "Start at ${countOf(start, unit)}"
                        else -> "Start countdown"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = if (canSave) T.onBrand else T.textFaint,
                )
            }
        }
    }
}

@Composable
private fun Field(compact: Boolean = false, content: @Composable () -> Unit) {
    Surface(shape = RoundedCornerShape(18.dp), color = T.surface, modifier = Modifier.fillMaxWidth()) {
        Box(Modifier.padding(horizontal = 18.dp, vertical = if (compact) 14.dp else 18.dp)) { content() }
    }
}

@Composable
private fun Chip(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(13.dp),
        color = if (selected) T.brandSoft else T.surfaceAlt,
        modifier = modifier,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) T.brandInk else T.textDim,
                maxLines = 1,
                modifier = Modifier.padding(horizontal = 13.dp, vertical = 10.dp),
            )
        }
    }
}
