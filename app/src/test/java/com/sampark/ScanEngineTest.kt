package com.sampark

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// --- Fakes ---

class FakeTransliterator(private val result: String? = "मराठी नाव") : Transliterator {
    override fun transliterate(name: String): String? = result
}

/**
 * preSeededCustomFields: contactId -> Pair(originalName, transliteratedName)
 * Represents contacts already processed by a prior scan (Data1, Data2 stored).
 * contacts list drives what getDisplayName returns initially.
 */
class FakeContactsRepository(
    contacts: List<Contact> = emptyList(),
    preSeededCustomFields: Map<Long, Pair<String, String>> = emptyMap()
) : ContactsRepository {

    private val displayNames = contacts.associate { it.id to it.displayName }.toMutableMap()
    private val customFields = preSeededCustomFields.toMutableMap()

    val updatedNames = mutableMapOf<Long, String>()

    val writtenCustomFields: Map<Long, Pair<String, String>> get() = customFields.toMap()
    fun hasAnyCustomFields(): Boolean = customFields.isNotEmpty()

    override fun allWritableContacts(): List<Contact> =
        displayNames.map { (id, name) -> Contact(id, name) }

    override fun hasCustomField(contactId: Long): Boolean = contactId in customFields

    override fun updateDisplayName(contactId: Long, newName: String) {
        updatedNames[contactId] = newName
        displayNames[contactId] = newName
    }

    override fun writeCustomField(contactId: Long, originalName: String, marathi: String) {
        customFields[contactId] = Pair(originalName, marathi)
    }

    override fun deleteAllCustomFields() { customFields.clear() }

    override fun allContactsWithCustomField(): List<ContactWithCustomField> =
        customFields.map { (id, pair) ->
            ContactWithCustomField(id, pair.first, pair.second)
        }

    override fun getDisplayName(contactId: Long): String? = displayNames[contactId]
}

// --- Scan tests ---

class ScanEngineTest {

    @Test
    fun `scan skips a contact that already has a Sampark custom field`() {
        val repo = FakeContactsRepository(
            contacts = listOf(Contact(id = 1L, displayName = "Rahul")),
            preSeededCustomFields = mapOf(1L to Pair("Rahul", "राहुल"))
        )

        runScan(repo, FakeTransliterator())

        // only the pre-seeded field exists, scan wrote nothing new
        assertEquals(mapOf(1L to Pair("Rahul", "राहुल")), repo.writtenCustomFields)
        assertTrue(repo.updatedNames.isEmpty())
    }

    @Test
    fun `scan transliterates a Latin-name contact and writes custom field`() {
        val repo = FakeContactsRepository(
            contacts = listOf(Contact(id = 1L, displayName = "Rahul Sharma"))
        )

        runScan(repo, FakeTransliterator(result = "राहुल शर्मा"))

        assertEquals("राहुल शर्मा", repo.updatedNames[1L])
        assertEquals(Pair("Rahul Sharma", "राहुल शर्मा"), repo.writtenCustomFields[1L])
    }

    @Test
    fun `scan skips a contact with a Devanagari-only name`() {
        val repo = FakeContactsRepository(
            contacts = listOf(Contact(id = 1L, displayName = "राहुल"))
        )

        runScan(repo, FakeTransliterator())

        assertTrue(repo.writtenCustomFields.isEmpty())
        assertTrue(repo.updatedNames.isEmpty())
    }

    @Test
    fun `scan skips a digit-only contact`() {
        val repo = FakeContactsRepository(
            contacts = listOf(Contact(id = 1L, displayName = "9876543210"))
        )

        runScan(repo, FakeTransliterator())

        assertTrue(repo.writtenCustomFields.isEmpty())
    }

    @Test
    fun `scan silently skips a contact when transliteration returns null`() {
        val repo = FakeContactsRepository(
            contacts = listOf(Contact(id = 1L, displayName = "Rahul"))
        )

        runScan(repo, FakeTransliterator(result = null))

        assertTrue(repo.writtenCustomFields.isEmpty())
        assertTrue(repo.updatedNames.isEmpty())
    }

