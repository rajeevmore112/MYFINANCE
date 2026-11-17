package com.myfinance.app.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface FinanceDao {
    @Query("SELECT * FROM accounts ORDER BY name") fun accounts(): Flow<List<BankAccount>>
    @Query("SELECT * FROM wallets ORDER BY name") fun wallets(): Flow<List<VirtualWallet>>
    @Query("SELECT * FROM wallets WHERE accountId = :accountId ORDER BY name") suspend fun walletsSnapshotForAccount(accountId: String): List<VirtualWallet>
    @Query("SELECT * FROM transactions ORDER BY dateEpochDay DESC, createdAt DESC") fun transactions(): Flow<List<FinanceTransaction>>
    @Query("SELECT * FROM transactions ORDER BY dateEpochDay DESC, createdAt DESC") suspend fun transactionsSnapshot(): List<FinanceTransaction>
    @Query("SELECT * FROM transactions WHERE dateEpochDay BETWEEN :from AND :to ORDER BY dateEpochDay DESC") fun transactionsBetween(from: Long, to: Long): Flow<List<FinanceTransaction>>
    @Query("SELECT * FROM budgets WHERE month = :month") fun budgetsForMonth(month: String): Flow<List<Budget>>
    @Query("SELECT * FROM budgets WHERE month = :month AND category = :category AND (accountId = :accountId OR (accountId IS NULL AND :accountId IS NULL)) LIMIT 1") suspend fun budget(month: String, category: String, accountId: String?): Budget?
    @Query("SELECT * FROM recurring_rules WHERE active = 1") fun activeRecurring(): Flow<List<RecurringRule>>
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertAccount(account: BankAccount)
    @Update suspend fun updateAccount(account: BankAccount)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertWallet(wallet: VirtualWallet)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertTransaction(tx: FinanceTransaction)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertTransaction(tx: FinanceTransaction)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertBudget(budget: Budget)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertRecurring(rule: RecurringRule)
    @Insert suspend fun insertAdjustment(adjustment: BalanceAdjustment)
    @Query("SELECT * FROM accounts WHERE id = :id") suspend fun account(id: String): BankAccount?
    @Query("SELECT * FROM wallets WHERE id = :id") suspend fun wallet(id: String): VirtualWallet?
    @Query("SELECT * FROM transactions WHERE id = :id") suspend fun transaction(id: String): FinanceTransaction?
    @Query("SELECT * FROM transactions WHERE linkId = :linkId") suspend fun linkedTransactions(linkId: String): List<FinanceTransaction>
    @Query("SELECT * FROM transactions WHERE accountId = :accountId") suspend fun accountTransactions(accountId: String): List<FinanceTransaction>
    @Query("SELECT * FROM transactions WHERE walletId = :walletId") suspend fun walletTransactions(walletId: String): List<FinanceTransaction>
    @Query("DELETE FROM transactions WHERE id = :id") suspend fun deleteTransaction(id: String)
    @Query("DELETE FROM transactions WHERE linkId = :linkId") suspend fun deleteLinkedTransactions(linkId: String)
    @Query("SELECT * FROM budgets") fun allBudgets(): Flow<List<Budget>>
    @Query("SELECT * FROM recurring_rules") fun allRecurring(): Flow<List<RecurringRule>>
}
