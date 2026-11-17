package com.myfinance.app

import android.app.Application
import com.myfinance.app.data.FinanceDatabase
import com.myfinance.app.data.FinanceRepository

class MyFinanceApp : Application() {
    lateinit var repository: FinanceRepository
        private set
    override fun onCreate() {
        super.onCreate()
        val db = FinanceDatabase.get(this)
        repository = FinanceRepository(db, this)
    }
}
