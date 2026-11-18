package com.automatelinux.countdown.ui

import androidx.compose.ui.window.DialogProperties

// decorFitsSystemWindows = false: the dialog window no longer consumes the insets, so
// WindowInsets.ime reaches its content and imePadding() can lift it above the keyboard.
actual fun imeAwareDialogProperties(): DialogProperties =
    DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