    @Test
    fun `scan returns count of successfully transliterated contacts`() {
        val repo = FakeContactsRepository(
            contacts = listOf(
                Contact(id = 1L, displayName = "Rahul"),
                Contact(id = 2L, displayName = "राहुल"),
                Contact(id = 3L, displayName = "Priya"),
            )
        )

        val count = runScan(repo, FakeTransliterator())

        assertEquals(2, count)
    }
}

// --- Rollback tests ---

class RollbackEngineTest {

    @Test
    fun `rollback restores original English name when current name matches transliterated`() {
        val repo = FakeContactsRepository(
            contacts = listOf(Contact(id = 1L, displayName = "राहुल शर्मा")),
            preSeededCustomFields = mapOf(1L to Pair("Rahul Sharma", "राहुल शर्मा"))
        )

        runRollback(repo)

        assertEquals("Rahul Sharma", repo.updatedNames[1L])
    }

    @Test
    fun `rollback deletes all custom fields after restoring names`() {
        val repo = FakeContactsRepository(
            contacts = listOf(Contact(id = 1L, displayName = "राहुल")),
            preSeededCustomFields = mapOf(1L to Pair("Rahul", "राहुल"))
        )

        runRollback(repo)

        assertFalse(repo.hasAnyCustomFields())
    }

    @Test
    fun `rollback skips a contact whose name was manually edited after transliteration`() {
        val repo = FakeContactsRepository(
            contacts = listOf(Contact(id = 1L, displayName = "Rahul S.")),
            preSeededCustomFields = mapOf(1L to Pair("Rahul Sharma", "राहुल शर्मा"))
        )

        runRollback(repo)

        assertNull(repo.updatedNames[1L])
        assertFalse(repo.hasAnyCustomFields())
    }

    @Test
    fun `rollback skips a contact that was deleted after transliteration`() {
        val repo = FakeContactsRepository(
            contacts = emptyList(),
            preSeededCustomFields = mapOf(1L to Pair("Rahul", "राहुल"))
        )

        runRollback(repo)

        assertTrue(repo.updatedNames.isEmpty())
        assertFalse(repo.hasAnyCustomFields())
    }

    @Test
    fun `rollback is resumable — already-restored contacts are skipped on second run`() {
        // Simulate state after interrupted rollback:
        // contact 1 already restored to English, but custom fields not yet deleted
        // contact 2 still showing Marathi
        val repo = FakeContactsRepository(
            contacts = listOf(
                Contact(id = 1L, displayName = "Rahul"),   // already restored
                Contact(id = 2L, displayName = "प्रिया"),  // not yet restored
            ),
            preSeededCustomFields = mapOf(
                1L to Pair("Rahul", "राहुल"),
                2L to Pair("Priya", "प्रिया")
            )
        )

        runRollback(repo)

        // contact 1 was already English — current ("Rahul") != Data2 ("राहुल") → skipped
        assertNull(repo.updatedNames[1L])
        // contact 2 still Marathi — current ("प्रिया") == Data2 ("प्रिया") → restored
        assertEquals("Priya", repo.updatedNames[2L])
        assertFalse(repo.hasAnyCustomFields())
    }

    @Test
    fun `rollback handles multiple contacts restoring all that match`() {
        val repo = FakeContactsRepository(
            contacts = listOf(
                Contact(id = 1L, displayName = "राहुल"),
                Contact(id = 2L, displayName = "प्रिया"),
                Contact(id = 3L, displayName = "Amit edited"), // user edited
            ),
            preSeededCustomFields = mapOf(
                1L to Pair("Rahul", "राहुल"),
                2L to Pair("Priya", "प्रिया"),
                3L to Pair("Amit", "अमित")
            )
        )

        runRollback(repo)

        assertEquals("Rahul", repo.updatedNames[1L])
        assertEquals("Priya", repo.updatedNames[2L])
        assertNull(repo.updatedNames[3L])
        assertFalse(repo.hasAnyCustomFields())
    }
}
