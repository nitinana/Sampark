package com.sampark

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import com.sampark.data.contacts.AndroidContactsRepository
import com.sampark.data.contacts.ContactsRepository
import com.sampark.data.ledger.LedgerDao
import com.sampark.data.ledger.LedgerDatabase
import com.sampark.data.status.AppStatusRepository
import com.sampark.domain.AppRouter
import com.sampark.domain.RunEngine
import com.sampark.domain.TransliterationEngine

private val Context.appStatusDataStore by preferencesDataStore(name = "app_status")

class AppContainer(context: Context) {
    val contactsRepository: ContactsRepository = AndroidContactsRepository(context)

    val ledgerDao: LedgerDao = LedgerDatabase.getInstance(context).ledgerDao()

    val appStatusRepository: AppStatusRepository = AppStatusRepository(context.appStatusDataStore)

    val transliterationEngine: TransliterationEngine = TransliterationEngine()

    val runEngine: RunEngine = RunEngine(contactsRepository, ledgerDao, transliterationEngine)

    val appRouter: AppRouter = AppRouter(contactsRepository, appStatusRepository)
}
