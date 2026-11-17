package com.myfinance.app.data

import androidx.room.withTransaction
import com.myfinance.app.domain.LedgerEngine
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import java.time.YearMonth
import java.io.InputStream
import java.io.OutputStream

class FinanceRepository(private val db: FinanceDatabase, context: android.content.Context) {
    private val dao = db.dao()
    private val backupManager = FinanceBackupManager(context, db)
    val accounts = dao.accounts()
    val wallets = dao.wallets()
    val transactions = dao.transactions()
    val budgets = dao.allBudgets()
    val recurringRules = dao.allRecurring()

    suspend fun initialiseDefaults() = db.withTransaction {
        val defaults = listOf(
            BankAccount("hdfc", "HDFC Bank", "Salary account", 0),
            BankAccount("sbi", "SBI", "Investment account", 0),
            BankAccount("kotak", "Kotak 811", "Savings and allowances", 0)
        )
        defaults.forEach { if (dao.account(it.id) == null) dao.insertAccount(it) }
        if (dao.wallet("emergency") == null) dao.insertWallet(VirtualWallet("emergency", "kotak", "Emergency Fund"))
        if (dao.wallet("allowance") == null) dao.insertWallet(VirtualWallet("allowance", "kotak", "Shift Allowance"))
    }

    suspend fun setOpeningBalance(accountId: String, amountPaise: Long) = db.withTransaction {
        require(amountPaise >= 0)
        val account = requireNotNull(dao.account(accountId))
        // Opening balance is intentionally immutable after ledger activity; use an adjustment instead.
        require(dao.accountTransactions(accountId).isEmpty()) { "Opening balance is locked after transactions. Use reconciliation." }
        dao.updateAccount(account.copy(openingBalancePaise = amountPaise))
    }

    suspend fun saveBudget(month: String, category: String, accountId: String?, limitPaise: Long, carryForward: Boolean, alert75: Boolean, alert90: Boolean, alert100: Boolean) = db.withTransaction {
        require(YearMonth.parse(month).toString() == month) { "Month must use YYYY-MM format" }
        require(category.isNotBlank() && limitPaise > 0L) { "Choose a category and enter a positive budget" }
        if (accountId != null) require(dao.account(accountId) != null) { "Unknown bank account" }
        val existing = dao.budget(month, category, accountId)
        dao.upsertBudget(Budget(existing?.id ?: UUID.randomUUID().toString(), month, category, accountId, limitPaise, carryForward, alert75, alert90, alert100))
    }

    suspend fun add(tx: FinanceTransaction) = db.withTransaction {
        validateTransaction(tx, null)
        dao.insertTransaction(tx)
    }

    suspend fun previewTransactionsCsv(csv: String): CsvImportPreview {
        val rows = try { FinanceCsv.parseTransactions(csv) } catch (e: Exception) {
            return CsvImportPreview(emptyList(), emptySet(), listOf(e.message ?: "Could not read this MYFINANCE CSV"))
        }
        val existing = dao.transactionsSnapshot().map(FinanceCsv::signature).toSet()
        val duplicateIds = rows.filter { FinanceCsv.signature(it) in existing }.map { it.id }.toSet()
        val errors = mutableListOf<String>()
        if (rows.map(FinanceCsv::signature).toSet().size != rows.size) errors += "The selected CSV contains repeated transaction rows. Remove duplicates before importing."
        val accounts = rows.map { it.accountId }.distinct().associateWith { dao.account(it) }
        val wallets = rows.mapNotNull { it.walletId }.distinct().associateWith { dao.wallet(it) }
        rows.forEach { tx ->
            if (accounts[tx.accountId] == null) errors += "Unknown account ID '${tx.accountId}' in '${tx.title}'."
            if (tx.walletId != null) {
                val wallet = wallets[tx.walletId]
                if (wallet == null) errors += "Unknown wallet ID '${tx.walletId}' in '${tx.title}'."
                else if (wallet.accountId != tx.accountId) errors += "Wallet '${wallet.name}' does not belong to account '${tx.accountId}'."
            }
        }
        rows.filter { it.linkId != null }.groupBy { it.linkId }.forEach { (_, pair) ->
            val duplicateSides = pair.count { it.id in duplicateIds }
            if (duplicateSides == 1) errors += "Only one side of a linked transfer already exists. Resolve that transfer before importing."
            if (pair.any { it.walletId != null }) {
                val out = pair.firstOrNull { it.type == TxType.WALLET_TRANSFER_OUT }
                val sourceWallet = out?.walletId?.let { wallets[it] }
                if (sourceWallet == null) errors += "A wallet transfer source is missing or invalid."
                else {
                    val history = dao.walletTransactions(sourceWallet.id)
                    if (com.myfinance.app.domain.LedgerEngine.walletBalance(sourceWallet.openingBalancePaise, history) < out.amountPaise) {
                        errors += "The imported wallet transfer would exceed the current source-wallet balance."
                    }
                }
            }
        }
        return CsvImportPreview(rows, duplicateIds, errors.distinct())
    }

