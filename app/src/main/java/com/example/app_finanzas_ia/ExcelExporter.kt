package com.example.app_finanzas_ia

import android.content.Context
import org.apache.poi.ss.usermodel.*
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.apache.poi.xssf.usermodel.XSSFCellStyle
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class ExcelExporter(private val context: Context) {

    private val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())

    fun exportToExcel(
        transactions: List<Transaction>,
        config: ExcelConfig = ExcelConfig()
    ): File {

        val workbook = XSSFWorkbook()
        val sheet = workbook.createSheet(config.sheetName)

        // Estilos
        val headerStyle = createHeaderStyle(workbook)
        val dateStyle = createDateStyle(workbook)
        val currencyStyle = createCurrencyStyle(workbook)

        var rowIndex = 0

        // Crear encabezados
        if (config.includeHeaders) {
            val headerRow = sheet.createRow(rowIndex++)
            val headers = listOf("Fecha", "Concepto", "Categoría", "Importe", "Tipo", "Fuente")

            headers.forEachIndexed { index, header ->
                val cell = headerRow.createCell(index)
                cell.setCellValue(header)
                cell.cellStyle = headerStyle
            }
        }

        // Añadir transacciones
        transactions.sortedByDescending { it.date }.forEach { transaction ->
            val row = sheet.createRow(rowIndex++)

            // Fecha
            val dateCell = row.createCell(0)
            dateCell.setCellValue(transaction.date)
            dateCell.cellStyle = dateStyle

            // Concepto
            row.createCell(1).setCellValue(transaction.concept)

            // Categoría
            row.createCell(2).setCellValue(transaction.category)

            // Importe
            val amountCell = row.createCell(3)
            amountCell.setCellValue(transaction.amount)
            amountCell.cellStyle = currencyStyle

            // Tipo
            val typeText = when (transaction.type) {
                TransactionType.INCOME -> "Ingreso"
                TransactionType.EXPENSE -> "Gasto"
                TransactionType.UNKNOWN -> "Desconocido"
            }
            row.createCell(4).setCellValue(typeText)

            // Fuente
            row.createCell(5).setCellValue(transaction.source)
        }

        // Ajustar ancho de columnas
        for (i in 0..5) {
            sheet.autoSizeColumn(i)
        }

        // Guardar archivo
        val file = File(context.getExternalFilesDir(null), config.fileName)
        FileOutputStream(file).use { outputStream ->
            workbook.write(outputStream)
        }
        workbook.close()

        return file
    }

    // Función deshabilitada temporalmente - usar exportToExcel directamente
    // fun appendToExistingExcel(
    //     existingFile

    private fun createHeaderStyle(workbook: Workbook): XSSFCellStyle {
        val style = workbook.createCellStyle() as XSSFCellStyle
        val font = workbook.createFont()

        font.bold = true
        font.fontHeightInPoints = 12
        style.setFont(font)
        style.fillForegroundColor = IndexedColors.GREY_25_PERCENT.index
        style.fillPattern = FillPatternType.SOLID_FOREGROUND
        style.borderBottom = BorderStyle.THIN
        style.borderTop = BorderStyle.THIN
        style.borderLeft = BorderStyle.THIN
        style.borderRight = BorderStyle.THIN

        return style
    }

    private fun createDateStyle(workbook: Workbook): XSSFCellStyle {
        val style = workbook.createCellStyle() as XSSFCellStyle
        val createHelper = workbook.creationHelper
        style.dataFormat = createHelper.createDataFormat().getFormat("dd/MM/yyyy HH:mm")
        return style
    }

    private fun createCurrencyStyle(workbook: Workbook): XSSFCellStyle {
        val style = workbook.createCellStyle() as XSSFCellStyle
        val createHelper = workbook.creationHelper
        style.dataFormat = createHelper.createDataFormat().getFormat("#,##0.00 €")
        return style
    }

    fun createSummarySheet(workbook: XSSFWorkbook, statistics: TransactionStatistics) {
        val sheet = workbook.createSheet("Resumen")
        var rowIndex = 0

        // Título
        val titleRow = sheet.createRow(rowIndex++)
        titleRow.createCell(0).setCellValue("RESUMEN FINANCIERO")

        rowIndex++ // Línea en blanco

        // Total gastos
        sheet.createRow(rowIndex++).apply {
            createCell(0).setCellValue("Total Gastos:")
            createCell(1).setCellValue(statistics.totalExpenses)
        }

        // Total ingresos
        sheet.createRow(rowIndex++).apply {
            createCell(0).setCellValue("Total Ingresos:")
            createCell(1).setCellValue(statistics.totalIncome)
        }

        // Balance
        sheet.createRow(rowIndex++).apply {
            createCell(0).setCellValue("Balance:")
            createCell(1).setCellValue(statistics.totalIncome - statistics.totalExpenses)
        }

        rowIndex++ // Línea en blanco

        // Desglose por categorías
        sheet.createRow(rowIndex++).apply {
            createCell(0).setCellValue("GASTOS POR CATEGORÍA")
        }

        statistics.categoriesBreakdown.entries
            .sortedByDescending { it.value }
            .forEach { (category, amount) ->
                sheet.createRow(rowIndex++).apply {
                    createCell(0).setCellValue(category)
                    createCell(1).setCellValue(amount)
                }
            }

        // Ajustar columnas
        sheet.autoSizeColumn(0)
        sheet.autoSizeColumn(1)
    }
}