package com.megamaced.crate.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import timber.log.Timber
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/**
 * Strip EXIF/GPS/timestamp metadata from image bytes before they leave the
 * device. The Nextcloud backend also re-encodes images through GD on upload
 * (see GdImageTrait), but stripping client-side gives defence-in-depth in
 * case the user is on an older server, and protects raw bytes in transit
 * if the request is ever logged or proxied.
 *
 * We strip by decoding to a Bitmap and re-encoding through Android's platform
 * codec, which discards EXIF/IPTC/XMP because Bitmap.compress doesn't preserve
 * metadata. Crucially, EVERY raster input is re-encoded — including HEIC/HEIF
 * and WebP, which modern phone cameras emit by default and which routinely
 * carry GPS. Anything that isn't recognised as PNG is normalised to JPEG;
 * we never return the original bytes for a successfully-decoded image, because
 * any un-re-encoded path leaks metadata.
 *
 * Because Bitmap.compress writes no orientation tag, we first read the source
 * EXIF orientation and bake the rotation into the pixels — otherwise a photo
 * that relied on an orientation tag would upload rotated.
 *
 * Decoding is downsampled to [MAX_DIMENSION] on the longest edge so a large
 * camera image (12MP+ → ~48 MB as ARGB_8888) can't OOM the process.
 *
 * Stripping fails closed. When the platform can't decode or re-encode the
 * image there is no way to remove its metadata, and the app tells users —
 * in the README and in the store listing — that EXIF/GPS is stripped before
 * anything leaves the device. Returning the original bytes would quietly break
 * that promise on exactly the images most likely to carry GPS, so [strip]
 * returns null instead and the caller refuses the upload.
 */

object ExifStrip {
    /**
     * Result of a strip. [mimeType] is the type of [bytes] as they now stand,
     * which is not necessarily what came in — a HEIC/HEIF or WebP source is
     * re-encoded to JPEG. Callers must label the upload with this, not the
     * source type, or the server is handed JPEG bytes claiming to be HEIC.
     */
    data class Stripped(
        val bytes: ByteArray,
        val mimeType: String,
    ) {
        // ByteArray uses identity equals/hashCode, so the generated data-class
        // implementations would be misleading. Compare contents instead.
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Stripped) return false
            return mimeType == other.mimeType && bytes.contentEquals(other.bytes)
        }

        override fun hashCode(): Int {
            val result = bytes.contentHashCode()
            return 31 * result + mimeType.hashCode()
        }
    }

    /**
     * Longest-edge cap for the re-encoded upload. Keeps peak memory bounded
     * and upload sizes sane; the server downscales further for thumbnails.
     */
    private const val MAX_DIMENSION = 2048

    /**
     * Re-encoded, metadata-free bytes — or null when the platform could not
     * strip this image, in which case it must not be uploaded.
     */
    fun strip(
        bytes: ByteArray,
        mimeType: String,
    ): Stripped? {
        // PNG stays PNG (lossless, may carry transparency); everything else —
        // JPEG, HEIC/HEIF, WebP, and unknown/`image/*` — is normalised to JPEG.
        val isPng = mimeType == "image/png"
        val outFormat =
            if (isPng) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
        val outMime = if (isPng) "image/png" else "image/jpeg"
        return try {
            // 1. Bounds-only pass to size the downsample factor without
            //    allocating the full bitmap.
            val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, boundsOpts)
            if (boundsOpts.outWidth <= 0 || boundsOpts.outHeight <= 0) {
                // Not a decodable raster, or a codec this device lacks. Nothing
                // can be stripped from bytes that can't be decoded.
                return failed(mimeType, "no decodable image bounds")
            }

            val decodeOpts =
                BitmapFactory.Options().apply {
                    inSampleSize = sampleSize(boundsOpts.outWidth, boundsOpts.outHeight, MAX_DIMENSION)
                }
            val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOpts)
                ?: return failed(mimeType, "the platform decoder returned no bitmap")

            // 2. Bake in the source orientation (the tag is lost on re-encode).
            val oriented = applyOrientation(decoded, readOrientation(bytes))

            val out = ByteArrayOutputStream(bytes.size)
            val quality = if (outFormat == Bitmap.CompressFormat.JPEG) 95 else 100
            val ok = oriented.compress(outFormat, quality, out)
            if (oriented !== decoded) decoded.recycle()
            oriented.recycle()
            if (ok) {
                Stripped(out.toByteArray(), outMime)
            } else {
                failed(mimeType, "re-encoding to $outMime failed")
            }
        } catch (t: Throwable) {
            failed(mimeType, "the strip threw", t)
        }
    }

    /**
     * No usable output. Logged at warn with the reason, because the upload the
     * caller is about to refuse looks to the user like an unexplained failure.
     */
    private fun failed(
        mimeType: String,
        reason: String,
        cause: Throwable? = null,
    ): Stripped? {
        Timber.w(cause, "Refusing to upload %s: EXIF could not be stripped (%s)", mimeType, reason)
        return null
    }

    /** Power-of-two subsample so the longest edge lands at or below [maxDim]. */
    private fun sampleSize(
        width: Int,
        height: Int,
        maxDim: Int,
    ): Int {
        var sample = 1
        var w = width
        var h = height
        while (w > maxDim || h > maxDim) {
            w /= 2
            h /= 2
            sample *= 2
        }
        return sample
    }

    private fun readOrientation(bytes: ByteArray): Int =
        try {
            ByteArrayInputStream(bytes).use { stream ->
                ExifInterface(stream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL,
                )
            }
        } catch (t: Throwable) {
            ExifInterface.ORIENTATION_NORMAL
        }

    private fun applyOrientation(
        bitmap: Bitmap,
        orientation: Int,
    ): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> {
                matrix.postRotate(90f)
            }

            ExifInterface.ORIENTATION_ROTATE_180 -> {
                matrix.postRotate(180f)
            }

            ExifInterface.ORIENTATION_ROTATE_270 -> {
                matrix.postRotate(270f)
            }

            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> {
                matrix.postScale(-1f, 1f)
            }

            ExifInterface.ORIENTATION_FLIP_VERTICAL -> {
                matrix.postScale(1f, -1f)
            }

            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.postScale(-1f, 1f)
            }

            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.postScale(-1f, 1f)
            }

            else -> {
                return bitmap
            }
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
}

/**
 * Raised by an upload path when [ExifStrip.strip] could not sanitise the image.
 * Carried as a distinct failure so the user is told the upload was refused to
 * protect their location data, rather than shown a generic server error.
 */
class ExifStripFailedException : Exception("Image metadata could not be stripped")
