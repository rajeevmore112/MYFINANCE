package com.myfinance.app

import android.os.Bundle
import android.os.Process
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.verticalScroll
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.myfinance.app.data.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

private val Ink = Color(0xFF17233D)
private val Muted = Color(0xFF71809A)
private val Canvas = Color(0xFFF5F7FC)
private val Accent = Color(0xFF5266D5)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repo = (application as MyFinanceApp).repository
        val vm = ViewModelProvider(this, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T = FinanceViewModel(repo) as T
        })[FinanceViewModel::class.java]
        setContent { MaterialTheme(colorScheme = lightColorScheme(primary = Accent, background = Canvas, surface = Color.White)) { FinanceHome(vm) } }
    }
}

class FinanceViewModel(private val repo: FinanceRepository) : ViewModel() {
    val accounts = repo.accounts
    val wallets = repo.wallets
    val transactions = repo.transactions
    val budgets = repo.budgets
    init { viewModelScope.launch { repo.initialiseDefaults() } }
    fun setOpening(id: String, rupees: String, onError: (String) -> Unit) = viewModelScope.launch {
        runCatching { repo.setOpeningBalance(id, rupees.toBigDecimal().movePointRight(2).longValueExact()) }.onFailure { onError(it.message ?: "Unable to save") }
    }
    fun addTransaction(type: TxType, account: String, title: String, rupees: String, category: String, onError: (String) -> Unit) = viewModelScope.launch {
        runCatching {
            require(type == TxType.INCOME || type == TxType.EXPENSE || type == TxType.INVESTMENT_CONTRIBUTION || type == TxType.INVESTMENT_WITHDRAWAL) { "Choose a supported transaction type" }
            val amount = rupees.toBigDecimal().movePointRight(2).longValueExact()
            require(title.isNotBlank()) { "Enter a title" }
            repo.add(FinanceTransaction(UUID.randomUUID().toString(), title.trim(), amount, LocalDate.now().toEpochDay(), account, type = type, category = category))
        }.onFailure { onError(it.message ?: "Unable to save") }
    }
    fun addTransfer(from: String, to: String, title: String, rupees: String, onError: (String) -> Unit) = viewModelScope.launch {
        runCatching {
            require(from != to) { "Choose two different accounts" }
            require(title.isNotBlank()) { "Enter a transfer title" }
            val amount = rupees.toBigDecimal().movePointRight(2).longValueExact()
            repo.transfer(from, to, amount, LocalDate.now().toEpochDay(), title.trim())
        }.onFailure { onError(it.message ?: "Unable to save transfer") }
    }
    fun addWalletEntry(wallet: VirtualWallet, type: TxType, title: String, rupees: String, onError: (String) -> Unit) = viewModelScope.launch {
        runCatching {
            require(type == TxType.WALLET_FUND || type == TxType.WALLET_SPEND) { "Choose a wallet allocation or spend" }
            require(title.isNotBlank()) { "Enter a purpose" }
            val amount = rupees.toBigDecimal().movePointRight(2).longValueExact()
            repo.add(FinanceTransaction(UUID.randomUUID().toString(), title.trim(), amount, LocalDate.now().toEpochDay(), wallet.accountId, wallet.id, type, if (type == TxType.WALLET_FUND) "Allocation" else "Personal"))
        }.onFailure { onError(it.message ?: "Unable to save wallet entry") }
    }
    fun addWalletTransfer(from: VirtualWallet, to: VirtualWallet, title: String, rupees: String, onError: (String) -> Unit) = viewModelScope.launch {
        runCatching {
            require(title.isNotBlank()) { "Enter a transfer title" }
            val amount = rupees.toBigDecimal().movePointRight(2).longValueExact()
            repo.walletTransfer(from.id, to.id, amount, LocalDate.now().toEpochDay(), title.trim())
        }.onFailure { onError(it.message ?: "Unable to save wallet transfer") }
    }
    fun reconcile(accountId: String, actualRupees: String, reason: String, onError: (String) -> Unit) = viewModelScope.launch {
        runCatching {
            require(reason.isNotBlank()) { "Enter an explanation for the adjustment" }
            val actual = actualRupees.toBigDecimal().movePointRight(2).longValueExact()
            repo.reconcile(accountId, actual, reason.trim(), LocalDate.now().toEpochDay())
        }.onFailure { onError(it.message ?: "Unable to reconcile balance") }
    }
    fun deleteTransaction(id: String, onError: (String) -> Unit) = viewModelScope.launch {
        runCatching { repo.delete(id) }.onFailure { onError(it.message ?: "Unable to delete transaction") }
    }
    fun updateTransaction(tx: FinanceTransaction, title: String, rupees: String, category: String, onError: (String) -> Unit) = viewModelScope.launch {
        runCatching {
            require(title.isNotBlank()) { "Enter a title" }
            val amount = rupees.toBigDecimal().movePointRight(2).longValueExact()
            repo.update(tx.copy(title = title.trim(), amountPaise = amount, category = category.trim()))
        }.onFailure { onError(it.message ?: "Unable to update transaction") }
    }
    fun saveBudget(month: String, category: String, accountId: String?, amount: String, carry: Boolean, alert75: Boolean, alert90: Boolean, alert100: Boolean, onError: (String) -> Unit) = viewModelScope.launch {
        runCatching {
            val limit = amount.toBigDecimal().movePointRight(2).longValueExact()
            repo.saveBudget(month, category, accountId, limit, carry, alert75, alert90, alert100)
        }.onFailure { onError(it.message ?: "Unable to save budget") }
    }
    suspend fun exportTransactionsCsv(): String = repo.exportTransactionsCsv()
    suspend fun previewTransactionsCsv(csv: String): CsvImportPreview = repo.previewTransactionsCsv(csv)
    suspend fun importTransactionsCsv(preview: CsvImportPreview): Pair<Int, Int> = repo.importTransactionsCsv(preview)
    suspend fun writeBackup(output: java.io.OutputStream) = repo.writeBackup(output)
    suspend fun restoreBackup(input: java.io.InputStream): String = repo.restoreBackup(input)
}

