package com.example.app_finanzas_ia

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var transactionStorage: TransactionStorage
    private lateinit var categoryManager: CategoryManager
    private lateinit var excelProcessor: ExcelProcessor
    private lateinit var excelExporter: ExcelExporter
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: TransactionAdapter
    private lateinit var tvTotalExpenses: TextView
    private lateinit var tvTotalIncome: TextView
    private lateinit var tvBalance: TextView
    private lateinit var btnImportExcel: Button
    private lateinit var btnExport: Button
    private lateinit var btnCategories: Button

    private val currencyFormat = NumberFormat.getCurrencyInstance(Locale("es", "ES"))

    // Selector de Excel
    private val excelPickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { processExcel(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initializeComponents()
        setupRecyclerView()
        setupButtons()
        loadTransactions()
    }

    private fun initializeComponents() {
        transactionStorage = TransactionStorage(this)
        categoryManager = CategoryManager(this)
        excelProcessor = ExcelProcessor(this)
        excelExporter = ExcelExporter(this)

        recyclerView = findViewById(R.id.recyclerViewTransactions)
        tvTotalExpenses = findViewById(R.id.tvTotalExpenses)
        tvTotalIncome = findViewById(R.id.tvTotalIncome)
        tvBalance = findViewById(R.id.tvBalance)
        btnImportExcel = findViewById(R.id.btnImportExcel)
        btnExport = findViewById(R.id.btnExport)
        btnCategories = findViewById(R.id.btnCategories)
    }

    private fun setupRecyclerView() {
        adapter = TransactionAdapter(
            onItemClick = { transaction -> showCategorizationDialog(transaction) },
            onItemLongClick = { transaction -> showDeleteDialog(transaction) }
        )

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
    }

    private fun setupButtons() {
        btnImportExcel.setOnClickListener {
            excelPickerLauncher.launch("*/*")
        }


        btnExport.setOnClickListener {
            exportToExcel()
        }

        btnCategories.setOnClickListener {
            showCategoriesManager()
        }
    }

    private fun processExcel(uri: Uri) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Procesando Excel...", Toast.LENGTH_SHORT).show()
                }

                // Procesar Excel
                val result = excelProcessor.processExcel(uri)

                if (!result.success) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            this@MainActivity,
                            "Error: ${result.errorMessage}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    return@launch
                }

                // Categorizar automáticamente
                val categorizedTransactions = result.transactions.map { transaction ->
                    val suggestedCategory = categoryManager.findCategoryForConcept(transaction.concept)
                    transaction.copy(category = suggestedCategory ?: "Sin categoría")
                }

                // Guardar transacciones
                val (added, duplicates) = transactionStorage.saveTransactions(categorizedTransactions)

                // Contar sin categorizar
                val uncategorizedCount = categorizedTransactions.count { it.category == "Sin categoría" }

                withContext(Dispatchers.Main) {
                    loadTransactions()

                    val message = buildString {
                        append("Excel procesado correctamente\n\n")
                        append("✅ Nuevas: $added\n")
                        if (duplicates > 0) {
                            append("⚠️ Duplicadas (ignoradas): $duplicates\n")
                        }

                        if (uncategorizedCount > 0) {
                            append("\n📋 $uncategorizedCount sin categorizar")
                        }
                    }

                    AlertDialog.Builder(this@MainActivity)
                        .setTitle("Importación Completada")
                        .setMessage(message)
                        .setPositiveButton("OK") { _, _ ->
                            // Mostrar transacciones sin categorizar
                            if (uncategorizedCount > 0) {
                                categorizePendingTransactions()
                            }
                        }
                        .show()
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@MainActivity,
                        "Error: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun categorizePendingTransactions() {
        val uncategorized = transactionStorage.getUncategorizedTransactions()
        if (uncategorized.isEmpty()) {
            Toast.makeText(this, "No hay transacciones pendientes", Toast.LENGTH_SHORT).show()
            return
        }

        var currentIndex = 0

        fun showNextTransaction() {
            if (currentIndex >= uncategorized.size) {
                Toast.makeText(this, "¡Categorización completada!", Toast.LENGTH_SHORT).show()
                loadTransactions()
                return
            }

            val transaction = uncategorized[currentIndex]
            showCategorizationDialog(transaction) {
                currentIndex++
                showNextTransaction()
            }
        }

        showNextTransaction()
    }

    private fun showCategorizationDialog(transaction: Transaction, onComplete: (() -> Unit)? = null) {
        val categories = categoryManager.getAllCategories().map { it.name }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Categorizar: ${transaction.concept}")
            .setMessage("Importe: ${currencyFormat.format(transaction.amount)}\nCategoría actual: ${transaction.category}")
            .setItems(categories) { _, which ->
                val selectedCategory = categories[which]

                // Actualizar transacción
                transactionStorage.updateTransactionCategory(transaction.id, selectedCategory)

                // Guardar regla para futuras transacciones
                categoryManager.saveRule(transaction.concept, selectedCategory)

                Toast.makeText(this, "Categoría actualizada", Toast.LENGTH_SHORT).show()
                loadTransactions()
                onComplete?.invoke()
            }
            .setNeutralButton("Nueva Categoría") { _, _ ->
                showAddCategoryDialog { newCategory ->
                    transactionStorage.updateTransactionCategory(transaction.id, newCategory)
                    categoryManager.saveRule(transaction.concept, newCategory)
                    Toast.makeText(this, "Categoría creada y asignada", Toast.LENGTH_SHORT).show()
                    loadTransactions()
                    onComplete?.invoke()
                }
            }
            .setNegativeButton("Cancelar") { _, _ ->
                onComplete?.invoke()
            }
            .show()
    }

    private fun showAddCategoryDialog(onCategoryCreated: (String) -> Unit) {
        val input = android.widget.EditText(this)
        input.hint = "Nombre de la categoría"

        AlertDialog.Builder(this)
            .setTitle("Nueva Categoría")
            .setView(input)
            .setPositiveButton("Crear") { _, _ ->
                val categoryName = input.text.toString().trim()
                if (categoryName.isNotEmpty()) {
                    categoryManager.addCategory(categoryName)
                    onCategoryCreated(categoryName)
                } else {
                    Toast.makeText(this, "El nombre no puede estar vacío", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun showCategoriesManager() {
        val categories = categoryManager.getAllCategories()
        val categoryNames = categories.map { it.name }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Gestión de Categorías")
            .setItems(categoryNames) { _, which ->
                val category = categories[which]
                showCategoryOptions(category)
            }
            .setPositiveButton("Nueva Categoría") { _, _ ->
                showAddCategoryDialog {
                    Toast.makeText(this, "Categoría '$it' creada", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cerrar", null)
            .show()
    }

    private fun showCategoryOptions(category: Category) {
        val transactionCount = transactionStorage.getTransactionsByCategory(category.name).size

        val message = buildString {
            append("Categoría: ${category.name}\n")
            append("Transacciones: $transactionCount\n")
            if (category.isDefault) {
                append("\n(Categoría predeterminada)")
            }
        }

        val builder = AlertDialog.Builder(this)
            .setTitle("Opciones de Categoría")
            .setMessage(message)

        if (!category.isDefault) {
            builder.setPositiveButton("Eliminar") { _, _ ->
                showDeleteCategoryConfirmation(category)
            }
        }

        builder.setNegativeButton("Cerrar", null)
            .show()
    }

    private fun showDeleteCategoryConfirmation(category: Category) {
        val transactionCount = transactionStorage.getTransactionsByCategory(category.name).size

        AlertDialog.Builder(this)
            .setTitle("Eliminar Categoría")
            .setMessage("¿Eliminar '${category.name}'?\n\n$transactionCount transacciones serán marcadas como 'Sin categoría'")
            .setPositiveButton("Eliminar") { _, _ ->
                // Reasignar transacciones
                val transactions = transactionStorage.getTransactionsByCategory(category.name)
                transactions.forEach {
                    transactionStorage.updateTransactionCategory(it.id, "Sin categoría")
                }

                // Eliminar categoría
                categoryManager.deleteCategory(category.name)
                Toast.makeText(this, "Categoría eliminada", Toast.LENGTH_SHORT).show()
                loadTransactions()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun loadTransactions() {
        CoroutineScope(Dispatchers.IO).launch {
            val transactions = transactionStorage.getAllTransactions()
            val statistics = transactionStorage.getStatistics()

            withContext(Dispatchers.Main) {
                adapter.updateTransactions(transactions)
                updateStatistics(statistics)
            }
        }
    }

    private fun updateStatistics(stats: TransactionStatistics) {
        tvTotalExpenses.text = "Gastos: ${currencyFormat.format(stats.totalExpenses)}"
        tvTotalIncome.text = "Ingresos: ${currencyFormat.format(stats.totalIncome)}"

        val balance = stats.totalIncome - stats.totalExpenses
        tvBalance.text = "Balance: ${currencyFormat.format(balance)}"

        tvBalance.setTextColor(
            if (balance >= 0)
                getColor(android.R.color.holo_green_dark)
            else
                getColor(android.R.color.holo_red_dark)
        )
    }

    private fun exportToExcel() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val transactions = transactionStorage.getAllTransactions()

                if (transactions.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            this@MainActivity,
                            "No hay transacciones para exportar",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    return@launch
                }

                val file = excelExporter.exportToExcel(transactions)

                withContext(Dispatchers.Main) {
                    val uri = FileProvider.getUriForFile(
                        this@MainActivity,
                        "${packageName}.fileprovider",
                        file
                    )

                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }

                    startActivity(Intent.createChooser(shareIntent, "Exportar Excel"))

                    Toast.makeText(
                        this@MainActivity,
                        "Excel exportado correctamente",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@MainActivity,
                        "Error al exportar: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun showDeleteDialog(transaction: Transaction) {
        AlertDialog.Builder(this)
            .setTitle("Eliminar Transacción")
            .setMessage("¿Estás seguro de que quieres eliminar esta transacción?\n\n${transaction.concept}\n${currencyFormat.format(transaction.amount)}")
            .setPositiveButton("Eliminar") { _, _ ->
                transactionStorage.deleteTransaction(transaction.id)
                loadTransactions()
                Toast.makeText(this, "Transacción eliminada", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
}