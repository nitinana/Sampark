package com.sampark.domain

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.sampark.data.contacts.ContactRef
import com.sampark.data.contacts.ContactsRepository
import com.sampark.data.status.AppStatusRepository
import com.sampark.data.status.Direction
import com.sampark.data.status.Phase
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

private class RouterFakeContactsRepository(var permission: Boolean) : ContactsRepository {
    override suspend fun hasContactsPermission() = permission
    override suspend fun getEligibleContacts(): List<ContactRef> = emptyList()
    override suspend fun getCurrentName(lookupKey: String): String? = null
    override suspend fun updateName(lookupKey: String, newName: String) {}
}

class AppRouterTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun createStatusRepository(): AppStatusRepository {
        val file = File(tempFolder.newFolder(), "app_status.preferences_pb")
        return AppStatusRepository(PreferenceDataStoreFactory.create(produceFile = { file }))
    }

    @Test
    fun `no permission and never requested before routes to Welcome`() = runTest {
        val router = AppRouter(RouterFakeContactsRepository(permission = false), createStatusRepository())
        assertEquals(Routes.WELCOME, router.resolveStartDestination())
    }

    @Test
    fun `no permission but requested before routes to PermissionDenied`() = runTest {
        val status = createStatusRepository()
        status.setPermissionRequestedBefore(true)
        val router = AppRouter(RouterFakeContactsRepository(permission = false), status)
        assertEquals(Routes.PERMISSION_DENIED, router.resolveStartDestination())
    }

    @Test
    fun `permission granted and direction NONE routes to Welcome`() = runTest {
        val router = AppRouter(RouterFakeContactsRepository(permission = true), createStatusRepository())
        assertEquals(Routes.WELCOME, router.resolveStartDestination())
    }

    @Test
    fun `translate running routes to RunTranslate`() = runTest {
        val status = createStatusRepository()
        status.setDirection(Direction.TRANSLATE)
        status.setPhase(Phase.RUNNING)
        val router = AppRouter(RouterFakeContactsRepository(permission = true), status)
        assertEquals(Routes.RUN_TRANSLATE, router.resolveStartDestination())
    }

    @Test
    fun `translate completed routes to Home`() = runTest {
        val status = createStatusRepository()
        status.setDirection(Direction.TRANSLATE)
        status.setPhase(Phase.COMPLETED)
        val router = AppRouter(RouterFakeContactsRepository(permission = true), status)
        assertEquals(Routes.HOME, router.resolveStartDestination())
    }

    @Test
    fun `rollback running routes to RunRollback`() = runTest {
        val status = createStatusRepository()
        status.setDirection(Direction.ROLLBACK)
        status.setPhase(Phase.RUNNING)
        val router = AppRouter(RouterFakeContactsRepository(permission = true), status)
        assertEquals(Routes.RUN_ROLLBACK, router.resolveStartDestination())
    }

    @Test
    fun `rollback completed routes to Home`() = runTest {
        val status = createStatusRepository()
        status.setDirection(Direction.ROLLBACK)
        status.setPhase(Phase.COMPLETED)
        val router = AppRouter(RouterFakeContactsRepository(permission = true), status)
        assertEquals(Routes.HOME, router.resolveStartDestination())
    }
}
