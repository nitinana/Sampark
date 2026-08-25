package com.sampark.ui.navigation

import android.Manifest
import android.content.ContentValues
import android.provider.ContactsContract
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.sampark.AppContainer
import com.sampark.data.contacts.FakeContactsProvider
import com.sampark.data.ledger.LedgerEntity
import com.sampark.data.ledger.LedgerStatus
import com.sampark.data.status.Direction
import com.sampark.data.status.Phase
import com.sampark.domain.Routes
import com.sampark.ui.theme.SamparkTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * Integration coverage for the navigation layer — the seam that produced the
 * cold-start auto-resume bug, which no screen-level or ViewModel-level test
 * could have caught on its own.
 */
@RunWith(RobolectricTestRunner::class)
class SamparkNavHostTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context get() = ApplicationProvider.getApplicationContext<android.app.Application>()

    private lateinit var container: AppContainer

    @Before
    fun setUp() {
        Robolectric.buildContentProvider(FakeContactsProvider::class.java)
            .create(ContactsContract.AUTHORITY)
        shadowOf(context).grantPermissions(
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.WRITE_CONTACTS
        )
        container = AppContainer(context)
        runBlocking { container.ledgerDao.clearAll() }
    }

    private fun insertContact(displayName: String): String {
        val resolver = context.contentResolver
        val rawContactUri = resolver.insert(ContactsContract.RawContacts.CONTENT_URI, ContentValues())!!
        val rawContactId = rawContactUri.lastPathSegment!!.toLong()
        resolver.insert(
            ContactsContract.Data.CONTENT_URI,
            ContentValues().apply {
                put(ContactsContract.Data.RAW_CONTACT_ID, rawContactId)
                put(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                put(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, displayName)
            }
        )
        val cursor = resolver.query(
            ContactsContract.Contacts.CONTENT_URI,
            arrayOf(ContactsContract.Contacts.LOOKUP_KEY, ContactsContract.Contacts.DISPLAY_NAME_PRIMARY),
            null, null, null
        )!!
        cursor.use {
            while (it.moveToNext()) {
                if (it.getString(1) == displayName) return it.getString(0)
            }
        }
        error("contact not found")
    }

    /** Reads the current display name straight from the provider, without coroutines. */
    private fun currentName(lookupKey: String): String? {
        val cursor = context.contentResolver.query(
            ContactsContract.Contacts.CONTENT_URI,
            arrayOf(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY),
            "${ContactsContract.Contacts.LOOKUP_KEY} = ?",
            arrayOf(lookupKey),
            null
        ) ?: return null
        return cursor.use { if (it.moveToFirst()) it.getString(0) else null }
    }

    @Test
    fun `cold start into a RUNNING translate run shows the paused prompt and renames nothing`() {
        val lookupKey = insertContact("Nitin Anande")
        runBlocking {
            container.ledgerDao.insertAll(
                listOf(LedgerEntity(lookupKey, "Nitin Anande", "", LedgerStatus.PENDING))
            )
            container.appStatusRepository.setDirection(Direction.TRANSLATE)
            container.appStatusRepository.setPhase(Phase.RUNNING)
        }

        composeRule.setContent {
            SamparkTheme {
                SamparkNavHost(container = container, startDestination = Routes.RUN_TRANSLATE)
            }
        }
        composeRule.waitForIdle()

        // Paused prompt, not a silently-resumed run.
        composeRule.onNodeWithText("सुरू ठेवा").assertExists()
        composeRule.onNodeWithText("रद्द करा").assertExists()
        assertEquals("Nitin Anande", currentName(lookupKey))
        assertEquals(
            LedgerStatus.PENDING,
            runBlocking { container.ledgerDao.getRowsByStatus(LedgerStatus.PENDING) }.single().status
        )

        // An explicit, fresh Resume tap is what actually starts the work.
        composeRule.onNodeWithText("सुरू ठेवा").performClick()
        awaitCondition("the resumed run to rename the contact") {
            currentName(lookupKey) != "Nitin Anande"
        }
    }

    /**
     * Compose's own waitUntil does not pump Robolectric's (paused) main looper
     * between polls, so work that hops to Dispatchers.IO and back to Main never
     * progresses. Idle the composition and the main looper explicitly instead.
     */
    private fun awaitCondition(what: String, timeoutMillis: Long = 10_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline) {
            composeRule.waitForIdle()
            shadowOf(android.os.Looper.getMainLooper()).idle()
            if (condition()) return
            Thread.sleep(20)
        }
        throw AssertionError("Timed out waiting for $what")
    }
}
