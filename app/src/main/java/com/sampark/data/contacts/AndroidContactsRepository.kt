package com.sampark.data.contacts

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val EXCLUDED_ACCOUNT_TYPES = setOf(
    "vnd.sec.contact.sim",
    "com.android.contacts.sim",
    "com.whatsapp",
    "com.whatsapp.w4b"
)

class AndroidContactsRepository(private val context: Context) : ContactsRepository {

    override suspend fun hasContactsPermission(): Boolean = withContext(Dispatchers.IO) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) ==
            PackageManager.PERMISSION_GRANTED
    }

    /**
     * Two ContentResolver queries total, regardless of contact count: one pass over
     * RawContacts to build contactId -> account types, and one pass over Contacts
     * joined against that map in memory. (An earlier version issued two extra
     * queries per contact, i.e. 2N+1 binder round trips.)
     */
    override suspend fun getEligibleContacts(): List<ContactRef> = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val results = mutableListOf<ContactRef>()

        val accountTypesByContactId = mutableMapOf<Long, MutableSet<String?>>()
        resolver.query(
            ContactsContract.RawContacts.CONTENT_URI,
            arrayOf(
                ContactsContract.RawContacts.CONTACT_ID,
                ContactsContract.RawContacts.ACCOUNT_TYPE
            ),
            null,
            null,
            null
        )?.use { rawCursor ->
            val contactIdIndex = rawCursor.getColumnIndexOrThrow(ContactsContract.RawContacts.CONTACT_ID)
            val accountTypeIndex = rawCursor.getColumnIndexOrThrow(ContactsContract.RawContacts.ACCOUNT_TYPE)
            while (rawCursor.moveToNext()) {
                if (rawCursor.isNull(contactIdIndex)) continue
                val contactId = rawCursor.getLong(contactIdIndex)
                val accountType = rawCursor.getString(accountTypeIndex)
                accountTypesByContactId.getOrPut(contactId) { mutableSetOf() }.add(accountType)
            }
        }

        val cursor = resolver.query(
            ContactsContract.Contacts.CONTENT_URI,
            arrayOf(
                ContactsContract.Contacts._ID,
                ContactsContract.Contacts.LOOKUP_KEY,
                ContactsContract.Contacts.DISPLAY_NAME_PRIMARY
            ),
            null,
            null,
            null
        ) ?: return@withContext emptyList<ContactRef>()

        cursor.use {
            val idIndex = it.getColumnIndexOrThrow(ContactsContract.Contacts._ID)
            val lookupKeyIndex = it.getColumnIndexOrThrow(ContactsContract.Contacts.LOOKUP_KEY)
            val nameIndex = it.getColumnIndexOrThrow(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY)
            while (it.moveToNext()) {
                val lookupKey = it.getString(lookupKeyIndex) ?: continue
                val name = it.getString(nameIndex) ?: continue
                val contactId = if (it.isNull(idIndex)) null else it.getLong(idIndex)
                val accountTypes = contactId?.let { id -> accountTypesByContactId[id] }
                // Unchanged exclusion semantics: excluded only when the contact has at
                // least one raw contact and ALL of them are of an excluded account type.
                val allExcluded = accountTypes != null &&
                    accountTypes.isNotEmpty() &&
                    accountTypes.all { accountType -> accountType in EXCLUDED_ACCOUNT_TYPES }
                if (allExcluded) continue
                if (!ContactEligibility.isEligibleForTranslation(name)) continue
                results.add(ContactRef(lookupKey, name))
            }
        }
        results
    }

    override suspend fun getCurrentName(lookupKey: String): String? = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val uri = ContactsContract.Contacts.CONTENT_URI
        val cursor = resolver.query(
            uri,
            arrayOf(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY),
            "${ContactsContract.Contacts.LOOKUP_KEY} = ?",
            arrayOf(lookupKey),
            null
        ) ?: return@withContext null

        cursor.use {
            if (!it.moveToFirst()) return@withContext null
            it.getString(it.getColumnIndexOrThrow(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY))
        }
    }

    override suspend fun updateName(lookupKey: String, newName: String) = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val rawContactId = findRawContactId(lookupKey) ?: return@withContext

        val values = ContentValues().apply {
            put(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, newName)
        }
        resolver.update(
            ContactsContract.Data.CONTENT_URI,
            values,
            "${ContactsContract.Data.RAW_CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} = ?",
            arrayOf(rawContactId.toString(), ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
        )
        Unit
    }

    // Intentionally single-raw-contact: returns the first raw contact under the
    // aggregate. Multi-raw-contact aggregation (e.g. one contact backed by both
    // a Google and a phone-storage raw contact) is an accepted trade-off — the
    // ledger keys/drift-compares on the aggregate lookupKey, not per-raw-contact.
    private fun findRawContactId(lookupKey: String): Long? {
        val resolver = context.contentResolver
        val contactUri = ContactsContract.Contacts.getLookupUri(
            resolver,
            android.net.Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_LOOKUP_URI, lookupKey)
        ) ?: return null

        val contactIdCursor = resolver.query(
            contactUri,
            arrayOf(ContactsContract.Contacts._ID),
            null, null, null
        ) ?: return null
        val contactId = contactIdCursor.use {
            if (!it.moveToFirst()) return null
            it.getLong(it.getColumnIndexOrThrow(ContactsContract.Contacts._ID))
        }

        val rawCursor = resolver.query(
            ContactsContract.RawContacts.CONTENT_URI,
            arrayOf(ContactsContract.RawContacts._ID),
            "${ContactsContract.RawContacts.CONTACT_ID} = ?",
            arrayOf(contactId.toString()),
            null
        ) ?: return null
        return rawCursor.use {
            if (!it.moveToFirst()) null
            else it.getLong(it.getColumnIndexOrThrow(ContactsContract.RawContacts._ID))
        }
    }
}
