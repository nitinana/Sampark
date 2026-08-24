package com.sampark.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sampark.data.ledger.LedgerDao
import com.sampark.data.ledger.LedgerStatus
import com.sampark.data.status.AppStatusRepository
import com.sampark.data.status.Direction
import com.sampark.data.status.Phase
import com.sampark.domain.RunEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class RunUiState(
    val done: Int = 0,
    val total: Int = 0,
    val isPaused: Boolean = false,
    val lastProcessed: Pair<String, String>? = null
)

class RunViewModel(
    private val direction: Direction,
    private val runEngine: RunEngine,
    private val appStatusRepository: AppStatusRepository,
    private val ledgerDao: LedgerDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(RunUiState())
    val uiState: StateFlow<RunUiState> = _uiState.asStateFlow()

    private var runJob: Job? = null

    fun start() {
        // Guard against re-invocation while a run is already in flight. start() is
        // called from a LaunchedEffect that re-runs whenever the activity is
        // recreated (rotation, font-scale change, dark-mode toggle), and without
        // this guard a second launch would orphan the first still-running loop:
        // pause() would then only cancel the second one while the first kept
        // silently renaming contacts behind a "paused" UI.
        if (runJob?.isActive == true) return
        runJob = viewModelScope.launch {
            refreshCounts()
            val onProcessed: suspend (String, String) -> Unit = { original, translated ->
                _uiState.value = _uiState.value.copy(lastProcessed = original to translated)
                refreshCounts()
            }
            try {
                if (direction == Direction.TRANSLATE) {
                    runEngine.runTranslate(onProcessed)
                } else {
                    runEngine.runRollback(onProcessed)
                }
            } catch (cancellation: CancellationException) {
                // pause()/cancel() cancel this job — must propagate, never swallow.
                throw cancellation
            } catch (error: Exception) {
                // A SecurityException/RemoteException/DB failure mid-run must not
                // crash the app halfway through rewriting someone's contacts.
                // Leave phase as RUNNING (so the run is resumable) and show the
                // existing, reassuring paused screen.
                _uiState.value = _uiState.value.copy(isPaused = true)
                return@launch
            }
            appStatusRepository.setPhase(Phase.COMPLETED)
            _uiState.value = _uiState.value.copy(isPaused = false)
        }
    }

    /**
     * Cold-start re-entry into a run that was left RUNNING by a previous process:
     * show the paused screen instead of silently resuming. The user must tap
     * Resume — an explicit, fresh tap — before any contact is touched again.
     *
     * No-op if this ViewModel has already started a run (it survives configuration
     * changes, so this can be re-invoked while a resumed run is in flight).
     */
    fun showPausedWithoutStarting() {
        if (runJob != null) return
        _uiState.value = _uiState.value.copy(isPaused = true)
        viewModelScope.launch { refreshCounts() }
    }

    fun pause() {
        runJob?.cancel()
        _uiState.value = _uiState.value.copy(isPaused = true)
    }

    fun resume() {
        _uiState.value = _uiState.value.copy(isPaused = false)
        start()
    }

    fun cancel() {
        check(direction == Direction.TRANSLATE) { "Rollback has no Cancel option" }
        runJob?.cancel()
        viewModelScope.launch {
            appStatusRepository.setPhase(Phase.COMPLETED)
        }
    }

    private suspend fun refreshCounts() {
        val total = if (direction == Direction.TRANSLATE) {
            ledgerDao.countAll().first()
        } else {
            ledgerDao.countByStatuses(
                listOf(LedgerStatus.TRANSLATED, LedgerStatus.ROLLED_BACK, LedgerStatus.SKIPPED_EXTERNAL_EDIT)
            ).first()
        }
        val done = if (direction == Direction.TRANSLATE) {
            ledgerDao.countByStatus(LedgerStatus.TRANSLATED).first()
        } else {
            ledgerDao.countByStatus(LedgerStatus.ROLLED_BACK).first()
        }
        _uiState.value = _uiState.value.copy(done = done, total = total)
    }
}
