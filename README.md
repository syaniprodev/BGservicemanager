# BG Service Manager

Aplikasi Android untuk memantau, menganalisis, dan menghentikan background service yang berjalan di perangkat.

## Fitur

- Lihat semua APK & service yang berjalan di background
- Estimasi konsumsi daya (mW) per APK dan per service
- Durasi uptime & total waktu service
- Sort: paling boros, A–Z, Z–A, uptime, total waktu, tingkat bahaya
- Analisis otomatis berdasarkan permission (tracker, bloatware, dsb)
- Saran merah/kuning/hijau (berbahaya / opsional / penting)
- Aksi: Hentikan Sementara, Paksa Berhenti, Nonaktifkan Permanen
- **Mode Root** (full access) & **Mode Non-Root** (akses terbatas)

## Mode Akses

| Fitur | Non-Root | Root |
|---|---|---|
| Lihat proses berjalan | ✅ terbatas | ✅ lengkap |
| Detail service per APK | ❌ | ✅ |
| Stop APK lain | ✅ terbatas | ✅ penuh |
| Nonaktifkan permanen | ❌ | ✅ |
| Estimasi watt akurat | ⚠️ estimasi | ✅ akurat |
| Lihat service sistem | ❌ | ✅ |

## Requirements

- Android 8.0+ (API 26+)
- Untuk mode root: perangkat sudah di-root (Magisk/KernelSU)
- Dependency: [libsu](https://github.com/topjohnwu/libsu) untuk akses root

## Setup

1. Clone repo ini
2. Buka di Android Studio
3. Sync Gradle
4. Build & install ke perangkat

```bash
git clone https://github.com/username/BGServiceManager.git
cd BGServiceManager
./gradlew assembleDebug
```

## Struktur Project

```
app/src/main/java/com/bgmanager/
├── ui/
│   ├── MainActivity.kt
│   ├── fragments/
│   │   └── AppListFragment.kt
│   └── adapters/
│       └── AppServiceAdapter.kt
├── data/
│   ├── models/
│   │   ├── AppInfo.kt
│   │   └── ServiceInfo.kt
│   └── repository/
│       ├── ServiceRepository.kt
│       └── RootServiceRepository.kt
├── service/
│   └── MonitorService.kt
└── utils/
    ├── RootUtils.kt
    ├── PermissionAnalyzer.kt
    └── PowerEstimator.kt
```

## Disclaimer

Aplikasi ini hanya untuk keperluan edukasi dan optimasi performa pribadi.
Menghentikan service sistem dapat menyebabkan ketidakstabilan perangkat.