@Composable
private fun FinanceHome(vm: FinanceViewModel) {
    val accounts by vm.accounts.collectAsStateWithLifecycle(emptyList())
    val wallets by vm.wallets.collectAsStateWithLifecycle(emptyList())
    val txs by vm.transactions.collectAsStateWithLifecycle(emptyList())
    val budgets by vm.budgets.collectAsStateWithLifecycle(emptyList())
    var showAdd by remember { mutableStateOf(false) }
    var showTransfer by remember { mutableStateOf(false) }
    var showWalletEntry by remember { mutableStateOf(false) }
    var showWalletTransfer by remember { mutableStateOf(false) }
    var showReconcile by remember { mutableStateOf(false) }
    var showBudgetEditor by remember { mutableStateOf(false) }
    var transactionToDelete by remember { mutableStateOf<FinanceTransaction?>(null) }
    var transactionToEdit by remember { mutableStateOf<FinanceTransaction?>(null) }
    var selectedTab by remember { mutableIntStateOf(0) }
    var transactionQuery by remember { mutableStateOf("") }
    var transactionAccountFilter by remember { mutableStateOf("") }
    var transactionTypeFilter by remember { mutableStateOf("ALL") }
    var transactionMonthFilter by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var exportMessage by remember { mutableStateOf<String?>(null) }
    var importMessage by remember { mutableStateOf<String?>(null) }
    var csvPreview by remember { mutableStateOf<CsvImportPreview?>(null) }
    var importingCsv by remember { mutableStateOf(false) }
    var backupMessage by remember { mutableStateOf<String?>(null) }
    var restoreUri by remember { mutableStateOf<Uri?>(null) }
    var restoreSafetyPath by remember { mutableStateOf<String?>(null) }
    var backupInProgress by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val csvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) coroutineScope.launch {
            runCatching {
                val csv = vm.exportTransactionsCsv()
                val output = requireNotNull(context.contentResolver.openOutputStream(uri)) { "Could not open the selected file" }
                output.bufferedWriter(Charsets.UTF_8).use { it.write(csv) }
            }.onSuccess { exportMessage = "CSV exported successfully." }
                .onFailure { error = it.message ?: "Could not export CSV" }
        }
    }
    val backupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri == null) backupInProgress = false
        else coroutineScope.launch {
            runCatching {
                val output = requireNotNull(context.contentResolver.openOutputStream(uri)) { "Could not open the selected file" }
                output.use { vm.writeBackup(it) }
            }.onSuccess { backupMessage = "Backup saved. Store the file somewhere private and separate from this device." }
                .onFailure { error = it.message ?: "Could not create backup" }
            backupInProgress = false
        }
    }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> restoreUri = uri }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) coroutineScope.launch {
            runCatching {
                val input = requireNotNull(context.contentResolver.openInputStream(uri)) { "Could not read the selected CSV" }
                val csv = input.bufferedReader(Charsets.UTF_8).use { it.readText() }
                vm.previewTransactionsCsv(csv)
            }.onSuccess { csvPreview = it }.onFailure { error = it.message ?: "Could not read MYFINANCE CSV" }
        }
    }
    val total = accounts.sumOf { account -> account.openingBalancePaise + txs.filter { it.accountId == account.id }.sumOf { com.myfinance.app.domain.LedgerEngine.bankDelta(it) } }
    val income = txs.filter { it.type == TxType.INCOME }.sumOf { it.amountPaise }
    val expenses = txs.filter { it.type == TxType.EXPENSE || it.type == TxType.WALLET_SPEND }.sumOf { it.amountPaise }
    val transactionMonths = txs.map { YearMonth.from(LocalDate.ofEpochDay(it.dateEpochDay)).toString() }.distinct().sortedDescending()
    val visibleTransactions = com.myfinance.app.domain.TransactionFilters.filter(
        txs, transactionQuery, transactionAccountFilter.ifBlank { null },
        transactionTypeFilter.takeIf { it != "ALL" }?.let { TxType.valueOf(it) },
        transactionMonthFilter.takeIf { it.isNotBlank() }?.let { YearMonth.parse(it) }
    )
    Scaffold(containerColor = Canvas, floatingActionButton = { if (selectedTab != 5 && selectedTab != 6) FloatingActionButton(onClick = { showAdd = true }, containerColor = Accent, contentColor = Color.White) { Icon(Icons.Default.Add, "Add transaction") } }, bottomBar = {
        val navItems = listOf(0 to "Home", 1 to "Accounts", 2 to "Activity", 3 to "Analytics", 6 to "More")
        NavigationBar { navItems.forEach { (destination, label) ->
            val selected = if (destination == 6) selectedTab >= 4 else selectedTab == destination
            val icon = when (destination) {
                0 -> Icons.Default.Home
                1 -> Icons.Default.AccountBalance
                2 -> Icons.Default.ReceiptLong
                3 -> Icons.Default.BarChart
                else -> Icons.Default.MoreHoriz
            }
            NavigationBarItem(selected = selected, onClick = { selectedTab = destination }, icon = { Icon(icon, contentDescription = label) }, label = { Text(label, maxLines = 1, softWrap = false) })
        } }
    }) { padding ->
        LazyColumn(Modifier.fillMaxSize().background(Canvas).padding(padding).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (selectedTab != 0) {
                when (selectedTab) {
                    1 -> {
                        item { Spacer(Modifier.height(8.dp)); Text("Accounts", style = MaterialTheme.typography.headlineMedium, color = Ink, fontWeight = FontWeight.Bold) }
                        item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Button(onClick = { showTransfer = true }, modifier = Modifier.weight(1f)) { Text("Bank transfer") }; Button(onClick = { showWalletEntry = true }, modifier = Modifier.weight(1f)) { Text("Wallet entry") } } }
                        if (wallets.size > 1) item { OutlinedButton(onClick = { showWalletTransfer = true }, modifier = Modifier.fillMaxWidth()) { Text("Move between Kotak wallets") } }
                        item { OutlinedButton(onClick = { showReconcile = true }, modifier = Modifier.fillMaxWidth()) { Text("Reconcile bank balance") } }
                        items(accounts) { account ->
                            val balance = account.openingBalancePaise + txs.filter { it.accountId == account.id }.sumOf { com.myfinance.app.domain.LedgerEngine.bankDelta(it) }
                            Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White), modifier = Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(18.dp)) {
                                    Text(account.name, color = Ink, fontWeight = FontWeight.Bold)
                                    Text(account.purpose, color = Muted, style = MaterialTheme.typography.bodySmall)
                                    Text(money(balance), color = Ink, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                                    if (account.id == "kotak") {
                                        val assigned = wallets.sumOf { w -> w.openingBalancePaise + txs.filter { it.walletId == w.id }.sumOf { com.myfinance.app.domain.LedgerEngine.walletDelta(it) } }
                                        Text("Allocated ${money(assigned)} · Unallocated ${money(balance - assigned)}", color = Muted, style = MaterialTheme.typography.bodySmall)
                                        wallets.forEach { wallet ->
                                            val walletBalance = wallet.openingBalancePaise + txs.filter { it.walletId == wallet.id }.sumOf { com.myfinance.app.domain.LedgerEngine.walletDelta(it) }
                                            Text("${wallet.name}: ${money(walletBalance)}", color = Muted, style = MaterialTheme.typography.bodySmall)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    2 -> {
                        item { Spacer(Modifier.height(8.dp)); Text("Transactions", style = MaterialTheme.typography.headlineMedium, color = Ink, fontWeight = FontWeight.Bold) }
                        item { OutlinedButton(onClick = { csvLauncher.launch("MYFINANCE-${LocalDate.now()}.csv") }, modifier = Modifier.fillMaxWidth()) { Text("Export transactions as CSV") } }
                        item { OutlinedTextField(transactionQuery, { transactionQuery = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Search title, category, notes") }, singleLine = true) }
                        item { SelectField("Account", transactionAccountFilter, listOf("" to "All accounts") + accounts.map { it.id to it.name }) { transactionAccountFilter = it } }
                        item { SelectField("Type", transactionTypeFilter, listOf("ALL" to "All types") + TxType.values().map { it.name to it.name.replace('_', ' ').lowercase().replaceFirstChar(Char::uppercase) }) { transactionTypeFilter = it } }
                        item { SelectField("Month", transactionMonthFilter, listOf("" to "All months") + transactionMonths.map { it to it }) { transactionMonthFilter = it } }
                        if (txs.isEmpty()) item { Text("No transactions yet. Use + to add income or an expense.", color = Muted) }
                        if (txs.isNotEmpty() && visibleTransactions.isEmpty()) item { Text("No transactions match these filters.", color = Muted) }
                        items(visibleTransactions) { tx ->
                            val delta = if (tx.walletId != null) com.myfinance.app.domain.LedgerEngine.walletDelta(tx) else com.myfinance.app.domain.LedgerEngine.bankDelta(tx)
                            Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                                Row(Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Column(Modifier.weight(1f)) { Text(tx.title, color = Ink, fontWeight = FontWeight.SemiBold); Text("${tx.category} · ${tx.accountId.uppercase()} · ${LocalDate.ofEpochDay(tx.dateEpochDay)}", color = Muted, style = MaterialTheme.typography.bodySmall) }
                                    Text((if (delta >= 0) "+" else "−") + money(tx.amountPaise), color = if (delta >= 0) Color(0xFF17865D) else Color(0xFFC04B4B), fontWeight = FontWeight.Bold)
                                    IconButton(onClick = { transactionToEdit = tx }) { Icon(Icons.Default.Edit, contentDescription = "Edit transaction", tint = Muted) }
                                    IconButton(onClick = { transactionToDelete = tx }) { Icon(Icons.Default.Delete, contentDescription = "Delete transaction", tint = Muted) }
                                }
                            }
                        }
                    }
                    3 -> {
                        val today = LocalDate.now()
                        val monthStart = today.withDayOfMonth(1).toEpochDay()
                        val monthTransactions = txs.filter { it.dateEpochDay >= monthStart && it.dateEpochDay <= today.toEpochDay() }
                        val monthIncome = monthTransactions.filter { it.type == TxType.INCOME }.sumOf { it.amountPaise }
                        val monthExpenses = monthTransactions.filter { com.myfinance.app.domain.LedgerEngine.isConsumptionExpense(it.type) }.sumOf { it.amountPaise }
                        val monthInvestmentContributions = monthTransactions.filter { it.type == TxType.INVESTMENT_CONTRIBUTION }.sumOf { it.amountPaise }
                        val monthInvestmentWithdrawals = monthTransactions.filter { it.type == TxType.INVESTMENT_WITHDRAWAL }.sumOf { it.amountPaise }
                        item { Spacer(Modifier.height(8.dp)); Text("This month", style = MaterialTheme.typography.headlineMedium, color = Ink, fontWeight = FontWeight.Bold) }
                        item { Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Text("Income", color = Muted); Text(money(monthIncome), color = Color(0xFF17865D), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold); Text("Consumption expenses", color = Muted); Text(money(monthExpenses), color = Color(0xFFC04B4B), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold); Text("Investment contributions · ${money(monthInvestmentContributions)}", color = Ink); Text("Investment withdrawals · ${money(monthInvestmentWithdrawals)}", color = Ink); Text("Investment movements and internal transfers are not included as consumption or salary income.", color = Muted, style = MaterialTheme.typography.bodySmall) } } }
                        val categoryTotals = monthTransactions.filter { com.myfinance.app.domain.LedgerEngine.isConsumptionExpense(it.type) }.groupBy { it.category }.mapValues { (_, rows) -> rows.sumOf { it.amountPaise } }.entries.sortedByDescending { it.value }
                        item { Text("Spending by category", style = MaterialTheme.typography.titleLarge, color = Ink, fontWeight = FontWeight.Bold) }
                        if (categoryTotals.isEmpty()) item { Text("No expenses recorded this month.", color = Muted) }
                        items(categoryTotals.toList()) { (category, amount) -> Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) { Text(category, color = Ink); Text(money(amount), color = Ink, fontWeight = FontWeight.SemiBold) } }
                    }
                    4 -> {
                        val currentMonth = YearMonth.now().toString()
                        val monthBudgets = budgets.filter { it.month == currentMonth }.sortedBy { it.category }
                        item { Spacer(Modifier.height(8.dp)); Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Text("Budgets · $currentMonth", style = MaterialTheme.typography.headlineSmall, color = Ink, fontWeight = FontWeight.Bold); IconButton(onClick = { showBudgetEditor = true }) { Icon(Icons.Default.Add, contentDescription = "Add budget", tint = Accent) } } }
                        if (monthBudgets.isEmpty()) item { Text("No budgets set for this month. Add one to track actual expense transactions against a limit.", color = Muted) }
                        items(monthBudgets) { budget ->
                            val period = YearMonth.parse(budget.month)
                            val from = period.atDay(1).toEpochDay()
                            val to = period.atEndOfMonth().toEpochDay()
                            val spent = txs.asSequence().filter { it.dateEpochDay in from..to && it.category == budget.category && com.myfinance.app.domain.LedgerEngine.isConsumptionExpense(it.type) && (budget.accountId == null || it.accountId == budget.accountId) }.fold(0L) { totalSpent, tx -> Math.addExact(totalSpent, tx.amountPaise) }
                            val previousBudget = budgets.firstOrNull { it.month == period.minusMonths(1).toString() && it.category == budget.category && it.accountId == budget.accountId && it.carryForward }
                            val previousSpent = if (previousBudget == null) 0L else txs.asSequence().filter { it.dateEpochDay in period.minusMonths(1).atDay(1).toEpochDay()..period.minusMonths(1).atEndOfMonth().toEpochDay() && it.category == budget.category && com.myfinance.app.domain.LedgerEngine.isConsumptionExpense(it.type) && (budget.accountId == null || it.accountId == budget.accountId) }.fold(0L) { sum, tx -> Math.addExact(sum, tx.amountPaise) }
                            val carryOver = previousBudget?.let { (it.limitPaise - previousSpent).coerceAtLeast(0L) } ?: 0L
                            val available = Math.addExact(budget.limitPaise, carryOver)
                            val percent = (java.math.BigDecimal.valueOf(spent).multiply(java.math.BigDecimal.valueOf(100)).divide(java.math.BigDecimal.valueOf(available), 0, java.math.RoundingMode.HALF_UP)).toInt()
                            val threshold = when {
                                budget.alert100 && spent >= available -> "100% budget reached"
                                budget.alert90 && percent >= 90 -> "90% alert crossed"
                                budget.alert75 && percent >= 75 -> "75% alert crossed"
                                else -> null
                            }
                            Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(budget.category, color = Ink, fontWeight = FontWeight.Bold)
                                    Text(if (budget.accountId == null) "All accounts" else accounts.firstOrNull { it.id == budget.accountId }?.name ?: budget.accountId, color = Muted, style = MaterialTheme.typography.bodySmall)
                                    Text("${money(spent)} of ${money(available)} · $percent%", color = Ink)
                                    LinearProgressIndicator(progress = { (spent.toDouble() / available.toDouble()).toFloat().coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth(), color = if (spent >= available) Color(0xFFC04B4B) else Accent)
                                    Text(if (spent <= available) "Remaining ${money(available - spent)}" else "Over budget ${money(spent - available)}", color = if (spent > available) Color(0xFFC04B4B) else Muted, style = MaterialTheme.typography.bodySmall)
                                    if (carryOver > 0L) Text("Includes ${money(carryOver)} unused from last month", color = Muted, style = MaterialTheme.typography.bodySmall)
                                    if (threshold != null) Text(threshold, color = Color(0xFFC04B4B), fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
                                    if (budget.carryForward) Text("Unused amount carries forward", color = Muted, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                    6 -> {
                        item { Spacer(Modifier.height(8.dp)); Text("More", style = MaterialTheme.typography.headlineMedium, color = Ink, fontWeight = FontWeight.Bold) }
                        item { Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Text("Budgets", color = Ink, fontWeight = FontWeight.Bold); Text("Track monthly category spending and carry-forward.", color = Muted); Button(onClick = { selectedTab = 4 }, modifier = Modifier.fillMaxWidth()) { Text("Open budgets") } } } }
                        item { Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Text("Settings & backups", color = Ink, fontWeight = FontWeight.Bold); Text("Local transaction CSV import/export, privacy, and backups.", color = Muted); OutlinedButton(onClick = { selectedTab = 5 }, modifier = Modifier.fillMaxWidth()) { Text("Open settings") } } } }
                    }
                    else -> {
                        item { Spacer(Modifier.height(8.dp)); Text("Settings", style = MaterialTheme.typography.headlineMedium, color = Ink, fontWeight = FontWeight.Bold) }
                        item { OutlinedButton(enabled = !importingCsv, onClick = { importLauncher.launch(arrayOf("text/*", "application/vnd.ms-excel")) }, modifier = Modifier.fillMaxWidth()) { Text("Import MYFINANCE CSV…") } }
                        item { OutlinedButton(onClick = { csvLauncher.launch("MYFINANCE-transactions-${LocalDate.now()}.csv") }, modifier = Modifier.fillMaxWidth()) { Text("Export transactions as CSV") } }
                        item { OutlinedButton(enabled = !backupInProgress, onClick = { backupInProgress = true; backupLauncher.launch("MYFINANCE-backup-${LocalDate.now()}.zip") }, modifier = Modifier.fillMaxWidth()) { Text("Create local backup") } }
                        item { OutlinedButton(onClick = { restoreLauncher.launch(arrayOf("application/zip", "application/octet-stream")) }, modifier = Modifier.fillMaxWidth()) { Text("Restore from backup…") } }
                        item { Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Text("Privacy", color = Ink, fontWeight = FontWeight.Bold); Text("Manual entry · no bank connection · no internet permission", color = Muted); Text("Backups are local and unencrypted. Restoring replaces current data after a safety copy is made; reopen MYFINANCE after restore.", color = Muted) } } }
                    }
                }
            } else {
            item { Spacer(Modifier.height(8.dp)); Text("MYFINANCE", style = MaterialTheme.typography.labelLarge, color = Accent, fontWeight = FontWeight.Bold); Text("Your money, clearly.", style = MaterialTheme.typography.headlineMedium, color = Ink, fontWeight = FontWeight.Bold) }
            item {
                Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Ink), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(22.dp)) {
                        Text("TOTAL TRACKED BANK BALANCE", color = Color(0xFFB9C5E5), style = MaterialTheme.typography.labelSmall)
                        Text(money(total), color = Color.White, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(18.dp)); Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Metric("Income", income, Color(0xFF8FE0C0)); Metric("Expenses", expenses, Color(0xFFFFB6AE)) }
                    }
                }
            }
            item { Text("Your accounts", style = MaterialTheme.typography.titleLarge, color = Ink, fontWeight = FontWeight.Bold) }
            items(accounts) { account ->
                val balance = account.openingBalancePaise + txs.filter { it.accountId == account.id }.sumOf { com.myfinance.app.domain.LedgerEngine.bankDelta(it) }
                Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Column { Text(account.name, color = Ink, fontWeight = FontWeight.Bold); Text(account.purpose, color = Muted, style = MaterialTheme.typography.bodySmall) }; Text(money(balance), color = Ink, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
                        if (account.id == "kotak") {
                            val assigned = wallets.sumOf { w -> w.openingBalancePaise + txs.filter { it.walletId == w.id }.sumOf { com.myfinance.app.domain.LedgerEngine.walletDelta(it) } }
                            Text("Wallet allocations  ·  ${money(assigned)}", color = Muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
                            Text("Unallocated Kotak cash  ·  ${money(balance - assigned)}", color = Muted, style = MaterialTheme.typography.bodySmall)
                            wallets.forEach { w -> val b = w.openingBalancePaise + txs.filter { it.walletId == w.id }.sumOf { com.myfinance.app.domain.LedgerEngine.walletDelta(it) }; Text("${w.name}: ${money(b)}", color = Muted, style = MaterialTheme.typography.bodySmall) }
                        }
                        if (account.openingBalancePaise == 0L && txs.none { it.accountId == account.id }) {
                            var opening by remember { mutableStateOf("") }
                            Row(verticalAlignment = Alignment.CenterVertically) { OutlinedTextField(opening, { opening = it }, Modifier.weight(1f), label = { Text("Opening balance (₹)") }, singleLine = true); Spacer(Modifier.width(8.dp)); Button(onClick = { vm.setOpening(account.id, opening) { error = it } }) { Text("Set") } }
                        }
                    }
                }
            }
            item { Text("Recent activity", style = MaterialTheme.typography.titleLarge, color = Ink, fontWeight = FontWeight.Bold) }
            items(txs.take(12)) { tx ->
                val delta = if (tx.walletId != null) com.myfinance.app.domain.LedgerEngine.walletDelta(tx) else com.myfinance.app.domain.LedgerEngine.bankDelta(tx)
                Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) { Row(Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Column(Modifier.weight(1f)) { Text(tx.title, color = Ink, fontWeight = FontWeight.SemiBold); Text("${tx.category}  ·  ${tx.accountId.uppercase()}", color = Muted, style = MaterialTheme.typography.bodySmall) }; Text((if (delta >= 0) "+" else "−") + money(tx.amountPaise), color = if (delta >= 0) Color(0xFF17865D) else Color(0xFFC04B4B), fontWeight = FontWeight.Bold) } }
            }
            item { Spacer(Modifier.height(72.dp)) }
            }
        }
    }
    if (showAdd) AddTransactionDialog(accounts.map { it.id to it.name }, onDismiss = { showAdd = false }, onSave = { type, account, title, amount, category -> vm.addTransaction(type, account, title, amount, category) { error = it }; showAdd = false })
    if (showTransfer) BankTransferDialog(accounts.map { it.id to it.name }, onDismiss = { showTransfer = false }, onSave = { from, to, title, amount -> vm.addTransfer(from, to, title, amount) { error = it }; showTransfer = false })
    if (showWalletEntry) WalletEntryDialog(wallets, onDismiss = { showWalletEntry = false }, onSave = { wallet, type, title, amount -> vm.addWalletEntry(wallet, type, title, amount) { error = it }; showWalletEntry = false })
    if (showWalletTransfer && wallets.size > 1) WalletTransferDialog(wallets, onDismiss = { showWalletTransfer = false }, onSave = { from, to, title, amount -> vm.addWalletTransfer(from, to, title, amount) { error = it }; showWalletTransfer = false })
    if (showReconcile) ReconciliationDialog(accounts, txs, onDismiss = { showReconcile = false }, onSave = { accountId, amount, reason -> vm.reconcile(accountId, amount, reason) { error = it }; showReconcile = false })
    if (showBudgetEditor) BudgetEditorDialog(accounts, onDismiss = { showBudgetEditor = false }, onSave = { month, category, accountId, amount, carry, a75, a90, a100 -> vm.saveBudget(month, category, accountId, amount, carry, a75, a90, a100) { error = it }; showBudgetEditor = false })
    csvPreview?.let { preview ->
        AlertDialog(
            onDismissRequest = { if (!importingCsv) csvPreview = null },
            title = { Text("Review CSV import") },
            text = {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${preview.rows.size} rows found · ${preview.readyCount} ready · ${preview.duplicateCount} already present")
                    Text("This accepts MYFINANCE transaction exports only. Bank statement CSVs must be categorized and converted first.", color = Muted)
                    preview.rows.take(5).forEach { tx -> Text("${LocalDate.ofEpochDay(tx.dateEpochDay)} · ${tx.accountId} · ${tx.type} · ${tx.title} · ${money(tx.amountPaise)}") }
                    if (preview.rows.size > 5) Text("…and ${preview.rows.size - 5} more rows", color = Muted)
                    preview.errors.forEach { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                Button(enabled = !importingCsv && preview.errors.isEmpty() && preview.readyCount > 0, onClick = {
                    importingCsv = true
                    coroutineScope.launch {
                        runCatching { vm.importTransactionsCsv(preview) }
                            .onSuccess { (added, skipped) -> importMessage = "Imported $added transactions. Skipped $skipped duplicates."; csvPreview = null }
                            .onFailure { error = it.message ?: "Import failed" }
                        importingCsv = false
                    }
                }) { Text(if (importingCsv) "Importing…" else "Import") }
            },
            dismissButton = { TextButton(onClick = { if (!importingCsv) csvPreview = null }) { Text("Cancel") } }
        )
    }
    transactionToDelete?.let { tx ->
        AlertDialog(onDismissRequest = { transactionToDelete = null }, title = { Text("Delete transaction?") }, text = { Text(if (tx.linkId != null) "This transaction belongs to a linked transfer. Both entries will be deleted." else "${tx.title} · ${money(tx.amountPaise)}") }, confirmButton = { Button(onClick = { vm.deleteTransaction(tx.id) { error = it }; transactionToDelete = null }) { Text("Delete") } }, dismissButton = { TextButton(onClick = { transactionToDelete = null }) { Text("Cancel") } })
    }
    transactionToEdit?.let { tx ->
        EditTransactionDialog(tx, onDismiss = { transactionToEdit = null }, onSave = { title, amount, category ->
            vm.updateTransaction(tx, title, amount, category) { error = it }
            transactionToEdit = null
        })
    }
    error?.let { message -> AlertDialog(onDismissRequest = { error = null }, confirmButton = { TextButton2("OK") { error = null } }, title = { Text("Could not save") }, text = { Text(message) }) }
    exportMessage?.let { message -> AlertDialog(onDismissRequest = { exportMessage = null }, confirmButton = { TextButton2("OK") { exportMessage = null } }, title = { Text("Export complete") }, text = { Text(message) }) }
    importMessage?.let { message -> AlertDialog(onDismissRequest = { importMessage = null }, confirmButton = { TextButton2("OK") { importMessage = null } }, title = { Text("Import complete") }, text = { Text(message) }) }
    backupMessage?.let { message -> AlertDialog(onDismissRequest = { backupMessage = null }, confirmButton = { TextButton2("OK") { backupMessage = null } }, title = { Text("Backup complete") }, text = { Text(message) }) }
    restoreUri?.let { uri ->
        AlertDialog(onDismissRequest = { restoreUri = null }, title = { Text("Replace current data?") }, text = { Text("The selected backup will replace all current MYFINANCE data. A safety copy will be stored privately on this device first. The app will close after restore; reopen it to use the restored data.") }, confirmButton = {
            Button(onClick = {
                restoreUri = null
                backupInProgress = true
                coroutineScope.launch {
                    runCatching {
                        val input = requireNotNull(context.contentResolver.openInputStream(uri)) { "Could not read the selected backup" }
                        input.use { vm.restoreBackup(it) }
                    }.onSuccess { restoreSafetyPath = it }.onFailure { error = it.message ?: "Could not restore backup" }
                    backupInProgress = false
                }
            }) { Text("Restore") }
        }, dismissButton = { TextButton(onClick = { restoreUri = null }) { Text("Cancel") } })
    }
    if (backupInProgress) AlertDialog(onDismissRequest = {}, title = { Text("Working with backup") }, text = { Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) { CircularProgressIndicator(); Text("Please keep MYFINANCE open until this finishes.") } }, confirmButton = {})
    restoreSafetyPath?.let { safetyPath -> AlertDialog(onDismissRequest = {}, title = { Text("Restore complete") }, text = { Text("Current data was saved to a private safety copy (${java.io.File(safetyPath).name}). Reopen MYFINANCE to continue.") }, confirmButton = { TextButton2("Close and reopen") { Process.killProcess(Process.myPid()) } }) }
}

