package com.clarohome.feature.communication

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import androidx.core.content.ContextCompat

/** Resolves phone numbers to saved contact names using the Contacts provider; never invents names. */
internal class PhoneNumberNameResolver(private val context: Context) {
    private val cache = HashMap<String, String?>()

    fun resolve(number: String?): String? {
        if (number.isNullOrBlank()) return null
        if (
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return null
        }
        return cache.getOrPut(number) { lookup(number) }
    }

    private fun lookup(number: String): String? = runCatching {
        context.contentResolver.query(
            Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(number),
            ),
            arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0)?.ifBlank { null } else null
        }
    }.getOrNull()
}
