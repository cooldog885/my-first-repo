package com.example.foldledger

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.example.foldledger.databinding.ActivityMainBinding
import com.example.foldledger.ui.AnalysisFragment
import com.example.foldledger.ui.EntryFragment
import com.example.foldledger.ui.ListFragment
import com.example.foldledger.ui.viewmodel.LedgerViewModel
import com.example.foldledger.util.ExportCsv
import com.example.foldledger.util.ImportCsv
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class MainActivity : AppCompatActivity() {

    /** 跨 Fragment 共享的 ViewModel */
    val viewModel: LedgerViewModel by lazy {
        androidx.lifecycle.ViewModelProvider(
            this,
            androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.getInstance(application)
        )[LedgerViewModel::class.java]
    }

    private val entryFragment = EntryFragment()
    private val listFragment = ListFragment()
    private val analysisFragment = AnalysisFragment()

    private var isTwoPane = false

    /** 导入 CSV：选择文件后解析并追加入库 */
    private val importLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) importCsv(uri) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isTwoPane = resources.configuration.smallestScreenWidthDp >= 720
        setContentView(R.layout.activity_main)

        setSupportActionBar(findViewById(R.id.app_toolbar))
        supportActionBar?.title = getString(R.string.app_name)

        if (isTwoPane) {
            // 内屏：双栏 —— 左录入、右上下明细+分析
            supportFragmentManager.beginTransaction()
                .replace(R.id.pane_entry, entryFragment)
                .replace(R.id.pane_list, listFragment)
                .replace(R.id.pane_analysis, analysisFragment)
                .commit()
        } else {
            // 外屏：单栏 + 底部导航
            supportFragmentManager.beginTransaction()
                .replace(R.id.nav_host_single, entryFragment, "entry")
                .commit()
            val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_nav)
            bottomNav.selectedItemId = R.id.nav_entry
            bottomNav.setOnItemSelectedListener { item ->
                when (item.itemId) {
                    R.id.nav_entry -> switchTo(entryFragment, "entry")
                    R.id.nav_list -> switchTo(listFragment, "list")
                    R.id.nav_analysis -> switchTo(analysisFragment, "analysis")
                }
                true
            }
        }
    }

    private fun switchTo(frag: Fragment, tag: String) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.nav_host_single, frag, tag)
            .commit()
    }

    /** 供 ListFragment 在单栏模式下跳转到录入页（双栏模式下录入始终可见，无需操作） */
    fun navigateToEntry() {
        if (!isTwoPane) {
            findViewById<BottomNavigationView>(R.id.bottom_nav)?.selectedItemId = R.id.nav_entry
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.top_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean =
        when (item.itemId) {
            R.id.action_export -> { exportData(); true }
            R.id.action_import -> {
                importLauncher.launch(
                    arrayOf(
                        "text/csv",
                        "text/comma-separated-values",
                        "application/octet-stream",
                        "*/*"
                    )
                )
                true
            }
            else -> super.onOptionsItemSelected(item)
        }

    /** 导出全部记录为 CSV 到「下载」目录，并弹出分享 */
    private fun exportData() {
        val list = viewModel.transactions.value ?: emptyList()
        if (list.isEmpty()) {
            Toast.makeText(this, "暂无数据可导出", Toast.LENGTH_SHORT).show()
            return
        }
        val result = ExportCsv.export(this, list)
        if (result == null) {
            Toast.makeText(this, "导出失败", Toast.LENGTH_SHORT).show()
            return
        }
        val (uri: Uri, name: String) = result
        Toast.makeText(this, "已导出到 下载/$name", Toast.LENGTH_LONG).show()
        val share = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(share, "导出完成"))
    }

    /** 导入 CSV：解析后让用户选择导入方式 */
    private fun importCsv(uri: Uri) {
        val result = ImportCsv.parse(this, uri)
        if (result.transactions.isEmpty()) {
            Toast.makeText(
                this,
                "没有可导入的数据（跳过 ${result.skipped} 条）",
                Toast.LENGTH_LONG
            ).show()
            return
        }
        val count = result.transactions.size
        MaterialAlertDialogBuilder(this)
            .setTitle("导入方式")
            .setMessage("共解析出 $count 条可导入记录，跳过 ${result.skipped} 条。请选择导入方式：")
            .setPositiveButton("清空后导入") { _, _ ->
                viewModel.replaceTransactions(result.transactions)
                Toast.makeText(this, "已清空并导入 $count 条", Toast.LENGTH_LONG).show()
            }
            .setNegativeButton("追加") { _, _ ->
                viewModel.importTransactions(result.transactions)
                Toast.makeText(this, "已追加 $count 条", Toast.LENGTH_LONG).show()
            }
            .setNeutralButton("去重导入") { _, _ ->
                viewModel.importUniqueTransactions(result.transactions)
                Toast.makeText(this, "已按去重导入", Toast.LENGTH_LONG).show()
            }
            .show()
    }
}
