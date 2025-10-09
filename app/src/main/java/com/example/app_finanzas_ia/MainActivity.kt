package com.example.app_finanzas_ia

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
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
    private lateinit var excelExporter: ExcelExporter
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: TransactionAdapter
    private lateinit var tvTotalExpenses: TextView
    private lateinit var tvTotalIncome: TextView
    private lateinit var tvBalance: TextView
    private lateinit var btnExport: Button
    private lateinit var btnPermissions: Button
    private lateinit var btnAddManual: Button

    private val currencyFormat = NumberFormat.getCurrencyInstance(Locale("es", "ES"))

    private val transactionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            // Recargar transacciones cuando se detecta una nueva
            loadTransactions()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initializeComponents()
        setupRecyclerView()
        setupButtons()
        checkNotificationPermission()
        loadTransactions()

        // Registrar receptor para nuevas transacciones
        val filter = IntentFilter("com.example.app_finanzas_ia.NEW_TRANSACTION")
        registerReceiver(transactionReceiver, filter, RECEIVER_NOT_EXPORTED)
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(transactionReceiver)
    }

    private fun initializeComponents() {
        transactionStorage = TransactionStorage(this)
        excelExporter = ExcelExporter(this)

        recyclerView = findViewById(R.id.recyclerViewTransactions)
        tvTotalExpenses = findViewById(R.id.tvTotalExpenses)
        tvTotalIncome = findViewById(R.id.tvTotalIncome)
        tvBalance = findViewById(R.id.tvBalance)
        btnExport = findViewById(R.id.btnExport)
        btnPermissions = findViewById(R.id.btnPermissions)
        btnAddManual = findViewById(R.id.btnAddManual)
    }

    private fun setupRecyclerView() {
        adapter = TransactionAdapter(
            onItemClick = { transaction -> showTransactionDetails(transaction) },
            onItemLongClick = { transaction -> showDeleteDialog(transaction) }
        )

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
    }

    private fun setupButtons() {
        btnExport.setOnClickListener {
            exportToExcel()
        }

        btnPermissions.setOnClickListener {
            requestNotificationPermission()
        }

        btnAddManual.setOnClickListener {
            showAddManualTransactionDialog()
        }
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

        // Cambiar color según el balance
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
                    // Compartir el archivo Excel
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

    private fun checkNotificationPermission() {
        val enabled = Settings.Secure.getString(
            contentResolver,
            "enabled_notification_listeners"
        )?.contains(packageName) ?: false

        btnPermissions.isEnabled = !enabled

        if (!enabled) {
            btnPermissions.text = "Activar Permisos"
            showPermissionAlert()
        } else {
            btnPermissions.text = "Permisos Activos ✓"
        }
    }

    private fun requestNotificationPermission() {
        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        startActivity(intent)
    }

    private fun showPermissionAlert() {
        AlertDialog.Builder(this)
            .setTitle("Permisos Necesarios")
            .setMessage("Para capturar notificaciones de pagos automáticamente, " +
                    "necesitas activar los permisos de acceso a notificaciones.\n\n" +
                    "Busca 'APP_Finanzas_IA' en la lista y actívalo.")
            .setPositiveButton("Ir a Configuración") { _, _ ->
                requestNotificationPermission()
            }
            .setNegativeButton("Más tarde", null)
            .show()
    }

    private fun showTransactionDetails(transaction: Transaction) {
        val details = """
            Fecha: ${transaction.date}
            Concepto: ${transaction.concept}
            Categoría: ${transaction.category}
            Importe: ${currencyFormat.format(transaction.amount)}
            Fuente: ${transaction.source}
            Tipo: ${when(transaction.type) {
            TransactionType.INCOME -> "Ingreso"
            TransactionType.EXPENSE -> "Gasto"
            TransactionType.UNKNOWN -> "Desconocido"
        }}
            
            Texto original:
            ${transaction.originalNotificationText}
        """.trimIndent()

        AlertDialog.Builder(this)
            .setTitle("Detalles de Transacción")
            .setMessage(details)
            .setPositiveButton("OK", null)
            .setNeutralButton("Editar") { _, _ ->
                // TODO: Implementar edición
                Toast.makeText(this, "Función en desarrollo", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun showDeleteDialog(transaction: Transaction) {
        AlertDialog.Builder(this)
            .setTitle("Eliminar Transacción")
            .setMessage("¿Estás seguro de que quieres eliminar esta transacción?")
            .setPositiveButton("Eliminar") { _, _ ->
                transactionStorage.deleteTransaction(transaction.id)
                loadTransactions()
                Toast.makeText(this, "Transacción eliminada", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun showAddManualTransactionDialog() {
        // TODO: Implementar diálogo para añadir transacción manual
        Toast.makeText(this, "Función en desarrollo", Toast.LENGTH_SHORT).show()
    }
}