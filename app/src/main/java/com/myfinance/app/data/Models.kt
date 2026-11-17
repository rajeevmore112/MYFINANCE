package com.myfinance.app.data

import androidx.room.*

@Entity(tableName = "accounts")
data class BankAccount(
    @PrimaryKey val id: String,
    val name: String,
    val purpose: String,
    val openingBalancePaise: Long,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "wallets", foreignKeys = [ForeignKey(entity = BankAccount::class, parentColumns = ["id"], childColumns = ["accountId"], onDelete = ForeignKey.CASCADE)], indices = [Index("accountId")])
data class VirtualWallet(
    @PrimaryKey val id: String,
    val accountId: String,
    val name: String,
    val openingBalancePaise: Long = 0
)

enum class TxType { INCOME, EXPENSE, TRANSFER_IN, TRANSFER_OUT, INVESTMENT_CONTRIBUTION, INVESTMENT_WITHDRAWAL, WALLET_FUND, WALLET_SPEND, WALLET_TRANSFER_IN, WALLET_TRANSFER_OUT, ADJUSTMENT_IN, ADJUSTMENT_OUT }

@Entity(tableName = "transactions", indices = [Index("accountId"), Index("walletId"), Index("dateEpochDay"), Index("linkId")])
data class FinanceTransaction(
    @PrimaryKey val id: String,
    val title: String,
    val amountPaise: Long,
    val dateEpochDay: Long,
    val accountId: String,
    val walletId: String? = null,
    val type: TxType,
    val category: String,
    val description: String? = null,
    val notes: String? = null,
    val linkId: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    init { require(amountPaise > 0) { "Transaction amount must be greater than zero paise" } }
}

@Entity(tableName = "categories", indices = [Index(value = ["name"], unique = true)])
data class TransactionCategory(@PrimaryKey val id: String, val name: String, val isIncome: Boolean = false)

@Entity(tableName = "budgets", indices = [Index(value = ["month", "category", "accountId"], unique = true)])
data class Budget(@PrimaryKey val id: String, val month: String, val category: String, val accountId: String?, val limitPaise: Long, val carryForward: Boolean = false, val alert75: Boolean = true, val alert90: Boolean = true, val alert100: Boolean = true)

@Entity(tableName = "recurring_rules")
data class RecurringRule(@PrimaryKey val id: String, val title: String, val amountPaise: Long, val accountId: String, val category: String, val frequency: String, val interval: Int = 1, val startEpochDay: Long, val endEpochDay: Long? = null, val nextDueEpochDay: Long, val requiresConfirmation: Boolean = true, val active: Boolean = true, val lastPostedOccurrence: String? = null)

@Entity(tableName = "balance_adjustments")
data class BalanceAdjustment(@PrimaryKey val id: String, val accountId: String, val expectedPaise: Long, val actualPaise: Long, val differencePaise: Long, val reason: String, val createdAt: Long = System.currentTimeMillis())

@Entity(tableName = "assets")
data class AdditionalAsset(@PrimaryKey val id: String, val name: String, val valuePaise: Long, val asOfEpochDay: Long)

@Entity(tableName = "liabilities")
data class Liability(@PrimaryKey val id: String, val name: String, val amountPaise: Long, val asOfEpochDay: Long)

@Entity(tableName = "backup_metadata")
data class BackupMetadata(@PrimaryKey val id: String, val createdAt: Long, val description: String, val checksum: String)
