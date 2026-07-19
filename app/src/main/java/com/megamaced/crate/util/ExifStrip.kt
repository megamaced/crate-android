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
 * On any failure the original bytes are returned: we'd rather upload an intact
 * image with metadata than fail the upload outright.
 */
object ExifStrip {
    /**
     * Longest-edge cap for the re-encoded upload. Keeps peak memory bounded
     * and upload sizes sane; the server downscales further for thumbnails.
     */
    private const val MAX_DIMENSION = 2048

    fun strip(
        bytes: ByteArray,
        mimeType: String,
    ): ByteArray {
        // PNG stays PNG (lossless, may carry transparency); everything else —
        // JPEG, HEIC/HEIF, WebP, and unknown/`image/*` — is normalised to JPEG.
        val outFormat =
            if (mimeType == "image/png") {
                Bitmap.CompressFormat.PNG
            } else {
                Bitmap.CompressFormat.JPEG
            }
        return try {
            // 1. Bounds-only pass to size the downsample factor without
            //    allocating the full bitmap.
            val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, boundsOpts)
            if (boundsOpts.outWidth <= 0 || boundsOpts.outHeight <= 0) {
                // Not a decodable raster (or an unsupported codec). Fall back
                // to the original bytes rather than fail the upload.
                return bytes
            }

            val decodeOpts =
                BitmapFactory.Options().apply {
                    inSampleSize = sampleSize(boundsOpts.outWidth, boundsOpts.outHeight, MAX_DIMENSION)
                }
            val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOpts) ?: return bytes

            // 2. Bake in the source orientation (the tag is lost on re-encode).
            val oriented = applyOrientation(decoded, readOrientation(bytes))

            val out = ByteArrayOutputStream(bytes.size)
            val quality = if (outFormat == Bitmap.CompressFormat.JPEG) 95 else 100
            val ok = oriented.compress(outFormat, quality, out)
            if (oriented !== decoded) decoded.recycle()
            oriented.recycle()
            if (ok) out.toByteArray() else bytes
        } catch (t: Throwable) {
            Timber.w(t, "EXIF strip failed; uploading original bytes")
            bytes
        }
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
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.postScale(-1f, 1f)
            }
            else -> return bitmap
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
}
