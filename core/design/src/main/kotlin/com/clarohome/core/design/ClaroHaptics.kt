package com.clarohome.core.design

import android.content.Context
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView

enum class HapticCue {
    KEYPAD_DIGIT,
    DPAD_STEP,
    SOFTKEY,
    CONFIRM_HEAVY,
    ALERT_DOUBLE,
}

interface ClaroHaptics {
    val enabled: Boolean
    fun perform(cue: HapticCue)

    fun keyClick() = perform(HapticCue.KEYPAD_DIGIT)
    fun dpadStep() = perform(HapticCue.DPAD_STEP)
    fun softkey() = perform(HapticCue.SOFTKEY)
    fun confirmHeavy() = perform(HapticCue.CONFIRM_HEAVY)
    fun alertDouble() = perform(HapticCue.ALERT_DOUBLE)
}

object NoOpClaroHaptics : ClaroHaptics {
    override val enabled: Boolean = false
    override fun perform(cue: HapticCue) = Unit
}

val LocalClaroHaptics = staticCompositionLocalOf<ClaroHaptics> { NoOpClaroHaptics }

class RecordingClaroHaptics(
    override val enabled: Boolean = true,
    private val sink: (HapticCue) -> Unit = {},
) : ClaroHaptics {
    val recordedCues = mutableListOf<HapticCue>()

    override fun perform(cue: HapticCue) {
        if (!enabled) return
        recordedCues += cue
        sink(cue)
    }
}

private class AndroidClaroHaptics(
    context: Context,
    private val view: View,
    override val enabled: Boolean,
) : ClaroHaptics {
    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        context.getSystemService(Vibrator::class.java)
    }

    override fun perform(cue: HapticCue) {
        if (!enabled) return
        val activeVibrator = vibrator?.takeIf { it.hasVibrator() }
        if (activeVibrator != null) {
            val effectId = when (cue) {
                HapticCue.KEYPAD_DIGIT -> VibrationEffect.EFFECT_CLICK
                HapticCue.DPAD_STEP -> VibrationEffect.EFFECT_TICK
                HapticCue.SOFTKEY -> VibrationEffect.EFFECT_CLICK
                HapticCue.CONFIRM_HEAVY -> VibrationEffect.EFFECT_HEAVY_CLICK
                HapticCue.ALERT_DOUBLE -> VibrationEffect.EFFECT_DOUBLE_CLICK
            }
            val effect = VibrationEffect.createPredefined(effectId)
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    activeVibrator.vibrate(
                        effect,
                        VibrationAttributes.createForUsage(VibrationAttributes.USAGE_TOUCH),
                    )
                } else {
                    vibrateLegacy(activeVibrator, effect)
                }
            }.onFailure {
                performViewFallback(cue)
            }
        } else {
            performViewFallback(cue)
        }
    }

    private fun performViewFallback(cue: HapticCue) {
        val feedbackConstant = when (cue) {
            HapticCue.KEYPAD_DIGIT -> HapticFeedbackConstants.KEYBOARD_TAP
            HapticCue.DPAD_STEP -> HapticFeedbackConstants.CLOCK_TICK
            HapticCue.SOFTKEY -> HapticFeedbackConstants.VIRTUAL_KEY
            HapticCue.CONFIRM_HEAVY -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                HapticFeedbackConstants.CONFIRM
            } else {
                HapticFeedbackConstants.LONG_PRESS
            }
            HapticCue.ALERT_DOUBLE -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                HapticFeedbackConstants.REJECT
            } else {
                HapticFeedbackConstants.LONG_PRESS
            }
        }
        view.performHapticFeedback(feedbackConstant)
    }

    @Suppress("DEPRECATION")
    private fun vibrateLegacy(target: Vibrator, effect: VibrationEffect) {
        target.vibrate(effect)
    }
}

@Composable
fun rememberClaroHaptics(enabled: Boolean = true): ClaroHaptics {
    val context = LocalContext.current
    val view = LocalView.current
    return remember(context, view, enabled) {
        AndroidClaroHaptics(context = context, view = view, enabled = enabled)
    }
}
