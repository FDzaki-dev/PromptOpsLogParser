# PromptOps LogParser

Aplikasi Android native **offline-first** untuk membaca, memfilter, dan merapikan file log
mentah (`.txt`, `.log`, `.zip` archive dari GitHub Actions/logcat) agar mudah dibaca di HP.
Baris "Error"/"Exception"/"Fatal"/"Crash" disorot merah, "Warning"/"Deprecated" disorot kuning,
kata kunci custom milik pengguna disorot ungu.

**Status: v1.7 · Personal use only · Tidak akan dirilis ke Play Store**

> Riwayat lengkap setiap versi ada di [`CHANGELOG.md`](./CHANGELOG.md). File ini sengaja
> dibuat ringkas & terkini — kalau kamu (Claude) memulai sesi baru untuk melanjutkan proyek
> ini, baca file ini dulu sebelum bertanya ulang ke user soal fitur apa yang sudah ada.

## Fitur Saat Ini (v1.7)
- Buka `.txt` / `.log` / `.zip` (auto-cari entri log pertama di dalam ZIP) via SAF
- Highlight otomatis: Error (merah), Warning (kuning), Kata Kunci Custom (ungu, dikelola user)
- Filter kata kunci real-time + toggle "Error/Exception & Custom saja"
- Peringatan persisten kalau file besar (>200KB) terpotong
- **Analisis Offline (Gratis)** — heuristik on-device, nol biaya, nol internet
- **Analisis AI (Cloud)** — panggilan nyata ke Claude (Anthropic API), API key milik user sendiri
- Riwayat 20 hasil analisis terakhir (offline & AI)
- **File Terkini**: hingga 10 file terakhir dibuka, tap untuk buka ulang (persistable URI permission)
- **Bagikan / Simpan hasil analisis**: share teks ke app lain, atau simpan sebagai `.json` via SAF
- Copy hasil analisis / crash log ke clipboard
- Dialog Bantuan dinamis: crash terakhir + kejadian terbaru + FAQ 7 poin
- Crash handler global (mencatat crash ke file lokal, self-diagnosing)
- App icon final (adaptive + legacy fallback), lokalisasi Inggris otomatis (`values-en/`)

## Status Roadmap
| Batch | Isi | Status |
|---|---|---|
| 1 | Keamanan (EncryptedSharedPreferences) + peringatan file terpotong | ✅ Selesai (v1.4) |
| 2a | Recent files + Save/Share hasil analisis | ✅ Selesai (v1.7) |
| 2b | Regex search + Badge counter | ⏳ Belum dikerjakan |
| 2c | Settings screen + Light mode | ⏳ Belum dikerjakan |
| 3 | Offline enhancement (custom keyword, riwayat analisis) | ✅ Selesai (v1.6) |
| 4 | Troubleshooting sejati (crash log, dialog dinamis, copy log) | ✅ Selesai (v1.6) |
| 5 | Kosmetik (app icon final, lokalisasi Inggris) | ✅ Selesai (v1.5) |

**Sengaja di luar cakupan** (keputusan user): fitur AI/Cloud tidak diprioritaskan (khawatir
biaya token, fokus ke mode Offline); item kesiapan rilis publik (privacy policy, aksesibilitas
TalkBack) di-skip karena app ini personal-use only.

## Arsitektur
- 100% Kotlin, native Android, View Binding, RecyclerView, Material Components
- minSdk 24, targetSdk/compileSdk 34
- Package: `com.fdzaki.promptopslogparser` (`.ai/`, `.diagnostics/`, `.scanner/` sub-package)
- CI: GitHub Actions (`.github/workflows/build.yml`) — build & sign APK release otomatis tiap push ke `main`

## Build & Signing
Keystore rilis dikelola lewat GitHub Secrets: `ANDROID_KEYSTORE_BASE64`,
`ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`.
Alias key: `promptopslogparser`.

## Workflow Update Standar (Termux)
```
LATEST_ZIP=$(ls -t ~/storage/downloads/PromptOpsLogParser*.zip | head -1) && echo "Pakai ZIP: $LATEST_ZIP" && cd ~/projects/PromptOpsLogParser && find . -mindepth 1 -maxdepth 1 ! -name '.git' -exec rm -rf {} + && cd ~/projects && unzip -o "$LATEST_ZIP" -d ~/projects/ && cd ~/projects/PromptOpsLogParser && git add -A && git commit -m "<ringkasan spesifik>" && git push
```
**Kalau `git push` ditolak (rejected):** `git pull --no-rebase origin main --no-edit && git push`
**Kalau merge conflict:** `git checkout --ours <file_yang_konflik>` lalu `git add <file>` → commit → push
