package com.example.app_finanzas_ia

import android.content.Context
import android.net.Uri
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.text.PDFTextStripper
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.*

class PDFProcessor(private val context: Context) {

    private val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale("es", "ES"))

    fun processPDF(uri: Uri): PDFProcessResult {
        try {
            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            if (inputStream == null) {
                return PDFProcessResult(
                    success = false,
                    errorMessage = "No se pudo abrir el PDF"
                )
            }

            val document = PDDocument.load(inputStream)
            val stripper = PDFTextStripper()
            val text = stripper.getText(document)
            document.close()
            inputStream.close()

            // Parsear las transacciones del texto extraído
            val transactions = parseTransactions(text)

            return PDFProcessResult(
                success = true,
                transactions = transactions,
                newTransactions = transactions.size
            )

        } catch (e: Exception) {
            return PDFProcessResult(
                success = false,
                errorMessage = "Error al procesar PDF: ${e.message}"
            )
        }
    }

    private fun parseTransactions(text: String): List<Transaction> {
        val transactions = mutableListOf<Transaction>()
        val lines = text.split("\n")

        var i = 0
        while (i < lines.size) {
            val line = lines[i].trim()

            // Buscar líneas que empiezan con fecha (formato: "29 ago 2025")
            if (line.matches(Regex("""^\d{1,2}\s+\w{3}\s+\d{4}.*"""))) {
                try {
                    val transaction = parseTransactionLine(line, lines, i)
                    if (transaction != null) {
                        transactions.add(transaction)
                    }
                } catch (e: Exception) {
                    // Ignorar líneas que no se puedan parsear
                }
            }
            i++
        }

        return transactions
    }

    private fun parseTransactionLine(line: String, allLines: List<String>, currentIndex: Int): Transaction? {
        try {
            // Extraer fecha (primeros 15 caracteres aprox)
            val datePart = line.substring(0, minOf(15, line.length)).trim()
            val date = parseDateFromLine(datePart) ?: return null

            // Buscar la línea de operación (siguiente línea que no sea "F. valor:")
            var operationLine = ""
            var nextIndex = currentIndex + 1

            while (nextIndex < allLines.size) {
                val nextLine = allLines[nextIndex].trim()
                if (nextLine.startsWith("F. valor:")) {
                    nextIndex++
                    continue
                }
                if (nextLine.isNotEmpty() && !nextLine.matches(Regex("""^\d{1,2}\s+\w{3}\s+\d{4}.*"""))) {
                    operationLine = nextLine
                    break
                }
                nextIndex++
            }

            if (operationLine.isEmpty()) return null

            // Extraer importe y saldo del final de la línea original
            val importeMatch = Regex("""(-?\d+(?:[.,]\d+)*,\d{2})€\s+(-?\d+(?:[.,]\d+)*,\d{2})€""").findAll(line).lastOrNull()
            if (importeMatch == null) return null

            val groups = importeMatch.groupValues
            val importe = parseAmount(groups[1])
            val saldo = parseAmount(groups[2])

            // Determinar tipo de transacción
            val type = if (importe < 0) TransactionType.EXPENSE else TransactionType.INCOME

            // Extraer concepto
            val concept = extractConcept(operationLine)

            return Transaction(
                date = date,
                amount = importe,
                concept = concept,
                category = "Sin categoría", // Se categorizará después
                balance = saldo,
                type = type,
                originalText = "$line\n$operationLine"
            )

        } catch (e: Exception) {
            return null
        }
    }

    private fun parseDateFromLine(datePart: String): Date? {
        return try {
            // Formato: "29 ago 2025"
            val parts = datePart.split(Regex("""\s+"""))
            if (parts.size >= 3) {
                val dateStr = "${parts[0]} ${parts[1]} ${parts[2]}"
                dateFormat.parse(dateStr)
            } else null
        } catch (e: Exception) {
            null
        }
    }

    private fun parseAmount(amountStr: String): Double {
        return try {
            amountStr
                .replace(".", "")  // Eliminar separadores de miles
                .replace(",", ".") // Convertir coma decimal a punto
                .toDouble()
        } catch (e: Exception) {
            0.0
        }
    }

    private fun extractConcept(operationLine: String): String {
        // Limpiar el concepto
        var concept = operationLine.trim()

        // Patrones comunes a eliminar
        val patterns = listOf(
            Regex(""", Tarjeta \d+.*"""),
            Regex(""", Comision \d+,\d+"""),
            Regex("""Tarj\. :\*\d+"""),
            Regex("""Nº Recibo \d+.*"""),
            Regex("""Ref\. Mandato \d+.*""")
        )

        for (pattern in patterns) {
            concept = pattern.replace(concept, "").trim()
        }

        // Acortar si es muy largo
        if (concept.length > 50) {
            concept = concept.substring(0, 50)
        }

        return concept
    }
}