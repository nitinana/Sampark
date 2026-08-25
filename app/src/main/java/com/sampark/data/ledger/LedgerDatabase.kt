package com.sampark.data.ledger

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters

class LedgerStatusConverters {
    @TypeConverter
    fun fromStatus(status: LedgerStatus): String = status.name

    @TypeConverter
    fun toStatus(value: String): LedgerStatus = LedgerStatus.valueOf(value)
}

@Database(entities = [LedgerEntity::class], version = 1, exportSchema = false)
@TypeConverters(LedgerStatusConverters::class)
abstract class LedgerDatabase : RoomDatabase() {
    abstract fun ledgerDao(): LedgerDao

    companion object {
        @Volatile private var instance: LedgerDatabase? = null

        fun getInstance(context: Context): LedgerDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    LedgerDatabase::class.java,
                    "ledger.db"
                ).build().also { instance = it }
            }
    }
}
