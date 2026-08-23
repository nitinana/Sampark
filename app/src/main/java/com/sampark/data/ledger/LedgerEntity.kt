package com.sampark.data.ledger

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "ledger")
data class LedgerEntity(
    @PrimaryKey val lookupKey: String,
    val originalName: String,
    val translatedName: String,
    val status: LedgerStatus
)
