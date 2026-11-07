package com.bgmanager.utils

/**
 * Estimasi konsumsi daya berdasarkan tipe service dan permission.
 *
 * Nilai baseline diambil dari rata-rata profil konsumsi chipset mid-range.
 * Mode root: bisa baca /proc/uid_cputime untuk data lebih akurat.
 * Mode non-root: estimasi berdasarkan permission + tipe service.
 */
object PowerEstimator {

    // Baseline mW per tipe aktivitas
    private const val BASE_CPU_IDLE_MW = 5
    private const val GPS_ACTIVE_MW = 85
    private const val GPS_PASSIVE_MW = 15
    private const val NETWORK_WIFI_MW = 30
    private const val NETWORK_CELLULAR_MW = 60
    private const val CAMERA_MW = 400
    private const val BLUETOOTH_MW = 10
    private const val SENSOR_MW = 12
    private const val WAKELOCK_PENALTY_MW = 20

    data class PowerProfile(
        val estimateMw: Int,
        val breakdown: Map<String, Int>  // komponen -> mW
    )

    /**
     * Estimasi dari permission yang dimiliki APK (non-root).
     */
    fun estimateFromPermissions(permissions: List<String>, serviceCount: Int): PowerProfile {
        val breakdown = mutableMapOf<String, Int>()
        var total = BASE_CPU_IDLE_MW * serviceCount
        breakdown["CPU idle"] = BASE_CPU_IDLE_MW * serviceCount

        if ("android.permission.ACCESS_FINE_LOCATION" in permissions) {
            breakdown["GPS aktif"] = GPS_ACTIVE_MW
            total += GPS_ACTIVE_MW
        } else if ("android.permission.ACCESS_COARSE_LOCATION" in permissions) {
            breakdown["GPS pasif"] = GPS_PASSIVE_MW
            total += GPS_PASSIVE_MW
        }

        if ("android.permission.INTERNET" in permissions) {
            breakdown["Network"] = NETWORK_WIFI_MW
            total += NETWORK_WIFI_MW
        }

        if ("android.permission.BLUETOOTH" in permissions ||
            "android.permission.BLUETOOTH_SCAN" in permissions) {
            breakdown["Bluetooth"] = BLUETOOTH_MW
            total += BLUETOOTH_MW
        }

        if ("android.permission.BODY_SENSORS" in permissions ||
            "android.permission.ACTIVITY_RECOGNITION" in permissions) {
            breakdown["Sensor"] = SENSOR_MW
            total += SENSOR_MW
        }

        if ("android.permission.WAKE_LOCK" in permissions) {
            breakdown["Wakelock"] = WAKELOCK_PENALTY_MW
            total += WAKELOCK_PENALTY_MW
        }

        return PowerProfile(estimateMw = total, breakdown = breakdown)
    }

    /**
     * Estimasi dari /proc/uid_cputime — butuh root atau shell.
     * Mengembalikan mW berdasarkan waktu CPU yang dikonsumsi.
     * @param cpuTimeMs waktu CPU dalam millisecond
     * @param cpuFreqMhz frekuensi CPU (opsional, default mid-range)
     */
    fun estimateFromCpuTime(cpuTimeMs: Long, cpuFreqMhz: Int = 1800): Int {
        // Rumus sederhana: power (mW) = energi (mWh) / waktu (h)
        // Asumsi: 1 GHz ≈ 100mW pada mid-range SoC
        val mwPerGhz = 100
        val freqGhz = cpuFreqMhz / 1000.0
        val estimatedMw = (freqGhz * mwPerGhz * (cpuTimeMs / 1000.0 / 3600.0) * 3600).toInt()
        return estimatedMw.coerceAtLeast(BASE_CPU_IDLE_MW)
    }

    fun formatMw(mw: Int): String {
        return if (mw >= 1000) "%.1fW".format(mw / 1000.0) else "${mw}mW"
    }

    fun getRiskColorForWatt(mw: Int): WattRisk {
        return when {
            mw >= 150 -> WattRisk.HIGH
            mw >= 80  -> WattRisk.MEDIUM
            else       -> WattRisk.LOW
        }
    }

    enum class WattRisk { HIGH, MEDIUM, LOW }
}
