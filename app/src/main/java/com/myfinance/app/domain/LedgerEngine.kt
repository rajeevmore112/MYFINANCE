package com.myfinance.app.domain

import com.myfinance.app.data.FinanceTransaction
import com.myfinance.app.data.TxType

/** All values are integer paise. No floating-point arithmetic is used. */
object LedgerEngine {
    fun bankDelta(tx: FinanceTransaction): Long = when (tx.type) {
        TxType.INCOME, TxType.TRANSFER_IN, TxType.INVESTMENT_WITHDRAWAL, TxType.ADJUSTMENT_IN -> tx.amountPaise
        TxType.EXPENSE, TxType.TRANSFER_OUT, TxType.INVESTMENT_CONTRIBUTION, TxType.WALLET_SPEND, TxType.ADJUSTMENT_OUT -> -tx.amountPaise
        TxType.WALLET_FUND, TxType.WALLET_TRANSFER_IN, TxType.WALLET_TRANSFER_OUT -> 0L
    }

    fun walletDelta(tx: FinanceTransaction): Long = when (tx.type) {
        TxType.WALLET_FUND, TxType.WALLET_TRANSFER_IN -> tx.amountPaise
        TxType.WALLET_SPEND, TxType.WALLET_TRANSFER_OUT -> -tx.amountPaise
        else -> 0L
    }

    fun bankBalance(openingPaise: Long, transactions: Iterable<FinanceTransaction>): Long =
        transactions.fold(openingPaise) { total, tx -> Math.addExact(total, bankDelta(tx)) }

    fun walletBalance(openingPaise: Long, transactions: Iterable<FinanceTransaction>): Long =
        transactions.fold(openingPaise) { total, tx -> Math.addExact(total, walletDelta(tx)) }

    /** Cash held across physical bank accounts. Virtual wallet allocations are never added here. */
    fun consolidatedBankBalance(accounts: Iterable<Pair<Long, Iterable<FinanceTransaction>>>): Long =
        accounts.fold(0L) { total, (opening, transactions) ->
            Math.addExact(total, bankBalance(opening, transactions))
        }

    /** Unassigned physical cash in the wallet's parent account. */
    fun unallocatedBalance(
        accountOpeningPaise: Long,
        accountTransactions: Iterable<FinanceTransaction>,
        walletOpeningsAndTransactions: Iterable<Pair<Long, Iterable<FinanceTransaction>>>
    ): Long {
        val physical = bankBalance(accountOpeningPaise, accountTransactions)
        val allocated = walletOpeningsAndTransactions.fold(0L) { total, (opening, transactions) ->
            Math.addExact(total, walletBalance(opening, transactions))
        }
        return Math.subtractExact(physical, allocated)
    }

    fun walletBalanceAfter(
        openingPaise: Long,
        existing: Iterable<FinanceTransaction>,
        candidate: FinanceTransaction
    ): Long = walletBalance(openingPaise, existing + candidate)

    fun isConsumptionExpense(type: TxType): Boolean = type == TxType.EXPENSE || type == TxType.WALLET_SPEND
    fun isIncome(type: TxType): Boolean = type == TxType.INCOME
    fun isInvestment(type: TxType): Boolean = type == TxType.INVESTMENT_CONTRIBUTION || type == TxType.INVESTMENT_WITHDRAWAL
}