    suspend fun importTransactionsCsv(preview: CsvImportPreview): Pair<Int, Int> = db.withTransaction {
        require(preview.errors.isEmpty()) { preview.errors.firstOrNull() ?: "Fix the CSV errors before importing." }
        val existingRows = dao.transactionsSnapshot()
        val existingSignatures = existingRows.map(FinanceCsv::signature).toSet()
        val newRows = preview.rows.filter { FinanceCsv.signature(it) !in existingSignatures }
        val linkedTypes = setOf(TxType.TRANSFER_IN, TxType.TRANSFER_OUT, TxType.WALLET_TRANSFER_IN, TxType.WALLET_TRANSFER_OUT)
        val newLinkedCounts = newRows.filter { it.type in linkedTypes }.groupingBy { it.linkId }.eachCount()
        require(newLinkedCounts.values.all { it == 2 }) { "Both sides of each new linked transfer must be present. Refresh the preview and try again." }
        val now = System.currentTimeMillis()
        val mappedLinks = newRows.mapNotNull { it.linkId }.distinct().associateWith { UUID.randomUUID().toString() }
        newRows.sortedWith(compareBy<FinanceTransaction> { it.dateEpochDay }.thenBy { it.accountId }).forEach { tx ->
            when (tx.type) {
                TxType.TRANSFER_IN, TxType.TRANSFER_OUT, TxType.WALLET_TRANSFER_IN, TxType.WALLET_TRANSFER_OUT -> {
                    require(dao.account(tx.accountId) != null) { "Unknown account '${tx.accountId}'." }
                    if (tx.type == TxType.WALLET_TRANSFER_IN || tx.type == TxType.WALLET_TRANSFER_OUT) {
                        val wallet = requireNotNull(tx.walletId?.let { dao.wallet(it) }) { "Unknown wallet for '${tx.title}'." }
                        require(wallet.accountId == tx.accountId) { "Wallet does not belong to account '${tx.accountId}'." }
                        if (tx.type == TxType.WALLET_TRANSFER_OUT) {
                            val balance = com.myfinance.app.domain.LedgerEngine.walletBalance(wallet.openingBalancePaise, dao.walletTransactions(wallet.id))
                            require(balance >= tx.amountPaise) { "Not enough balance in the source wallet for '${tx.title}'." }
                        }
                    } else require(tx.walletId == null) { "Bank transfers cannot be assigned to a wallet." }
                }
                else -> validateTransaction(tx, null)
            }
            dao.insertTransaction(tx.copy(linkId = tx.linkId?.let(mappedLinks::get), createdAt = now, updatedAt = now))
        }
        newRows.size to (preview.rows.size - newRows.size)
    }

