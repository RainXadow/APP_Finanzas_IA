package com.example.app_finanzas_ia

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

/**
 * Clase para almacenar y recuperar transacciones con detección de duplicados
 */
class TransactionStorage(private val context: Context) {

    private val gson = Gson()
    private val fileName = "transactions.json"

    /**
     * Guarda transacciones evitando duplicados
     */
    fun saveTransactions(newTransactions: List<Transaction>): Pair<Int, Int> {
        val existingTransactions = getAllTransactions().toMutableList()
        val existingKeys = existingTransactions.map { it.getUniqueKey() }.toSet()

        var addedCount = 0
        var duplicateCount = 0

        for (transaction in newTransactions) {
            if (!existingKeys.contains(transaction.getUniqueKey())) {
                existingTransactions.add(transaction)
                addedCount++
            } else {
                duplicateCount++
            }
        }

        saveAllTransactions(existingTransactions)
        return Pair(addedCount, duplicateCount)
    }

    fun saveTransaction(transaction: Transaction) {
        val transactions = getAllTransactions().toMutableList()
        val existingKeys = transactions.map { it.getUniqueKey() }

        if (!existingKeys.contains(transaction.getUniqueKey())) {
            transactions.add(transaction)
            saveAllTransactions(transactions)
        }
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

    /**
     * Actualiza la categoría de una transacción
     */
    fun updateTransactionCategory(transactionId: Long, newCategory: String) {
        val transactions = getAllTransactions().toMutableList()
        val index = transactions.indexOfFirst { it.id == transactionId }

        if (index != -1) {
            val transaction = transactions[index]
            transactions[index] = transaction.copy(category = newCategory)
            saveAllTransactions(transactions)
        }
    }

    /**
     * Obtiene transacciones sin categorizar
     */
    fun getUncategorizedTransactions(): List<Transaction> {
        return getAllTransactions().filter {
            it.category == "Sin categoría" || it.category.isEmpty()
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
            totalIncome = income.sumOf { kotlin.math.abs(it.amount) },
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