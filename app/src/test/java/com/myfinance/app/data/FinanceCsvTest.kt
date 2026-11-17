package com.myfinance.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.LocalDate

class FinanceCsvTest {
    @Test fun exportsAmountsAndEscapesCsvCells() {
        val row = FinanceTransaction(
            id = "tx", title = "Cafe, \"North\"", amountPaise = 12345,
            dateEpochDay = LocalDate.of(2026, 11, 10).toEpochDay(), accountId = "hdfc",
            type = TxType.EXPENSE, category = "Food"
        )

        val lines = FinanceCsv.transactions(listOf(row)).lines()
        assertEquals("date,account_id,type,title,amount_inr,category,wallet_id,description,notes,transfer_link_id", lines[0])
        assertEquals("2026-11-10,hdfc,EXPENSE,\"Cafe, \"\"North\"\"\",123.45,Food,,,,", lines[1])
    }

    @Test fun parsesExportAndPreservesQuotedFieldsAndPaise() {
        val csv = "date,account_id,type,title,amount_inr,category,wallet_id,description,notes,transfer_link_id\r\n" +
            "2026-11-10,hdfc,EXPENSE,\"Cafe, \"\"North\"\"\",123.45,Food,,\"UPI, ref 4\",,\r\n"
        val parsed = FinanceCsv.parseTransactions(csv).single()
        assertEquals("Cafe, \"North\"", parsed.title)
        assertEquals(12_345L, parsed.amountPaise)
        assertEquals("UPI, ref 4", parsed.description)
    }

    @Test fun rejectsIncompleteLinkedTransferAndExcessPrecision() {
        val header = "date,account_id,type,title,amount_inr,category,wallet_id,description,notes,transfer_link_id\n"
        val singleTransfer = header + "2026-11-10,hdfc,TRANSFER_OUT,Transfer,10.00,Transfer,,, ,link-1\n"
        assertThrows(IllegalArgumentException::class.java) { FinanceCsv.parseTransactions(singleTransfer) }
        val invalidAmount = header + "2026-11-10,hdfc,EXPENSE,Coffee,1.001,Food,,,,\n"
        assertThrows(IllegalArgumentException::class.java) { FinanceCsv.parseTransactions(invalidAmount) }
    }

    @Test fun parsesOnlyBalancedBankTransferPairs() {
        val csv = "date,account_id,type,title,amount_inr,category,wallet_id,description,notes,transfer_link_id\n" +
            "2026-11-10,hdfc,TRANSFER_OUT,Move,50.00,Transfer,,,,pair-1\n" +
            "2026-11-10,sbi,TRANSFER_IN,Move,50.00,Transfer,,,,pair-1\n"
        val parsed = FinanceCsv.parseTransactions(csv)
        assertEquals(2, parsed.size)
        assertEquals("hdfc", parsed.first().accountId)
        assertEquals("sbi", parsed.last().accountId)
    }

    @Test fun importSignatureIsStableAcrossGeneratedIdsAndTransferLinkRemapping() {
        val first = FinanceTransaction("first", "Transfer", 5_000, 20_700, "hdfc", type = TxType.TRANSFER_OUT, category = "Transfer", linkId = "old-link")
        val reimported = first.copy(id = "second", linkId = "new-link")
        assertEquals(FinanceCsv.signature(first), FinanceCsv.signature(reimported))
    }
}
