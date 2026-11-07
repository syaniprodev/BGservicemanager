package com.bgmanager.utils

import com.bgmanager.data.models.RiskLevel

/**
 * Menganalisis permission APK untuk menentukan level risiko dan alasan.
 * Tidak butuh root — cukup PackageManager.
 */
object PermissionAnalyzer {

    // Package yang dikenal penting — jangan dimatiin
    private val SYSTEM_SAFE = setOf(
        "com.android.phone",
        "com.android.systemui",
        "com.google.android.gms",
        "com.google.android.gsf",
        "com.android.settings",
        "com.whatsapp",
        "com.google.android.apps.maps",
        "com.miHoYo.GenshinImpact",
        "com.android.launcher3"
    )

    // Permission yang menandakan tracker/ads
    private val TRACKER_PERMISSIONS = setOf(
        "android.permission.ACCESS_FINE_LOCATION",
        "android.permission.ACCESS_BACKGROUND_LOCATION",
        "android.permission.READ_CONTACTS",
        "android.permission.READ_CALL_LOG",
        "com.google.android.gms.permission.AD_ID"
    )

    // Permission yang dikombinasi = curiga banget
    private val SUSPICIOUS_COMBO = setOf(
        "android.permission.RECEIVE_BOOT_COMPLETED",
        "android.permission.INTERNET",
        "android.permission.ACCESS_NETWORK_STATE"
    )

    // Keyword nama service yang mencurigakan
    private val SUSPICIOUS_SERVICE_KEYWORDS = listOf(
        "analytic", "tracker", "ads", "ad_", "_ad", "metric",
        "telemetry", "beacon", "collect", "monitor", "spy",
        "push_notification", "spam", "promo", "clickbait"
    )

    // Keyword nama service yang aman
    private val SAFE_SERVICE_KEYWORDS = listOf(
        "phone", "call", "message", "auth", "sync",
        "update", "backup", "location_fg", "navigation"
    )

    data class AnalysisResult(
        val riskLevel: RiskLevel,
        val reason: String,
        val actions: List<com.bgmanager.data.models.ServiceAction>
    )

    fun analyze(
        packageName: String,
        appName: String,
        permissions: List<String>,
        serviceNames: List<String>,
        isSystemApp: Boolean
    ): AnalysisResult {
        // Langsung aman kalau masuk whitelist
        if (packageName in SYSTEM_SAFE) {
            return AnalysisResult(
                riskLevel = RiskLevel.GREEN,
                reason = "Sistem atau aplikasi penting — jangan dimatikan.",
                actions = listOf(
                    com.bgmanager.data.models.ServiceAction.STOP_TEMPORARY
                )
            )
        }

        val trackerPerms = permissions.intersect(TRACKER_PERMISSIONS)
        val hasSuspiciousCombo = SUSPICIOUS_COMBO.all { it in permissions }
        val suspiciousServiceCount = serviceNames.count { svc ->
            SUSPICIOUS_SERVICE_KEYWORDS.any { kw -> svc.lowercase().contains(kw) }
        }
        val hasSuspiciousServiceName = suspiciousServiceCount > 0

        // Skor risiko
        var score = 0
        if (trackerPerms.isNotEmpty()) score += 3
        if (hasSuspiciousCombo) score += 2
        if (hasSuspiciousServiceName) score += suspiciousServiceCount * 2
        if (isSystemApp && packageName.contains("vendor", ignoreCase = true)) score += 2
        if (appName.lowercase().contains("ads") || appName.lowercase().contains("backup")) score += 1

        return when {
            score >= 5 -> AnalysisResult(
                riskLevel = RiskLevel.RED,
                reason = buildRedReason(trackerPerms, hasSuspiciousServiceName, appName),
                actions = listOf(
                    com.bgmanager.data.models.ServiceAction.FORCE_STOP,
                    com.bgmanager.data.models.ServiceAction.DISABLE_PERMANENT
                )
            )
            score >= 2 -> AnalysisResult(
                riskLevel = RiskLevel.YELLOW,
                reason = buildYellowReason(trackerPerms, hasSuspiciousCombo, appName),
                actions = listOf(
                    com.bgmanager.data.models.ServiceAction.STOP_TEMPORARY,
                    com.bgmanager.data.models.ServiceAction.DISABLE_PERMANENT
                )
            )
            else -> AnalysisResult(
                riskLevel = RiskLevel.GREEN,
                reason = "Tidak terdeteksi aktivitas mencurigakan.",
                actions = listOf(
                    com.bgmanager.data.models.ServiceAction.STOP_TEMPORARY,
                    com.bgmanager.data.models.ServiceAction.FORCE_STOP
                )
            )
        }
    }

    private fun buildRedReason(
        trackerPerms: Set<String>,
        hasSuspiciousService: Boolean,
        appName: String
    ): String {
        val parts = mutableListOf<String>()
        if (trackerPerms.isNotEmpty()) parts.add("punya permission tracking lokasi/kontak")
        if (hasSuspiciousService) parts.add("nama service mengandung kata kunci iklan/tracker")
        if (appName.lowercase().contains("ads")) parts.add("nama APK mengandung 'ads'")
        return "Terdeteksi: ${parts.joinToString(", ")}. Sebaiknya dimatikan."
    }

    private fun buildYellowReason(
        trackerPerms: Set<String>,
        hasSuspiciousCombo: Boolean,
        appName: String
    ): String {
        val parts = mutableListOf<String>()
        if (hasSuspiciousCombo) parts.add("autostart + akses internet di background")
        if (trackerPerms.isNotEmpty()) parts.add("ada permission sensitif")
        return "Perhatian: ${parts.joinToString(", ")}. Opsional, tergantung kebutuhan kamu."
    }
}
