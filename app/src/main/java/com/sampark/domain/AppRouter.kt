package com.sampark.domain

import com.sampark.data.contacts.ContactsRepository
import com.sampark.data.status.AppStatusRepository
import com.sampark.data.status.Direction
import com.sampark.data.status.Phase
import kotlinx.coroutines.flow.first

class AppRouter(
    private val contactsRepository: ContactsRepository,
    private val appStatusRepository: AppStatusRepository
) {
    suspend fun resolveStartDestination(): String {
        if (!contactsRepository.hasContactsPermission()) {
            return if (appStatusRepository.permissionRequestedBefore.first()) {
                Routes.PERMISSION_DENIED
            } else {
                Routes.WELCOME
            }
        }

        val direction = appStatusRepository.direction.first()
        val phase = appStatusRepository.phase.first()

        return when {
            direction == Direction.NONE -> Routes.WELCOME
            direction == Direction.TRANSLATE && phase == Phase.RUNNING -> Routes.RUN_TRANSLATE
            direction == Direction.TRANSLATE && phase == Phase.COMPLETED -> Routes.HOME
            direction == Direction.ROLLBACK && phase == Phase.RUNNING -> Routes.RUN_ROLLBACK
            direction == Direction.ROLLBACK && phase == Phase.COMPLETED -> Routes.HOME
            else -> Routes.WELCOME
        }
    }
}
