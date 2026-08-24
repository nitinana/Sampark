package com.sampark.domain

import com.sampark.data.contacts.ContactsRepository
import com.sampark.data.ledger.LedgerDao
import com.sampark.data.ledger.LedgerEntity
import com.sampark.data.ledger.LedgerStatus
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

class RunEngine(
    private val contactsRepository: ContactsRepository,
    private val ledgerDao: LedgerDao,
    private val transliterationEngine: TransliterationEngine
) {
    suspend fun runTranslate(onContactProcessed: suspend (originalName: String, translatedName: String) -> Unit = { _, _ -> }) {
        ledgerDao.clearAll()
        val eligibleContacts = contactsRepository.getEligibleContacts()
        ledgerDao.insertAll(
            eligibleContacts.map { contact ->
                LedgerEntity(contact.lookupKey, contact.name, "", LedgerStatus.PENDING)
            }
        )

        val pendingRows = ledgerDao.getRowsByStatus(LedgerStatus.PENDING)
        for (row in pendingRows) {
            currentCoroutineContext().ensureActive()
            val translatedName = transliterationEngine.transliterate(row.originalName)
            contactsRepository.updateName(row.lookupKey, translatedName)
            ledgerDao.updateTranslatedNameAndStatus(row.lookupKey, translatedName, LedgerStatus.TRANSLATED)
            onContactProcessed(row.originalName, translatedName)
        }
    }

    suspend fun runRollback(onContactProcessed: suspend (originalName: String, translatedName: String) -> Unit = { _, _ -> }) {
        val translatedRows = ledgerDao.getRowsByStatus(LedgerStatus.TRANSLATED)
        for (row in translatedRows) {
            currentCoroutineContext().ensureActive()
            val currentName = contactsRepository.getCurrentName(row.lookupKey)
            if (currentName == null || currentName != row.translatedName) {
                ledgerDao.updateStatus(row.lookupKey, LedgerStatus.SKIPPED_EXTERNAL_EDIT)
            } else {
                contactsRepository.updateName(row.lookupKey, row.originalName)
                ledgerDao.updateStatus(row.lookupKey, LedgerStatus.ROLLED_BACK)
            }
            onContactProcessed(row.originalName, row.translatedName)
        }
    }
}
