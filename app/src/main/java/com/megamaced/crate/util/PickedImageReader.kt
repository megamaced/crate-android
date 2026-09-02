package com.megamaced.crate.util

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.io.InputStream
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

    /**
     * Full contents, bounded by [MAX_PICKED_IMAGE_BYTES].
     *
     * The up-front check in the picker can only use the length the provider
     * declares, and cloud/document providers routinely declare none — so the
     * cap has to be enforced here as well, where the bytes actually arrive.
     */
    suspend fun read(uri: String): ReadResult

    sealed interface ReadResult {
        data class Success(
            val bytes: ByteArray,
        ) : ReadResult {
            // ByteArray identity: data-class equals would compare references,
            // which is worse than useless in a test assertion.
            override fun equals(other: Any?): Boolean = this === other || (other is Success && bytes.contentEquals(other.bytes))

            override fun hashCode(): Int = bytes.contentHashCode()
        }

        /** The image is over the cap, whatever length the provider declared. */
        data object TooLarge : ReadResult

        /** Unreadable or empty — a revoked grant, a deleted file, a provider error. */
        data object Unavailable : ReadResult
    }
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

        override suspend fun read(uri: String): PickedImageReader.ReadResult =
            withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver
                        .openInputStream(Uri.parse(uri))
                        ?.use { readBounded(it) }
                        ?: PickedImageReader.ReadResult.Unavailable
                }.onFailure {
                    // A picker grant lasts only for the current process, so a
                    // Uri restored after process death can legitimately fail.
                    Timber.w(it, "Couldn't read picked image")
                }.getOrElse { PickedImageReader.ReadResult.Unavailable }
            }

        /**
         * Reads at most one byte past the cap, then stops.
         *
         * `readBytes()` would keep growing its buffer for as long as the
         * provider keeps producing, so a 25 MiB limit the provider never
         * declared bought nothing: a multi-hundred-megabyte pick still got read
         * whole, and the resulting OutOfMemoryError was swallowed as a silently
         * failed upload.
         */
        private fun readBounded(stream: InputStream): PickedImageReader.ReadResult {
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(READ_CHUNK_BYTES)
            var total = 0L
            while (true) {
                val read = stream.read(buffer)
                if (read == -1) break
                total += read
                if (total > MAX_PICKED_IMAGE_BYTES) return PickedImageReader.ReadResult.TooLarge
                out.write(buffer, 0, read)
            }
            val bytes = out.toByteArray()
            return if (bytes.isEmpty()) {
                PickedImageReader.ReadResult.Unavailable
            } else {
                PickedImageReader.ReadResult.Success(bytes)
            }
        }

        private companion object {
            const val READ_CHUNK_BYTES = 64 * 1024
        }
    }
