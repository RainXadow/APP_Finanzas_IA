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
import java.text.SimpleDateFormat
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
    private lateinit var btnStatistics: Button
    private lateinit var btnFilterUncategorized: Button

    private val currencyFormat = NumberFormat.getCurrencyInstance(Locale("es", "ES"))
    private var showingUncategorizedOnly = false

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

    override fun onResume() {
        super.onResume()
        // Recargar al volver de la pantalla de estadísticas
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
        btnStatistics = findViewById(R.id.btnStatistics)
        btnFilterUncategorized = findViewById(R.id.btnFilterUncategorized)
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

        btnStatistics.setOnClickListener {
            // Abrir actividad de estadísticas
            val intent = Intent(this, StatisticsActivity::class.java)
            startActivity(intent)
        }

        btnFilterUncategorized.setOnClickListener {
            toggleUncategorizedFilter()
        }
    }

    private fun toggleUncategorizedFilter() {
        showingUncategorizedOnly = !showingUncategorizedOnly

        if (showingUncategorizedOnly) {
            btnFilterUncategorized.text = "📋 Mostrar Todas"
            loadUncategorizedTransactions()
        } else {
            btnFilterUncategorized.text = "🔍 Sin Categoría"
            loadTransactions()
        }
    }

    private fun loadUncategorizedTransactions() {
        CoroutineScope(Dispatchers.IO).launch {
            val transactions = transactionStorage.getUncategorizedTransactions()
            val statistics = transactionStorage.getStatistics()

            withContext(Dispatchers.Main) {
                adapter.updateTransactions(transactions)
                updateStatistics(statistics)

                if (transactions.isEmpty()) {
                    Toast.makeText(
                        this@MainActivity,
                        "✅ Todas las transacciones están categorizadas",
                        Toast.LENGTH_SHORT
                    ).show()
                    showingUncategorizedOnly = false
                    btnFilterUncategorized.text = "🔍 Sin Categoría"
                }
            }
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
                            append("\n\nPulsa '🔍 Sin Categoría' para verlas")
                        }
                    }

                    AlertDialog.Builder(this@MainActivity)
                        .setTitle("Importación Completada")
                        .setMessage(message)
                        .setPositiveButton("OK", null)
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

    private fun showCategorizationDialog(transaction: Transaction, onComplete: (() -> Unit)? = null) {
        val categories = categoryManager.getAllCategories()
        val categoryNames = categories.map { it.name }.toMutableList()

        // Añadir opciones especiales al final
        categoryNames.add("➕ Crear Nueva Categoría")
        categoryNames.add("⚙️ Gestionar Categorías")

        // Encontrar el índice de la categoría actual
        val currentIndex = categories.indexOfFirst { it.name == transaction.category }

        AlertDialog.Builder(this)
            .setTitle("Seleccionar Categoría")
            .setMessage("${transaction.concept}\n${currencyFormat.format(transaction.amount)}\n\n📂 Actual: ${transaction.category}")
            .setSingleChoiceItems(categoryNames.toTypedArray(), currentIndex) { dialog, which ->
                when {
                    which == categoryNames.size - 2 -> {
                        // Crear nueva categoría
                        dialog.dismiss()
                        showAddCategoryDialog { newCategory ->
                            transactionStorage.updateTransactionCategory(transaction.id, newCategory)
                            categoryManager.saveRule(transaction.concept, newCategory)
                            Toast.makeText(this, "✅ Categoría creada: $newCategory", Toast.LENGTH_SHORT).show()
                            reloadAndComplete(onComplete)
                        }
                    }
                    which == categoryNames.size - 1 -> {
                        // Gestionar categorías
                        dialog.dismiss()
                        showCategoryManagementDialog {
                            // Después de gestionar, volver a mostrar el diálogo
                            showCategorizationDialog(transaction, onComplete)
                        }
                    }
                    else -> {
                        // Categoría normal seleccionada
                        val selectedCategory = categoryNames[which]

                        transactionStorage.updateTransactionCategory(transaction.id, selectedCategory)
                        categoryManager.saveRule(transaction.concept, selectedCategory)

                        Toast.makeText(this, "✅ ${transaction.concept.take(20)}... → $selectedCategory", Toast.LENGTH_SHORT).show()

                        dialog.dismiss()
                        reloadAndComplete(onComplete)
                    }
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun reloadAndComplete(onComplete: (() -> Unit)?) {
        if (showingUncategorizedOnly) {
            loadUncategorizedTransactions()
        } else {
            loadTransactions()
        }
        onComplete?.invoke()
    }

    private fun showCategoryManagementDialog(onClose: (() -> Unit)? = null) {
        val categories = categoryManager.getAllCategories()
        val categoryItems = categories.map { category ->
            val count = transactionStorage.getTransactionsByCategory(category.name).size
            "${category.name} ($count)"
        }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("⚙️ Gestionar Categorías")
            .setItems(categoryItems) { _, which ->
                val category = categories[which]
                showCategoryOptionsDialog(category) {
                    // Volver a mostrar el diálogo de gestión
                    showCategoryManagementDialog(onClose)
                }
            }
            .setPositiveButton("➕ Nueva") { _, _ ->
                showAddCategoryDialog {
                    Toast.makeText(this, "✅ Categoría '$it' creada", Toast.LENGTH_SHORT).show()
                    showCategoryManagementDialog(onClose)
                }
            }
            .setNegativeButton("Cerrar") { _, _ ->
                onClose?.invoke()
            }
            .show()
    }

    private fun showCategoryOptionsDialog(category: Category, onComplete: (() -> Unit)? = null) {
        val transactionCount = transactionStorage.getTransactionsByCategory(category.name).size

        val options = if (category.isDefault) {
            arrayOf("📊 Ver Transacciones ($transactionCount)")
        } else {
            arrayOf(
                "✏️ Renombrar",
                "🗑️ Eliminar",
                "📊 Ver Transacciones ($transactionCount)"
            )
        }

        AlertDialog.Builder(this)
            .setTitle(category.name)
            .setMessage(if (category.isDefault) "(Categoría predeterminada)" else "$transactionCount transacciones")
            .setItems(options) { _, which ->
                when {
                    !category.isDefault && which == 0 -> {
                        // Renombrar
                        showRenameCategoryDialog(category, onComplete)
                    }
                    !category.isDefault && which == 1 -> {
                        // Eliminar
                        showDeleteCategoryDialog(category, onComplete)
                    }
                    else -> {
                        // Ver transacciones (último item)
                        showCategoryTransactionsDialog(category)
                        onComplete?.invoke()
                    }
                }
            }
            .setNegativeButton("Volver") { _, _ ->
                onComplete?.invoke()
            }
            .show()
    }

    private fun showRenameCategoryDialog(category: Category, onComplete: (() -> Unit)? = null) {
        val input = android.widget.EditText(this)
        input.hint = "Nuevo nombre"
        input.setText(category.name)

        AlertDialog.Builder(this)
            .setTitle("Renombrar Categoría")
            .setMessage("Categoría actual: ${category.name}")
            .setView(input)
            .setPositiveButton("Guardar") { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isNotEmpty() && newName != category.name) {
                    // Verificar que no existe ya
                    val exists = categoryManager.getAllCategories().any { it.name == newName }
                    if (exists) {
                        Toast.makeText(this, "❌ Ya existe una categoría con ese nombre", Toast.LENGTH_SHORT).show()
                        onComplete?.invoke()
                    } else {
                        // Actualizar todas las transacciones
                        transactionStorage.getTransactionsByCategory(category.name).forEach {
                            transactionStorage.updateTransactionCategory(it.id, newName)
                        }

                        // Eliminar categoría antigua y crear nueva
                        categoryManager.deleteCategory(category.name)
                        categoryManager.addCategory(newName)

                        Toast.makeText(this, "✅ Renombrada: ${category.name} → $newName", Toast.LENGTH_SHORT).show()
                        loadTransactions()
                        onComplete?.invoke()
                    }
                } else {
                    Toast.makeText(this, "❌ Nombre inválido", Toast.LENGTH_SHORT).show()
                    onComplete?.invoke()
                }
            }
            .setNegativeButton("Cancelar") { _, _ ->
                onComplete?.invoke()
            }
            .show()
    }

    private fun showDeleteCategoryDialog(category: Category, onComplete: (() -> Unit)? = null) {
        val transactionCount = transactionStorage.getTransactionsByCategory(category.name).size

        AlertDialog.Builder(this)
            .setTitle("🗑️ Eliminar Categoría")
            .setMessage("¿Eliminar '${category.name}'?\n\n$transactionCount transacciones se marcarán como 'Sin categoría'")
            .setPositiveButton("Eliminar") { _, _ ->
                // Reasignar transacciones
                transactionStorage.getTransactionsByCategory(category.name).forEach {
                    transactionStorage.updateTransactionCategory(it.id, "Sin categoría")
                }

                // Eliminar categoría
                categoryManager.deleteCategory(category.name)
                Toast.makeText(this, "✅ Categoría eliminada", Toast.LENGTH_SHORT).show()
                loadTransactions()
                onComplete?.invoke()
            }
            .setNegativeButton("Cancelar") { _, _ ->
                onComplete?.invoke()
            }
            .show()
    }

    private fun showCategoryTransactionsDialog(category: Category) {
        val transactions = transactionStorage.getTransactionsByCategory(category.name).take(15)

        val message = if (transactions.isEmpty()) {
            "No hay transacciones en esta categoría"
        } else {
            buildString {
                append("Últimas transacciones:\n\n")
                transactions.forEach {
                    val date = SimpleDateFormat("dd/MM", Locale.getDefault()).format(it.date)
                    append("📅 $date - ${it.concept.take(25)}\n")
                    append("   ${currencyFormat.format(it.amount)}\n\n")
                }

                val total = transactionStorage.getTransactionsByCategory(category.name).size
                if (total > 15) {
                    append("... y ${total - 15} más")
                }
            }
        }

        AlertDialog.Builder(this)
            .setTitle("${category.name}")
            .setMessage(message)
            .setPositiveButton("OK", null)
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

    private fun loadTransactions() {
        CoroutineScope(Dispatchers.IO).launch {
            val transactions = transactionStorage.getAllTransactions()
            val statistics = transactionStorage.getStatistics()

            withContext(Dispatchers.Main) {
                adapter.updateTransactions(transactions)
                updateStatistics(statistics)

                // Actualizar contador del botón
                val uncategorizedCount = transactionStorage.getUncategorizedTransactions().size
                if (uncategorizedCount > 0 && !showingUncategorizedOnly) {
                    btnFilterUncategorized.text = "🔍 Sin Categoría ($uncategorizedCount)"
                } else if (!showingUncategorizedOnly) {
                    btnFilterUncategorized.text = "🔍 Sin Categoría"
                }
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

                if (showingUncategorizedOnly) {
                    loadUncategorizedTransactions()
                } else {
                    loadTransactions()
                }
                Toast.makeText(this, "Transacción eliminada", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
}