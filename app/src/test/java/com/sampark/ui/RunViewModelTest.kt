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
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

private class FakeContactsRepository(
    /**
     * When true, every [updateName] suspends on a virtual-time delay, so the run
     * loop can be observed mid-flight (and two concurrent loops would interleave).
     */
    private val suspendOnUpdate: Boolean = false,
    /** When non-null, [updateName] throws this on the Nth (1-based) call. */
    private val throwOnUpdateCall: Int? = null
) : ContactsRepository {
    val names = mutableMapOf<String, String>()
    val updatedNames = mutableListOf<String>()

    override suspend fun hasContactsPermission() = true
    override suspend fun getEligibleContacts(): List<ContactRef> = names.map { ContactRef(it.key, it.value) }
    override suspend fun getCurrentName(lookupKey: String): String? = names[lookupKey]
    override suspend fun updateName(lookupKey: String, newName: String) {
        if (suspendOnUpdate) delay(1)
        updatedNames.add(lookupKey)
        if (throwOnUpdateCall != null && updatedNames.size == throwOnUpdateCall) {
            throw SecurityException("contacts write failed")
        }
        names[lookupKey] = newName
    }
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

    @Test
    fun `start called twice while running does not create two concurrent run loops`() = runTest {
        val dao = createDao()
        dao.insertAll(
            listOf(
                LedgerEntity("key1", "Nitin", "", LedgerStatus.PENDING),
                LedgerEntity("key2", "Swarra", "", LedgerStatus.PENDING),
                LedgerEntity("key3", "Aparna", "", LedgerStatus.PENDING)
            )
        )
        val contacts = FakeContactsRepository(suspendOnUpdate = true).apply {
            names["key1"] = "Nitin"
            names["key2"] = "Swarra"
            names["key3"] = "Aparna"
        }
        val status = createStatusRepository()
        status.setDirection(Direction.TRANSLATE)
        status.setPhase(Phase.RUNNING)
        val engine = RunEngine(contacts, dao, TransliterationEngine())
        val viewModel = RunViewModel(Direction.TRANSLATE, engine, status, dao)

        // Simulates the activity being recreated (rotation / font-scale change)
        // while a run is in flight: the LaunchedEffect fires start() a second time.
        viewModel.start()
        testDispatcher.scheduler.runCurrent()
        viewModel.start()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf("key1", "key2", "key3"), contacts.updatedNames)
    }

    @Test
    fun `pause after a duplicate start actually stops all work`() = runTest {
        val dao = createDao()
        dao.insertAll(
            (1..6).map { LedgerEntity("key$it", "Name$it", "", LedgerStatus.PENDING) }
        )
        val contacts = FakeContactsRepository(suspendOnUpdate = true).apply {
            (1..6).forEach { names["key$it"] = "Name$it" }
        }
        val status = createStatusRepository()
        status.setDirection(Direction.TRANSLATE)
        status.setPhase(Phase.RUNNING)
        val engine = RunEngine(contacts, dao, TransliterationEngine())
        val viewModel = RunViewModel(Direction.TRANSLATE, engine, status, dao)

        viewModel.start()
        testDispatcher.scheduler.runCurrent()
        viewModel.start()
        testDispatcher.scheduler.runCurrent()
        viewModel.pause()
        val processedAtPause = contacts.updatedNames.size
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(processedAtPause, contacts.updatedNames.size)
        assertTrue(viewModel.uiState.value.isPaused)
        assertEquals(Phase.RUNNING, status.phase.first())
    }

    @Test
    fun `engine failure mid-run leaves the run paused and resumable instead of crashing`() = runTest {
        val dao = createDao()
        dao.insertAll(
            listOf(
                LedgerEntity("key1", "Nitin", "", LedgerStatus.PENDING),
                LedgerEntity("key2", "Swarra", "", LedgerStatus.PENDING)
            )
        )
        val contacts = FakeContactsRepository(throwOnUpdateCall = 2).apply {
            names["key1"] = "Nitin"
            names["key2"] = "Swarra"
        }
        val status = createStatusRepository()
        status.setDirection(Direction.TRANSLATE)
        status.setPhase(Phase.RUNNING)
        val engine = RunEngine(contacts, dao, TransliterationEngine())
        val viewModel = RunViewModel(Direction.TRANSLATE, engine, status, dao)

        viewModel.start()
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isPaused)
        assertEquals(Phase.RUNNING, status.phase.first())
    }

    @Test
    fun `showPausedWithoutStarting does not touch any contact and leaves phase RUNNING`() = runTest {
        val dao = createDao()
        dao.insertAll(listOf(LedgerEntity("key1", "Nitin", "", LedgerStatus.PENDING)))
        val contacts = FakeContactsRepository().apply { names["key1"] = "Nitin" }
        val status = createStatusRepository()
        status.setDirection(Direction.TRANSLATE)
        status.setPhase(Phase.RUNNING)
        val engine = RunEngine(contacts, dao, TransliterationEngine())
        val viewModel = RunViewModel(Direction.TRANSLATE, engine, status, dao)

        viewModel.showPausedWithoutStarting()
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isPaused)
        assertEquals(emptyList<String>(), contacts.updatedNames)
        assertEquals("Nitin", contacts.names["key1"])
        assertEquals(Phase.RUNNING, status.phase.first())

        // ...and an explicit Resume tap still runs the whole thing.
        viewModel.resume()
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(listOf("key1"), contacts.updatedNames)
        assertEquals(Phase.COMPLETED, status.phase.first())
    }

    @Test
    fun `showPausedWithoutStarting is a no-op once a run has already started`() = runTest {
        val dao = createDao()
        dao.insertAll(listOf(LedgerEntity("key1", "Nitin", "", LedgerStatus.PENDING)))
        val contacts = FakeContactsRepository(suspendOnUpdate = true).apply { names["key1"] = "Nitin" }
        val status = createStatusRepository()
        status.setDirection(Direction.TRANSLATE)
        status.setPhase(Phase.RUNNING)
        val engine = RunEngine(contacts, dao, TransliterationEngine())
        val viewModel = RunViewModel(Direction.TRANSLATE, engine, status, dao)

        viewModel.start()
        testDispatcher.scheduler.runCurrent()
        viewModel.showPausedWithoutStarting()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(false, viewModel.uiState.value.isPaused)
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
