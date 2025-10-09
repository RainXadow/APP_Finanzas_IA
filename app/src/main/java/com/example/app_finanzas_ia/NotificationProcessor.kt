package com.example.app_finanzas_ia

import android.content.Context
import java.util.*

class NotificationProcessor(private val context: Context) {

    // Patrones de expresiones regulares para extraer información
    private val amountPatterns = listOf(
        Regex("""(\d{1,3}(?:[.,]\d{3})*[.,]\d{2})\s*€"""),  // 1.234,56 €
        Regex("""€\s*(\d{1,3}(?:[.,]\d{3})*[.,]\d{2})"""),  // € 1.234,56
        Regex("""(\d+[.,]\d{2})\s*EUR"""),                   // 123,45 EUR
        Regex("""(\d+[.,]\d{2})\s*euros?"""),                // 123,45 euros
    )

    private val chargeKeywords = listOf(
        "cargo", "pago", "compra", "débito", "retirada",
        "domiciliación", "transferencia enviada"
    )

    private val incomeKeywords = listOf(
        "abono", "ingreso", "transferencia recibida", "devolución"
    )

    fun processNotification(
        notificationText: String,
        packageName: String
    ): NotificationProcessResult {

        try {
            // Extraer cantidad
            val amount = extractAmount(notificationText)
            if (amount == null) {
                return NotificationProcessResult(
                    success = false,
                    transaction = null,
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
                packageName.contains("google") -> "Google Pay"
                packageName.contains("bbva") -> "BBVA"
                else -> "Desconocido"
            }

            // Categorizar automáticamente
            val category = categorizeTransaction(concept, notificationText)

            // Crear transacción
            val transaction = Transaction(
                date = Date(),
                amount = if (type == TransactionType.EXPENSE) -amount else amount,
                concept = concept,
                category = category,
                source = source,
                type = type,
                originalNotificationText = notificationText,
                isManual = false
            )

            return NotificationProcessResult(
                success = true,
                transaction = transaction
            )

        } catch (e: Exception) {
            return NotificationProcessResult(
                success = false,
                transaction = null,
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
            if (line.contains(" de ", ignoreCase = true)) {
                val parts = line.split(" de ", ignoreCase = true)
                if (parts.size > 1) {
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

    private fun categorizeTransaction(concept: String, fullText: String): String {
        val lowerConcept = concept.lowercase()
        val lowerText = fullText.lowercase()

        return when {
            // Alimentación
            lowerConcept.contains("mercadona") ||
                    lowerConcept.contains("carrefour") ||
                    lowerConcept.contains("supermercado") ||
                    lowerConcept.contains("lidl") ||
                    lowerConcept.contains("aldi") -> "Alimentación"

            // Restaurantes
            lowerConcept.contains("restaurante") ||
                    lowerConcept.contains("bar") ||
                    lowerText.contains("comida") -> "Restaurantes"

            // Transporte
            lowerConcept.contains("gasolina") ||
                    lowerConcept.contains("repsol") ||
                    lowerConcept.contains("cepsa") ||
                    lowerConcept.contains("uber") ||
                    lowerConcept.contains("cabify") ||
                    lowerConcept.contains("renfe") -> "Transporte"

            // Ocio
            lowerConcept.contains("cine") ||
                    lowerConcept.contains("spotify") ||
                    lowerConcept.contains("netflix") ||
                    lowerConcept.contains("amazon") -> "Ocio"

            // Servicios
            lowerText.contains("recibo") ||
                    lowerText.contains("domiciliación") -> "Servicios"

            // Transferencias
            lowerText.contains("transferencia") -> "Transferencias"

            else -> "Otros"
        }
    }
}