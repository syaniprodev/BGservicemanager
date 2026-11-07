package com.bgmanager.data.models

data class AppInfo(
    val packageName: String,
    val appName: String,
    val icon: android.graphics.drawable.Drawable?,
    val ramUsageMb: Int,
    val wattEstimateMw: Int,
    val uptimeSec: Long,
    val totalServiceTimeSec: Long,
    val services: List<ServiceInfo>,
    val riskLevel: RiskLevel,
    val riskReason: String,
    val permissions: List<String>,
    val actions: List<ServiceAction>
)

data class ServiceInfo(
    val name: String,
    val type: String,
    val wattMw: Int,
    val durationSec: Long,
    val pid: Int = -1
)

enum class RiskLevel {
    RED,    // bloatware/tracker — matiin
    YELLOW, // opsional
    GREEN   // penting/sistem
}

enum class ServiceAction {
    STOP_TEMPORARY,
    FORCE_STOP,
    DISABLE_PERMANENT
}
