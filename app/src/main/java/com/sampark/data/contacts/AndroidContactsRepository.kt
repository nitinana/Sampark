package com.sampark.data.contacts

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.core.content.ContextCompat

private val EXCLUDED_ACCOUNT_TYPES = setOf(
    "vnd.sec.contact.sim",
    "com.android.contacts.sim",
    "com.whatsapp",
    "com.whatsapp.w4b"
)

class AndroidContactsRepository(private val context: Context) : ContactsRepository {

    override fun hasContactsPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) ==
            PackageManager.PERMISSION_GRANTED

    override fun getEligibleContacts(): List<ContactRef> {
        val resolver = context.contentResolver
        val results = mutableListOf<ContactRef>()

        val cursor = resolver.query(
            ContactsContract.Contacts.CONTENT_URI,
            arrayOf(
                ContactsContract.Contacts.LOOKUP_KEY,
                ContactsContract.Contacts.DISPLAY_NAME_PRIMARY
            ),
            null,
            null,
            null
        ) ?: return emptyList()

        cursor.use {
            val lookupKeyIndex = it.getColumnIndexOrThrow(ContactsContract.Contacts.LOOKUP_KEY)
            val nameIndex = it.getColumnIndexOrThrow(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY)
            while (it.moveToNext()) {
                val lookupKey = it.getString(lookupKeyIndex) ?: continue
                val name = it.getString(nameIndex) ?: continue
                if (isExcludedAccountType(lookupKey)) continue
                if (!ContactEligibility.isEligibleForTranslation(name)) continue
                results.add(ContactRef(lookupKey, name))
            }
        }
        return results
    }

    override fun getCurrentName(lookupKey: String): String? {
        val resolver = context.contentResolver
        val uri = ContactsContract.Contacts.CONTENT_URI
        val cursor = resolver.query(
            uri,
            arrayOf(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY),
            "${ContactsContract.Contacts.LOOKUP_KEY} = ?",
            arrayOf(lookupKey),
            null
        ) ?: return null

        cursor.use {
            if (!it.moveToFirst()) return null
            return it.getString(it.getColumnIndexOrThrow(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY))
        }
    }

    override fun updateName(lookupKey: String, newName: String) {
        val resolver = context.contentResolver
        val rawContactId = findRawContactId(lookupKey) ?: return

        val values = ContentValues().apply {
            put(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, newName)
        }
        resolver.update(
            ContactsContract.Data.CONTENT_URI,
            values,
            "${ContactsContract.Data.RAW_CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} = ?",
            arrayOf(rawContactId.toString(), ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
        )
    }

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

    private fun isExcludedAccountType(lookupKey: String): Boolean {
        val resolver = context.contentResolver
        val contactUri = ContactsContract.Contacts.getLookupUri(
            resolver,
            android.net.Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_LOOKUP_URI, lookupKey)
        ) ?: return false

        val contactIdCursor = resolver.query(
            contactUri, arrayOf(ContactsContract.Contacts._ID), null, null, null
        ) ?: return false
        val contactId = contactIdCursor.use {
            if (!it.moveToFirst()) return false
            it.getLong(it.getColumnIndexOrThrow(ContactsContract.Contacts._ID))
        }

        val rawCursor = resolver.query(
            ContactsContract.RawContacts.CONTENT_URI,
            arrayOf(ContactsContract.RawContacts.ACCOUNT_TYPE),
            "${ContactsContract.RawContacts.CONTACT_ID} = ?",
            arrayOf(contactId.toString()),
            null
        ) ?: return false

        return rawCursor.use {
            var allExcluded = it.count > 0
            while (it.moveToNext()) {
                val accountType = it.getString(it.getColumnIndexOrThrow(ContactsContract.RawContacts.ACCOUNT_TYPE))
                if (accountType !in EXCLUDED_ACCOUNT_TYPES) allExcluded = false
            }
            allExcluded
        }
    }
}
