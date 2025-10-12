package com.example.app_finanzas_ia

import java.util.Date

/**
 * Modelo de datos para una transacción financiera
 */
data class Transaction(
    val id: Long = System.currentTimeMillis(),
    val date: Date,
    val amount: Double,
    val concept: String,
    val category: String = "Sin categoría",
    val source: String = "PDF Santander",
    val type: TransactionType,
    val balance: Double = 0.0, // Saldo después de la transacción
    val originalText: String = "",
    val isManual: Boolean = false
) {
    /**
     * Genera una clave única para detectar duplicados
     */
    fun getUniqueKey(): String {
        val dateStr = date.time.toString()
        val amountStr = String.format("%.2f", amount)
        return "$dateStr-${concept.take(30)}-$amountStr"
    }
}

enum class TransactionType {
    INCOME,     // Ingreso
    EXPENSE,    // Gasto
    UNKNOWN     // Desconocido
}

/**
 * Categoría personalizada por el usuario
 */
data class Category(
    val id: Long = System.currentTimeMillis(),
    val name: String,
    val keywords: MutableSet<String> = mutableSetOf(), // Palabras clave asociadas
    val isDefault: Boolean = false
)

/**
 * Regla de categorización automática
 */
data class CategorizationRule(
    val concept: String, // Concepto normalizado
    val categoryName: String,
    val lastUsed: Date = Date()
)

/**
 * Clase para almacenar configuración de exportación Excel
 */
data class ExcelConfig(
    val fileName: String = "finanzas.xlsx",
    val sheetName: String = "Transacciones",
    val includeHeaders: Boolean = true,
    val dateFormat: String = "dd/MM/yyyy",
    val autoExport: Boolean = false
)

/**
 * Clase para el resultado del procesamiento de archivos (Excel, CSV, PDF)
 */
data class FileProcessResult(
    val success: Boolean,
    val transactions: List<Transaction> = emptyList(),
    val duplicatesFound: Int = 0,
    val newTransactions: Int = 0,
    val errorMessage: String? = null
)

// Alias para mantener compatibilidad
typealias PDFProcessResult = FileProcessResult
typealias ExcelProcessResult = FileProcessResult