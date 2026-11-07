package com.bgmanager.data.repository

import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.bgmanager.data.models.AppInfo
import com.bgmanager.data.models.RiskLevel
import com.bgmanager.data.models.ServiceAction
import com.bgmanager.data.models.ServiceInfo
import com.bgmanager.utils.PermissionAnalyzer
import com.bgmanager.utils.PowerEstimator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Repository mode NON-ROOT.
 *
 * Keterbatasan Android 8+:
 * - getRunningServices() hanya return service milik app sendiri
 * - getRunningAppProcesses() return semua proses tapi tanpa detail service
 * - Estimasi power dari permission, bukan battery stats langsung
 */
class ServiceRepository(private val context: Context) {

    private val activityManager =
        context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    private val packageManager = context.packageManager

    suspend fun getRunningApps(): List<AppInfo> = withContext(Dispatchers.IO) {
        val runningProcesses = activityManager.runningAppProcesses ?: return@withContext emptyList()
        val startTime = System.currentTimeMillis()

        runningProcesses
            .filter { it.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_SERVICE }
            .mapNotNull { process ->
                buildAppInfo(process, startTime)
            }
            .sortedByDescending { it.wattEstimateMw }
    }

    private fun buildAppInfo(
        process: ActivityManager.RunningAppProcessInfo,
        startTime: Long
    ): AppInfo? {
        val packageName = process.pkgList?.firstOrNull() ?: return null

        return try {
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            val appName = packageManager.getApplicationLabel(appInfo).toString()
            val icon = packageManager.getApplicationIcon(packageName)

            // Ambil permissions
            val permissions = try {
                val pkgInfo = packageManager.getPackageInfo(
                    packageName,
                    PackageManager.GET_PERMISSIONS
                )
                pkgInfo.requestedPermissions?.toList() ?: emptyList()
            } catch (e: Exception) { emptyList() }

            // Estimasi RAM
            val memInfo = activityManager.getProcessMemoryInfo(intArrayOf(process.pid))
            val ramMb = if (memInfo.isNotEmpty()) {
                (memInfo[0].totalPss / 1024)
            } else 0

            // Estimasi power dari permission
            val powerProfile = PowerEstimator.estimateFromPermissions(permissions, 1)

            // Analisis risiko
            val analysis = PermissionAnalyzer.analyze(
                packageName = packageName,
                appName = appName,
                permissions = permissions,
                serviceNames = emptyList(), // non-root gak bisa lihat service names
                isSystemApp = (appInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
            )

            // Non-root: service info sangat terbatas
            val services = listOf(
                ServiceInfo(
                    name = "BackgroundProcess",
                    type = importanceToType(process.importance),
                    wattMw = powerProfile.estimateMw,
                    durationSec = (startTime - (process.lastTrimLevel * 1000L)).coerceAtLeast(0) / 1000,
                    pid = process.pid
                )
            )

            AppInfo(
                packageName = packageName,
                appName = appName,
                icon = icon,
                ramUsageMb = ramMb,
                wattEstimateMw = powerProfile.estimateMw,
                uptimeSec = 0L, // tidak tersedia tanpa root
                totalServiceTimeSec = 0L,
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

    /**
     * Hentikan sementara — non-root hanya bisa kill background processes.
     */
    fun stopTemporary(packageName: String): Result<Unit> {
        return try {
            activityManager.killBackgroundProcesses(packageName)
            Result.success(Unit)
        } catch (e: SecurityException) {
            Result.failure(e)
        }
    }

    /**
     * Force stop tidak tersedia tanpa root di Android 8+.
     */
    fun forceStop(packageName: String): Result<Unit> {
        return Result.failure(
            UnsupportedOperationException("Force stop butuh akses root di Android 8+")
        )
    }

    /**
     * Disable permanen tidak tersedia tanpa root.
     */
    fun disablePermanent(packageName: String): Result<Unit> {
        return Result.failure(
            UnsupportedOperationException("Nonaktifkan permanen butuh akses root")
        )
    }

    private fun importanceToType(importance: Int): String = when (importance) {
        ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND -> "Foreground"
        ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND_SERVICE -> "Foreground Service"
        ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE -> "Visible"
        ActivityManager.RunningAppProcessInfo.IMPORTANCE_SERVICE -> "Service"
        ActivityManager.RunningAppProcessInfo.IMPORTANCE_CACHED -> "Cached"
        else -> "Background"
    }
}
