package com.megamaced.crate.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerFeaturesTest {
    @Test
    fun `ratings need Crate 1_15_0 or later`() {
        assertTrue(serverSupportsRatings("1.15.0"))
        assertTrue(serverSupportsRatings("1.15.2"))
        assertTrue(serverSupportsRatings("2.0.0"))
        assertFalse(serverSupportsRatings("1.14.0"))
        assertFalse(serverSupportsRatings("0.4.2"))
    }

    @Test
    fun `an unknown server version counts as one without ratings`() {
        assertFalse(serverSupportsRatings(null))
        assertFalse(serverSupportsRatings(""))
        assertFalse(serverSupportsRatings("unknown"))
    }
}
