package com.clarohome.feature.communication

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.database.sqlite.SQLiteException
import android.net.Uri
import android.provider.ContactsContract
import android.util.Log
import androidx.core.content.ContextCompat

/** Resolves phone numbers to saved contact details using the Contacts provider; never invents names. */
internal class PhoneNumberNameResolver(private val context: Context) {
    private data class Details(val name: String?, val photoUri: String?)
    private val cache = HashMap<String, Details?>()

    fun resolve(number: String?): String? = details(number)?.name

    fun resolvePhotoUri(number: String?): String? = details(number)?.photoUri

    private fun details(number: String?): Details? {
        if (number.isNullOrBlank()) return null
        if (
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return null
        }
        if (!cache.containsKey(number)) cache[number] = lookup(number)
        return cache[number]
    }

    private fun lookup(number: String): Details? = try {
        context.contentResolver.query(
            Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(number),
            ),
            arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME, ContactsContract.PhoneLookup.PHOTO_URI),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                Details(
                    name = cursor.getString(0)?.ifBlank { null },
                    photoUri = cursor.getString(1)?.ifBlank { null },
                )
            } else {
                null
            }
        }
    } catch (_: SecurityException) {
        Log.w("ClaroContacts", "Contact lookup permission was denied.")
        null
    } catch (_: SQLiteException) {
        Log.w("ClaroContacts", "The contacts provider could not resolve a phone number.")
        null
    }
}
