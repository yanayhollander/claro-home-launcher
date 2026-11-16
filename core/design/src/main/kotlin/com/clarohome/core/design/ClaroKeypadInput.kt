package com.clarohome.core.design

import android.os.SystemClock

data class ClassicMultiTapState(
    val fieldKey: String,
    val digit: Char,
    val cycleIndex: Int,
    val replaceIndex: Int,
    val committedValue: String,
    val timestampElapsedMillis: Long,
)

private val classicMultiTapAlphabets = mapOf(
    '1' to ".,?!1",
    '2' to "abc2",
    '3' to "def3",
    '4' to "ghi4",
    '5' to "jkl5",
    '6' to "mno6",
    '7' to "pqrs7",
    '8' to "tuv8",
    '9' to "wxyz9",
    '0' to " 0",
    '*' to ".,?!*",
)

fun applyClassicKeypadInput(
    currentValue: String,
    digit: Char,
    fieldKey: String,
    numericMode: Boolean,
    phoneNumberField: Boolean = false,
    previousTap: ClassicMultiTapState?,
    selectionStart: Int = currentValue.length,
    selectionEnd: Int = selectionStart,
): Pair<String, ClassicMultiTapState?> {
    require(selectionStart in 0..currentValue.length && selectionEnd in 0..currentValue.length)
    val start = minOf(selectionStart, selectionEnd)
    val end = maxOf(selectionStart, selectionEnd)
    val prefix = currentValue.take(start)
    val suffix = currentValue.drop(end)
    if (phoneNumberField) {
        val inserted = when {
            digit == '*' && prefix.isEmpty() -> "+"
            digit in "0123456789*#" -> digit.toString()
            else -> ""
        }
        return (prefix + inserted + suffix) to null
    }
    if (numericMode) return (prefix + digit + suffix) to null

    val alphabet = classicMultiTapAlphabets[digit] ?: return currentValue to null
    val now = SystemClock.elapsedRealtime()
    val continuing = previousTap != null &&
        previousTap.fieldKey == fieldKey &&
        previousTap.digit == digit &&
        (now - previousTap.timestampElapsedMillis) < 1_000L &&
        previousTap.committedValue == currentValue &&
        previousTap.replaceIndex in currentValue.indices &&
        start == end && start == previousTap.replaceIndex + 1
    val replaceIndex = if (continuing && previousTap != null) previousTap.replaceIndex else start
    val nextCycleIndex = if (continuing && previousTap != null) {
        (previousTap.cycleIndex + 1) % alphabet.length
    } else {
        0
    }
    val insertionPrefix = currentValue.take(replaceIndex)
    val shouldCapitalize = insertionPrefix.isEmpty() ||
        insertionPrefix.endsWith(". ") ||
        insertionPrefix.endsWith("? ") ||
        insertionPrefix.endsWith("! ")
    val rawChar = alphabet[nextCycleIndex]
    val nextChar = if (shouldCapitalize && rawChar.isLetter()) rawChar.uppercaseChar() else rawChar
    val updatedValue = insertionPrefix + nextChar + suffix
    return updatedValue to ClassicMultiTapState(
        fieldKey = fieldKey,
        digit = digit,
        cycleIndex = nextCycleIndex,
        replaceIndex = replaceIndex,
        committedValue = updatedValue,
        timestampElapsedMillis = now,
    )
}
