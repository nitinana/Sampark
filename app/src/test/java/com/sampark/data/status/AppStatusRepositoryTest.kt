package com.sampark.data.status

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class AppStatusRepositoryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun createRepository(): AppStatusRepository {
        val file = File(tempFolder.newFolder(), "app_status.preferences_pb")
        val dataStore = PreferenceDataStoreFactory.create(produceFile = { file })
        return AppStatusRepository(dataStore)
    }

    @Test
    fun `direction defaults to NONE`() = runTest {
        val repo = createRepository()
        assertEquals(Direction.NONE, repo.direction.first())
    }

    @Test
    fun `phase defaults to COMPLETED`() = runTest {
        val repo = createRepository()
        assertEquals(Phase.COMPLETED, repo.phase.first())
    }

    @Test
    fun `permissionRequestedBefore defaults to false`() = runTest {
        val repo = createRepository()
        assertEquals(false, repo.permissionRequestedBefore.first())
    }

    @Test
    fun `setDirection persists and is readable back`() = runTest {
        val repo = createRepository()
        repo.setDirection(Direction.TRANSLATE)
        assertEquals(Direction.TRANSLATE, repo.direction.first())
    }

    @Test
    fun `setPhase persists and is readable back`() = runTest {
        val repo = createRepository()
        repo.setPhase(Phase.RUNNING)
        assertEquals(Phase.RUNNING, repo.phase.first())
    }

    @Test
    fun `setPermissionRequestedBefore persists and is readable back`() = runTest {
        val repo = createRepository()
        repo.setPermissionRequestedBefore(true)
        assertEquals(true, repo.permissionRequestedBefore.first())
    }
}
