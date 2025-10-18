package com.example.app_finanzas_ia

import android.content.Context
import android.net.Uri
import com.opencsv.CSVReader
import org.apache.poi.ss.usermodel.DateUtil
import org.apache.poi.ss.usermodel.WorkbookFactory
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.*

class ExcelProcessor(private val context: Context) {

    private val dateFormats = listOf(
        SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()),
        SimpleDateFormat("dd/MM/yy", Locale.getDefault()),
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()),
        SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()),
        SimpleDateFormat("dd MMM yyyy", Locale("es", "ES")),
        SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
    )

    fun processExcel(uri: Uri): ExcelProcessResult {
        return try {
            val mimeType = context.contentResolver.getType(uri)
            val fileName = getFileName(uri)

            when {
                fileName?.endsWith(".csv", ignoreCase = true) == true ||
                        mimeType == "text/csv" -> processCSV(uri)

                fileName?.endsWith(".xlsx", ignoreCase = true) == true ||
                        fileName?.endsWith(".xls", ignoreCase = true) == true ||
                        mimeType == "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" ||
                        mimeType == "application/vnd.ms-excel" -> processXLSX(uri)

                else -> ExcelProcessResult(
                    success = false,
                    errorMessage = "Formato no soportado. Usa .xlsx, .xls o .csv"
                )
            }
        } catch (e: Exception) {
            ExcelProcessResult(
                success = false,
                errorMessage = "Error al procesar archivo: ${e.message}"
            )
        }
    }

    private fun processXLSX(uri: Uri): ExcelProcessResult {
        val transactions = mutableListOf<Transaction>()

        try {
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: return ExcelProcessResult(success = false, errorMessage = "No se pudo abrir el archivo")

            val workbook = WorkbookFactory.create(inputStream)
            val sheet = workbook.getSheetAt(0) // Primera hoja

            // Buscar la fila de encabezados
            val headerRow = findHeaderRow(sheet) ?: return ExcelProcessResult(
                success = false,
                errorMessage = "No se encontraron los encabezados en el archivo"
            )

            // Mapear columnas
            val columnMapping = mapColumns(headerRow)

            if (columnMapping.isEmpty()) {
                return ExcelProcessResult(
                    success = false,
                    errorMessage = "No se encontraron las columnas necesarias (Fecha, Concepto, Importe)"
                )
            }

            // Procesar filas de datos a partir de la fila siguiente a los encabezados
            val startRow = headerRow.rowNum + 1
            for (rowIndex in startRow..sheet.lastRowNum) {
                val row = sheet.getRow(rowIndex) ?: continue

                try {
                    val transaction = parseRow(row, columnMapping)
                    if (transaction != null) {
                        transactions.add(transaction)
                    }
                } catch (e: Exception) {
                    continue
                }
            }

            workbook.close()
            inputStream.close()

            return ExcelProcessResult(
                success = true,
                transactions = transactions,
                newTransactions = transactions.size
            )

        } catch (e: Exception) {
            return ExcelProcessResult(
                success = false,
                errorMessage = "Error al leer Excel: ${e.message}"
            )
        }
    }

    private fun findHeaderRow(sheet: org.apache.poi.ss.usermodel.Sheet): org.apache.poi.ss.usermodel.Row? {
        for (rowIndex in 0..sheet.lastRowNum) {
            val row = sheet.getRow(rowIndex) ?: continue
            for (cellIndex in 0 until row.lastCellNum) {
                val cell = row.getCell(cellIndex) ?: continue
                val header = cell.toString().trim().lowercase()
                if (header.contains("fecha operación") || header.contains("concepto") || header.contains("importe")) {
                    android.util.Log.d("ExcelMap", "Encabezado encontrado en fila $rowIndex")
                    return row
                }
            }
        }
        return null
    }

    private fun processCSV(uri: Uri): ExcelProcessResult {
        val transactions = mutableListOf<Transaction>()

        try {
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: return ExcelProcessResult(success = false, errorMessage = "No se pudo abrir el archivo")

            val reader = CSVReader(InputStreamReader(inputStream))
            val allRows = reader.readAll()

            if (allRows.isEmpty()) {
                return ExcelProcessResult(success = false, errorMessage = "El archivo CSV está vacío")
            }

            // Buscar la fila de encabezados
            var headerIndex = -1
            var headers: Array<String>? = null
            for (i in allRows.indices) {
                val row = allRows[i]
                if (row.any { it.trim().lowercase().contains("fecha operación") || it.trim().lowercase().contains("concepto") || it.trim().lowercase().contains("importe") }) {
                    headerIndex = i
                    headers = row
                    break
                }
            }

            if (headers == null) {
                return ExcelProcessResult(
                    success = false,
                    errorMessage = "No se encontraron las columnas necesarias en el CSV"
                )
            }

            val columnMapping = mapCSVColumns(headers)

            if (columnMapping.isEmpty()) {
                return ExcelProcessResult(
                    success = false,
                    errorMessage = "No se encontraron las columnas necesarias en el CSV"
                )
            }

            // Procesar filas de datos a partir de la fila siguiente a los encabezados
            for (rowIndex in headerIndex + 1 until allRows.size) {
                val row = allRows[rowIndex]

                try {
                    val transaction = parseCSVRow(row, columnMapping)
                    if (transaction != null) {
                        transactions.add(transaction)
                    }
                } catch (e: Exception) {
                    continue
                }
            }

            reader.close()
            inputStream.close()

            return ExcelProcessResult(
                success = true,
                transactions = transactions,
                newTransactions = transactions.size
            )

        } catch (e: Exception) {
            return ExcelProcessResult(
                success = false,
                errorMessage = "Error al leer CSV: ${e.message}"
            )
        }
    }

    private fun mapColumns(headerRow: org.apache.poi.ss.usermodel.Row): Map<String, Int> {
        val mapping = mutableMapOf<String, Int>()

        for (cellIndex in 0 until headerRow.lastCellNum) {
            val cell = headerRow.getCell(cellIndex) ?: continue
            val header = cell.toString().trim().lowercase()
            android.util.Log.d("ExcelMap", "Columna $cellIndex: '$header'")

            when {
                header.contains("fecha operación") -> {
                    mapping["fecha"] = cellIndex
                    android.util.Log.d("ExcelMap", "Mapeado 'fecha' en columna $cellIndex")
                }
                header.contains("concepto") || header.contains("descripcion") ||
                        header.contains("operación") -> {
                    mapping["concepto"] = cellIndex
                    android.util.Log.d("ExcelMap", "Mapeado 'concepto' en columna $cellIndex")
                }
                header.contains("importe") || header.contains("cantidad") ||
                        header.contains("monto") -> {
                    mapping["importe"] = cellIndex
                    android.util.Log.d("ExcelMap", "Mapeado 'importe' en columna $cellIndex")
                }
                header.contains("saldo") -> {
                    mapping["saldo"] = cellIndex
                    android.util.Log.d("ExcelMap", "Mapeado 'saldo' en columna $cellIndex")
                }
                header.contains("tipo") -> {
                    mapping["tipo"] = cellIndex
                    android.util.Log.d("ExcelMap", "Mapeado 'tipo' en columna $cellIndex")
                }
                header.contains("categoria") || header.contains("categoría") -> {
                    mapping["categoria"] = cellIndex
                    android.util.Log.d("ExcelMap", "Mapeado 'categoria' en columna $cellIndex")
                }
            }
        }

        android.util.Log.d("ExcelMap", "Resultado del mapeo: $mapping")

        // Verificar que al menos tenemos fecha, concepto e importe
        return if (mapping.containsKey("fecha") &&
            mapping.containsKey("concepto") &&
            mapping.containsKey("importe")) {
            mapping
        } else {
            android.util.Log.d("ExcelMap", "No se encontraron las columnas necesarias (fecha, concepto, importe)")
            emptyMap()
        }
    }

    private fun mapCSVColumns(headers: Array<String>): Map<String, Int> {
        val mapping = mutableMapOf<String, Int>()

        headers.forEachIndexed { index, header ->
            val headerLower = header.trim().lowercase()

            when {
                headerLower.contains("Fecha operación") -> mapping["fecha"] = index
                headerLower.contains("Concepto") || headerLower.contains("descripcion") ||
                        headerLower.contains("Operación") -> mapping["concepto"] = index
                headerLower.contains("Importe") || headerLower.contains("cantidad") ||
                        headerLower.contains("monto") -> mapping["importe"] = index
                headerLower.contains("Saldo") -> mapping["saldo"] = index
                headerLower.contains("tipo") -> mapping["tipo"] = index
                headerLower.contains("categoria") || headerLower.contains("categoría") -> mapping["categoria"] = index
            }
        }

        return if (mapping.containsKey("fecha") &&
            mapping.containsKey("concepto") &&
            mapping.containsKey("importe")) {
            mapping
        } else {
            emptyMap()
        }
    }

    private fun parseRow(
        row: org.apache.poi.ss.usermodel.Row,
        columnMapping: Map<String, Int>
    ): Transaction? {
        try {
            // Fecha
            val dateCell = row.getCell(columnMapping["fecha"]!!)
            val date = when {
                dateCell == null -> return null
                dateCell.cellType == org.apache.poi.ss.usermodel.CellType.NUMERIC &&
                        DateUtil.isCellDateFormatted(dateCell) -> dateCell.dateCellValue
                else -> parseDate(dateCell.toString())
            } ?: return null

            // Concepto
            val conceptCell = row.getCell(columnMapping["concepto"]!!)
            val concept = conceptCell?.toString()?.trim() ?: return null
            if (concept.isEmpty()) return null

            // Importe
            val amountCell = row.getCell(columnMapping["importe"]!!)
            val amount = when {
                amountCell == null -> return null
                amountCell.cellType == org.apache.poi.ss.usermodel.CellType.NUMERIC ->
                    amountCell.numericCellValue
                else -> parseAmount(amountCell.toString())
            } ?: return null

            // Saldo (opcional)
            val balance = columnMapping["saldo"]?.let { index ->
                val balanceCell = row.getCell(index)
                when {
                    balanceCell == null -> 0.0
                    balanceCell.cellType == org.apache.poi.ss.usermodel.CellType.NUMERIC ->
                        balanceCell.numericCellValue
                    else -> parseAmount(balanceCell.toString()) ?: 0.0
                }
            } ?: 0.0

            // Determinar tipo
            val type = if (amount < 0) TransactionType.EXPENSE else TransactionType.INCOME

            return Transaction(
                date = date,
                amount = amount,
                concept = concept,
                category = "Sin categoría",
                balance = balance,
                type = type,
                originalText = "Excel: $concept"
            )

        } catch (e: Exception) {
            return null
        }
    }

    private fun parseCSVRow(
        row: Array<String>,
        columnMapping: Map<String, Int>
    ): Transaction? {
        try {
            // Fecha
            val dateStr = row.getOrNull(columnMapping["fecha"]!!)?.trim() ?: return null
            val date = parseDate(dateStr) ?: return null

            // Concepto
            val concept = row.getOrNull(columnMapping["concepto"]!!)?.trim() ?: return null
            if (concept.isEmpty()) return null

            // Importe
            val amountStr = row.getOrNull(columnMapping["importe"]!!)?.trim() ?: return null
            val amount = parseAmount(amountStr) ?: return null

            // Saldo (opcional)
            val balance = columnMapping["saldo"]?.let { index ->
                row.getOrNull(index)?.let { parseAmount(it) } ?: 0.0
            } ?: 0.0

            // Determinar tipo
            val type = if (amount < 0) TransactionType.EXPENSE else TransactionType.INCOME

            return Transaction(
                date = date,
                amount = amount,
                concept = concept,
                category = "Sin categoría",
                balance = balance,
                type = type,
                originalText = "CSV: $concept"
            )

        } catch (e: Exception) {
            return null
        }
    }

    private fun parseDate(dateStr: String): Date? {
        for (format in dateFormats) {
            try {
                return format.parse(dateStr)
            } catch (e: Exception) {
                continue
            }
        }
        return null
    }

    private fun parseAmount(amountStr: String): Double? {
        return try {
            amountStr
                .replace("€", "")
                .replace("EUR", "")
                .replace(" ", "")
                .replace(".", "")  // Eliminar separadores de miles
                .replace(",", ".") // Convertir coma decimal a punto
                .trim()
                .toDouble()
        } catch (e: Exception) {
            null
        }
    }

    private fun getFileName(uri: Uri): String? {
        return try {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (nameIndex >= 0) it.getString(nameIndex) else null
                } else null
            }
        } catch (e: Exception) {
            null
        }
    }
}