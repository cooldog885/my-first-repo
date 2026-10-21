package com.example.foldledger.ui

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.foldledger.MainActivity
import com.example.foldledger.R
import com.example.foldledger.data.entities.CategoryEntity
import com.example.foldledger.data.entities.Transaction
import com.example.foldledger.databinding.FragmentEntryBinding
import com.example.foldledger.ui.model.Analytics
import com.example.foldledger.ui.model.Categories
import com.example.foldledger.ui.model.PaymentMethods
import com.example.foldledger.ui.viewmodel.LedgerViewModel
import com.example.foldledger.util.DateUtils
import com.example.foldledger.util.MoneyFormat
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlin.math.abs
import kotlinx.coroutines.launch
import java.util.Calendar

class EntryFragment : Fragment() {

    private var _binding: FragmentEntryBinding? = null
    private val binding get() = _binding!!

    private val vm: LedgerViewModel by lazy {
        (requireActivity() as MainActivity).viewModel
    }

    private var selectedDate: Long = System.currentTimeMillis()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentEntryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        buildCategoryChips()
        binding.chipCategories.setOnCheckedStateChangeListener { _, _ -> syncDraft() }
        setupPaymentSpinner()
        setupTypeToggle()
        setupDatePicker()
        setupDailyLimit()

        // 分类变化时（含自定义增删）重建选择区
        vm.categories.observe(viewLifecycleOwner) { buildCategoryChips() }

        // 还原草稿 / 编辑态
        populateFromDraft()

        // 双栏模式下，从明细页发起编辑时实时回填
        vm.editEvent.observe(viewLifecycleOwner) { t ->
            if (t != null) populateFromDraft()
        }

        binding.btnSave.setOnClickListener { onSave() }

