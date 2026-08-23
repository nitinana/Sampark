package com.sampark.data.ledger

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface LedgerDao {
    @Insert
    suspend fun insertAll(rows: List<LedgerEntity>)

    @Query("UPDATE ledger SET status = :status WHERE lookupKey = :lookupKey")
    suspend fun updateStatus(lookupKey: String, status: LedgerStatus)

    @Query("SELECT COUNT(*) FROM ledger")
    fun countAll(): Flow<Int>

    @Query("SELECT COUNT(*) FROM ledger WHERE status = :status")
    fun countByStatus(status: LedgerStatus): Flow<Int>

    @Query("SELECT COUNT(*) FROM ledger WHERE status IN (:statuses)")
    fun countByStatuses(statuses: List<LedgerStatus>): Flow<Int>

    @Query("SELECT * FROM ledger WHERE status = :status")
    suspend fun getRowsByStatus(status: LedgerStatus): List<LedgerEntity>

    @Query("DELETE FROM ledger")
    suspend fun clearAll()
}
