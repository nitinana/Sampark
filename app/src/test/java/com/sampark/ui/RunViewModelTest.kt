package com.sampark.ui

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.sampark.data.contacts.ContactRef
import com.sampark.data.contacts.ContactsRepository
import com.sampark.data.ledger.LedgerDatabase
import com.sampark.data.ledger.LedgerEntity
import com.sampark.data.ledger.LedgerStatus
import com.sampark.data.status.AppStatusRepository
import com.sampark.data.status.Direction
import com.sampark.data.status.Phase
import com.sampark.domain.RunEngine
import com.sampark.domain.TransliterationEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

private class FakeContactsRepository : ContactsRepository {
    val names = mutableMapOf<String, String>()
    override fun hasContactsPermission() = true
    override fun getEligibleContacts(): List<ContactRef> = names.map { ContactRef(it.key, it.value) }
    override fun getCurrentName(lookupKey: String): String? = names[lookupKey]
    override fun updateName(lookupKey: String, newName: String) { names[lookupKey] = newName }
}

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class RunViewModelTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createStatusRepository(): AppStatusRepository {
        val file = File(tempFolder.newFolder(), "app_status.preferences_pb")
        return AppStatusRepository(
            PreferenceDataStoreFactory.create(
                scope = CoroutineScope(testDispatcher),
                produceFile = { file }
            )
        )
    }

    private fun createDao() = Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext(),
        LedgerDatabase::class.java
    ).allowMainThreadQueries()
        .setQueryExecutor { it.run() }
        .setTransactionExecutor { it.run() }
        .build().ledgerDao()

    @Test
    fun `start runs to completion and marks phase COMPLETED`() = runTest {
        val dao = createDao()
        dao.insertAll(listOf(LedgerEntity("key1", "Nitin", "", LedgerStatus.PENDING)))
        val contacts = FakeContactsRepository().apply { names["key1"] = "Nitin" }
        val status = createStatusRepository()
        status.setDirection(Direction.TRANSLATE)
        status.setPhase(Phase.RUNNING)
        val engine = RunEngine(contacts, dao, TransliterationEngine())
        val viewModel = RunViewModel(Direction.TRANSLATE, engine, status, dao)

        viewModel.start()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(Phase.COMPLETED, status.phase.first())
        assertEquals(1, viewModel.uiState.value.done)
        assertEquals(1, viewModel.uiState.value.total)
    }

    @Test
    fun `cancel on translate direction sets phase COMPLETED early`() = runTest {
        val dao = createDao()
        dao.insertAll(
            listOf(
                LedgerEntity("key1", "Nitin", "", LedgerStatus.PENDING),
                LedgerEntity("key2", "Swarra", "", LedgerStatus.PENDING)
            )
        )
        val contacts = FakeContactsRepository().apply {
            names["key1"] = "Nitin"
            names["key2"] = "Swarra"
        }
        val status = createStatusRepository()
        status.setDirection(Direction.TRANSLATE)
        status.setPhase(Phase.RUNNING)
        val engine = RunEngine(contacts, dao, TransliterationEngine())
        val viewModel = RunViewModel(Direction.TRANSLATE, engine, status, dao)

        viewModel.cancel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(Phase.COMPLETED, status.phase.first())
    }

    @Test(expected = IllegalStateException::class)
    fun `cancel on rollback direction throws`() = runTest {
        val dao = createDao()
        val status = createStatusRepository()
        val engine = RunEngine(FakeContactsRepository(), dao, TransliterationEngine())
        val viewModel = RunViewModel(Direction.ROLLBACK, engine, status, dao)

        viewModel.cancel()
    }
}
