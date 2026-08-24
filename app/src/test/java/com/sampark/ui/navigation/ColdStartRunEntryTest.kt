package com.sampark.ui.navigation

import com.sampark.domain.Routes
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ColdStartRunEntryTest {

    @Test
    fun `run route resolved as the cold-start destination is a cold-start entry`() {
        val entry = ColdStartRunEntry(Routes.RUN_TRANSLATE)
        assertTrue(entry.isColdStartEntryFor(Routes.RUN_TRANSLATE))
    }

    @Test
    fun `the cold-start answer is single-use`() {
        val entry = ColdStartRunEntry(Routes.RUN_TRANSLATE)
        assertTrue(entry.isColdStartEntryFor(Routes.RUN_TRANSLATE))
        // Reaching the same route again later in this process (e.g. "translate
        // again" from Home) is a deliberate tap, not a cold-start re-entry.
        assertFalse(entry.isColdStartEntryFor(Routes.RUN_TRANSLATE))
    }

    @Test
    fun `a run route reached after a fresh permission grant is not a cold-start entry`() {
        val entry = ColdStartRunEntry(Routes.WELCOME)
        assertFalse(entry.isColdStartEntryFor(Routes.RUN_TRANSLATE))
    }

    @Test
    fun `a rollback run confirmed by the user is not a cold-start entry`() {
        val entry = ColdStartRunEntry(Routes.HOME)
        assertFalse(entry.isColdStartEntryFor(Routes.RUN_ROLLBACK))
    }

    @Test
    fun `cold start into rollback is a cold-start entry only for the rollback route`() {
        val entry = ColdStartRunEntry(Routes.RUN_ROLLBACK)
        assertFalse(entry.isColdStartEntryFor(Routes.RUN_TRANSLATE))
        assertTrue(entry.isColdStartEntryFor(Routes.RUN_ROLLBACK))
    }
}
