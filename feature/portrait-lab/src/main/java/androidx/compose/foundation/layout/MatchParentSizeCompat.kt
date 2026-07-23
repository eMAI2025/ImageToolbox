/*
 * Temporary compatibility adapter for the Compose version used by ImageToolbox.
 * Remove after PortraitLabContent uses fillMaxSize directly.
 */

package androidx.compose.foundation.layout

import androidx.compose.ui.Modifier

/** Equivalent to fillMaxSize for children that already inherit Box constraints. */
fun Modifier.matchParentSize(): Modifier = fillMaxSize()
