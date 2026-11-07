package com.bgmanager.ui.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bgmanager.R
import com.bgmanager.data.models.AppInfo
import com.bgmanager.data.models.RiskLevel
import com.bgmanager.data.models.ServiceAction
import com.bgmanager.databinding.ItemAppServiceBinding

class AppServiceAdapter(
    private val onActionClick: (AppInfo, ServiceAction) -> Unit
) : ListAdapter<AppInfo, AppServiceAdapter.ViewHolder>(DiffCallback) {

    private val expandedIds = mutableSetOf<String>()

    inner class ViewHolder(
        private val binding: ItemAppServiceBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(app: AppInfo) {
            binding.appName.text = app.appName
            binding.appPkg.text = app.packageName
            binding.appIcon.setImageDrawable(app.icon)

            // RAM + watt badges
            binding.badgeRam.text = "${app.ramUsageMb} MB"
            binding.badgeWatt.text = formatMw(app.wattEstimateMw)
            binding.badgeUptime.text = formatDuration(app.uptimeSec)

            // Risk indicator
            val (dotColor, badgeBg, badgeText) = riskColors(app.riskLevel)
            binding.riskDot.setBackgroundColor(dotColor)
            binding.badgeRisk.text = riskLabel(app.riskLevel)
            binding.badgeRisk.setTextColor(badgeText)
            binding.badgeRisk.setBackgroundColor(badgeBg)

            // Energy bar
            val pct = (app.wattEstimateMw / 300f).coerceIn(0f, 1f)
            binding.energyBar.progress = (pct * 100).toInt()
            binding.energyBar.progressTintList =
                android.content.res.ColorStateList.valueOf(wattBarColor(app.wattEstimateMw))

            // Expand/collapse
            val isExpanded = app.packageName in expandedIds
            binding.detailPanel.visibility = if (isExpanded) View.VISIBLE else View.GONE
            binding.expandIcon.rotation = if (isExpanded) 180f else 0f

            binding.appRow.setOnClickListener {
                if (isExpanded) expandedIds.remove(app.packageName)
                else expandedIds.add(app.packageName)
                notifyItemChanged(adapterPosition)
            }

            if (isExpanded) bindDetail(app)
        }

        private fun bindDetail(app: AppInfo) {
            // Warning box
            if (app.riskReason.isNotBlank() && app.riskLevel != RiskLevel.GREEN) {
                binding.warnBox.visibility = View.VISIBLE
                binding.warnText.text = app.riskReason
            } else {
                binding.warnBox.visibility = View.GONE
            }

            // Mini stat cards
            binding.statUptime.text = formatDuration(app.uptimeSec)
            binding.statTotalTime.text = formatDuration(app.totalServiceTimeSec)
            binding.statWattDetail.text = formatMw(app.wattEstimateMw)

            // Service list
            binding.serviceList.removeAllViews()
            val inflater = LayoutInflater.from(binding.root.context)
            app.services.forEach { svc ->
                val svcView = inflater.inflate(R.layout.item_service, binding.serviceList, false)
                svcView.findViewById<android.widget.TextView>(R.id.svcName).text = svc.name
                svcView.findViewById<android.widget.TextView>(R.id.svcType).text = svc.type
                svcView.findViewById<android.widget.TextView>(R.id.svcDur).text =
                    formatDuration(svc.durationSec)
                svcView.findViewById<android.widget.TextView>(R.id.svcWatt).text =
                    formatMw(svc.wattMw)
                binding.serviceList.addView(svcView)
            }

            // Action buttons — tampilkan sesuai mode root
            binding.btnStopTemp.visibility =
                if (ServiceAction.STOP_TEMPORARY in app.actions) View.VISIBLE else View.GONE
            binding.btnForceStop.visibility =
                if (ServiceAction.FORCE_STOP in app.actions) View.VISIBLE else View.GONE
            binding.btnDisable.visibility =
                if (ServiceAction.DISABLE_PERMANENT in app.actions) View.VISIBLE else View.GONE

            binding.btnStopTemp.setOnClickListener { onActionClick(app, ServiceAction.STOP_TEMPORARY) }
            binding.btnForceStop.setOnClickListener { onActionClick(app, ServiceAction.FORCE_STOP) }
            binding.btnDisable.setOnClickListener { onActionClick(app, ServiceAction.DISABLE_PERMANENT) }
        }

        private fun riskColors(risk: RiskLevel): Triple<Int, Int, Int> = when (risk) {
            RiskLevel.RED    -> Triple(0xFFE24B4A.toInt(), 0xFFFCEBEB.toInt(), 0xFFA32D2D.toInt())
            RiskLevel.YELLOW -> Triple(0xFFEF9F27.toInt(), 0xFFFAEEDA.toInt(), 0xFF854F0B.toInt())
            RiskLevel.GREEN  -> Triple(0xFF639922.toInt(), 0xFFEAF3DE.toInt(), 0xFF3B6D11.toInt())
        }

        private fun riskLabel(risk: RiskLevel) = when (risk) {
            RiskLevel.RED    -> "Matiin"
            RiskLevel.YELLOW -> "Perhatian"
            RiskLevel.GREEN  -> "Penting"
        }

        private fun wattBarColor(mw: Int) = when {
            mw >= 150 -> 0xFFE24B4A.toInt()
            mw >= 80  -> 0xFFEF9F27.toInt()
            else       -> 0xFF639922.toInt()
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemAppServiceBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    private object DiffCallback : DiffUtil.ItemCallback<AppInfo>() {
        override fun areItemsTheSame(a: AppInfo, b: AppInfo) = a.packageName == b.packageName
        override fun areContentsTheSame(a: AppInfo, b: AppInfo) = a == b
    }
}

fun formatDuration(sec: Long): String = when {
    sec <= 0  -> "—"
    sec < 60  -> "${sec}d"
    sec < 3600 -> "${sec / 60}m"
    sec < 86400 -> "${sec / 3600}j"
    sec < 604800 -> "${sec / 86400}h"
    else -> "${sec / 604800}mg"
}

fun formatMw(mw: Int): String =
    if (mw >= 1000) "%.1fW".format(mw / 1000.0) else "${mw}mW"
