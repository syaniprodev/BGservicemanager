package com.bgmanager.utils

import com.topjohnwu.superuser.Shell

/**
 * Wrapper untuk operasi yang butuh akses root via libsu.
 * Semua fungsi suspend — jalankan di coroutine.
 */
object RootUtils {

    // Cek apakah root tersedia dan sudah di-grant
    fun isRootAvailable(): Boolean {
        return Shell.getShell().isRoot
    }

    fun requestRoot(): Boolean {
        return try {
            val result = Shell.cmd("id").exec()
            result.isSuccess && result.out.any { it.contains("uid=0") }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Ambil semua service yang berjalan via `dumpsys activity services`.
     * Return: list string mentah dari dumpsys.
     */
    fun getRunningServicesRoot(): List<String> {
        val result = Shell.cmd("dumpsys activity services").exec()
        return if (result.isSuccess) result.out else emptyList()
    }

    /**
     * Ambil info proses lengkap via `ps -A`.
     */
    fun getAllProcesses(): List<String> {
        val result = Shell.cmd("ps -A").exec()
        return if (result.isSuccess) result.out else emptyList()
    }

    /**
     * Paksa berhenti APK via `am force-stop`.
     */
    fun forceStop(packageName: String): Boolean {
        val result = Shell.cmd("am force-stop $packageName").exec()
        return result.isSuccess
    }

    /**
     * Nonaktifkan APK permanen via `pm disable-user`.
     * Hanya works untuk APK sistem atau dengan root.
     */
    fun disablePackage(packageName: String): Boolean {
        val result = Shell.cmd("pm disable-user --user 0 $packageName").exec()
        return result.isSuccess
    }

    /**
     * Re-enable APK yang sudah dinonaktifkan.
     */
    fun enablePackage(packageName: String): Boolean {
        val result = Shell.cmd("pm enable --user 0 $packageName").exec()
        return result.isSuccess
    }

    /**
     * Baca /proc/uid_cputime untuk estimasi CPU usage per UID.
     * Return map: uid -> cpuTimeMs
     */
    fun getCpuTimePerUid(): Map<Int, Long> {
        val result = Shell.cmd("cat /proc/uid_cputime/show_uid_stat").exec()
        if (!result.isSuccess) return emptyMap()

        return result.out.mapNotNull { line ->
            // Format: uid: user_time sys_time
            val parts = line.trim().split("\\s+".toRegex())
            if (parts.size >= 3) {
                val uid = parts[0].trimEnd(':').toIntOrNull()
                val userTime = parts[1].toLongOrNull()
                val sysTime = parts[2].toLongOrNull()
                if (uid != null && userTime != null && sysTime != null) {
                    uid to (userTime + sysTime)
                } else null
            } else null
        }.toMap()
    }

    /**
     * Ambil battery stats per package.
     * Return: map packageName -> drain mAh
     */
    fun getBatteryStatsPerPackage(): Map<String, Double> {
        val result = Shell.cmd("dumpsys batterystats --charged").exec()
        if (!result.isSuccess) return emptyMap()

        val map = mutableMapOf<String, Double>()
        var currentPkg: String? = null

        result.out.forEach { line ->
            val pkgMatch = Regex("Package: (\\S+)").find(line)
            if (pkgMatch != null) currentPkg = pkgMatch.groupValues[1]

            val drainMatch = Regex("Drain: ([0-9.]+)mAh").find(line)
            if (drainMatch != null && currentPkg != null) {
                map[currentPkg!!] = drainMatch.groupValues[1].toDoubleOrNull() ?: 0.0
            }
        }
        return map
    }

    /**
     * Parse output `dumpsys activity services` jadi list package + service name.
     */
    fun parseRunningServices(dumpsysLines: List<String>): List<Pair<String, String>> {
        val result = mutableListOf<Pair<String, String>>()
        val serviceRegex = Regex("\\* ServiceRecord\\{.*\\s+(\\S+)/(\\S+)\\}")

        dumpsysLines.forEach { line ->
            val match = serviceRegex.find(line)
            if (match != null) {
                val pkg = match.groupValues[1]
                val svc = match.groupValues[2].substringAfterLast(".")
                result.add(pkg to svc)
            }
        }
        return result
    }
}
