package com.automatelinux.countdown.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.automatelinux.countdown.data.Countdown
import com.automatelinux.countdown.data.Tick
import com.automatelinux.countdown.ui.theme.Num
import com.automatelinux.countdown.ui.theme.T

/** How the amount relates to VAT, in the words it is said: "1,500 plus VAT". */
private val VatChoices = listOf("plus" to "+ VAT", "included" to "incl. VAT", "none" to "no VAT")

/** "1500" for 1500.0, "1500.5" otherwise — the way the amount was typed. */
fun formatAmount(a: Double): String = if (a % 1.0 == 0.0) a.toLong().toString() else a.toString()

/** "₪1500 + VAT" for a row; empty when the day has no amount. */
fun payLabel(t: Tick): String {
    val a = t.amount ?: return ""
    return "₪${formatAmount(a)}" + when (t.vat) { "plus" -> " + VAT"; "included" -> " incl. VAT"; else -> "" }
}

/**
 * What a counted day was and what it paid — asked the moment it is counted, so the day
 * never has to be filled in afterwards. Its job comes first (recent ones are a tap away),
 * then the amount, prefilled from the last paid day: on a usual day that is one tap on
 * Save. The amount becomes the day's income in tazrim.
 *
 * Also opened by tapping a day in the history, to change either afterwards.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DayDialog(
    title: String,
    countdown: Countdown,
    tick: Tick,
    isNew: Boolean,
    onSave: (job: String, amount: Double?, vat: String) -> Unit,
    onDismiss: () -> Unit,
) {
    // The last paid day other than this one: what a new day most likely paid too.
    val lastPaid = remember(countdown, tick.id) {
        countdown.chronological.lastOrNull { it.id != tick.id && it.amount != null }
    }
    // Recent jobs, newest first, each once — tapping one fills in what it paid as well.
    val recent = remember(countdown, tick.id) {
        countdown.chronological.asReversed()
            .filter { it.id != tick.id && it.title.isNotBlank() }
            .distinctBy { it.title.trim() }
            .take(4)
    }
    val start = if (isNew) lastPaid else tick.takeIf { it.amount != null }
    var job by remember { mutableStateOf(tick.title) }
    var amountText by remember { mutableStateOf(start?.amount?.let(::formatAmount) ?: "") }
    var vat by remember { mutableStateOf(start?.vat?.ifBlank { null } ?: "plus") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    val amount = amountText.replace(",", "").toDoubleOrNull()?.takeIf { it > 0 }
    val amountInvalid = amountText.isNotBlank() && amount == null
    fun save() { if (!amountInvalid) onSave(job, amount, vat) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = T.surface,
        titleContentColor = T.text,
        shape = RoundedCornerShape(24.dp),
        title = {
            Column {
                Text(title, style = MaterialTheme.typography.titleLarge)
                if (isNew) Text("Counted — what was it?", style = MaterialTheme.typography.bodyMedium, color = T.textDim)
            }
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Label("What was the day")
                Box(Modifier.fillMaxWidth()) {
                    InputBox {
                        if (job.isEmpty()) Text("Job, client, place", style = MaterialTheme.typography.bodyLarge, color = T.textFaint)
                        BasicTextField(
                            value = job,
                            onValueChange = { job = it.take(80) },
                            textStyle = MaterialTheme.typography.bodyLarge.copy(color = T.text),
                            cursorBrush = SolidColor(T.brandInk),
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
                            modifier = Modifier.fillMaxWidth().focusRequester(focus),
                        )
                    }
                }
                if (recent.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        recent.forEach { r ->
                            Pill(r.title, selected = job.trim() == r.title.trim()) {
                                job = r.title
                                r.amount?.let { amountText = formatAmount(it); vat = r.vat.ifBlank { "plus" } }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(18.dp))
                Label("What it paid")
                InputBox {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("₪", style = Num.medium, color = T.textDim)
                        Spacer(Modifier.width(8.dp))
                        Box(Modifier.weight(1f)) {
                            if (amountText.isEmpty()) Text("Nothing", style = Num.medium, color = T.textFaint)
                            BasicTextField(
                                value = amountText,
                                onValueChange = { v -> amountText = v.filter { it.isDigit() || it == '.' || it == ',' }.take(9) },
                                singleLine = true,
                                textStyle = Num.medium.copy(color = if (amountInvalid) T.danger else T.text),
                                cursorBrush = SolidColor(T.brandInk),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = { save() }),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    VatChoices.forEach { (key, label) ->
                        Pill(label, selected = vat == key, modifier = Modifier.weight(1f)) { vat = key }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    when {
                        amountInvalid -> "That is not an amount"
                        amount != null -> "Goes into tazrim as this day's income"
                        else -> "No amount — nothing goes into tazrim"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = if (amountInvalid) T.danger else T.textFaint,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = ::save, enabled = !amountInvalid) {
                Text("Save", color = if (amountInvalid) T.textFaint else T.brandInk, style = MaterialTheme.typography.labelLarge)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(if (isNew) "Later" else "Cancel", color = T.textDim, style = MaterialTheme.typography.labelLarge)
            }
        },
    )
}

@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = T.textFaint, modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))
}

@Composable
private fun InputBox(content: @Composable () -> Unit) {
    Surface(shape = RoundedCornerShape(14.dp), color = T.surfaceAlt, modifier = Modifier.fillMaxWidth()) {
        Box(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) { content() }
    }
}

@Composable
private fun Pill(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(12.dp), color = if (selected) T.brandSoft else T.surfaceAlt, modifier = modifier) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) T.brandInk else T.textDim,
                maxLines = 1,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            )
        }
    }
}
