package com.automatelinux.countdown.ui

import androidx.compose.ui.window.DialogProperties

/**
 * Properties for a dialog that holds a text field. The platform dialog window does not
 * shrink when the keyboard opens, so a tall dialog's buttons end up under the keyboard
 * (the day dialog's Save, 2026-11-18). These hand the keyboard's insets to the dialog's
 * content instead, where `Modifier.imePadding()` keeps the whole dialog above it.
 */
expect fun imeAwareDialogProperties(): DialogProperties
