package com.myfinance.app.data

import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

data class CsvImportPreview(
    val rows: List<FinanceTransaction>,
    val duplicateIds: Set<String>,
    val errors: List<String>
) {
    val readyCount: Int get() = rows.count { it.id !in duplicateIds }
    val duplicateCount: Int get() = duplicateIds.size
}

object FinanceCsv {
    private val header = listOf("date", "account_id", "type", "title", "amount_inr", "category", "wallet_id", "description", "notes", "transfer_link_id")
    private val bankTransferTypes = setOf(TxType.TRANSFER_IN, TxType.TRANSFER_OUT)
    private val walletTransferTypes = setOf(TxType.WALLET_TRANSFER_IN, TxType.WALLET_TRANSFER_OUT)

    fun transactions(rows: List<FinanceTransaction>): String = buildString {
        appendLine(header.joinToString(",") { cell(it) })
        rows.forEach { tx ->
            val amount = "${tx.amountPaise / 100}.${(tx.amountPaise % 100).toString().padStart(2, '0')}"
            val values = listOf(
                LocalDate.ofEpochDay(tx.dateEpochDay).toString(), tx.accountId, tx.type.name, tx.title,
                amount, tx.category, tx.walletId.orEmpty(), tx.description.orEmpty(), tx.notes.orEmpty(), tx.linkId.orEmpty()
            )
            appendLine(values.joinToString(",") { cell(it) })
        }
    }

    /** Reads only MYFINANCE's own export format. Bank statement CSVs must be reviewed and converted first. */
    fun parseTransactions(csv: String): List<FinanceTransaction> {
        val records = parseRecords(csv)
        require(records.isNotEmpty()) { "The CSV file is empty" }
        require(records.first().mapIndexed { i, value -> if (i == 0) value.removePrefix("\uFEFF") else value } == header) {
            "This file is not a MYFINANCE transactions CSV. Exported MYFINANCE CSV files use the required header."
        }
        val rows = records.drop(1).mapIndexedNotNull { index, fields ->
            val line = index + 2
            if (fields.size == 1 && fields[0].isBlank()) return@mapIndexedNotNull null
            require(fields.size == header.size) { "Line $line: expected ${header.size} columns, found ${fields.size}" }
            try {
                val date = LocalDate.parse(fields[0].trim())
                val account = fields[1].trim().lowercase()
                require(account.matches(Regex("[a-z0-9_-]{1,64}"))) { "Invalid account ID" }
                val type = TxType.valueOf(fields[2].trim().uppercase())
                val title = fields[3].trim()
                require(title.isNotBlank()) { "Transaction title is required" }
                val amount = BigDecimal(fields[4].trim()).movePointRight(2).longValueExact()
                require(amount > 0L) { "Amount must be greater than zero paise" }
                val category = fields[5].trim()
                require(category.isNotBlank()) { "Category is required" }
                val wallet = fields[6].trim().ifBlank { null }
                val link = fields[9].trim().ifBlank { null }
                val id = UUID.randomUUID().toString()
                FinanceTransaction(id, title, amount, date.toEpochDay(), account, wallet, type, category,
                    fields[7].ifBlank { null }, fields[8].ifBlank { null }, link)
            } catch (e: Exception) {
                throw IllegalArgumentException("Line $line: ${e.message ?: "invalid transaction"}", e)
            }
        }
        validateTransferPairs(rows)
        return rows
    }

    fun signature(tx: FinanceTransaction): String = listOf(
        tx.dateEpochDay, tx.accountId, tx.type.name, tx.title.trim(), tx.amountPaise, tx.category.trim(),
        tx.walletId.orEmpty(), tx.description.orEmpty(), tx.notes.orEmpty()
    ).joinToString("\u001f")

    private fun validateTransferPairs(rows: List<FinanceTransaction>) {
        val linkedTypes = bankTransferTypes + walletTransferTypes
        require(rows.none { it.type in linkedTypes && it.linkId.isNullOrBlank() }) {
            "Every bank or wallet transfer must include both linked rows from the same MYFINANCE export."
        }
        require(rows.none { it.type !in linkedTypes && it.linkId != null }) {
            "Only transfer entries may contain a transfer link ID."
        }
        rows.filter { it.linkId != null }.groupBy { it.linkId }.forEach { (link, pair) ->
            require(pair.size == 2) { "Transfer link '$link' must contain exactly two rows." }
            val (a, b) = pair
            require(a.amountPaise == b.amountPaise && a.dateEpochDay == b.dateEpochDay) {
                "Transfer link '$link' has different dates or amounts."
            }
            val bankPair = setOf(a.type, b.type) == setOf(TxType.TRANSFER_IN, TxType.TRANSFER_OUT) && a.accountId != b.accountId
            val walletPair = setOf(a.type, b.type) == setOf(TxType.WALLET_TRANSFER_IN, TxType.WALLET_TRANSFER_OUT) &&
                a.accountId == b.accountId && a.walletId != b.walletId && a.walletId != null && b.walletId != null
            require(bankPair || walletPair) { "Transfer link '$link' is not a valid matching account or wallet pair." }
        }
    }

    private fun parseRecords(text: String): List<List<String>> {
        val records = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val field = StringBuilder()
        var quoted = false
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                quoted && c == '"' && i + 1 < text.length && text[i + 1] == '"' -> { field.append('"'); i++ }
                c == '"' && !quoted && field.isEmpty() -> quoted = true
                c == '"' && quoted -> quoted = false
                c == ',' && !quoted -> { row += field.toString(); field.clear() }
                (c == '\n' || c == '\r') && !quoted -> {
                    if (c == '\r' && i + 1 < text.length && text[i + 1] == '\n') i++
                    row += field.toString(); field.clear(); records += row; row = mutableListOf()
                }
                else -> field.append(c)
            }
            i++
        }
        require(!quoted) { "CSV contains an unterminated quoted field" }
        if (field.isNotEmpty() || row.isNotEmpty()) { row += field.toString(); records += row }
        return records
    }

    private fun cell(value: String): String = if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
        "\"${value.replace("\"", "\"\"")}\""
    } else value
}
