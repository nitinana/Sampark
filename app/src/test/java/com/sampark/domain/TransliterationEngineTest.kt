package com.sampark.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.robolectric.RobolectricTestRunner
import org.junit.runner.RunWith

@RunWith(RobolectricTestRunner::class)
class TransliterationEngineTest {

    private val engine = TransliterationEngine()

    @Test
    fun `transliterates a simple English name to Devanagari`() {
        val result = engine.transliterate("Nitin")
        assertNotEquals("Nitin", result)
        assertTrue(result.isNotBlank())
        assertTrue(result.any { it.code in 0x0900..0x097F }) // Devanagari Unicode block
    }

    @Test
    fun `transliteration is deterministic for the same input`() {
        assertEquals(engine.transliterate("Nitin Anande"), engine.transliterate("Nitin Anande"))
    }
}
