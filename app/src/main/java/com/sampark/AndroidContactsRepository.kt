package com.sampark

import android.content.ContentProviderOperation
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.provider.ContactsContract

private const val MIME_TYPE = "vnd.android.cursor.item/com.sampark.original_name"

internal fun isExcludedAccountType(accountType: String?): Boolean =
    accountType == "com.android.sim" || accountType == "com.whatsapp"

class AndroidContactsRepository(private val context: Context) : ContactsRepository {

    override fun allWritableContacts(): List<Contact> {
        val excludedIds = excludedContactIds()
        val contacts = mutableListOf<Contact>()
        val cursor = context.contentResolver.query(
            ContactsContract.Contacts.CONTENT_URI,
            arrayOf(ContactsContract.Contacts._ID, ContactsContract.Contacts.DISPLAY_NAME_PRIMARY),
            "${ContactsContract.Contacts.HAS_PHONE_NUMBER} = 1",
            null,
            null
        ) ?: return contacts

        cursor.use {
            val idCol = it.getColumnIndexOrThrow(ContactsContract.Contacts._ID)
            val nameCol = it.getColumnIndexOrThrow(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY)
            while (it.moveToNext()) {
                val id = it.getLong(idCol)
                if (id in excludedIds) continue
                val name = it.getString(nameCol) ?: continue
                contacts.add(Contact(id, name))
            }
        }
        return contacts
    }

    private fun excludedContactIds(): Set<Long> {
        val ids = mutableSetOf<Long>()
        val cursor = context.contentResolver.query(
            ContactsContract.RawContacts.CONTENT_URI,
            arrayOf(ContactsContract.RawContacts.CONTACT_ID, ContactsContract.RawContacts.ACCOUNT_TYPE),
            null, null, null
        ) ?: return ids
        cursor.use {
            val idCol = it.getColumnIndexOrThrow(ContactsContract.RawContacts.CONTACT_ID)
            val typeCol = it.getColumnIndexOrThrow(ContactsContract.RawContacts.ACCOUNT_TYPE)
            while (it.moveToNext()) {
                if (isExcludedAccountType(it.getString(typeCol))) ids.add(it.getLong(idCol))
            }
        }
        return ids
    }

    override fun hasCustomField(contactId: Long): Boolean {
        val cursor = context.contentResolver.query(
            ContactsContract.Data.CONTENT_URI,
            arrayOf(ContactsContract.Data._ID),
            "${ContactsContract.Data.CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} = ?",
            arrayOf(contactId.toString(), MIME_TYPE),
            null
        ) ?: return false
        return cursor.use { it.count > 0 }
    }

    override fun updateDisplayName(contactId: Long, newName: String) {
        val rawId = getRawContactId(contactId) ?: return
        val ops = ArrayList<ContentProviderOperation>()
        ops.add(
            ContentProviderOperation.newUpdate(ContactsContract.Data.CONTENT_URI)
                .withSelection(
                    "${ContactsContract.Data.RAW_CONTACT_ID} = ? AND " +
                    "${ContactsContract.Data.MIMETYPE} = '${ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE}'",
                    arrayOf(rawId.toString())
                )
                .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, newName)
                .build()
        )
        context.contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
    }

    override fun writeCustomField(contactId: Long, originalName: String, marathi: String) {
        val rawId = getRawContactId(contactId) ?: return
        val values = ContentValues().apply {
            put(ContactsContract.Data.RAW_CONTACT_ID, rawId)
            put(ContactsContract.Data.MIMETYPE, MIME_TYPE)
            put(ContactsContract.Data.DATA1, originalName)
            put(ContactsContract.Data.DATA2, marathi)
        }
        context.contentResolver.insert(ContactsContract.Data.CONTENT_URI, values)
    }

    override fun deleteAllCustomFields() {
        context.contentResolver.delete(
            ContactsContract.Data.CONTENT_URI,
            "${ContactsContract.Data.MIMETYPE} = ?",
            arrayOf(MIME_TYPE)
        )
    }

    override fun allContactsWithCustomField(): List<ContactWithCustomField> {
        val result = mutableListOf<ContactWithCustomField>()
        val cursor = context.contentResolver.query(
            ContactsContract.Data.CONTENT_URI,
            arrayOf(ContactsContract.Data.CONTACT_ID, ContactsContract.Data.DATA1, ContactsContract.Data.DATA2),
            "${ContactsContract.Data.MIMETYPE} = ?",
            arrayOf(MIME_TYPE),
            null
        ) ?: return result

        cursor.use {
            val idCol = it.getColumnIndexOrThrow(ContactsContract.Data.CONTACT_ID)
            val data1Col = it.getColumnIndexOrThrow(ContactsContract.Data.DATA1)
            val data2Col = it.getColumnIndexOrThrow(ContactsContract.Data.DATA2)
            while (it.moveToNext()) {
                result.add(ContactWithCustomField(it.getLong(idCol), it.getString(data1Col), it.getString(data2Col)))
            }
        }
        return result
    }

    override fun getDisplayName(contactId: Long): String? {
        val cursor = context.contentResolver.query(
            ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, contactId),
            arrayOf(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY),
            null, null, null
        ) ?: return null
        return cursor.use { if (it.moveToFirst()) it.getString(0) else null }
    }

    private fun getRawContactId(contactId: Long): Long? {
        val cursor = context.contentResolver.query(
            ContactsContract.RawContacts.CONTENT_URI,
            arrayOf(ContactsContract.RawContacts._ID),
            "${ContactsContract.RawContacts.CONTACT_ID} = ?",
            arrayOf(contactId.toString()),
            null
        ) ?: return null
        return cursor.use { if (it.moveToFirst()) it.getLong(0) else null }
    }
}
