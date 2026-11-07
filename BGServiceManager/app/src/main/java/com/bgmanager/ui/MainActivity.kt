package com.bgmanager.ui

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.bgmanager.R
import com.bgmanager.databinding.ActivityMainBinding
import com.bgmanager.ui.fragments.AppListFragment
import com.bgmanager.utils.RootUtils
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var isRootMode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        // Cek apakah usage stats permission sudah diberikan
        if (!hasUsageStatsPermission()) {
            showUsageStatsDialog()
        }

        // Cek dan request root
        checkRootAccess()

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, AppListFragment.newInstance(isRootMode))
                .commit()
        }

        // Toggle root/non-root
        binding.fabRootToggle.setOnClickListener {
            toggleRootMode()
        }

        updateRootFab()
    }

    private fun checkRootAccess() {
        isRootMode = RootUtils.isRootAvailable()
        if (!isRootMode) {
            // Coba request root
            val granted = RootUtils.requestRoot()
            isRootMode = granted
            if (!granted) {
                Snackbar.make(
                    binding.root,
                    "Mode non-root: fitur terbatas",
                    Snackbar.LENGTH_LONG
                ).setAction("Info") {
                    showRootInfoDialog()
                }.show()
            }
        }
    }

    private fun toggleRootMode() {
        if (!RootUtils.isRootAvailable() && !isRootMode) {
            showRootInfoDialog()
            return
        }
        isRootMode = !isRootMode
        updateRootFab()

        // Reload fragment dengan mode baru
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, AppListFragment.newInstance(isRootMode))
            .commit()

        Snackbar.make(
            binding.root,
            if (isRootMode) "Mode root aktif — akses penuh" else "Mode non-root",
            Snackbar.LENGTH_SHORT
        ).show()
    }

    private fun updateRootFab() {
        binding.fabRootToggle.text = if (isRootMode) "ROOT" else "NO ROOT"
        binding.fabRootToggle.setIconResource(
            if (isRootMode) R.drawable.ic_root_on else R.drawable.ic_root_off
        )
    }

    private fun showRootInfoDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Akses Root")
            .setMessage(
                "Mode non-root memiliki keterbatasan:\n\n" +
                "• Tidak bisa lihat detail service APK lain\n" +
                "• Tidak bisa force stop semua APK\n" +
                "• Tidak bisa nonaktifkan APK sistem\n" +
                "• Estimasi watt kurang akurat\n\n" +
                "Untuk fitur penuh, root perangkat kamu dengan Magisk atau KernelSU."
            )
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showUsageStatsDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Permission Diperlukan")
            .setMessage("Izinkan akses Usage Stats untuk melihat penggunaan APK secara lebih detail.")
            .setPositiveButton("Buka Pengaturan") { _, _ ->
                startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            }
            .setNegativeButton("Nanti", null)
            .show()
    }

    private fun hasUsageStatsPermission(): Boolean {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_refresh -> {
                (supportFragmentManager.findFragmentById(R.id.fragment_container)
                    as? AppListFragment)?.refresh()
                true
            }
            R.id.action_root_info -> {
                showRootInfoDialog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
