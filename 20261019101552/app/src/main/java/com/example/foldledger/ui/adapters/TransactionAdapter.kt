package com.example.foldledger.ui.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.foldledger.data.entities.Transaction
import com.example.foldledger.databinding.ItemTransactionBinding
import com.example.foldledger.ui.model.Categories
import com.example.foldledger.ui.model.PaymentMethods
import com.example.foldledger.util.DateUtils
import com.example.foldledger.util.MoneyFormat

sealed class ListItem {
    data class Header(val date: Long, val dailyTotal: Double, val count: Int) : ListItem()
    data class Record(val t: Transaction) : ListItem()
}

class TransactionAdapter(
    private val onEdit: (Transaction) -> Unit,
    private val onDelete: (Transaction) -> Unit
) : ListAdapter<ListItem, RecyclerView.ViewHolder>(Diff) {

    companion object {
        private val Diff = object : DiffUtil.ItemCallback<ListItem>() {
            override fun areItemsTheSame(a: ListItem, b: ListItem): Boolean = when {
                a is ListItem.Header && b is ListItem.Header -> a.date == b.date
                a is ListItem.Record && b is ListItem.Record -> a.t.id == b.t.id
                else -> false
            }
            override fun areContentsTheSame(a: ListItem, b: ListItem): Boolean = a == b
        }
        const val TYPE_HEADER = 0
        const val TYPE_RECORD = 1
    }

    override fun getItemViewType(position: Int): Int =
        if (getItem(position) is ListItem.Header) TYPE_HEADER else TYPE_RECORD

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_HEADER) {
            val v = inflater.inflate(
                com.example.foldledger.R.layout.item_date_header, parent, false
            )
            HeaderHolder(v)
        } else {
            val binding = ItemTransactionBinding.inflate(inflater, parent, false)
            RecordHolder(binding)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is ListItem.Header -> (holder as HeaderHolder).bind(item)
            is ListItem.Record -> (holder as RecordHolder).bind(item.t)
        }
    }

    class HeaderHolder(v: View) : RecyclerView.ViewHolder(v) {
        private val date = v.findViewById<android.widget.TextView>(com.example.foldledger.R.id.tv_date)
        private val total = v.findViewById<android.widget.TextView>(com.example.foldledger.R.id.tv_daily_total)
        private val count = v.findViewById<android.widget.TextView>(com.example.foldledger.R.id.tv_count)
        fun bind(h: ListItem.Header) {
            date.text = DateUtils.formatDate(h.date)
            total.text = "支出 ¥${MoneyFormat.format(h.dailyTotal)}"
            count.text = "${h.count} 笔"
        }
    }

    inner class RecordHolder(private val b: ItemTransactionBinding) :
        RecyclerView.ViewHolder(b.root) {

        init {
            b.btnEdit.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    (getItem(pos) as? ListItem.Record)?.let { onEdit(it.t) }
                }
            }
            b.btnDelete.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    (getItem(pos) as? ListItem.Record)?.let { onDelete(it.t) }
                }
            }
        }

        fun bind(t: Transaction) {
            if (t.type == "expense") {
                b.tvEmoji.text = Categories.emojiOf(t.category)
                b.tvCategory.text = Categories.labelOf(t.category)
                b.tvAmount.text = "-¥${MoneyFormat.format(t.amount)}"
                b.tvAmount.setTextColor(0xFFE53935.toInt())
                b.tvPayment.text = PaymentMethods.labelOf(t.paymentMethod)
            } else {
                b.tvEmoji.text = "💰"
                b.tvCategory.text = Categories.INCOME_LABEL
                b.tvAmount.text = "+¥${MoneyFormat.format(t.amount)}"
                b.tvAmount.setTextColor(0xFF43A047.toInt())
                b.tvPayment.text = ""
            }
            b.tvNote.text = t.note.ifBlank { "" }
            b.tvNote.visibility = if (t.note.isBlank()) View.GONE else View.VISIBLE
        }
    }
}
