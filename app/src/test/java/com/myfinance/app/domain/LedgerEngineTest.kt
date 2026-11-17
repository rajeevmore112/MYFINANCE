package com.myfinance.app.domain

import com.myfinance.app.data.FinanceTransaction
import com.myfinance.app.data.TxType
import org.junit.Assert.assertEquals
import org.junit.Test

class LedgerEngineTest {
    private fun tx(id: String, account: String, amount: Long, type: TxType, wallet: String? = null, link: String? = null) =
        FinanceTransaction(id, id, amount, 1, account, wallet, type, "test", linkId = link)

    @Test fun internalTransferDoesNotChangeConsolidatedCash() {
        val hdfc = listOf(tx("out", "hdfc", 500_000, TxType.TRANSFER_OUT))
        val sbi = listOf(tx("in", "sbi", 500_000, TxType.TRANSFER_IN))
        assertEquals(4_500_000L, LedgerEngine.bankBalance(5_000_000, hdfc))
        assertEquals(2_500_000L, LedgerEngine.bankBalance(2_000_000, sbi))
        assertEquals(7_000_000L, LedgerEngine.bankBalance(7_000_000, emptyList()))
    }

    @Test fun walletAllocationDoesNotChangePhysicalBankBalance() {
        val allocation = tx("fund", "kotak", 2_000_000, TxType.WALLET_FUND, "emergency")
        val spend = tx("spend", "kotak", 200_000, TxType.WALLET_SPEND, "emergency")
        assertEquals(2_800_000L, LedgerEngine.bankBalance(3_000_000, listOf(allocation, spend)))
        assertEquals(1_800_000L, LedgerEngine.walletBalance(0, listOf(allocation, spend)))
    }

    @Test fun walletToWalletTransferLeavesBankUnchanged() {
        val out = tx("out", "kotak", 500_000, TxType.WALLET_TRANSFER_OUT, "allowance")
        val into = tx("in", "kotak", 500_000, TxType.WALLET_TRANSFER_IN, "emergency")
        assertEquals(3_000_000L, LedgerEngine.bankBalance(3_000_000, listOf(out, into)))
        assertEquals(-500_000L, LedgerEngine.walletBalance(0, listOf(out)))
        assertEquals(500_000L, LedgerEngine.walletBalance(0, listOf(into)))
    }

    @Test fun walletAllocationsAreNotDoubleCountedAndUnallocatedCashIsCorrect() {
        val emergencyFund = tx("emergency", "kotak", 2_000_000, TxType.WALLET_FUND, "emergency")
        val allowanceFund = tx("allowance", "kotak", 1_000_000, TxType.WALLET_FUND, "allowance")
        val allowanceSpend = tx("spend", "kotak", 200_000, TxType.WALLET_SPEND, "allowance")
        val physicalTransactions = listOf(emergencyFund, allowanceFund, allowanceSpend)
        val allocation = LedgerEngine.unallocatedBalance(
            3_000_000,
            physicalTransactions,
            listOf(0L to listOf(emergencyFund), 0L to listOf(allowanceFund, allowanceSpend))
        )
        assertEquals(0L, allocation)
        assertEquals(2_800_000L, LedgerEngine.bankBalance(3_000_000, physicalTransactions))
        assertEquals(2_800_000L, LedgerEngine.consolidatedBankBalance(listOf(3_000_000L to physicalTransactions, 0L to emptyList())))
    }

    @Test fun walletBalanceAfterIncludesOpeningAllocation() {
        val spend = tx("spend", "kotak", 200_000, TxType.WALLET_SPEND, "allowance")
        assertEquals(800_000L, LedgerEngine.walletBalanceAfter(1_000_000, emptyList(), spend))
        assertEquals(-200_000L, LedgerEngine.walletBalanceAfter(0, emptyList(), spend))
    }

    @Test fun investmentContributionIsNotConsumptionExpense() {
        assertEquals(-100_000L, LedgerEngine.bankDelta(tx("invest", "sbi", 100_000, TxType.INVESTMENT_CONTRIBUTION)))
        assertEquals(false, LedgerEngine.isConsumptionExpense(TxType.INVESTMENT_CONTRIBUTION))
    }

    @Test fun investmentWithdrawalIncreasesCashWithoutCountingAsIncome() {
        val withdrawal = tx("withdrawal", "sbi", 250_000, TxType.INVESTMENT_WITHDRAWAL)
        assertEquals(250_000L, LedgerEngine.bankDelta(withdrawal))
        assertEquals(false, LedgerEngine.isIncome(withdrawal.type))
        assertEquals(false, LedgerEngine.isConsumptionExpense(withdrawal.type))
    }
}
