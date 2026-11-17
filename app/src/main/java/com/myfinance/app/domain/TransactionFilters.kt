package com.myfinance.app.domain

import com.myfinance.app.data.FinanceTransaction
import com.myfinance.app.data.TxType
import java.time.LocalDate
import java.time.YearMonth

object TransactionFilters {
    fun filter(
        transactions: List<FinanceTransaction>,
        query: String = "",
        accountId: String? = null,
        type: TxType? = null,
        month: YearMonth? = null
    ): List<FinanceTransaction> {
        val normalized = query.trim().lowercase()
        return transactions.filter { tx ->
            (normalized.isEmpty() || listOf(tx.title, tx.category, tx.description.orEmpty(), tx.notes.orEmpty()).any { normalized in it.lowercase() }) &&
                (accountId == null || tx.accountId == accountId) &&
                (type == null || tx.type == type) &&
                (month == null || YearMonth.from(LocalDate.ofEpochDay(tx.dateEpochDay)) == month)
        }
    }
}
