package com.clarohome.core.design

import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.material3.ripple
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DelegatingNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * Clear D-pad/keyboard focus marker used by every clickable surface in Claro:
 * a thick amber ring with a dark inner edge and a light tint, so the focused
 * item is obvious on light and dark backgrounds. Touch ripples are preserved.
 */
internal object ClaroFocusIndication : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode =
        ClaroFocusIndicationNode(interactionSource)

    override fun hashCode(): Int = -1

    override fun equals(other: Any?): Boolean = other === this
}

private val FocusRingColor = Color(0xFFFFB000)
private val FocusInnerColor = Color(0xFF111827)

private class ClaroFocusIndicationNode(
    private val interactionSource: InteractionSource,
) : DelegatingNode(), DrawModifierNode {
    private var focused = false

    init {
        delegate(ripple().create(interactionSource))
    }

    override fun onAttach() {
        coroutineScope.launch {
            val active = mutableSetOf<FocusInteraction.Focus>()
            interactionSource.interactions.collect { interaction ->
                when (interaction) {
                    is FocusInteraction.Focus -> active.add(interaction)
                    is FocusInteraction.Unfocus -> active.remove(interaction.focus)
                }
                val nowFocused = active.isNotEmpty()
                if (nowFocused != focused) {
                    focused = nowFocused
                    invalidateDraw()
                }
            }
        }
    }

    override fun ContentDrawScope.draw() {
        drawContent()
        if (focused) drawClaroFocusRing()
    }
}

private fun DrawScope.drawClaroFocusRing() {
    drawRect(color = FocusRingColor.copy(alpha = 0.22f), style = Fill)
    val outer = 4.dp.toPx()
    val inner = 2.dp.toPx()
    drawRect(
        color = FocusRingColor,
        topLeft = Offset(outer / 2, outer / 2),
        size = Size(size.width - outer, size.height - outer),
        style = Stroke(width = outer),
    )
    drawRect(
        color = FocusInnerColor,
        topLeft = Offset(outer + inner / 2, outer + inner / 2),
        size = Size(size.width - 2 * outer - inner, size.height - 2 * outer - inner),
        style = Stroke(width = inner),
    )
}

/**
 * Draws the Claro focus ring over a Material button (which brings its own ripple and ignores
 * [LocalIndication]). Apply it to the button's own modifier chain.
 */
fun Modifier.claroFocusRing(): Modifier = composed {
    var focused by remember { mutableStateOf(false) }
    this
        .onFocusChanged { focused = it.hasFocus }
        .drawWithContent {
            drawContent()
            if (focused) drawClaroFocusRing()
        }
}
