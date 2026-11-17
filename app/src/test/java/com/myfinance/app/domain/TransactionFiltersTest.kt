package com.myfinance.app.domain

import com.myfinance.app.data.FinanceTransaction
import com.myfinance.app.data.TxType
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class TransactionFiltersTest {
    @Test fun filtersByTextAccountTypeAndMonth() {
        val coffee = FinanceTransaction("1", "Coffee shop", 500, LocalDate.of(2026, 11, 2).toEpochDay(), "hdfc", type = TxType.EXPENSE, category = "Food", notes = "team lunch")
        val rent = FinanceTransaction("2", "Rent", 100_000, LocalDate.of(2026, 11, 3).toEpochDay(), "hdfc", type = TxType.EXPENSE, category = "Housing")
        val salary = FinanceTransaction("3", "November pay", 200_000, LocalDate.of(2026, 11, 1).toEpochDay(), "sbi", type = TxType.INCOME, category = "Salary")

        val matches = TransactionFilters.filter(listOf(coffee, rent, salary), "TEAM", "hdfc", TxType.EXPENSE, YearMonth.of(2026, 11))

        assertEquals(listOf(coffee), matches)
    }
}
