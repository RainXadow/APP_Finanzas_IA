package com.example.app_finanzas_ia

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

class TransactionAdapter(
    private val onItemClick: (Transaction) -> Unit,
    private val onItemLongClick: (Transaction) -> Unit
) : RecyclerView.Adapter<TransactionAdapter.TransactionViewHolder>() {

    private var transactions: List<Transaction> = emptyList()
    private val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
    private val currencyFormat = NumberFormat.getCurrencyInstance(Locale("es", "ES"))

    fun updateTransactions(newTransactions: List<Transaction>) {
        transactions = newTransactions.sortedByDescending { it.date }
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TransactionViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_transaction, parent, false)
        return TransactionViewHolder(view)
    }

    override fun onBindViewHolder(holder: TransactionViewHolder, position: Int) {
        holder.bind(transactions[position])
    }

    override fun getItemCount(): Int = transactions.size

    inner class TransactionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvConcept: TextView = itemView.findViewById(R.id.tvConcept)
        private val tvAmount: TextView = itemView.findViewById(R.id.tvAmount)
        private val tvDate: TextView = itemView.findViewById(R.id.tvDate)
        private val tvCategory: TextView = itemView.findViewById(R.id.tvCategory)

        fun bind(transaction: Transaction) {
            tvConcept.text = transaction.concept
            tvAmount.text = currencyFormat.format(transaction.amount)
            tvDate.text = dateFormat.format(transaction.date)
            tvCategory.text = transaction.category

            // Color según tipo
            val color = when (transaction.type) {
                TransactionType.INCOME -> android.R.color.holo_green_dark
                TransactionType.EXPENSE -> android.R.color.holo_red_dark
                TransactionType.UNKNOWN -> android.R.color.darker_gray
            }
            tvAmount.setTextColor(itemView.context.getColor(color))

            itemView.setOnClickListener { onItemClick(transaction) }
            itemView.setOnLongClickListener {
                onItemLongClick(transaction)
                true
            }
        }
    }
}