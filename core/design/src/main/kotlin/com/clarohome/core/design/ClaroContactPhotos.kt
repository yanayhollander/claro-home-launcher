package com.clarohome.core.design

import android.content.ContentResolver
import android.database.ContentObserver
import android.database.sqlite.SQLiteException
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

internal val LocalClaroContactPhotoRevision = compositionLocalOf { 0L }

@Composable
fun ClaroContactPhotos(content: @Composable () -> Unit) {
    val resolver = LocalContext.current.contentResolver
    val revision = remember(resolver) { mutableLongStateOf(0L) }
    DisposableEffect(resolver) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                revision.longValue++
            }
        }
        val registered = try {
            resolver.registerContentObserver(ContactsContract.AUTHORITY_URI, true, observer)
            true
        } catch (_: SecurityException) {
            Log.w("ClaroAvatar", "Contact changes could not be observed.")
            false
        }
        onDispose { if (registered) resolver.unregisterContentObserver(observer) }
    }
    CompositionLocalProvider(LocalClaroContactPhotoRevision provides revision.longValue, content = content)
}

internal fun ContentResolver.currentContactPhoto(
    phoneNumber: String?,
    lookupKey: String?,
    fallbackUri: String?,
): String? {
    val url = when {
        lookupKey != null -> Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_LOOKUP_URI, lookupKey)
        phoneNumber != null -> Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(phoneNumber))
        else -> return fallbackUri
    }
    return try {
        query(
            url,
            arrayOf(ContactsContract.Contacts.PHOTO_URI, ContactsContract.Contacts.PHOTO_THUMBNAIL_URI),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                cursor.getString(0)?.ifBlank { null } ?: cursor.getString(1)?.ifBlank { null }
            } else {
                null
            }
        } ?: fallbackUri.takeIf { lookupKey != null }
    } catch (_: SecurityException) {
        Log.w("ClaroAvatar", "Contact photo lookup permission was denied.")
        fallbackUri
    } catch (_: SQLiteException) {
        Log.w("ClaroAvatar", "The contacts provider could not look up the photo.")
        fallbackUri
    } catch (_: IllegalArgumentException) {
        Log.w("ClaroAvatar", "The contacts provider rejected the photo lookup.")
        fallbackUri
    }
}
