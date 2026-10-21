package com.example.foldledger.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.foldledger.MainActivity
import com.example.foldledger.data.entities.Transaction
import com.example.foldledger.databinding.FragmentAnalysisBinding
import com.example.foldledger.ui.adapters.TopAdapter
import com.example.foldledger.ui.charts.BarChartView
import com.example.foldledger.ui.charts.LineChartView
import com.example.foldledger.ui.charts.PieChartView
import com.example.foldledger.ui.model.Analytics
import com.example.foldledger.ui.model.Categories
import com.example.foldledger.util.DateUtils
import com.example.foldledger.util.MoneyFormat
import com.example.foldledger.ui.viewmodel.LedgerViewModel

class AnalysisFragment : Fragment() {

    private var _binding: FragmentAnalysisBinding? = null
    private val binding get() = _binding!!

    private val vm: LedgerViewModel by lazy { (requireActivity() as MainActivity).viewModel }

    private lateinit var topAdapter: TopAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAnalysisBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        topAdapter = TopAdapter()
        binding.rvTop.layoutManager = LinearLayoutManager(requireContext())
        binding.rvTop.adapter = topAdapter

        vm.transactions.observe(viewLifecycleOwner) { list ->
            render(list)
        }
    }

    private fun render(list: List<Transaction>) {
        // 本月概览
        val (inc, exp) = Analytics.monthSummary(list)
        val balance = inc - exp
        binding.tvIncome.text = "¥${MoneyFormat.format(inc)}"
        binding.tvExpense.text = "¥${MoneyFormat.format(exp)}"
        binding.tvBalance.text = "¥${MoneyFormat.format(balance)}"
        binding.tvBalance.setTextColor(
            if (balance >= 0) 0xFF2E7D32.toInt() else 0xFFC62828.toInt()
        )

        // 饼图：本月分类占比
        val s = DateUtils.startOfMonth(System.currentTimeMillis())
        val e = DateUtils.endOfMonth(System.currentTimeMillis())
        val sums = Analytics.categorySums(list, s, e)
        binding.pieChart.centerTotal = exp
        binding.pieChart.centerLabel = "本月支出"
        binding.pieChart.slices = sums.map {
            PieChartView.Slice(it.first.label, it.second, it.first.color)
        }

        // 折线：近 6 个月趋势
        val trend = Analytics.monthlyTrend(list, 6)
        binding.lineChart.points = trend.map {
            LineChartView.Point(it.first, it.second)
        }

        // 条形：分类 TOP5（全部）
        val top5 = Analytics.topCategories(list, 5)
        binding.barChart.bars = top5.map {
            BarChartView.Bar(it.first.label, it.second, it.first.color)
        }

        // TOP10 单笔
        topAdapter.submitList(Analytics.topAmounts(list, 10))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
