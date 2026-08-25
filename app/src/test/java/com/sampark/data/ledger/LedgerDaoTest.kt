package com.sampark.data.ledger

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LedgerDaoTest {

    private fun createDao(): LedgerDao {
        val db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            LedgerDatabase::class.java
        ).allowMainThreadQueries().build()
        return db.ledgerDao()
    }

    @Test
    fun `insertAll then countAll reflects inserted rows`() = runTest {
        val dao = createDao()
        dao.insertAll(
            listOf(
                LedgerEntity("key1", "Nitin", "स्वारा", LedgerStatus.PENDING),
                LedgerEntity("key2", "Swarra", "निनान", LedgerStatus.PENDING)
            )
        )
        assertEquals(2, dao.countAll().first())
    }

    @Test
    fun `updateStatus moves a row from PENDING to TRANSLATED`() = runTest {
        val dao = createDao()
        dao.insertAll(listOf(LedgerEntity("key1", "Nitin", "निनान", LedgerStatus.PENDING)))
        dao.updateStatus("key1", LedgerStatus.TRANSLATED)
        assertEquals(1, dao.countByStatus(LedgerStatus.TRANSLATED).first())
        assertEquals(0, dao.countByStatus(LedgerStatus.PENDING).first())
    }

    @Test
    fun `getRowsByStatus returns only matching rows`() = runTest {
        val dao = createDao()
        dao.insertAll(
            listOf(
                LedgerEntity("key1", "Nitin", "निनान", LedgerStatus.TRANSLATED),
                LedgerEntity("key2", "Swarra", "स्वारा", LedgerStatus.PENDING)
            )
        )
        val translated = dao.getRowsByStatus(LedgerStatus.TRANSLATED)
        assertEquals(1, translated.size)
        assertEquals("key1", translated[0].lookupKey)
    }

    @Test
    fun `countByStatuses sums matching statuses`() = runTest {
        val dao = createDao()
        dao.insertAll(
            listOf(
                LedgerEntity("key1", "Nitin", "निनान", LedgerStatus.TRANSLATED),
                LedgerEntity("key2", "Swarra", "स्वारा", LedgerStatus.ROLLED_BACK),
                LedgerEntity("key3", "Amit", "अमित", LedgerStatus.PENDING)
            )
        )
        val count = dao.countByStatuses(listOf(LedgerStatus.TRANSLATED, LedgerStatus.ROLLED_BACK)).first()
        assertEquals(2, count)
    }

    @Test
    fun `clearAll removes every row`() = runTest {
        val dao = createDao()
        dao.insertAll(listOf(LedgerEntity("key1", "Nitin", "निनान", LedgerStatus.PENDING)))
        dao.clearAll()
        assertEquals(0, dao.countAll().first())
    }
}
