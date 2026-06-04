package com.megamaced.crate.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import timber.log.Timber
import java.io.ByteArrayOutputStream

/**
 * Strip EXIF/GPS/timestamp metadata from image bytes before they leave the
 * device. The Nextcloud backend also re-encodes images through GD on upload
 * (see GdImageTrait), but stripping client-side gives defence-in-depth in
 * case the user is on an older server, and protects raw bytes in transit
 * if the request is ever logged or proxied.
 *
 * We strip by decoding to a Bitmap and re-encoding through Android's
 * platform codec, which discards EXIF/IPTC/XMP because Bitmap.compress
 * doesn't preserve metadata. JPEG re-encodes at quality 95 (visually
 * lossless for one round-trip — the server's GD pass is the second
 * encode and that one bumps to quality 90); PNG re-encodes losslessly.
 * WebP and GIF are returned unchanged — animated formats would lose
 * animation through a Bitmap round-trip and rarely carry GPS metadata
 * in practice.
 *
 * On any failure the original bytes are returned: we'd rather upload an
 * intact image with metadata than fail the upload outright.
 */
object ExifStrip {
    fun strip(
        bytes: ByteArray,
        mimeType: String,
    ): ByteArray {
        val format = when (mimeType) {
            "image/jpeg" -> Bitmap.CompressFormat.JPEG
            "image/png" -> Bitmap.CompressFormat.PNG
            else -> return bytes
        }
        return try {
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                ?: return bytes
            val out = ByteArrayOutputStream(bytes.size)
            val quality = if (format == Bitmap.CompressFormat.JPEG) 95 else 100
            val ok = bitmap.compress(format, quality, out)
            bitmap.recycle()
            if (ok) out.toByteArray() else bytes
        } catch (t: Throwable) {
            Timber.w(t, "EXIF strip failed; uploading original bytes")
            bytes
        }
    }
}
