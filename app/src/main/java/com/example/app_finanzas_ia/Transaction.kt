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
    val source: String, // "Santander", "Google Pay", etc.
    val type: TransactionType,
    val originalNotificationText: String,
    val isManual: Boolean = false
)

enum class TransactionType {
    INCOME,     // Ingreso
    EXPENSE,    // Gasto
    UNKNOWN     // Desconocido
}

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
 * Clase para el resultado del procesamiento de notificación
 */
data class NotificationProcessResult(
    val success: Boolean,
    val transaction: Transaction?,
    val errorMessage: String? = null
)