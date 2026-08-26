package com.megamaced.crate.util

import android.util.Log
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import timber.log.Timber

/**
 * The JVM unit-test runtime has no image codecs — `BitmapFactory` is stubbed
 * and reports no bounds — which is exactly the "platform can't decode this"
 * path we care about here: the upload still goes ahead with the original
 * bytes, so the result has to say so and the fallback has to be logged.
 *
 * The successful re-encode path needs a real codec and is covered on-device.
 */
class ExifStripTest {
    private val logs = mutableListOf<Triple<Int, String?, Throwable?>>()

    private val recorder = object : Timber.Tree() {
        override fun log(
            priority: Int,
            tag: String?,
            message: String,
            t: Throwable?,
        ) {
            logs += Triple(priority, message, t)
        }
    }

    @Before
    fun setUp() {
        Timber.plant(recorder)
    }

    @After
    fun tearDown() {
        Timber.uprootAll()
    }

    @Test
    fun `an undecodable image is returned untouched and reported as not stripped`() {
        val original = byteArrayOf(1, 2, 3, 4, 5)

        val result = ExifStrip.strip(original, "image/heic")

        assertFalse(result.stripped)
        assertArrayEquals(original, result.bytes)
        // The source type is kept, because the bytes are the source bytes.
        assertEquals("image/heic", result.mimeType)
    }

    @Test
    fun `the fallback is logged as a warning naming the type and the retained metadata`() {
        ExifStrip.strip(byteArrayOf(9, 9, 9), "image/jpeg")

        val warning = logs.singleOrNull { it.first == Log.WARN }
        assertTrue("expected exactly one warning, got $logs", warning != null)
        val message = warning?.second.orEmpty()
        assertTrue(message, message.contains("EXIF NOT stripped"))
        assertTrue(message, message.contains("image/jpeg"))
        assertTrue(message, message.contains("GPS"))
    }

    @Test
    fun `a PNG that cannot be decoded is not silently relabelled`() {
        val result = ExifStrip.strip(byteArrayOf(7), "image/png")

        assertFalse(result.stripped)
        assertEquals("image/png", result.mimeType)
    }

    @Test
    fun `equality distinguishes a stripped result from an untouched one`() {
        val bytes = byteArrayOf(1, 2, 3)

        assertEquals(
            ExifStrip.Stripped(bytes, "image/jpeg"),
            ExifStrip.Stripped(bytes.copyOf(), "image/jpeg"),
        )
        assertNotEquals(
            ExifStrip.Stripped(bytes, "image/jpeg"),
            ExifStrip.Stripped(bytes, "image/jpeg", stripped = false),
        )
    }
}
