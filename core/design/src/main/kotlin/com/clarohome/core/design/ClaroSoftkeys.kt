package com.clarohome.core.design

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Hardware-style keys of the Classic deck that a screen's action dock can take over so the
 * on-screen dock does not consume vertical space.
 */
enum class ClaroSoftkey {
    /** The green key (normally Call). Default for a primary dock button. */
    GREEN,

    /** The grey Menu key. Default for a secondary dock button. */
    MENU,

    /** The Back key, relabelled for the screen (for example Cancel). */
    BACK,

    /** The action already exists as the plain Back key, so the button is dropped. */
    HIDDEN,
}

@Stable
class ClaroSoftkeyAction internal constructor(val slot: ClaroSoftkey) {
    var label by mutableStateOf("")
        internal set
    var icon by mutableStateOf<ClaroIconKind?>(null)
        internal set
    var enabled by mutableStateOf(true)
        internal set
    var onClick by mutableStateOf({})
        internal set
}

/** Collects the dock actions of the visible screen for the Classic deck to render. */
@Stable
class ClaroSoftkeyHost {
    private val actions = mutableStateListOf<ClaroSoftkeyAction>()

    // The newest registration wins so an entering screen overrides a screen still animating out.
    fun actionFor(slot: ClaroSoftkey): ClaroSoftkeyAction? = actions.lastOrNull { it.slot == slot }

    internal fun register(action: ClaroSoftkeyAction) {
        actions.add(action)
    }

    internal fun unregister(action: ClaroSoftkeyAction) {
        actions.remove(action)
    }
}

/** Non-null only where a Classic deck is shown and consumes [ClaroSoftkeyHost] actions. */
val LocalClaroSoftkeyHost = staticCompositionLocalOf<ClaroSoftkeyHost?> { null }

internal val LocalClaroDockCollecting = compositionLocalOf { false }

/**
 * Registers a dock button as a Classic softkey. Returns true when the button was handed to the
 * deck and must not be drawn on screen.
 */
@Composable
internal fun registerClaroSoftkey(
    slot: ClaroSoftkey?,
    defaultSlot: ClaroSoftkey,
    label: String,
    softkeyLabel: String?,
    icon: ClaroIconKind?,
    enabled: Boolean,
    onClick: () -> Unit,
    keepOnScreen: Boolean = false,
): Boolean {
    val host = LocalClaroSoftkeyHost.current
    if (host == null || !LocalClaroDockCollecting.current || keepOnScreen) return false
    val resolved = slot ?: defaultSlot
    if (resolved == ClaroSoftkey.HIDDEN) return true
    val action = remember(resolved) { ClaroSoftkeyAction(resolved) }
    SideEffect {
        action.label = softkeyLabel ?: label
        action.icon = icon
        action.enabled = enabled
        action.onClick = onClick
    }
    DisposableEffect(host, action) {
        host.register(action)
        onDispose { host.unregister(action) }
    }
    return true
}