@Composable private fun TextButton2(label: String, onClick: () -> Unit) = TextButton(onClick = onClick) { Text(label) }
@Composable private fun Metric(label: String, value: Long, tint: Color) { Column { Text(label, color = Color(0xFFB9C5E5), style = MaterialTheme.typography.labelSmall); Text(money(value), color = tint, fontWeight = FontWeight.Bold) } }
@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun SelectField(label: String, selected: String, options: List<Pair<String, String>>, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
        OutlinedTextField(options.firstOrNull { it.first == selected }?.second ?: selected, {}, Modifier.menuAnchor().fillMaxWidth(), readOnly = true, label = { Text(label) })
        ExposedDropdownMenu(expanded, { expanded = false }) {
            options.forEach { (id, name) -> DropdownMenuItem(text = { Text(name) }, onClick = { onSelect(id); expanded = false }) }
        }
    }
}

@Composable private fun BankTransferDialog(accounts: List<Pair<String, String>>, onDismiss: () -> Unit, onSave: (String, String, String, String) -> Unit) {
    var from by remember { mutableStateOf(accounts.firstOrNull()?.first ?: "") }
    var to by remember { mutableStateOf(accounts.getOrNull(1)?.first ?: "") }
    var title by remember { mutableStateOf("Transfer") }
    var amount by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Transfer between accounts") }, text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SelectField("From account", from, accounts) { from = it }
        SelectField("To account", to, accounts) { to = it }
        OutlinedTextField(title, { title = it }, label = { Text("Description") }, singleLine = true)
        OutlinedTextField(amount, { amount = it }, label = { Text("Amount (₹)") }, singleLine = true)
    } }, confirmButton = { Button(enabled = accounts.size > 1 && from != to && title.isNotBlank() && amount.toBigDecimalOrNull()?.let { it > java.math.BigDecimal.ZERO } == true, onClick = { onSave(from, to, title, amount) }) { Text("Transfer") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable private fun BudgetEditorDialog(accounts: List<BankAccount>, onDismiss: () -> Unit, onSave: (String, String, String?, String, Boolean, Boolean, Boolean, Boolean) -> Unit) {
    var month by remember { mutableStateOf(YearMonth.now().toString()) }
    var category by remember { mutableStateOf("Food") }
    var accountId by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var carryForward by remember { mutableStateOf(false) }
    var alert75 by remember { mutableStateOf(true) }
    var alert90 by remember { mutableStateOf(true) }
    var alert100 by remember { mutableStateOf(true) }
    val categories = listOf("Food", "Groceries", "Transportation", "Rent", "Utilities", "Shopping", "Entertainment", "Subscriptions", "Medical", "Travel", "Personal", "Other")
    val validMonth = runCatching { YearMonth.parse(month) }.isSuccess
    val validAmount = amount.toBigDecimalOrNull()?.let { it > java.math.BigDecimal.ZERO } == true
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Monthly budget") }, text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(month, { month = it }, label = { Text("Month (YYYY-MM)") }, singleLine = true)
        SelectField("Category", category, categories.map { it to it }) { category = it }
        SelectField("Account scope", accountId, listOf("" to "All accounts") + accounts.map { it.id to it.name }) { accountId = it }
        OutlinedTextField(amount, { amount = it }, label = { Text("Limit (₹)") }, singleLine = true)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Text("Carry unused amount forward"); Switch(carryForward, { carryForward = it }) }
        Text("Show alerts when spending reaches", color = Muted, style = MaterialTheme.typography.labelMedium)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Text("75%"); Switch(alert75, { alert75 = it }) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Text("90%"); Switch(alert90, { alert90 = it }) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Text("100%"); Switch(alert100, { alert100 = it }) }
    } }, confirmButton = { Button(enabled = validMonth && validAmount && category.isNotBlank(), onClick = { onSave(month, category, accountId.ifBlank { null }, amount, carryForward, alert75, alert90, alert100) }) { Text("Save budget") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable private fun WalletEntryDialog(wallets: List<VirtualWallet>, onDismiss: () -> Unit, onSave: (VirtualWallet, TxType, String, String) -> Unit) {
    var selectedId by remember { mutableStateOf(wallets.firstOrNull()?.id ?: "") }
    var type by remember { mutableStateOf(TxType.WALLET_FUND) }
    var title by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Kotak wallet entry") }, text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SelectField("Wallet", selectedId, wallets.map { it.id to it.name }) { selectedId = it }
        SelectField("Type", type.name, listOf(TxType.WALLET_FUND.name to "Allocate cash", TxType.WALLET_SPEND.name to "Spend from wallet")) { type = TxType.valueOf(it) }
        OutlinedTextField(title, { title = it }, label = { Text("Purpose / reason") }, singleLine = true)
        OutlinedTextField(amount, { amount = it }, label = { Text("Amount (₹)") }, singleLine = true)
    } }, confirmButton = { Button(enabled = selectedId.isNotBlank() && title.isNotBlank() && amount.toBigDecimalOrNull()?.let { it > java.math.BigDecimal.ZERO } == true, onClick = { wallets.firstOrNull { it.id == selectedId }?.let { onSave(it, type, title, amount) } }) { Text("Save") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable private fun WalletTransferDialog(wallets: List<VirtualWallet>, onDismiss: () -> Unit, onSave: (VirtualWallet, VirtualWallet, String, String) -> Unit) {
    val source = wallets.firstOrNull()
    val sameAccountWallets = wallets.filter { it.accountId == source?.accountId }
    var fromId by remember { mutableStateOf(source?.id ?: "") }
    var toId by remember { mutableStateOf(sameAccountWallets.getOrNull(1)?.id ?: "") }
    var title by remember { mutableStateOf("Wallet transfer") }
    var amount by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Move between wallets") }, text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SelectField("From wallet", fromId, sameAccountWallets.map { it.id to it.name }) { fromId = it }
        SelectField("To wallet", toId, sameAccountWallets.map { it.id to it.name }) { toId = it }
        OutlinedTextField(title, { title = it }, label = { Text("Description") }, singleLine = true)
        OutlinedTextField(amount, { amount = it }, label = { Text("Amount (₹)") }, singleLine = true)
    } }, confirmButton = { Button(enabled = sameAccountWallets.size > 1 && fromId != toId && title.isNotBlank() && amount.toBigDecimalOrNull()?.let { it > java.math.BigDecimal.ZERO } == true, onClick = { val from = sameAccountWallets.firstOrNull { it.id == fromId }; val to = sameAccountWallets.firstOrNull { it.id == toId }; if (from != null && to != null) onSave(from, to, title, amount) }) { Text("Move") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun AddTransactionDialog(accounts: List<Pair<String, String>>, onDismiss: () -> Unit, onSave: (TxType, String, String, String, String) -> Unit) {
    var type by remember { mutableStateOf(TxType.EXPENSE) }
    var account by remember { mutableStateOf(accounts.firstOrNull()?.first ?: "hdfc") }; var title by remember { mutableStateOf("") }; var amount by remember { mutableStateOf("") }; var category by remember { mutableStateOf("Food") }
    val categories = when (type) {
        TxType.INCOME -> listOf("Salary", "Additional income", "Refund", "Other income")
        TxType.INVESTMENT_CONTRIBUTION, TxType.INVESTMENT_WITHDRAWAL -> listOf("Investments")
        else -> listOf("Food", "Groceries", "Transportation", "Rent", "Utilities", "Shopping", "Entertainment", "Subscriptions", "Medical", "Travel", "Personal", "Other")
    }
    LaunchedEffect(type) { category = categories.first() }
    val typeLabel = when (type) { TxType.INCOME -> "Income"; TxType.EXPENSE -> "Expense"; TxType.INVESTMENT_CONTRIBUTION -> "Investment contribution"; else -> "Investment withdrawal" }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Add $typeLabel") }, text = { Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        var typeExpanded by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(expanded = typeExpanded, onExpandedChange = { typeExpanded = !typeExpanded }) { OutlinedTextField(typeLabel, {}, Modifier.menuAnchor().fillMaxWidth(), readOnly = true, label = { Text("Type") }); ExposedDropdownMenu(typeExpanded, { typeExpanded = false }) { listOf(TxType.EXPENSE, TxType.INCOME, TxType.INVESTMENT_CONTRIBUTION, TxType.INVESTMENT_WITHDRAWAL).forEach { option -> val label = when (option) { TxType.INCOME -> "Income"; TxType.EXPENSE -> "Expense"; TxType.INVESTMENT_CONTRIBUTION -> "Investment contribution"; else -> "Investment withdrawal" }; DropdownMenuItem(text = { Text(label) }, onClick = { type = option; typeExpanded = false }) } } }
        OutlinedTextField(title, { title = it }, label = { Text("Title") }, singleLine = true)
        OutlinedTextField(amount, { amount = it }, label = { Text("Amount (₹)") }, singleLine = true)
        var expanded by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) { OutlinedTextField(category, {}, Modifier.menuAnchor().fillMaxWidth(), readOnly = true, label = { Text("Category") }); ExposedDropdownMenu(expanded, { expanded = false }) { categories.forEach { DropdownMenuItem(text = { Text(it) }, onClick = { category = it; expanded = false }) } } }
        var accountExpanded by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(expanded = accountExpanded, onExpandedChange = { accountExpanded = !accountExpanded }) { OutlinedTextField(accounts.firstOrNull { it.first == account }?.second ?: account, {}, Modifier.menuAnchor().fillMaxWidth(), readOnly = true, label = { Text("Account") }); ExposedDropdownMenu(accountExpanded, { accountExpanded = false }) { accounts.forEach { (id, name) -> DropdownMenuItem(text = { Text(name) }, onClick = { account = id; accountExpanded = false }) } } }
    } }, confirmButton = { Button(enabled = title.isNotBlank() && amount.toBigDecimalOrNull()?.let { it > java.math.BigDecimal.ZERO } == true && accounts.isNotEmpty(), onClick = { onSave(type, account, title, amount, category) }) { Text("Save") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}
@Composable private fun ReconciliationDialog(accounts: List<BankAccount>, transactions: List<FinanceTransaction>, onDismiss: () -> Unit, onSave: (String, String, String) -> Unit) {
    var accountId by remember { mutableStateOf(accounts.firstOrNull()?.id ?: "") }
    var actual by remember { mutableStateOf("") }
    var reason by remember { mutableStateOf("") }
    val account = accounts.firstOrNull { it.id == accountId }
    val expected = account?.let { selected ->
        com.myfinance.app.domain.LedgerEngine.bankBalance(selected.openingBalancePaise, transactions.filter { it.accountId == selected.id })
    }
    val parsedActual = actual.toBigDecimalOrNull()?.let { value -> runCatching { value.movePointRight(2).longValueExact() }.getOrNull() }
    val difference = if (expected != null && parsedActual != null && parsedActual >= 0L) runCatching { Math.subtractExact(parsedActual, expected) }.getOrNull() else null
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Reconcile bank balance") }, text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SelectField("Account", accountId, accounts.map { it.id to it.name }) { accountId = it }
        if (expected != null) Text("Calculated balance: ${money(expected)}", color = Muted, style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(actual, { actual = it }, label = { Text("Actual balance (₹)") }, singleLine = true)
        if (difference != null) Text("Adjustment: ${money(difference)}", color = if (difference >= 0L) Color(0xFF17865D) else Color(0xFFC04B4B), style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(reason, { reason = it }, label = { Text("Explanation (required)") }, minLines = 2)
    } }, confirmButton = { Button(enabled = account != null && reason.isNotBlank() && parsedActual != null && parsedActual >= 0L, onClick = { onSave(accountId, actual, reason) }) { Text("Save adjustment") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

private fun money(paise: Long): String {
    val formatter = java.text.NumberFormat.getNumberInstance(java.util.Locale.forLanguageTag("en-IN"))
    formatter.minimumFractionDigits = 2
    formatter.maximumFractionDigits = 2
    return "₹" + formatter.format(java.math.BigDecimal.valueOf(paise, 2))
}

@Composable private fun EditTransactionDialog(tx: FinanceTransaction, onDismiss: () -> Unit, onSave: (String, String, String) -> Unit) {
    var title by remember(tx.id) { mutableStateOf(tx.title) }
    var amount by remember(tx.id) { mutableStateOf(java.math.BigDecimal.valueOf(tx.amountPaise, 2).toPlainString()) }
    var category by remember(tx.id) { mutableStateOf(tx.category) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (tx.linkId == null) "Edit transaction" else "Edit linked transfer") }, text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(title, { title = it }, label = { Text("Title") }, singleLine = true)
        OutlinedTextField(amount, { amount = it }, label = { Text("Amount (₹)") }, singleLine = true)
        if (tx.linkId == null) OutlinedTextField(category, { category = it }, label = { Text("Category") }, singleLine = true)
        if (tx.linkId != null) Text("Both linked entries will be updated together.", color = Muted, style = MaterialTheme.typography.bodySmall)
    } }, confirmButton = { Button(enabled = title.isNotBlank() && (tx.linkId != null || category.isNotBlank()) && amount.toBigDecimalOrNull()?.let { it > java.math.BigDecimal.ZERO } == true, onClick = { onSave(title, amount, category) }) { Text("Save") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}
