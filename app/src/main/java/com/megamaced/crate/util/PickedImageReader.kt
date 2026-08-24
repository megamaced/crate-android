package com.megamaced.crate.util

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/** Largest picked image accepted, in bytes. */
const val MAX_PICKED_IMAGE_BYTES: Long = 25L * 1024 * 1024

/**
 * Reads an image the user picked through the system picker.
 *
 * Every call touches a ContentProvider, and for a cloud-only Google Photos
 * entry that means a synchronous network download — so none of it may happen
 * on the main thread, and the bytes are read once at upload time rather than
 * held in UI state.
 */
interface PickedImageReader {
    /** MIME type the provider reports, or null when it reports none. */
    suspend fun mimeType(uri: String): String?

    /** Declared byte length, or null when the provider doesn't report one. */
    suspend fun length(uri: String): Long?

    /** Full contents, or null when the image can't be read or is empty. */
    suspend fun read(uri: String): ByteArray?
}

@Singleton
class ContentResolverImageReader
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : PickedImageReader {
        override suspend fun mimeType(uri: String): String? =
            withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.getType(Uri.parse(uri)) }.getOrNull()
            }

        override suspend fun length(uri: String): Long? =
            withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver
                        .openAssetFileDescriptor(Uri.parse(uri), "r")
                        ?.use { it.length }
                        ?.takeIf { it >= 0 }
                }.getOrNull()
            }

        override suspend fun read(uri: String): ByteArray? =
            withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver
                        .openInputStream(Uri.parse(uri))
                        ?.use { it.readBytes() }
                        ?.takeIf { it.isNotEmpty() }
                }.onFailure {
                    // A picker grant lasts only for the current process, so a
                    // Uri restored after process death can legitimately fail.
                    Timber.w(it, "Couldn't read picked image")
                }.getOrNull()
            }
    }
