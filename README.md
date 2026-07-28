# PromptOps LogParser

Aplikasi Android native offline untuk membaca, memfilter, dan merapikan file log mentah
(`.txt`, `.log`, logcat GitHub Actions) agar mudah dibaca di HP. Baris yang mengandung
kata "Error"/"Exception"/"Fatal"/"Crash" disorot merah, dan "Warning"/"Deprecated" disorot kuning.

## Fitur v1.0 (Initial Release)
- Buka file log via Storage Access Framework (mendukung `.txt`, `.log`, dan file tanpa ekstensi seperti export logcat CI)
- Parsing baris-per-baris sepenuhnya offline, tanpa izin storage klasik
- RecyclerView dengan font monospace agar log tetap presisi dibaca
- Highlight otomatis:
  - Merah: `error`, `exception`, `fatal`, `crash`
  - Kuning: `warn`, `warning`, `deprecated`
- Filter kata kunci real-time (case-insensitive)
- Toggle "Error/Exception saja" untuk menyaring hanya baris bermasalah
- Counter jumlah baris ditampilkan vs total baris file
- Empty state saat belum ada file dibuka
- Tema gelap bawaan agar nyaman dibaca lama

## Arsitektur
- 100% Kotlin, native Android (tanpa dependency cloud/API)
- View Binding, RecyclerView, Material Components
- minSdk 24, targetSdk/compileSdk 34
- CI: GitHub Actions (`.github/workflows/build.yml`) — build & sign APK release otomatis setiap push ke `main`

## Build & Signing
Keystore rilis dikelola lewat GitHub Secrets:
- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

Alias key: `promptopslogparser`
