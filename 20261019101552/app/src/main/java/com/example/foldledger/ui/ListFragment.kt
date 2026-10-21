package com.example.foldledger.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.foldledger.MainActivity
import com.example.foldledger.data.entities.Transaction
import com.example.foldledger.databinding.FragmentListBinding
import com.example.foldledger.ui.adapters.ListItem
import com.example.foldledger.ui.adapters.TransactionAdapter
import com.example.foldledger.ui.viewmodel.LedgerViewModel
import com.example.foldledger.util.DateUtils
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class ListFragment : Fragment() {

    private var _binding: FragmentListBinding? = null
    private val binding get() = _binding!!

    private val vm: LedgerViewModel by lazy { (requireActivity() as MainActivity).viewModel }

    private lateinit var adapter: TransactionAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = TransactionAdapter(
            onEdit = { t -> onEdit(t) },
            onDelete = { t -> confirmDelete(t) }
        )
        binding.rvList.layoutManager = LinearLayoutManager(requireContext())
        binding.rvList.adapter = adapter

        vm.transactions.observe(viewLifecycleOwner) { list ->
            submit(list)
        }
    }

    private fun submit(list: List<Transaction>) {
        if (list.isEmpty()) {
            binding.tvEmpty.visibility = View.VISIBLE
            binding.rvList.visibility = View.GONE
            adapter.submitList(emptyList())
            return
        }
        binding.tvEmpty.visibility = View.GONE
        binding.rvList.visibility = View.VISIBLE

        val sorted = list.sortedWith(
            compareByDescending<Transaction> { it.date }.thenByDescending { it.createdAt }
        )
        val items = mutableListOf<ListItem>()
        sorted.groupBy { DateUtils.dayStart(it.date) }.forEach { (day, recs) ->
            val dailyExpense = recs.filter { it.type == "expense" }.sumOf { it.amount }
            items.add(ListItem.Header(day, dailyExpense, recs.size))
            recs.forEach { items.add(ListItem.Record(it)) }
        }
        adapter.submitList(items)
    }

    private fun onEdit(t: Transaction) {
        vm.startEdit(t)
        (requireActivity() as MainActivity).navigateToEntry()
    }

    private fun confirmDelete(t: Transaction) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("删除记录")
            .setMessage("确定要删除这条记录吗？此操作不可撤销。")
            .setPositiveButton("删除") { _, _ -> vm.delete(t) }
            .setNegativeButton("取消", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
