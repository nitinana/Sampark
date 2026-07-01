package com.sampark

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContactDetectorTest {

    @Test
    fun `returns true for a name containing Latin characters`() {
        assertTrue(containsLatinChars("Rahul Sharma"))
    }

    @Test
    fun `returns false for a Devanagari-only name`() {
        assertFalse(containsLatinChars("राहुल शर्मा"))
    }

    @Test
    fun `returns false for a digit-only entry`() {
        assertFalse(containsLatinChars("9876543210"))
    }

    @Test
    fun `returns false for an empty string`() {
        assertFalse(containsLatinChars(""))
    }

    @Test
    fun `returns true for a name mixing Latin and Devanagari`() {
        assertTrue(containsLatinChars("Rahul राहुल"))
    }

    @Test
    fun `returns true for a name with only punctuation and Latin letters`() {
        assertTrue(containsLatinChars("Dr. Patil"))
    }

    @Test
    fun `returns false for a name with digits and punctuation but no letters`() {
        assertFalse(containsLatinChars("+91 98765-43210"))
    }
}
