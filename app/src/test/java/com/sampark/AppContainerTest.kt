package com.sampark

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AppContainerTest {

    @Test
    fun `provides all core dependencies`() {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        val container = AppContainer(context)

        assertNotNull(container.contactsRepository)
        assertNotNull(container.ledgerDao)
        assertNotNull(container.appStatusRepository)
        assertNotNull(container.transliterationEngine)
        assertNotNull(container.runEngine)
        assertNotNull(container.appRouter)
    }
}
