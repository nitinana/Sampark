package com.sampark

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import com.sampark.data.contacts.AndroidContactsRepository
import com.sampark.data.contacts.ContactsRepository
import com.sampark.data.ledger.LedgerDao
import com.sampark.data.ledger.LedgerDatabase
import com.sampark.data.status.AppStatusRepository
import com.sampark.data.status.Direction
import com.sampark.domain.AppRouter
import com.sampark.domain.RunEngine
import com.sampark.domain.TransliterationEngine
import com.sampark.ui.RunViewModel

private val Context.appStatusDataStore by preferencesDataStore(name = "app_status")

class AppContainer(context: Context) {
    val contactsRepository: ContactsRepository = AndroidContactsRepository(context)

    val ledgerDao: LedgerDao = LedgerDatabase.getInstance(context).ledgerDao()

    val appStatusRepository: AppStatusRepository = AppStatusRepository(context.appStatusDataStore)

    val transliterationEngine: TransliterationEngine = TransliterationEngine()

    val runEngine: RunEngine = RunEngine(contactsRepository, ledgerDao, transliterationEngine)

    val appRouter: AppRouter = AppRouter(contactsRepository, appStatusRepository)

    fun runViewModelFactory(direction: Direction): ViewModelProvider.Factory =
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                return RunViewModel(direction, runEngine, appStatusRepository, ledgerDao) as T
            }
        }
}
