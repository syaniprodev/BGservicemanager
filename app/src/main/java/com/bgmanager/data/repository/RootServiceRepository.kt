package com.bgmanager.data.repository

import android.content.Context
import android.content.pm.PackageManager
import com.bgmanager.data.models.AppInfo
import com.bgmanager.data.models.ServiceAction
import com.bgmanager.data.models.ServiceInfo
import com.bgmanager.utils.PermissionAnalyzer
import com.bgmanager.utils.PowerEstimator
import com.bgmanager.utils.RootUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Repository mode ROOT.
 * Akses penuh via libsu: dumpsys, ps, am force-stop, pm disable.
 */
class RootServiceRepository(private val context: Context) {

    private val packageManager = context.packageManager

    suspend fun getRunningApps(): List<AppInfo> = withContext(Dispatchers.IO) {
        if (!RootUtils.isRootAvailable()) return@withContext emptyList()

        // Ambil semua service via dumpsys
        val dumpsysLines = RootUtils.getRunningServicesRoot()
        val servicesByPkg = RootUtils.parseRunningServices(dumpsysLines)
            .groupBy({ it.first }, { it.second })

        // Ambil CPU time per UID untuk estimasi power akurat
        val cpuTimeByUid = RootUtils.getCpuTimePerUid()

        // Ambil battery drain per package
        val batteryDrain = RootUtils.getBatteryStatsPerPackage()

        // Semua proses dari `ps -A`
        val processes = RootUtils.getAllProcesses()
        val processMap = parseProcessList(processes) // map pkgName -> (pid, ram)

        // Build AppInfo per package yang punya service
        servicesByPkg.keys.mapNotNull { pkgName ->
            buildRootAppInfo(
                pkgName = pkgName,
                serviceNames = servicesByPkg[pkgName] ?: emptyList(),
                cpuTimeByUid = cpuTimeByUid,
                batteryDrain = batteryDrain,
                processMap = processMap
            )
        }.sortedByDescending { it.wattEstimateMw }
    }

    private fun buildRootAppInfo(
        pkgName: String,
        serviceNames: List<String>,
        cpuTimeByUid: Map<Int, Long>,
        batteryDrain: Map<String, Double>,
        processMap: Map<String, ProcessEntry>
    ): AppInfo? {
        return try {
            val appInfo = packageManager.getApplicationInfo(pkgName, 0)
            val appName = packageManager.getApplicationLabel(appInfo).toString()
            val icon = packageManager.getApplicationIcon(pkgName)

            val permissions = try {
                val pkgInfo = packageManager.getPackageInfo(
                    pkgName, PackageManager.GET_PERMISSIONS
                )
                pkgInfo.requestedPermissions?.toList() ?: emptyList()
            } catch (e: Exception) { emptyList() }

            val uid = appInfo.uid
            val cpuTime = cpuTimeByUid[uid] ?: 0L
            val drain = batteryDrain[pkgName] ?: 0.0
            val proc = processMap[pkgName]

            // Estimasi power — kalau ada data CPU, pakai itu; kalau tidak, dari permission
            val wattMw = if (cpuTime > 0) {
                PowerEstimator.estimateFromCpuTime(cpuTime)
            } else {
                PowerEstimator.estimateFromPermissions(permissions, serviceNames.size).estimateMw
            }

            // Build detail per service
            val services = serviceNames.mapIndexed { i, svcName ->
                ServiceInfo(
                    name = svcName,
                    type = guessServiceType(svcName, permissions),
                    wattMw = (wattMw / serviceNames.size.coerceAtLeast(1)),
                    durationSec = proc?.uptimeSec ?: 0L,
                    pid = proc?.pid ?: -1
                )
            }

            val analysis = PermissionAnalyzer.analyze(
                packageName = pkgName,
                appName = appName,
                permissions = permissions,
                serviceNames = serviceNames,
                isSystemApp = (appInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
            )

            AppInfo(
                packageName = pkgName,
                appName = appName,
                icon = icon,
                ramUsageMb = proc?.ramMb ?: 0,
                wattEstimateMw = wattMw,
                uptimeSec = proc?.uptimeSec ?: 0L,
                totalServiceTimeSec = proc?.uptimeSec?.times(7) ?: 0L, // estimasi 7x sesi
                services = services,
                riskLevel = analysis.riskLevel,
                riskReason = analysis.reason,
                permissions = permissions,
                actions = analysis.actions
            )
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
    }

    fun forceStop(packageName: String): Result<Unit> {
        return if (RootUtils.forceStop(packageName)) Result.success(Unit)
        else Result.failure(RuntimeException("Gagal force stop $packageName"))
    }

    fun disablePermanent(packageName: String): Result<Unit> {
        return if (RootUtils.disablePackage(packageName)) Result.success(Unit)
        else Result.failure(RuntimeException("Gagal nonaktifkan $packageName"))
    }

    fun enablePackage(packageName: String): Result<Unit> {
        return if (RootUtils.enablePackage(packageName)) Result.success(Unit)
        else Result.failure(RuntimeException("Gagal aktifkan $packageName"))
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    data class ProcessEntry(val pid: Int, val ramMb: Int, val uptimeSec: Long)

    private fun parseProcessList(lines: List<String>): Map<String, ProcessEntry> {
        val map = mutableMapOf<String, ProcessEntry>()
        lines.drop(1).forEach { line ->
            val parts = line.trim().split("\\s+".toRegex())
            if (parts.size >= 9) {
                val pid = parts[1].toIntOrNull() ?: return@forEach
                val rss = parts[5].toIntOrNull() ?: 0
                val name = parts.last()
                map[name] = ProcessEntry(
                    pid = pid,
                    ramMb = rss / 1024,
                    uptimeSec = 0L // perlu parsing dari stat, disederhanakan
                )
            }
        }
        return map
    }

    private fun guessServiceType(serviceName: String, permissions: List<String>): String {
        val lower = serviceName.lowercase()
        return when {
            lower.contains("sync")       -> "Sync"
            lower.contains("push") ||
            lower.contains("notif")      -> "Notifikasi"
            lower.contains("track") ||
            lower.contains("analytic")   -> "Tracking"
            lower.contains("ads") ||
            lower.contains("ad_")        -> "Iklan"
            lower.contains("location") ||
            lower.contains("gps")        -> "Lokasi"
            lower.contains("backup")     -> "Backup"
            lower.contains("update")     -> "Update"
            lower.contains("auth")       -> "Auth"
            lower.contains("call") ||
            lower.contains("phone")      -> "Telepon"
            lower.contains("media") ||
            lower.contains("upload")     -> "Upload"
            "android.permission.ACCESS_FINE_LOCATION" in permissions -> "Lokasi"
            else                         -> "Background"
        }
    }
}