    suspend fun update(tx: FinanceTransaction) = db.withTransaction {
        val old = requireNotNull(dao.transaction(tx.id)) { "Transaction no longer exists" }
        require(tx.title.isNotBlank() && tx.category.isNotBlank()) { "Transaction title and category are required" }
        require(old.type == tx.type && old.accountId == tx.accountId && old.walletId == tx.walletId && old.linkId == tx.linkId) {
            "Transaction type, account, wallet, and transfer link cannot be changed"
        }
        if (old.linkId != null) require(tx.category == old.category) { "Transfer category cannot be changed" }
        val now = System.currentTimeMillis()
        if (old.linkId == null) {
            validateTransaction(tx, old.id)
            dao.upsertTransaction(tx.copy(updatedAt = now))
        } else {
            val linked = dao.linkedTransactions(old.linkId)
            require(linked.size == 2) { "Linked transfer is incomplete and cannot be edited" }
            val counterpart = linked.single { it.id != old.id }
            val opposite = (old.type == TxType.TRANSFER_OUT && counterpart.type == TxType.TRANSFER_IN) ||
                (old.type == TxType.TRANSFER_IN && counterpart.type == TxType.TRANSFER_OUT) ||
                (old.type == TxType.WALLET_TRANSFER_OUT && counterpart.type == TxType.WALLET_TRANSFER_IN) ||
                (old.type == TxType.WALLET_TRANSFER_IN && counterpart.type == TxType.WALLET_TRANSFER_OUT)
            require(opposite && counterpart.amountPaise == old.amountPaise) { "Linked transfer entries are not balanced" }
            val source = if (old.type == TxType.WALLET_TRANSFER_OUT || old.type == TxType.WALLET_TRANSFER_IN) {
                if (old.type == TxType.WALLET_TRANSFER_OUT) old else counterpart
            } else null
            if (source != null) {
                val sourceWalletId = requireNotNull(source.walletId) { "Wallet transfer is missing its source wallet" }
                val wallet = requireNotNull(dao.wallet(sourceWalletId))
                val historyWithoutOldTransfer = dao.walletTransactions(wallet.id).filterNot { it.linkId == old.linkId }
                val available = LedgerEngine.walletBalance(wallet.openingBalancePaise, historyWithoutOldTransfer)
                require(available >= tx.amountPaise) { "Not enough balance in the source wallet" }
            }
            dao.upsertTransaction(tx.copy(updatedAt = now))
            dao.upsertTransaction(counterpart.copy(title = tx.title, amountPaise = tx.amountPaise, dateEpochDay = tx.dateEpochDay, updatedAt = now))
        }
    }

    private suspend fun validateTransaction(tx: FinanceTransaction, replacingId: String?) {
        require(tx.title.isNotBlank() && tx.category.isNotBlank()) { "Transaction title and category are required" }
        require(tx.type != TxType.TRANSFER_IN && tx.type != TxType.TRANSFER_OUT &&
            tx.type != TxType.WALLET_TRANSFER_IN && tx.type != TxType.WALLET_TRANSFER_OUT) {
            "Transfers must be created through the linked transfer operation"
        }
        require(dao.account(tx.accountId) != null) { "Unknown bank account" }
        val walletTypes = setOf(TxType.WALLET_FUND, TxType.WALLET_SPEND)
        require((tx.type in walletTypes) == (tx.walletId != null)) {
            "Wallet funding and spending require a wallet; other transactions cannot use one"
        }
        if (tx.walletId != null) {
            val wallet = requireNotNull(dao.wallet(tx.walletId)) { "Unknown wallet" }
            require(wallet.accountId == tx.accountId) { "Wallet does not belong to selected account" }
            val history = dao.walletTransactions(wallet.id).filterNot { it.id == replacingId }
            val balanceAfter = LedgerEngine.walletBalanceAfter(wallet.openingBalancePaise, history, tx)
            require(balanceAfter >= 0L) { "Transaction would make the wallet balance negative" }
            if (tx.type == TxType.WALLET_FUND) {
                val account = requireNotNull(dao.account(tx.accountId))
                val accountHistory = dao.accountTransactions(tx.accountId).filterNot { it.id == replacingId }
                val wallets = dao.walletsSnapshotForAccount(tx.accountId)
                val unallocated = LedgerEngine.unallocatedBalance(
                    account.openingBalancePaise,
                    accountHistory,
                    wallets.map { walletRow ->
                        walletRow.openingBalancePaise to dao.walletTransactions(walletRow.id).filterNot { it.id == replacingId }
                    }
                )
                require(unallocated >= tx.amountPaise) { "Not enough unallocated account balance to fund this wallet" }
            }
        }
    }

