# Accounting model

All amounts are positive integer paise in transaction rows; transaction type determines the signed effect.

| Type | Physical bank | Wallet |
|---|---:|---:|
| Income | + | 0 |
| Expense | - | 0 |
| Transfer in/out | + / - | 0 |
| Investment withdrawal/contribution | + / - | 0 |
| Wallet fund | 0 | + |
| Wallet spend | - | - |
| Wallet transfer in/out | 0 | + / - |
| Adjustment in/out | + / - | 0 |

A wallet spend reduces both the wallet's allocation and the actual Kotak balance. Moving an allocation from unallocated Kotak cash into a wallet does not change the physical bank balance. Moving between two Kotak wallets creates linked opposite wallet entries and has zero physical bank impact. Bank-to-bank transfers are linked opposite entries and are excluded from consolidated income and consumption expense.

The ledger engine is the single calculation source for bank and wallet balances. Investment contributions are not classified as consumption expenses.
