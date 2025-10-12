package com.example.app_finanzas_ia

import android.content.Context
import java.util.*

/**
 * NOTA: Esta clase ya no se usa en la versión actual (basada en PDFs)
 * Se mantiene por compatibilidad pero puede ser eliminada
 */
class NotificationProcessor(private val context: Context) {

    // Patrones de expresiones regulares para extraer información
    private val amountPatterns = listOf(
        Regex("""(\d{1,3}(?:[.,]\d{3})*[.,]\d{2})\s*€"""),          // 1.234,56 €
        Regex("""€\s*(\d{1,3}(?:[.,]\d{3})*[.,]\d{2})"""),          // € 1.234,56
        Regex("""(\d+[.,]\d{2})\s*EUR""", RegexOption.IGNORE_CASE), // 123,45 EUR
        Regex("""(\d+[.,]\d{2})\s*euros?""", RegexOption.IGNORE_CASE), // 123,45 euros
        Regex("""(\d+[.,]\d{2})EUR""", RegexOption.IGNORE_CASE),    // 11,49EUR (sin espacio)
        Regex("""BIZUM de (\d+[.,]\d{2}) EUR""", RegexOption.IGNORE_CASE), // Bizum Santander
        Regex("""de efectivo de (\d+[.,]\d{2})EUR""", RegexOption.IGNORE_CASE), // Retiradas cajero
        Regex("""ingreso de efectivo de (\d+[.,]\d{2})EUR""", RegexOption.IGNORE_CASE), // Ingresos cajero
    )

    private val chargeKeywords = listOf(
        "cargo", "pago", "compra", "débito", "retirada",
        "domiciliación", "transferencia enviada", "con mastercard",
        "con visa", "retirada de efectivo"
    )

    private val incomeKeywords = listOf(
        "abono", "ingreso", "transferencia recibida", "devolución",
        "has recibido", "ingreso de efectivo", "bizum"
    )

    fun processNotification(
        notificationText: String,
        packageName: String
    ): PDFProcessResult {

        try {
            // Extraer cantidad
            val amount = extractAmount(notificationText)
            if (amount == null) {
                return PDFProcessResult(
                    success = false,
                    transactions = emptyList(),
                    errorMessage = "No se pudo extraer la cantidad"
                )
            }

            // Determinar tipo de transacción
            val type = determineTransactionType(notificationText)

            // Extraer concepto
            val concept = extractConcept(notificationText)

            // Determinar fuente
            val source = when {
                packageName.contains("santander") -> "Santander"
                packageName.contains("google") || packageName.contains("wallet") -> "Google Wallet"
                packageName.contains("bbva") -> "BBVA"
                else -> "Desconocido"
            }

            // Crear transacción
            val transaction = Transaction(
                date = Date(),
                amount = if (type == TransactionType.EXPENSE) -amount else amount,
                concept = concept,
                category = "Sin categoría",
                source = source,
                type = type,
                originalText = notificationText,
                isManual = false
            )

            return PDFProcessResult(
                success = true,
                transactions = listOf(transaction),
                newTransactions = 1
            )

        } catch (e: Exception) {
            return PDFProcessResult(
                success = false,
                transactions = emptyList(),
                errorMessage = e.message
            )
        }
    }

    private fun extractAmount(text: String): Double? {
        for (pattern in amountPatterns) {
            val match = pattern.find(text)
            if (match != null) {
                val amountStr = match.groupValues[1]
                    .replace(".", "")
                    .replace(",", ".")
                return amountStr.toDoubleOrNull()
            }
        }
        return null
    }

    private fun determineTransactionType(text: String): TransactionType {
        val lowerText = text.lowercase()

        // Verificar palabras clave de cargo
        if (chargeKeywords.any { lowerText.contains(it) }) {
            return TransactionType.EXPENSE
        }

        // Verificar palabras clave de ingreso
        if (incomeKeywords.any { lowerText.contains(it) }) {
            return TransactionType.INCOME
        }

        return TransactionType.UNKNOWN
    }

    private fun extractConcept(text: String): String {
        val lowerText = text.lowercase()

        // Caso especial: Google Wallet / Google Pay
        if (lowerText.contains("con mastercard") || lowerText.contains("con visa")) {
            val lines = text.split("\n")
            if (lines.isNotEmpty()) {
                for (line in lines) {
                    if (!line.contains("€") && !line.contains("EUR") &&
                        !line.contains("mastercard", ignoreCase = true) &&
                        !line.contains("visa", ignoreCase = true) &&
                        line.trim().isNotEmpty()) {
                        return line.trim().take(50)
                    }
                }
            }
            return "Compra con tarjeta"
        }

        // Caso especial: BIZUM
        if (lowerText.contains("bizum")) {
            val match = Regex("""de ([A-Z\s.]+) por Pagos""", RegexOption.IGNORE_CASE).find(text)
            if (match != null) {
                return "Bizum de ${match.groupValues[1].trim()}"
            }
            return "Bizum"
        }

        // Caso especial: Cajero automático
        if (lowerText.contains("cajero")) {
            val match = Regex("""cajero (\d+\.\d+\.\d+\.\d+)""", RegexOption.IGNORE_CASE).find(text)
            if (match != null) {
                return "Cajero ${match.groupValues[1]}"
            }
            if (lowerText.contains("ingreso")) {
                return "Ingreso en cajero"
            }
            return "Retirada en cajero"
        }

        // Intentar extraer el concepto de la notificación
        val lines = text.split("\n")

        // Buscar línea con "en " o "de "
        for (line in lines) {
            if (line.contains(" en ", ignoreCase = true)) {
                val parts = line.split(" en ", ignoreCase = true)
                if (parts.size > 1) {
                    return parts[1].trim().take(50)
                }
            }
            if (line.contains(" de ", ignoreCase = true) && !line.contains("€") && !line.contains("EUR")) {
                val parts = line.split(" de ", ignoreCase = true)
                if (parts.size > 1 && !parts[1].trim().matches(Regex("""\d+[.,]\d+.*"""))) {
                    return parts[1].trim().take(50)
                }
            }
        }

        // Si no se encuentra, usar la segunda línea o primera línea
        return if (lines.size > 1) {
            lines[1].trim().take(50)
        } else {
            lines[0].trim().take(50)
        }
    }
}