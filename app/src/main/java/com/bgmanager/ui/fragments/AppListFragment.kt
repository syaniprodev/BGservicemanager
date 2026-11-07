package com.bgmanager.ui.fragments



import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.bgmanager.data.models.AppInfo
import com.bgmanager.data.models.ServiceAction
import com.bgmanager.data.models.SortMode
import com.bgmanager.data.repository.RootServiceRepository
import com.bgmanager.data.repository.ServiceRepository
import com.bgmanager.databinding.FragmentAppListBinding
import com.bgmanager.ui.adapters.AppServiceAdapter
import com.google.android.material.chip.Chip
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

class AppListFragment : Fragment() {

    private var _binding: FragmentAppListBinding? = null
    private val binding get() = _binding!!

    private var isRootMode = false
    private lateinit var adapter: AppServiceAdapter
    private var allApps: List<AppInfo> = emptyList()
    private var currentSort = SortMode.WATT_DESC

    private val rootRepo by lazy { RootServiceRepository(requireContext()) }
    private val normalRepo by lazy { ServiceRepository(requireContext()) }

    companion object {
        fun newInstance(isRoot: Boolean) = AppListFragment().apply {
            arguments = Bundle().apply { putBoolean("is_root", isRoot) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isRootMode = arguments?.getBoolean("is_root") ?: false
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAppListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupSortChips()
        loadApps()

        binding.swipeRefresh.setOnRefreshListener { loadApps() }

        // Banner mode
        binding.modeBanner.text = if (isRootMode)
            "Mode root — akses penuh" else "Mode non-root — fitur terbatas"
        binding.modeBanner.setBackgroundColor(
            if (isRootMode) 0xFF1D9E75.toInt() else 0xFFBA7517.toInt()
        )
    }

    private fun setupRecyclerView() {
        adapter = AppServiceAdapter(
            onActionClick = { app, action -> handleAction(app, action) }
        )
        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@AppListFragment.adapter
        }
    }

    private fun setupSortChips() {
        val chips = mapOf(
            binding.chipWatt      to SortMode.WATT_DESC,
            binding.chipAz        to SortMode.AZ,
            binding.chipZa        to SortMode.ZA,
            binding.chipUptime    to SortMode.UPTIME_DESC,
            binding.chipTotalTime to SortMode.TOTAL_TIME_DESC,
            binding.chipRisk      to SortMode.RISK
        )
        chips.forEach { (chip, mode) ->
            chip.setOnClickListener {
                currentSort = mode
                applySort()
            }
        }
    }

    fun refresh() = loadApps()

    private fun loadApps() {
        binding.swipeRefresh.isRefreshing = true
        binding.emptyState.visibility = View.GONE

        lifecycleScope.launch {
            allApps = if (isRootMode) rootRepo.getRunningApps()
                      else normalRepo.getRunningApps()

            binding.swipeRefresh.isRefreshing = false
            applySort()
            updateStats()

            if (allApps.isEmpty()) {
                binding.emptyState.visibility = View.VISIBLE
            }
        }
    }

    private fun applySort() {
        val sorted = when (currentSort) {
            SortMode.WATT_DESC       -> allApps.sortedByDescending { it.wattEstimateMw }
            SortMode.AZ              -> allApps.sortedBy { it.appName }
            SortMode.ZA              -> allApps.sortedByDescending { it.appName }
            SortMode.UPTIME_DESC     -> allApps.sortedByDescending { it.uptimeSec }
            SortMode.TOTAL_TIME_DESC -> allApps.sortedByDescending { it.totalServiceTimeSec }
            SortMode.RISK            -> allApps.sortedBy { it.riskLevel.ordinal }
        }
        adapter.submitList(sorted)
    }

    private fun updateStats() {
        val totalWatt = allApps.sumOf { it.wattEstimateMw }
        val red = allApps.count { it.riskLevel == com.bgmanager.data.models.RiskLevel.RED }
        val yellow = allApps.count { it.riskLevel == com.bgmanager.data.models.RiskLevel.YELLOW }

        binding.statTotal.text = "${allApps.size} APK"
        binding.statWatt.text = formatMw(totalWatt)
        binding.statRed.text = "$red berbahaya"
        binding.statYellow.text = "$yellow perhatian"
    }

    private fun handleAction(app: AppInfo, action: ServiceAction) {
        val result = when (action) {
            ServiceAction.STOP_TEMPORARY   ->
                if (isRootMode) Result.success(Unit)
                else normalRepo.stopTemporary(app.packageName)

            ServiceAction.FORCE_STOP       ->
                if (isRootMode) rootRepo.forceStop(app.packageName)
                else normalRepo.forceStop(app.packageName)

            ServiceAction.DISABLE_PERMANENT ->
                if (isRootMode) rootRepo.disablePermanent(app.packageName)
                else normalRepo.disablePermanent(app.packageName)
        }

        result.fold(
            onSuccess = {
                Snackbar.make(
                    binding.root,
                    "${app.appName}: ${action.label()} berhasil",
                    Snackbar.LENGTH_SHORT
                ).show()
                loadApps()
            },
            onFailure = { e ->
                Snackbar.make(
                    binding.root,
                    e.message ?: "Gagal",
                    Snackbar.LENGTH_LONG
                ).setAction("Detail") {
                    if (!isRootMode) showRootNeededSnackbar()
                }.show()
            }
        )
    }

    private fun showRootNeededSnackbar() {
        Snackbar.make(
            binding.root,
            "Fitur ini butuh akses root. Aktifkan mode root di toolbar.",
            Snackbar.LENGTH_LONG
        ).show()
    }

    private fun formatMw(mw: Int) =
        if (mw >= 1000) "%.1f W".format(mw / 1000.0) else "$mw mW"

    private fun ServiceAction.label() = when (this) {
        ServiceAction.STOP_TEMPORARY    -> "Dihentikan sementara"
        ServiceAction.FORCE_STOP        -> "Dipaksa berhenti"
        ServiceAction.DISABLE_PERMANENT -> "Dinonaktifkan permanen"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

