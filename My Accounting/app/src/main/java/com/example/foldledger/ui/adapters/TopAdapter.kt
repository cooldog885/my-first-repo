package com.example.foldledger.ui.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.foldledger.data.entities.Transaction
import com.example.foldledger.databinding.ItemTopBinding
import com.example.foldledger.ui.model.Categories
import com.example.foldledger.util.DateUtils
import com.example.foldledger.util.MoneyFormat

class TopAdapter : ListAdapter<Transaction, TopAdapter.VH>(Diff) {

    companion object {
        private val Diff = object : DiffUtil.ItemCallback<Transaction>() {
            override fun areItemsTheSame(a: Transaction, b: Transaction) = a.id == b.id
            override fun areContentsTheSame(a: Transaction, b: Transaction) = a == b
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemTopBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(b)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(getItem(position), position + 1)
    }

    class VH(private val b: ItemTopBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(t: Transaction, rank: Int) {
            b.tvRank.text = rank.toString()
            b.tvEmoji.text = Categories.emojiOf(t.category)
            b.tvCategory.text = Categories.labelOf(t.category)
            b.tvDate.text = DateUtils.formatDate(t.date)
            b.tvAmount.text = "¥${MoneyFormat.format(t.amount)}"
        }
    }
}
