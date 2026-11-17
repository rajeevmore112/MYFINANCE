package com.myfinance.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters

class FinanceConverters {
    @TypeConverter fun fromType(value: TxType): String = value.name
    @TypeConverter fun toType(value: String): TxType = TxType.valueOf(value)
}

@Database(entities = [BankAccount::class, VirtualWallet::class, FinanceTransaction::class, TransactionCategory::class, Budget::class, RecurringRule::class, BalanceAdjustment::class, AdditionalAsset::class, Liability::class, BackupMetadata::class], version = 1, exportSchema = true)
@TypeConverters(FinanceConverters::class)
abstract class FinanceDatabase : RoomDatabase() {
    abstract fun dao(): FinanceDao
    companion object {
        @Volatile private var INSTANCE: FinanceDatabase? = null
        fun get(context: Context): FinanceDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(context.applicationContext, FinanceDatabase::class.java, "myfinance.db").build().also { INSTANCE = it }
        }
    }
}
