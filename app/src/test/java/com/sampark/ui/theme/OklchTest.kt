package com.sampark.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class OklchTest {

    @Test
    fun `zero lightness and chroma is black`() {
        val color = oklch(0f, 0f, 0f)
        assertTrue(color.red < 0.01f)
        assertTrue(color.green < 0.01f)
        assertTrue(color.blue < 0.01f)
    }

    @Test
    fun `full lightness and zero chroma is white`() {
        val color = oklch(1f, 0f, 0f)
        assertTrue(color.red > 0.99f)
        assertTrue(color.green > 0.99f)
        assertTrue(color.blue > 0.99f)
    }

    @Test
    fun `zero chroma produces a neutral gray regardless of hue`() {
        val color = oklch(0.5f, 0f, 200f)
        assertEquals(color.red, color.green, 0.01f)
        assertEquals(color.green, color.blue, 0.01f)
    }

    @Test
    fun `increasing lightness at fixed chroma and hue increases perceived brightness`() {
        val darker = oklch(0.3f, 0.1f, 45f)
        val lighter = oklch(0.7f, 0.1f, 45f)
        val darkerSum = darker.red + darker.green + darker.blue
        val lighterSum = lighter.red + lighter.green + lighter.blue
        assertTrue(lighterSum > darkerSum)
    }
}
