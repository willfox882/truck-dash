package com.truckdash.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import com.truckdash.core.PaneSpec

/** Provided by [DesignScale]. Defaults to the full portrait shape so previews and tests need no setup. */
val LocalPaneSpec = staticCompositionLocalOf { PaneSpec.Full }

/** Picks a value for the current pane: [full] normally, [compact] for a half pane, [tiny] (defaults to compact) for a strip. */
@Composable
fun <T> pane(full: T, compact: T, tiny: T = compact): T {
    val p = LocalPaneSpec.current
    return when {
        p.tiny -> tiny
        p.compact -> compact
        else -> full
    }
}
