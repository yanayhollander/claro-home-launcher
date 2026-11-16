package com.clarohome.core.design

import android.text.InputType
import android.util.Log
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.InterceptPlatformTextInput
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.PlatformTextInputInterceptor
import kotlinx.coroutines.CompletableDeferred

val LocalClaroOpenKeyboard = staticCompositionLocalOf<(() -> Unit)?> { null }
val LocalClaroKeypadInput = staticCompositionLocalOf<((Char) -> Unit)?> { null }
val LocalClaroDeleteText = staticCompositionLocalOf<(() -> Boolean)?> { null }
val LocalClaroKeypadNumericMode = staticCompositionLocalOf<Boolean?> { null }

private class KeyboardRequest {
    var open by mutableStateOf<(() -> Unit)?>(null)
    var input by mutableStateOf<((Char) -> Unit)?>(null)
    var delete by mutableStateOf<(() -> Boolean)?>(null)
    var numericMode by mutableStateOf<Boolean?>(null)
}

@Composable
fun ClaroKeyboardContainer(
    manualKeyboard: Boolean,
    content: @Composable () -> Unit,
) {
    val keyboard = remember { KeyboardRequest() }
    val softwareKeyboard = LocalSoftwareKeyboardController.current
    val interceptor = remember(manualKeyboard, keyboard, softwareKeyboard) {
        PlatformTextInputInterceptor { request, nextHandler ->
            if (!manualKeyboard) {
                nextHandler.startInputMethod(request)
            } else {
                val requested = CompletableDeferred<Unit>()
                val open: () -> Unit = {
                    if (!requested.complete(Unit)) softwareKeyboard?.show()
                }
                val editorInfo = EditorInfo()
                val connection = request.createInputConnection(editorInfo)
                keyboard.open = open
                val keypad = ManualKeypadInput(connection, editorInfo) {
                    keyboard.numericMode = it
                }
                keyboard.input = keypad::input
                keyboard.delete = keypad::delete
                try {
                    requested.await()
                    nextHandler.startInputMethod(request)
                } finally {
                    connection.closeConnection()
                    if (keyboard.open === open) {
                        keyboard.open = null
                        keyboard.input = null
                        keyboard.delete = null
                        keyboard.numericMode = null
                    }
                }
            }
        }
    }
    InterceptPlatformTextInput(interceptor) {
        CompositionLocalProvider(
            LocalClaroOpenKeyboard provides if (manualKeyboard) keyboard.open else null,
            LocalClaroKeypadInput provides if (manualKeyboard) keyboard.input else null,
            LocalClaroDeleteText provides if (manualKeyboard) keyboard.delete else null,
            LocalClaroKeypadNumericMode provides if (manualKeyboard) keyboard.numericMode else null,
            content = content,
        )
    }
}

private class ManualKeypadInput(
    private val connection: InputConnection,
    editorInfo: EditorInfo,
    private val onModeChange: (Boolean) -> Unit,
) {
    private val inputClass = editorInfo.inputType and InputType.TYPE_MASK_CLASS
    private val phoneNumberField = inputClass == InputType.TYPE_CLASS_PHONE
    private val numericField = phoneNumberField ||
        inputClass == InputType.TYPE_CLASS_NUMBER ||
        inputClass == InputType.TYPE_CLASS_DATETIME
    private var numericMode = numericField
    private var previousTap: ClassicMultiTapState? = null

    init {
        onModeChange(numericMode)
    }

    fun input(digit: Char) {
        if (digit == '#' && !numericField) {
            numericMode = !numericMode
            previousTap = null
            onModeChange(numericMode)
            return
        }
        val extracted = connection.getExtractedText(ExtractedTextRequest(), 0)
        val value = extracted?.text?.toString()
        if (
            extracted == null || value == null || extracted.startOffset != 0 ||
            extracted.selectionStart !in 0..value.length ||
            extracted.selectionEnd !in 0..value.length
        ) {
            Log.w("ClaroKeypad", "The focused field did not provide editable text.")
            return
        }
        val (updated, nextTap) = applyClassicKeypadInput(
            currentValue = value,
            digit = digit,
            fieldKey = "focused-field",
            numericMode = numericMode,
            phoneNumberField = phoneNumberField,
            previousTap = previousTap,
            selectionStart = extracted.selectionStart,
            selectionEnd = extracted.selectionEnd,
        )
        val start = nextTap?.replaceIndex
            ?: minOf(extracted.selectionStart, extracted.selectionEnd)
        val end = maxOf(extracted.selectionStart, extracted.selectionEnd)
        val inserted = updated.substring(start, updated.length - (value.length - end))
        connection.beginBatchEdit()
        val accepted = try {
            connection.setSelection(start, end) && connection.commitText(inserted, 1)
        } finally {
            connection.endBatchEdit()
        }
        if (accepted) {
            previousTap = nextTap
        } else {
            previousTap = null
            Log.w("ClaroKeypad", "The focused field rejected keypad input.")
        }
    }

    fun delete(): Boolean {
        previousTap = null
        val extracted = connection.getExtractedText(ExtractedTextRequest(), 0)
        val value = extracted?.text
        if (
            extracted == null || value == null || extracted.startOffset != 0 ||
            extracted.selectionStart !in 0..value.length ||
            extracted.selectionEnd !in 0..value.length
        ) {
            Log.w("ClaroKeypad", "The focused field did not provide text for deletion.")
            return true
        }
        val start = minOf(extracted.selectionStart, extracted.selectionEnd)
        val end = maxOf(extracted.selectionStart, extracted.selectionEnd)
        if (start == 0 && end == 0) return false
        connection.beginBatchEdit()
        val accepted = try {
            if (start != end) {
                connection.setSelection(start, end) && connection.commitText("", 1)
            } else {
                connection.deleteSurroundingTextInCodePoints(1, 0)
            }
        } finally {
            connection.endBatchEdit()
        }
        if (!accepted) Log.w("ClaroKeypad", "The focused field rejected text deletion.")
        return true
    }
}
