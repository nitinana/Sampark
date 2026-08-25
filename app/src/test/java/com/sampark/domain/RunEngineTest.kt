package com.sampark.domain

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.sampark.data.contacts.ContactRef
import com.sampark.data.contacts.ContactsRepository
import com.sampark.data.ledger.LedgerDatabase
import com.sampark.data.ledger.LedgerEntity
import com.sampark.data.ledger.LedgerStatus
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

private class FakeContactsRepository : ContactsRepository {
    val names = mutableMapOf<String, String>() // lookupKey -> current name
    var permission = true
    var getEligibleContactsCallCount = 0

    override suspend fun hasContactsPermission() = permission
    override suspend fun getEligibleContacts(): List<ContactRef> {
        getEligibleContactsCallCount++
        return names.map { (key, name) -> ContactRef(key, name) }
    }

    override suspend fun getCurrentName(lookupKey: String): String? = names[lookupKey]

    override suspend fun updateName(lookupKey: String, newName: String) {
        names[lookupKey] = newName
    }
}

@RunWith(RobolectricTestRunner::class)
class RunEngineTest {

    private fun createDao() = Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext(),
        LedgerDatabase::class.java
    ).allowMainThreadQueries().build().ledgerDao()

    @Test
    fun `runTranslate transliterates every PENDING row and marks it TRANSLATED`() = runTest {
        val dao = createDao()
        val contacts = FakeContactsRepository().apply {
            names["key1"] = "Nitin"
            names["key2"] = "Swarra"
        }
        dao.insertAll(
            listOf(
                LedgerEntity("key1", "Nitin", "", LedgerStatus.PENDING),
                LedgerEntity("key2", "Swarra", "", LedgerStatus.PENDING)
            )
        )
        val engine = RunEngine(contacts, dao, TransliterationEngine())

        engine.runTranslate()

        val translated = dao.getRowsByStatus(LedgerStatus.TRANSLATED)
        assertEquals(2, translated.size)
        assertEquals(contacts.names["key1"], translated.first { it.lookupKey == "key1" }.translatedName)
    }

    @Test
    fun `runTranslate does not seed the ledger itself`() = runTest {
        val dao = createDao()
        val contacts = FakeContactsRepository().apply {
            names["key1"] = "Nitin"
            names["key2"] = "Swarra"
        }
        val engine = RunEngine(contacts, dao, TransliterationEngine())

        // Ledger is empty; nothing has called seedLedgerForTranslate().
        engine.runTranslate()

        assertEquals(0, dao.getRowsByStatus(LedgerStatus.TRANSLATED).size)
        assertEquals(0, contacts.getEligibleContactsCallCount)
    }

    @Test
    fun `runTranslate resumed mid-run does not touch already TRANSLATED rows`() = runTest {
        val dao = createDao()
        // key1 was already translated in an interrupted earlier pass of the same run.
        val contacts = FakeContactsRepository().apply {
            names["key1"] = "निनान"
            names["key2"] = "Swarra"
        }
        dao.insertAll(
            listOf(
                LedgerEntity("key1", "Nitin", "निनान", LedgerStatus.TRANSLATED),
                LedgerEntity("key2", "Swarra", "", LedgerStatus.PENDING)
            )
        )
        val engine = RunEngine(contacts, dao, TransliterationEngine())

        // Simulates RunViewModel.resume() -> start() -> runTranslate() again, with no reseed.
        engine.runTranslate()

        assertEquals(0, contacts.getEligibleContactsCallCount)
        val translated = dao.getRowsByStatus(LedgerStatus.TRANSLATED)
        assertEquals(2, translated.size)
        val key1Row = translated.first { it.lookupKey == "key1" }
        assertEquals("Nitin", key1Row.originalName)
        assertEquals("निनान", key1Row.translatedName)
        assertEquals("निनान", contacts.getCurrentName("key1"))
    }

    @Test
    fun `seedLedgerForTranslate seeds the ledger from currently eligible contacts, clearing any stale rows first`() = runTest {
        val dao = createDao()
        // Stale row left over from a previous run, for a contact that no longer exists.
        dao.insertAll(listOf(LedgerEntity("stale-key", "Ghost", "घोस्ट", LedgerStatus.TRANSLATED)))

        val contacts = FakeContactsRepository().apply {
            names["key1"] = "Nitin"
            names["key2"] = "Swarra"
        }
        val engine = RunEngine(contacts, dao, TransliterationEngine())

        engine.seedLedgerForTranslate()

        val pending = dao.getRowsByStatus(LedgerStatus.PENDING)
        assertEquals(2, pending.size)
        assertEquals(0, dao.getRowsByStatus(LedgerStatus.TRANSLATED).size)
        assertEquals(true, pending.none { it.lookupKey == "stale-key" })
    }

    @Test
    fun `runRollback reverts a row whose name still matches translatedName`() = runTest {
        val dao = createDao()
        val contacts = FakeContactsRepository().apply {
            names["key1"] = "निनान"
        }
        dao.insertAll(listOf(LedgerEntity("key1", "Nitin", "निनान", LedgerStatus.TRANSLATED)))
        val engine = RunEngine(contacts, dao, TransliterationEngine())

        engine.runRollback()

        assertEquals("Nitin", contacts.getCurrentName("key1"))
        assertEquals(1, dao.getRowsByStatus(LedgerStatus.ROLLED_BACK).size)
    }

    @Test
    fun `runRollback skips a row whose name has drifted since translation`() = runTest {
        val dao = createDao()
        val contacts = FakeContactsRepository().apply {
            names["key1"] = "Someone Else Entirely"
        }
        dao.insertAll(listOf(LedgerEntity("key1", "Nitin", "निनान", LedgerStatus.TRANSLATED)))
        val engine = RunEngine(contacts, dao, TransliterationEngine())

        engine.runRollback()

        assertEquals("Someone Else Entirely", contacts.getCurrentName("key1"))
        assertEquals(1, dao.getRowsByStatus(LedgerStatus.SKIPPED_EXTERNAL_EDIT).size)
    }

    @Test
    fun `runRollback skips a row whose contact no longer exists`() = runTest {
        val dao = createDao()
        val contacts = FakeContactsRepository() // key1 never added -> getCurrentName returns null
        dao.insertAll(listOf(LedgerEntity("key1", "Nitin", "निनान", LedgerStatus.TRANSLATED)))
        val engine = RunEngine(contacts, dao, TransliterationEngine())

        engine.runRollback()

        assertEquals(1, dao.getRowsByStatus(LedgerStatus.SKIPPED_EXTERNAL_EDIT).size)
    }

    @Test
    fun `runTranslate invokes the progress callback per contact`() = runTest {
        val dao = createDao()
        val contacts = FakeContactsRepository().apply { names["key1"] = "Nitin" }
        dao.insertAll(listOf(LedgerEntity("key1", "Nitin", "", LedgerStatus.PENDING)))
        val engine = RunEngine(contacts, dao, TransliterationEngine())

        val processed = mutableListOf<Pair<String, String>>()
        engine.runTranslate { original, translated -> processed.add(original to translated) }

        assertEquals(1, processed.size)
        assertEquals("Nitin", processed[0].first)
    }
}
