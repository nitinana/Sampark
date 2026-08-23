package com.sampark.data.contacts

import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.ContactsContract

/**
 * Minimal in-memory stand-in for the system ContactsProvider2, used only by
 * Robolectric unit tests. Robolectric's shadows-framework does not ship a
 * shadow for the real contacts content provider (it lives outside AOSP
 * frameworks/base, in the separate ContactsProvider app), so there is
 * nothing behind `content://com.android.contacts/...` URIs unless a test
 * registers its own provider for that authority. This class implements just
 * enough of RawContacts/Data/Contacts query-insert-update semantics to
 * exercise [AndroidContactsRepository] end to end.
 */
class FakeContactsProvider : ContentProvider() {

    private data class RawContactRow(val id: Long, val contactId: Long, val accountType: String?, val accountName: String?)
    private data class DataRow(val id: Long, val rawContactId: Long, val mimetype: String, var displayName: String?)
    private data class ContactRow(val id: Long, val lookupKey: String, var displayNamePrimary: String?)

    private val rawContacts = mutableListOf<RawContactRow>()
    private val dataRows = mutableListOf<DataRow>()
    private val contacts = mutableListOf<ContactRow>()

    private var nextRawContactId = 1L
    private var nextDataId = 1L
    private var nextContactId = 1L

    private val matcher = UriMatcher(UriMatcher.NO_MATCH).apply {
        addURI(ContactsContract.AUTHORITY, "raw_contacts", RAW_CONTACTS)
        addURI(ContactsContract.AUTHORITY, "raw_contacts/#", RAW_CONTACTS_ID)
        addURI(ContactsContract.AUTHORITY, "data", DATA)
        addURI(ContactsContract.AUTHORITY, "data/#", DATA_ID)
        addURI(ContactsContract.AUTHORITY, "contacts", CONTACTS)
        addURI(ContactsContract.AUTHORITY, "contacts/#", CONTACTS_ID)
        addURI(ContactsContract.AUTHORITY, "contacts/lookup/*", CONTACTS_LOOKUP)
        addURI(ContactsContract.AUTHORITY, "contacts/lookup/*/#", CONTACTS_LOOKUP_ID)
    }

