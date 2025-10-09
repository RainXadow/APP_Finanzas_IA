package com.example.app_finanzas_ia

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

/**
 * Clase para almacenar y recuperar transacciones
 * Usa archivos JSON para simplicidad (se puede migrar a Room Database)
 */
class TransactionStorage(private val context: Context) {

    private val gson = Gson()
    private val fileName = "transactions.json"

    fun saveTransaction(transaction: Transaction) {
        val transactions = getAllTransactions().toMutableList()
        transactions.add(transaction)
        saveAllTransactions(transactions)
    }

    fun getAllTransactions(): List<Transaction> {
        val file = File(context.filesDir, fileName)

        if (!file.exists()) {
            return emptyList()
        }

        return try {
            val json = file.readText()
            val type = object : TypeToken<List<Transaction>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun deleteTransaction(transactionId: Long) {
        val transactions = getAllTransactions()
            .filter { it.id != transactionId }
        saveAllTransactions(transactions)
    }

    fun updateTransaction(transaction: Transaction) {
        val transactions = getAllTransactions().toMutableList()
        val index = transactions.indexOfFirst { it.id == transaction.id }

        if (index != -1) {
            transactions[index] = transaction
            saveAllTransactions(transactions)
        }
    }

    fun getTransactionsByDateRange(startDate: Long, endDate: Long): List<Transaction> {
        return getAllTransactions().filter {
            it.date.time in startDate..endDate
        }
    }

    fun getTransactionsByCategory(category: String): List<Transaction> {
        return getAllTransactions().filter {
            it.category == category
        }
    }

    fun clearAllTransactions() {
        saveAllTransactions(emptyList())
    }

    private fun saveAllTransactions(transactions: List<Transaction>) {
        val file = File(context.filesDir, fileName)
        val json = gson.toJson(transactions)
        file.writeText(json)
    }

    fun getStatistics(): TransactionStatistics {
        val transactions = getAllTransactions()
        val expenses = transactions.filter { it.type == TransactionType.EXPENSE }
        val income = transactions.filter { it.type == TransactionType.INCOME }

        return TransactionStatistics(
            totalExpenses = expenses.sumOf { kotlin.math.abs(it.amount) },
            totalIncome = income.sumOf { it.amount },
            transactionCount = transactions.size,
            expenseCount = expenses.size,
            incomeCount = income.size,
            categoriesBreakdown = transactions
                .groupBy { it.category }
                .mapValues { it.value.sumOf { t -> kotlin.math.abs(t.amount) } }
        )
    }
}

data class TransactionStatistics(
    val totalExpenses: Double,
    val totalIncome: Double,
    val transactionCount: Int,
    val expenseCount: Int,
    val incomeCount: Int,
    val categoriesBreakdown: Map<String, Double>
)