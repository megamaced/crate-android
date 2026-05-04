package com.macebox.crate.ui.network

import androidx.compose.runtime.compositionLocalOf

/**
 * True when the device is online. Provided at the top of the composition by
 * [com.macebox.crate.ui.navigation.CrateScaffold]; defaults to true so screens
 * rendered outside the scaffold (e.g. previews) do not appear in an
 * artificially-offline state.
 */
val LocalIsOnline = compositionLocalOf { true }
