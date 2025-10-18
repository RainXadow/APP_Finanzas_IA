package com.example.app_finanzas_ia

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

class StatisticsActivity : AppCompatActivity() {

    private lateinit var transactionStorage: TransactionStorage
    private lateinit var categoryManager: CategoryManager
    private lateinit var spinnerMonth: Spinner
    private lateinit var tvTotalIncome: TextView
    private lateinit var tvTotalExpenses: TextView
    private lateinit var tvBalance: TextView
    private lateinit var tvTransactionCount: TextView
    private lateinit var layoutCategories: LinearLayout
    private lateinit var chartContainer: LinearLayout
    private lateinit var btnManageCategories: Button

    private val currencyFormat = NumberFormat.getCurrencyInstance(Locale("es", "ES"))
    private val monthFormat = SimpleDateFormat("MMMM yyyy", Locale("es", "ES"))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            setContentView(R.layout.activity_statistics)

            // Configurar ActionBar
            supportActionBar?.setDisplayHomeAsUpEnabled(true)
            supportActionBar?.title = "Estadísticas"

            initializeViews()
            setupMonthSpinner()
            setupButtons()
        } catch (e: Exception) {
            // Si hay error al cargar el layout, mostrar mensaje y cerrar
            Toast.makeText(this, "Error al cargar estadísticas: ${e.message}", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun initializeViews() {
        transactionStorage = TransactionStorage(this)
        categoryManager = CategoryManager(this)

        spinnerMonth = findViewById(R.id.spinnerMonth)
        tvTotalIncome = findViewById(R.id.tvTotalIncome)
        tvTotalExpenses = findViewById(R.id.tvTotalExpenses)
        tvBalance = findViewById(R.id.tvBalance)
        tvTransactionCount = findViewById(R.id.tvTransactionCount)
        layoutCategories = findViewById(R.id.layoutCategories)
        chartContainer = findViewById(R.id.chartContainer)
        btnManageCategories = findViewById(R.id.btnManageCategories)
    }

    private fun setupMonthSpinner() {
        val transactions = transactionStorage.getAllTransactions()

        // Obtener meses disponibles
        val months = transactions
            .map {
                val calendar = Calendar.getInstance()
                calendar.time = it.date
                calendar.set(Calendar.DAY_OF_MONTH, 1)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                calendar.time
            }
            .distinct()
            .sortedDescending()

        val monthStrings = mutableListOf("Todos los meses")
        monthStrings.addAll(months.map { monthFormat.format(it) })

        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, monthStrings)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerMonth.adapter = adapter

        spinnerMonth.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (position == 0) {
                    loadStatistics(null)
                } else {
                    loadStatistics(months[position - 1])
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // Cargar estadísticas iniciales
        loadStatistics(null)
    }

    private fun setupButtons() {
        btnManageCategories.setOnClickListener {
            showCategoriesManager()
        }
    }

    private fun loadStatistics(month: Date?) {
        CoroutineScope(Dispatchers.IO).launch {
            val allTransactions = transactionStorage.getAllTransactions()

            val filteredTransactions = if (month != null) {
                val calendar = Calendar.getInstance()
                calendar.time = month
                val year = calendar.get(Calendar.YEAR)
                val monthOfYear = calendar.get(Calendar.MONTH)

                allTransactions.filter {
                    val transCalendar = Calendar.getInstance()
                    transCalendar.time = it.date
                    transCalendar.get(Calendar.YEAR) == year &&
                            transCalendar.get(Calendar.MONTH) == monthOfYear
                }
            } else {
                allTransactions
            }

            val expenses = filteredTransactions.filter { it.type == TransactionType.EXPENSE }
            val income = filteredTransactions.filter { it.type == TransactionType.INCOME }

            val totalExpenses = expenses.sumOf { kotlin.math.abs(it.amount) }
            val totalIncome = income.sumOf { kotlin.math.abs(it.amount) }
            val balance = totalIncome - totalExpenses

            val categoriesBreakdown = filteredTransactions
                .groupBy { it.category }
                .mapValues { it.value.sumOf { t -> kotlin.math.abs(t.amount) } }
                .toList()
                .sortedByDescending { it.second }

            withContext(Dispatchers.Main) {
                updateUI(totalIncome, totalExpenses, balance, filteredTransactions.size, categoriesBreakdown)
            }
        }
    }

    private fun updateUI(
        totalIncome: Double,
        totalExpenses: Double,
        balance: Double,
        transactionCount: Int,
        categoriesBreakdown: List<Pair<String, Double>>
    ) {
        tvTotalIncome.text = currencyFormat.format(totalIncome)
        tvTotalExpenses.text = currencyFormat.format(totalExpenses)
        tvBalance.text = currencyFormat.format(balance)
        tvTransactionCount.text = "$transactionCount transacciones"

        // Color del balance
        tvBalance.setTextColor(
            if (balance >= 0)
                ContextCompat.getColor(this, android.R.color.holo_green_dark)
            else
                ContextCompat.getColor(this, android.R.color.holo_red_dark)
        )

        // Actualizar categorías
        updateCategoriesBreakdown(categoriesBreakdown, totalExpenses)

        // Actualizar gráfico
        updateChart(totalIncome, totalExpenses)
    }

    private fun updateCategoriesBreakdown(breakdown: List<Pair<String, Double>>, total: Double) {
        layoutCategories.removeAllViews()

        if (breakdown.isEmpty()) {
            val noData = TextView(this)
            noData.text = "No hay datos para mostrar"
            noData.setPadding(16, 32, 16, 32)
            layoutCategories.addView(noData)
            return
        }

        breakdown.forEach { (category, amount) ->
            val percentage = if (total > 0) (amount / total * 100) else 0.0

            val card = layoutInflater.inflate(R.layout.item_category_stat, layoutCategories, false) as CardView

            val tvCategoryName = card.findViewById<TextView>(R.id.tvCategoryName)
            val tvCategoryAmount = card.findViewById<TextView>(R.id.tvCategoryAmount)
            val tvCategoryPercentage = card.findViewById<TextView>(R.id.tvCategoryPercentage)
            val progressBar = card.findViewById<ProgressBar>(R.id.progressBar)

            tvCategoryName.text = category
            tvCategoryAmount.text = currencyFormat.format(amount)
            tvCategoryPercentage.text = String.format("%.1f%%", percentage)
            progressBar.progress = percentage.toInt()

            card.setOnClickListener {
                showCategoryTransactions(category)
            }

            layoutCategories.addView(card)
        }
    }

    private fun updateChart(income: Double, expenses: Double) {
        chartContainer.removeAllViews()

        val chartView = layoutInflater.inflate(R.layout.view_bar_chart, chartContainer, false)

        val barIncome = chartView.findViewById<View>(R.id.barIncome)
        val barExpenses = chartView.findViewById<View>(R.id.barExpenses)
        val tvIncomeLabel = chartView.findViewById<TextView>(R.id.tvIncomeLabel)
        val tvExpensesLabel = chartView.findViewById<TextView>(R.id.tvExpensesLabel)

        val maxAmount = maxOf(income, expenses)
        val heightIncome = if (maxAmount > 0) (income / maxAmount * 300).toInt() else 0
        val heightExpenses = if (maxAmount > 0) (expenses / maxAmount * 300).toInt() else 0

        val paramsIncome = barIncome.layoutParams
        paramsIncome.height = heightIncome
        barIncome.layoutParams = paramsIncome

        val paramsExpenses = barExpenses.layoutParams
        paramsExpenses.height = heightExpenses
        barExpenses.layoutParams = paramsExpenses

        tvIncomeLabel.text = currencyFormat.format(income)
        tvExpensesLabel.text = currencyFormat.format(expenses)

        chartContainer.addView(chartView)
    }

    private fun showCategoryTransactions(category: String) {
        val transactions = transactionStorage.getTransactionsByCategory(category)

        val message = buildString {
            append("Transacciones en $category:\n\n")
            transactions.take(10).forEach {
                append("• ${it.concept}: ${currencyFormat.format(it.amount)}\n")
            }
            if (transactions.size > 10) {
                append("\n... y ${transactions.size - 10} más")
            }
        }

        AlertDialog.Builder(this)
            .setTitle(category)
            .setMessage(message)
            .setPositiveButton("OK", null)
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
            .setPositiveButton("➕ Nueva") { _, _ ->
                showAddCategoryDialog()
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
            .setTitle("Opciones")
            .setMessage(message)

        if (!category.isDefault) {
            builder.setPositiveButton("🗑️ Eliminar") { _, _ ->
                deleteCategory(category)
            }
        }

        builder.setNegativeButton("Cerrar", null)
            .show()
    }

    private fun deleteCategory(category: Category) {
        AlertDialog.Builder(this)
            .setTitle("¿Eliminar categoría?")
            .setMessage("${transactionStorage.getTransactionsByCategory(category.name).size} transacciones serán marcadas como 'Sin categoría'")
            .setPositiveButton("Eliminar") { _, _ ->
                transactionStorage.getTransactionsByCategory(category.name).forEach {
                    transactionStorage.updateTransactionCategory(it.id, "Sin categoría")
                }
                categoryManager.deleteCategory(category.name)
                Toast.makeText(this, "Categoría eliminada", Toast.LENGTH_SHORT).show()
                loadStatistics(null)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun showAddCategoryDialog() {
        val input = EditText(this)
        input.hint = "Nombre de la categoría"

        AlertDialog.Builder(this)
            .setTitle("Nueva Categoría")
            .setView(input)
            .setPositiveButton("Crear") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    categoryManager.addCategory(name)
                    Toast.makeText(this, "✅ Categoría creada", Toast.LENGTH_SHORT).show()
                    loadStatistics(null)
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
}