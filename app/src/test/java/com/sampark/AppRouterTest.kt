package com.sampark

import org.junit.Assert.assertEquals
import org.junit.Test

class AppRouterTest {

    private fun prefs(setupComplete: Boolean = true, scanInProgress: Boolean = false) =
        FakeAppPreferences().apply {
            this.setupComplete = setupComplete
            this.scanInProgress = scanInProgress
        }

    private fun repo(hasCustomFields: Boolean = false) =
        FakeContactsRepository(
            preSeededCustomFields = if (hasCustomFields)
                mapOf(1L to Pair("Rahul", "राहुल")) else emptyMap()
        )

    @Test
    fun `routes to SETUP when setup is not complete`() {
        assertEquals(AppScreen.SETUP, resolveAppScreen(prefs(setupComplete = false), repo()))
    }

    @Test
    fun `routes to SCANNING when scan_in_progress is true`() {
        assertEquals(AppScreen.SCANNING, resolveAppScreen(prefs(scanInProgress = true), repo()))
    }

    @Test
    fun `routes to ACTIVE when setup complete and custom fields exist`() {
        assertEquals(AppScreen.ACTIVE, resolveAppScreen(prefs(), repo(hasCustomFields = true)))
    }

    @Test
    fun `routes to INACTIVE when setup complete and no custom fields`() {
        assertEquals(AppScreen.INACTIVE, resolveAppScreen(prefs(), repo(hasCustomFields = false)))
    }

    @Test
    fun `SCANNING takes priority over custom fields check`() {
        assertEquals(
            AppScreen.SCANNING,
            resolveAppScreen(prefs(scanInProgress = true), repo(hasCustomFields = true))
        )
    }
}
