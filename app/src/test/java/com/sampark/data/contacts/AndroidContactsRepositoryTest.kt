package com.sampark.data.contacts

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class AndroidContactsRepositoryTest {

    @Before
    fun registerFakeContactsProvider() {
        // Robolectric ships no shadow for the real system ContactsProvider
        // (it lives outside AOSP frameworks/base), so a fake must be
        // registered for the "com.android.contacts" authority before any
        // ContactsContract query/insert/update will resolve to anything.
        Robolectric.buildContentProvider(FakeContactsProvider::class.java)
            .create(ContactsContract.AUTHORITY)
    }

    private fun grantContactsPermission() {
        shadowOf(ApplicationProvider.getApplicationContext<android.app.Application>())
            .grantPermissions(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS)
    }

    private fun insertContact(displayName: String, accountType: String? = null, accountName: String? = null): String {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        val resolver = context.contentResolver

        val rawContactValues = ContentValues().apply {
            if (accountType != null) put(ContactsContract.RawContacts.ACCOUNT_TYPE, accountType)
            if (accountName != null) put(ContactsContract.RawContacts.ACCOUNT_NAME, accountName)
        }
        val rawContactUri = resolver.insert(ContactsContract.RawContacts.CONTENT_URI, rawContactValues)!!
        val rawContactId = rawContactUri.lastPathSegment!!.toLong()

        val nameValues = ContentValues().apply {
            put(ContactsContract.Data.RAW_CONTACT_ID, rawContactId)
            put(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
            put(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, displayName)
        }
        resolver.insert(ContactsContract.Data.CONTENT_URI, nameValues)

        val cursor = resolver.query(
            rawContactUri,
            arrayOf(ContactsContract.RawContacts.CONTACT_ID),
            null,
            null,
            null
        )!!
        cursor.moveToFirst()
        val contactId = cursor.getLong(0)
        cursor.close()

        val lookupCursor = resolver.query(
            ContactsContract.Contacts.CONTENT_URI,
            arrayOf(ContactsContract.Contacts.LOOKUP_KEY),
            "${ContactsContract.Contacts._ID} = ?",
            arrayOf(contactId.toString()),
            null
        )!!
        lookupCursor.moveToFirst()
        val lookupKey = lookupCursor.getString(0)
        lookupCursor.close()

        return lookupKey
    }

    @Test
    fun `hasContactsPermission is false without permission`() {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        val repository = AndroidContactsRepository(context)
        assertFalse(runBlocking { repository.hasContactsPermission() })
    }

    @Test
    fun `hasContactsPermission is true once granted`() {
        grantContactsPermission()
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        val repository = AndroidContactsRepository(context)
        assertTrue(runBlocking { repository.hasContactsPermission() })
    }

    @Test
    fun `getEligibleContacts includes an eligible English name`() {
        grantContactsPermission()
        insertContact("Nitin Anande")
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        val repository = AndroidContactsRepository(context)
        val contacts = runBlocking { repository.getEligibleContacts() }
        assertTrue(contacts.any { it.name == "Nitin Anande" })
    }

    @Test
    fun `getEligibleContacts excludes a SIM-only contact`() {
        grantContactsPermission()
        insertContact("Sim Contact", accountType = "vnd.sec.contact.sim", accountName = "SIM")
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        val repository = AndroidContactsRepository(context)
        val contacts = runBlocking { repository.getEligibleContacts() }
        assertFalse(contacts.any { it.name == "Sim Contact" })
    }

    @Test
    fun `getEligibleContacts joins account types per contact, not across contacts`() {
        // Regression guard for the batched (2-query) account-type lookup: excluded
        // account types must only exclude their own contact.
        grantContactsPermission()
        insertContact("Whatsapp Only", accountType = "com.whatsapp", accountName = "WhatsApp")
        insertContact("Sim Contact", accountType = "vnd.sec.contact.sim", accountName = "SIM")
        insertContact("Phone Contact")
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        val repository = AndroidContactsRepository(context)

        val contacts = runBlocking { repository.getEligibleContacts() }

        assertEquals(listOf("Phone Contact"), contacts.map { it.name })
    }

    @Test
    fun `getEligibleContacts excludes an already-Devanagari name`() {
        grantContactsPermission()
        insertContact("नितीन आनंदे")
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        val repository = AndroidContactsRepository(context)
        val contacts = runBlocking { repository.getEligibleContacts() }
        assertFalse(contacts.any { it.name == "नितीन आनंदे" })
    }

    @Test
    fun `updateName changes the contact display name`() {
        grantContactsPermission()
        val lookupKey = insertContact("Nitin Anande")
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        val repository = AndroidContactsRepository(context)

        runBlocking { repository.updateName(lookupKey, "निनान आनंदे") }

        assertEquals("निनान आनंदे", runBlocking { repository.getCurrentName(lookupKey) })
    }

    @Test
    fun `getCurrentName returns null for an unknown lookup key`() {
        grantContactsPermission()
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        val repository = AndroidContactsRepository(context)
        assertNull(runBlocking { repository.getCurrentName("does-not-exist") })
    }
}