        vm.transactions.observe(viewLifecycleOwner) { list ->
            updateWarnings(list)
        }
        updateWarnings(vm.transactions.value ?: emptyList())
    }

    /** 依据当前草稿回填表单（新增 / 编辑 / 配置变更后均适用） */
    private fun populateFromDraft() {
        val d = vm.draft.value ?: LedgerViewModel.Draft()
        selectedDate = d.date
        binding.etAmount.setText(d.amountText)
        binding.etNote.setText(d.note)
        binding.tvDate.text = DateUtils.formatDate(selectedDate)
        if (d.type == "income") {
            binding.typeToggle.check(R.id.btnIncome)
        } else {
            binding.typeToggle.check(R.id.btnExpense)
        }
        if (d.category.isNotBlank()) {
            binding.chipCategories.clearCheck()
            for (i in 0 until binding.chipCategories.childCount) {
                val c = binding.chipCategories.getChildAt(i) as Chip
                if (c.tag == d.category) binding.chipCategories.check(c.id)
            }
        } else {
            binding.chipCategories.clearCheck()
        }
        val payPos = PaymentMethods.ALL.indexOfFirst { it.key == d.paymentMethod }
            .coerceAtLeast(0)
        binding.spPayment.setSelection(payPos)
    }

    /** 自定义分类调色板 */
    private val CUSTOM_COLORS = listOf(
        0xFFE57373.toInt(), 0xFF4FC3F7.toInt(), 0xFFBA68C8.toInt(), 0xFF81C784.toInt(),
        0xFFFFB74D.toInt(), 0xFF4DB6AC.toInt(), 0xFF7986CB.toInt(), 0xFFA1887F.toInt(),
        0xFF90A4AE.toInt(), 0xFFF06292.toInt(), 0xFF9575CD.toInt(), 0xFF4DD0E1.toInt()
    )

    private fun buildCategoryChips() {
        binding.chipCategories.removeAllViews()
        val cats = vm.categories.value ?: emptyList()
        cats.forEach { e ->
            val chip = Chip(requireContext()).apply {
                text = "${e.emoji} ${e.label}"
                tag = e.key
                isCheckable = true
                isClickable = true
            }
            binding.chipCategories.addView(chip)
        }
        // 「＋ 自定义」入口
        val add = Chip(requireContext()).apply {
            text = "＋ 自定义"
            tag = "add_custom"
            isCheckable = false
            isClickable = true
            setOnClickListener { openCategoryManager() }
        }
        binding.chipCategories.addView(add)

        // 还原当前草稿所选分类
        val sel = vm.draft.value?.category ?: ""
        if (sel.isNotBlank()) {
            for (i in 0 until binding.chipCategories.childCount) {
                val c = binding.chipCategories.getChildAt(i)
                if (c is Chip && c.tag == sel) {
                    binding.chipCategories.check(c.id)
                    break
                }
            }
        }
    }

    /** 管理自定义分类：列出可删，并提供新增入口 */
    private fun openCategoryManager() {
        val ctx = requireContext()
        val scroll = ScrollView(ctx)
        var dialogRef: androidx.appcompat.app.AlertDialog? = null
        val container = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 24)
        }
        val addBtn = MaterialButton(ctx).apply {
            text = "＋ 添加自定义分类"
            setOnClickListener { openAddCategoryDialog() }
        }
        container.addView(addBtn)

        val customs = (vm.categories.value ?: emptyList()).filter { !it.isPreset }
        if (customs.isEmpty()) {
            container.addView(TextView(ctx).apply {
                text = "暂无自定义分类"
                setPadding(0, 24, 0, 8)
            })
        } else {
            customs.forEach { e ->
                val row = LinearLayout(ctx).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                }
                val label = TextView(ctx).apply {
                    text = "${e.emoji} ${e.label}"
                    textSize = 16f
                }
                row.addView(label, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                row.addView(MaterialButton(ctx).apply {
                    text = "删除"
                    setOnClickListener {
                        vm.deleteCategory(e)
                        Toast.makeText(ctx, "已删除：${e.label}", Toast.LENGTH_SHORT).show()
                        dialogRef?.dismiss()
                        openCategoryManager()
                    }
                })
                container.addView(row)
            }
        }
        scroll.addView(container)
        dialogRef = MaterialAlertDialogBuilder(ctx)
            .setTitle("管理分类")
            .setView(scroll)
            .setPositiveButton("完成", null)
            .show()
    }

    /** 新增自定义分类对话框 */
    private fun openAddCategoryDialog() {
        val ctx = requireContext()
        val layout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 24)
        }
        val nameEdit = TextInputEditText(ctx).apply { hint = "分类名称" }
        layout.addView(TextInputLayout(ctx).apply { addView(nameEdit) })

        val emojis = listOf(
            "🍔", "🚌", "🛍️", "🏠", "🎮", "💊", "📚", "📱",
            "📦", "☕", "🐱", "⚽", "💡", "✈️", "🎁", "🍱"
        )
        var picked = emojis[0]
        val emojiGroup = ChipGroup(ctx)
        emojis.forEach { em ->
            emojiGroup.addView(Chip(ctx).apply { text = em; isCheckable = true })
        }
        emojiGroup.setOnCheckedStateChangeListener { g, ids ->
            if (ids.isNotEmpty()) {
                picked = (g.findViewById(ids[0]) as? Chip)?.text?.toString() ?: picked
            }
        }
        layout.addView(TextView(ctx).apply { text = "选择图标"; setPadding(0, 12, 0, 4) })
        layout.addView(emojiGroup)

        MaterialAlertDialogBuilder(ctx)
            .setTitle("添加分类")
            .setView(layout)
            .setPositiveButton("添加") { _, _ ->
                val name = nameEdit.text.toString().trim()
                if (name.isNotBlank()) {
                    val color = CUSTOM_COLORS[abs(name.hashCode()) % CUSTOM_COLORS.size]
                    vm.addCategory(name, picked, color)
                    Toast.makeText(ctx, "已添加：$name", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun setupPaymentSpinner() {
        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            PaymentMethods.ALL.map { it.label }
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spPayment.adapter = adapter
        binding.spPayment.onItemSelectedListener =
            object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    p: android.widget.AdapterView<*>, v: View?, pos: Int, id: Long
                ) = syncDraft()
                override fun onNothingSelected(p: android.widget.AdapterView<*>) = Unit
            }
    }

    private fun setupTypeToggle() {
        binding.typeToggle.addOnButtonCheckedListener { _, _, _ -> syncDraft(); applyTypeUi() }
        applyTypeUi()
    }

    private fun applyTypeUi() {
        val isExpense = binding.typeToggle.checkedButtonId == R.id.btnExpense
        binding.chipCategories.visibility = if (isExpense) View.VISIBLE else View.GONE
        binding.tvCategoryTitle.visibility = if (isExpense) View.VISIBLE else View.GONE
        binding.spPayment.visibility = if (isExpense) View.VISIBLE else View.GONE
        binding.tvPaymentTitle.visibility = if (isExpense) View.VISIBLE else View.GONE
    }

    private fun setupDatePicker() {
        binding.tvDate.setOnClickListener {
            val c = Calendar.getInstance().apply { timeInMillis = selectedDate }
            DatePickerDialog(
                requireContext(),
                { _, y, m, day ->
                    c.set(y, m, day)
                    selectedDate = c.timeInMillis
                    binding.tvDate.text = DateUtils.formatDate(selectedDate)
                    syncDraft()
                },
                c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)
            ).show()
        }
    }

    private fun setupDailyLimit() {
        binding.tvDailyLimit.setOnClickListener { openLimitDialog() }
    }

    private fun openLimitDialog() {
        val edit = android.widget.EditText(requireContext()).apply {
            setText(vm.dailyLimit.toLong().takeIf { vm.dailyLimit % 1 == 0.0 }?.toString() ?: vm.dailyLimit.toString())
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            hint = "每日支出上限（元）"
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("设置每日支出上限")
            .setView(edit)
            .setPositiveButton("保存") { _, _ ->
                val v = edit.text.toString().toDoubleOrNull()
                if (v != null && v > 0) {
                    vm.saveDailyLimit(v)
                    updateWarnings(vm.transactions.value ?: emptyList())
                    Toast.makeText(requireContext(), "已保存", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun currentType(): String =
        if (binding.typeToggle.checkedButtonId == R.id.btnIncome) "income" else "expense"

    private fun currentCategory(): String {
        val id = binding.chipCategories.checkedChipId
        if (id == View.NO_ID) return ""
        val chip = binding.chipCategories.findViewById<Chip>(id)
        return chip?.tag as? String ?: ""
    }

    private fun syncDraft() {
        vm.draft.value = LedgerViewModel.Draft(
            type = currentType(),
            amountText = binding.etAmount.text.toString(),
            category = if (currentType() == "expense") currentCategory() else "",
            paymentMethod = PaymentMethods.ALL.getOrNull(binding.spPayment.selectedItemPosition)?.key
                ?: "other",
            date = selectedDate,
            note = binding.etNote.text.toString()
        )
    }

    private fun updateWarnings(list: List<Transaction>) {
        val (inc, exp) = Analytics.monthSummary(list)
        // 赤字预警
        if (exp > inc) {
            binding.tvDeficit.visibility = View.VISIBLE
            binding.tvDeficit.text =
                "⚠️ 本月已赤字 ¥${MoneyFormat.format(exp - inc)}（收入 ¥${MoneyFormat.format(inc)} / 支出 ¥${MoneyFormat.format(exp)}）"
        } else {
            binding.tvDeficit.visibility = View.GONE
        }
        // 每日限额提示
        val today = Analytics.todayExpense(list)
        binding.tvDailyLimit.visibility = View.VISIBLE
        val limit = vm.dailyLimit
        val ratio = if (limit > 0) (today / limit * 100).toInt() else 0
        binding.tvDailyLimit.text =
            "今日已支出 ¥${MoneyFormat.format(today)} / 上限 ¥${MoneyFormat.format(limit)}（${ratio}%）"
        binding.tvDailyLimit.setTextColor(
            if (limit > 0 && today > limit) 0xFFC62828.toInt() else 0xFF607D8B.toInt()
        )
    }

    private fun onSave() {
        syncDraft()
        val draft = vm.draft.value ?: return
        val result = vm.upsert(draft)
        val t = result.getOrElse {
            Toast.makeText(requireContext(), it.message ?: "保存失败", Toast.LENGTH_SHORT).show()
            return
        }
        // 每日超限提醒（仅支出）
        if (t.type == "expense") {
            lifecycleScope.launch {
                val total = vm.todayExpenseWith(t.amount)
                if (vm.dailyLimit > 0 && total > vm.dailyLimit) {
                    Toast.makeText(
                        requireContext(),
                        "⚠️ 今日支出已超上限 ¥${MoneyFormat.format(vm.dailyLimit)}（合计 ¥${MoneyFormat.format(total)}）",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
        Toast.makeText(
            requireContext(),
            if (vm.editingId != null) "已更新" else "已保存",
            Toast.LENGTH_SHORT
        ).show()
        vm.resetDraft()
        // 清空输入
        binding.etAmount.text?.clear()
        binding.etNote.text?.clear()
        binding.chipCategories.clearCheck()
        binding.typeToggle.check(R.id.btnExpense)
        selectedDate = System.currentTimeMillis()
        binding.tvDate.text = DateUtils.formatDate(selectedDate)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
