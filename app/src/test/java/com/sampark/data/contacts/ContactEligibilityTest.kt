package com.sampark.data.contacts

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContactEligibilityTest {

    @Test
    fun `plain English name is eligible`() {
        assertTrue(ContactEligibility.isEligibleForTranslation("Nitin Anande"))
    }

    @Test
    fun `already Devanagari name is not eligible`() {
        assertFalse(ContactEligibility.isEligibleForTranslation("नितीन आनंदे"))
    }

    @Test
    fun `blank name is not eligible`() {
        assertFalse(ContactEligibility.isEligibleForTranslation(""))
        assertFalse(ContactEligibility.isEligibleForTranslation("   "))
    }

    @Test
    fun `purely numeric name is not eligible`() {
        assertFalse(ContactEligibility.isEligibleForTranslation("9876543210"))
    }

    @Test
    fun `symbols-only name is not eligible`() {
        assertFalse(ContactEligibility.isEligibleForTranslation("---"))
    }

    @Test
    fun `mixed-script name is not eligible`() {
        assertFalse(ContactEligibility.isEligibleForTranslation("Suresh शर्मा"))
    }

    @Test
    fun `non-English Latin-script name with diacritics is not eligible`() {
        assertFalse(ContactEligibility.isEligibleForTranslation("René"))
    }

    @Test
    fun `all-caps acronym is not eligible`() {
        assertFalse(ContactEligibility.isEligibleForTranslation("IT Support"))
        assertFalse(ContactEligibility.isEligibleForTranslation("HR"))
    }

    @Test
    fun `bare initials are not eligible`() {
        assertFalse(ContactEligibility.isEligibleForTranslation("J"))
        assertFalse(ContactEligibility.isEligibleForTranslation("R.K."))
    }

    @Test
    fun `leetspeak nickname is not eligible`() {
        assertFalse(ContactEligibility.isEligibleForTranslation("K@ran"))
        assertFalse(ContactEligibility.isEligibleForTranslation("Ash_ley"))
    }

    @Test
    fun `URL-like name is not eligible`() {
        assertFalse(ContactEligibility.isEligibleForTranslation("www.example.com"))
    }

    @Test
    fun `ordinary two-word name with a period is still eligible`() {
        assertTrue(ContactEligibility.isEligibleForTranslation("Nitin Anande"))
        assertTrue(ContactEligibility.isEligibleForTranslation("Swarra Anande"))
    }
}
