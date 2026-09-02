package com.megamaced.crate.util

import android.util.Log
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import timber.log.Timber

/**
 * The JVM unit-test runtime has no image codecs — `BitmapFactory` is stubbed
 * and reports no bounds — which is exactly the "platform can't decode this"
 * path we care about here: nothing can be stripped, so nothing may be
 * uploaded, and the refusal has to be logged with its reason.
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
    fun `an undecodable image yields nothing to upload`() {
        // Returning the original bytes here would upload GPS, timestamps and
        // camera serials from exactly the images most likely to carry them,
        // while the README and store listing promise they are stripped.
        assertNull(ExifStrip.strip(byteArrayOf(1, 2, 3, 4, 5), "image/heic"))
    }

    @Test
    fun `the refusal is logged as a warning naming the type and the reason`() {
        ExifStrip.strip(byteArrayOf(9, 9, 9), "image/jpeg")

        val warning = logs.singleOrNull { it.first == Log.WARN }
        assertTrue("expected exactly one warning, got $logs", warning != null)
        val message = warning?.second.orEmpty()
        assertTrue(message, message.contains("Refusing to upload"))
        assertTrue(message, message.contains("image/jpeg"))
        assertTrue(message, message.contains("EXIF could not be stripped"))
    }

    @Test
    fun `a PNG that cannot be decoded is refused too`() {
        assertNull(ExifStrip.strip(byteArrayOf(7), "image/png"))
    }

    @Test
    fun `equality compares the bytes rather than the reference`() {
        val bytes = byteArrayOf(1, 2, 3)

        assertEquals(
            ExifStrip.Stripped(bytes, "image/jpeg"),
            ExifStrip.Stripped(bytes.copyOf(), "image/jpeg"),
        )
        assertNotEquals(
            ExifStrip.Stripped(bytes, "image/jpeg"),
            ExifStrip.Stripped(byteArrayOf(4, 5, 6), "image/jpeg"),
        )
    }
}