    override fun onCreate(): Boolean = true

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        return when (matcher.match(uri)) {
            RAW_CONTACTS -> {
                val contactId = nextContactId++
                contacts.add(ContactRow(contactId, "lookup-$contactId", null))

                val rawId = nextRawContactId++
                rawContacts.add(
                    RawContactRow(
                        id = rawId,
                        contactId = contactId,
                        accountType = values?.getAsString(ContactsContract.RawContacts.ACCOUNT_TYPE),
                        accountName = values?.getAsString(ContactsContract.RawContacts.ACCOUNT_NAME)
                    )
                )
                Uri.withAppendedPath(ContactsContract.RawContacts.CONTENT_URI, rawId.toString())
            }

            DATA -> {
                val rawContactId = values?.getAsLong(ContactsContract.Data.RAW_CONTACT_ID) ?: return null
                val mimetype = values.getAsString(ContactsContract.Data.MIMETYPE) ?: return null
                val displayName = values.getAsString(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME)

                val dataId = nextDataId++
                dataRows.add(DataRow(dataId, rawContactId, mimetype, displayName))

                if (mimetype == ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE) {
                    val raw = rawContacts.find { it.id == rawContactId }
                    if (raw != null) {
                        contacts.find { it.id == raw.contactId }?.displayNamePrimary = displayName
                    }
                }
                Uri.withAppendedPath(ContactsContract.Data.CONTENT_URI, dataId.toString())
            }

            else -> null
        }
    }

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<String>?): Int {
        return when (matcher.match(uri)) {
            DATA -> {
                if (values == null) return 0
                val newName = values.getAsString(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME)
                val (rawContactId, mimetype) = parseDataSelection(selection, selectionArgs) ?: return 0

                var updated = 0
                dataRows.filter { it.rawContactId == rawContactId && it.mimetype == mimetype }.forEach {
                    it.displayName = newName
                    updated++
                }
                // Only mirror into the aggregate Contacts row if the update actually
                // matched a real Data row — otherwise a wrong mimetype/raw-contact-id
                // in production code would silently "succeed" against this fake.
                if (updated > 0) {
                    val raw = rawContacts.find { it.id == rawContactId }
                    if (raw != null) {
                        contacts.find { it.id == raw.contactId }?.displayNamePrimary = newName
                    }
                }
                updated
            }
            else -> 0
        }
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int = 0

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String?
    ): Cursor? {
        return when (matcher.match(uri)) {
            RAW_CONTACTS_ID -> {
                val id = uri.lastPathSegment!!.toLong()
                val row = rawContacts.find { it.id == id } ?: return emptyCursor(projection)
                cursorFromRawContacts(projection, listOf(row))
            }

            RAW_CONTACTS -> {
                val matching = when {
                    selection == null -> rawContacts
                    matchesSingleColumnSelection(selection, ContactsContract.RawContacts.CONTACT_ID) -> {
                        val contactId = selectionArgs?.getOrNull(0)?.toLongOrNull()
                        if (contactId == null) emptyList() else rawContacts.filter { it.contactId == contactId }
                    }
                    // Unrecognized selection column: fail closed (empty), never fall
                    // through to "return everything" — a wrong column name in
                    // production code must show up as a test failure, not a silent pass.
                    else -> emptyList()
                }
                cursorFromRawContacts(projection, matching)
            }

            CONTACTS -> {
                val matching = when {
                    selection == null -> contacts
                    matchesSingleColumnSelection(selection, ContactsContract.Contacts._ID) -> {
                        val id = selectionArgs?.getOrNull(0)?.toLongOrNull()
                        if (id == null) emptyList() else contacts.filter { it.id == id }
                    }
                    matchesSingleColumnSelection(selection, ContactsContract.Contacts.LOOKUP_KEY) -> {
                        val lookupKey = selectionArgs?.getOrNull(0)
                        if (lookupKey == null) emptyList() else contacts.filter { it.lookupKey == lookupKey }
                    }
                    else -> emptyList()
                }
                cursorFromContacts(projection, matching)
            }

            CONTACTS_ID -> {
                val id = uri.lastPathSegment!!.toLong()
                val matching = contacts.filter { it.id == id }
                cursorFromContacts(projection, matching)
            }

            CONTACTS_LOOKUP -> {
                val lookupKey = uri.pathSegments[2]
                val matching = contacts.filter { it.lookupKey == lookupKey }
                cursorFromContacts(projection, matching)
            }

            CONTACTS_LOOKUP_ID -> {
                val id = uri.lastPathSegment!!.toLong()
                val matching = contacts.filter { it.id == id }
                cursorFromContacts(projection, matching)
            }

            else -> emptyCursor(projection)
        }
    }

    /**
     * Matches only the exact single-column shape production code uses:
     * "<column> = ?". Deliberately strict (no startsWith/substring matching)
     * so a selection against a different column can never be mistaken for
     * this one.
     */
    private fun matchesSingleColumnSelection(selection: String, column: String): Boolean =
        selection.trim() == "$column = ?"

    private fun parseDataSelection(selection: String?, args: Array<String>?): Pair<Long, String>? {
        if (selection == null || args == null || args.size < 2) return null
        val expected = "${ContactsContract.Data.RAW_CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} = ?"
        // Verify the selection actually names RAW_CONTACT_ID/MIMETYPE before
        // trusting args[0]/args[1] positionally — a selection built against the
        // wrong columns must not silently match by position alone.
        if (selection.trim() != expected) return null
        return args[0].toLongOrNull()?.let { it to args[1] }
    }

    private fun cursorFromRawContacts(projection: Array<out String>?, rows: List<RawContactRow>): Cursor {
        val columns = projection ?: arrayOf(
            ContactsContract.RawContacts._ID,
            ContactsContract.RawContacts.CONTACT_ID,
            ContactsContract.RawContacts.ACCOUNT_TYPE,
            ContactsContract.RawContacts.ACCOUNT_NAME
        )
        val cursor = MatrixCursor(columns)
        for (row in rows) {
            cursor.addRow(
                columns.map { column ->
                    when (column) {
                        ContactsContract.RawContacts._ID -> row.id
                        ContactsContract.RawContacts.CONTACT_ID -> row.contactId
                        ContactsContract.RawContacts.ACCOUNT_TYPE -> row.accountType
                        ContactsContract.RawContacts.ACCOUNT_NAME -> row.accountName
                        else -> null
                    }
                }
            )
        }
        return cursor
    }

    private fun cursorFromContacts(projection: Array<out String>?, rows: List<ContactRow>): Cursor {
        val columns = projection ?: arrayOf(
            ContactsContract.Contacts._ID,
            ContactsContract.Contacts.LOOKUP_KEY,
            ContactsContract.Contacts.DISPLAY_NAME_PRIMARY
        )
        val cursor = MatrixCursor(columns)
        for (row in rows) {
            cursor.addRow(
                columns.map { column ->
                    when (column) {
                        ContactsContract.Contacts._ID -> row.id
                        ContactsContract.Contacts.LOOKUP_KEY -> row.lookupKey
                        ContactsContract.Contacts.DISPLAY_NAME_PRIMARY -> row.displayNamePrimary
                        else -> null
                    }
                }
            )
        }
        return cursor
    }

    private fun emptyCursor(projection: Array<out String>?): Cursor =
        MatrixCursor(projection ?: arrayOf(ContactsContract.Contacts._ID))

    companion object {
        private const val RAW_CONTACTS = 1
        private const val RAW_CONTACTS_ID = 2
        private const val DATA = 3
        private const val DATA_ID = 4
        private const val CONTACTS = 5
        private const val CONTACTS_ID = 6
        private const val CONTACTS_LOOKUP = 7
        private const val CONTACTS_LOOKUP_ID = 8
    }
}
