package com.sampark.ui.navigation

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.sampark.data.ledger.LedgerDatabase
import com.sampark.data.ledger.LedgerEntity
import com.sampark.data.ledger.LedgerStatus
import com.sampark.data.status.Direction
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CompletionSummaryTest {

    private fun createDao() = Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext(),
        LedgerDatabase::class.java
    ).allowMainThreadQueries().build().ledgerDao()

    @Test
    fun `translate summary reports the number of translated contacts`() = runTest {
        val dao = createDao()
        dao.insertAll(
            listOf(
                LedgerEntity("k1", "Nitin", "नितीन", LedgerStatus.TRANSLATED),
                LedgerEntity("k2", "Swarra", "स्वारा", LedgerStatus.TRANSLATED),
                LedgerEntity("k3", "Aparna", "", LedgerStatus.PENDING)
            )
        )

        val summary = buildCompletionSummary(Direction.TRANSLATE, dao)

        assertEquals("2 संपर्कांची नावं मराठीत बदलली.", summary)
    }

    @Test
    fun `rollback summary reports the number of restored contacts`() = runTest {
        val dao = createDao()
        dao.insertAll(
            listOf(
                LedgerEntity("k1", "Nitin", "नितीन", LedgerStatus.ROLLED_BACK),
                LedgerEntity("k2", "Swarra", "स्वारा", LedgerStatus.ROLLED_BACK),
                LedgerEntity("k3", "Aparna", "अपर्णा", LedgerStatus.ROLLED_BACK)
            )
        )

        val summary = buildCompletionSummary(Direction.ROLLBACK, dao)

        assertEquals("3 संपर्क परत इंग्रजीत बदलले.", summary)
    }

    @Test
    fun `contacts skipped because they were edited afterwards are surfaced`() = runTest {
        val dao = createDao()
        dao.insertAll(
            listOf(
                LedgerEntity("k1", "Nitin", "नितीन", LedgerStatus.ROLLED_BACK),
                LedgerEntity("k2", "Swarra", "स्वारा", LedgerStatus.SKIPPED_EXTERNAL_EDIT),
                LedgerEntity("k3", "Aparna", "अपर्णा", LedgerStatus.SKIPPED_EXTERNAL_EDIT)
            )
        )

        val summary = buildCompletionSummary(Direction.ROLLBACK, dao)

        assertTrue(summary.startsWith("1 संपर्क परत इंग्रजीत बदलले."))
        assertTrue(summary.contains("2 संपर्कांची नावं"))
        assertTrue(summary.contains("सुरक्षित ठेवली आहेत"))
    }

    @Test
    fun `no skipped contacts means no extra sentence`() = runTest {
        val dao = createDao()
        dao.insertAll(listOf(LedgerEntity("k1", "Nitin", "नितीन", LedgerStatus.TRANSLATED)))

        val summary = buildCompletionSummary(Direction.TRANSLATE, dao)

        assertEquals("1 संपर्कांची नावं मराठीत बदलली.", summary)
    }
}