    suspend fun transfer(from: String, to: String, amountPaise: Long, dateEpochDay: Long, title: String) = db.withTransaction {
        require(from != to && amountPaise > 0)
        require(dao.account(from) != null && dao.account(to) != null)
        val id = UUID.randomUUID().toString()
        val common = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        dao.insertTransaction(FinanceTransaction("$id-out", title, amountPaise, dateEpochDay, from, type = TxType.TRANSFER_OUT, category = "Transfer", linkId = common, createdAt = now, updatedAt = now))
        dao.insertTransaction(FinanceTransaction("$id-in", title, amountPaise, dateEpochDay, to, type = TxType.TRANSFER_IN, category = "Transfer", linkId = common, createdAt = now, updatedAt = now))
    }

    suspend fun walletTransfer(fromWallet: String, toWallet: String, amountPaise: Long, dateEpochDay: Long, title: String) = db.withTransaction {
        require(fromWallet != toWallet && amountPaise > 0)
        val from = requireNotNull(dao.wallet(fromWallet)); val to = requireNotNull(dao.wallet(toWallet))
        require(from.accountId == to.accountId) { "Wallet transfers must stay within the same bank account" }
        val sourceBalance = LedgerEngine.walletBalance(from.openingBalancePaise, dao.walletTransactions(from.id))
        require(sourceBalance >= amountPaise) { "Not enough balance in the source wallet" }
        val link = UUID.randomUUID().toString(); val id = UUID.randomUUID().toString(); val now = System.currentTimeMillis()
        dao.insertTransaction(FinanceTransaction("$id-out", title, amountPaise, dateEpochDay, from.accountId, fromWallet, TxType.WALLET_TRANSFER_OUT, "Wallet transfer", linkId = link, createdAt = now, updatedAt = now))
        dao.insertTransaction(FinanceTransaction("$id-in", title, amountPaise, dateEpochDay, to.accountId, toWallet, TxType.WALLET_TRANSFER_IN, "Wallet transfer", linkId = link, createdAt = now, updatedAt = now))
    }

    suspend fun reconcile(accountId: String, actualPaise: Long, reason: String, dateEpochDay: Long) = db.withTransaction {
        require(actualPaise >= 0 && reason.isNotBlank())
        val account = requireNotNull(dao.account(accountId))
        val txs = dao.accountTransactions(accountId)
        val expected = LedgerEngine.bankBalance(account.openingBalancePaise, txs)
        val difference = Math.subtractExact(actualPaise, expected)
        if (difference != 0L) {
            val id = UUID.randomUUID().toString(); val now = System.currentTimeMillis()
            val adjustmentAmount = if (difference < 0) Math.negateExact(difference) else difference
            dao.insertTransaction(FinanceTransaction(id, "Balance adjustment", adjustmentAmount, dateEpochDay, accountId, type = if (difference > 0) TxType.ADJUSTMENT_IN else TxType.ADJUSTMENT_OUT, category = "Adjustment", description = reason, createdAt = now, updatedAt = now))
            dao.insertAdjustment(BalanceAdjustment(UUID.randomUUID().toString(), accountId, expected, actualPaise, difference, reason))
        }
    }

    suspend fun delete(id: String) = db.withTransaction {
        val tx = dao.transaction(id) ?: return@withTransaction
        if (tx.linkId != null) dao.deleteLinkedTransactions(tx.linkId) else dao.deleteTransaction(id)
    }

    suspend fun balances(accountId: String): Pair<Long, Long> {
        val account = requireNotNull(dao.account(accountId))
        val bank = dao.accountTransactions(accountId)
        val walletTotal = dao.walletsSnapshotForAccount(accountId).sumOf { wallet ->
            LedgerEngine.walletBalance(wallet.openingBalancePaise, dao.walletTransactions(wallet.id))
        }
        return LedgerEngine.bankBalance(account.openingBalancePaise, bank) to walletTotal
    }

    suspend fun exportTransactionsCsv(): String = FinanceCsv.transactions(dao.transactionsSnapshot())

    suspend fun writeBackup(output: OutputStream) = backupManager.writeBackup(output)

    suspend fun restoreBackup(input: InputStream): String = backupManager.restore(input)
}
