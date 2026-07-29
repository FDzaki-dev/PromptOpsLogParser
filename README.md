# PromptOps LogParser

Aplikasi Android native offline untuk membaca, memfilter, dan merapikan file log mentah
(`.txt`, `.log`, logcat GitHub Actions) agar mudah dibaca di HP. Baris yang mengandung
kata "Error"/"Exception"/"Fatal"/"Crash" disorot merah, dan "Warning"/"Deprecated" disorot kuning.

## Fitur v1.5 — Batch 5: App Icon Final + Lokalisasi Inggris
- **App icon baru**: chevron terminal `>` + 4 baris log dengan baris ERROR disorot merah,
  konsisten di adaptive icon (Android 8+) maupun legacy fallback PNG (Android 7.0-7.1 / API 24-25)
- **Lokalisasi bahasa Inggris** (`values-en/`): seluruh label UI & pesan Toast utama otomatis
  berganti ke Inggris kalau bahasa sistem HP di-set ke Inggris — tanpa perlu setting manual di app
- Semua Toast di `MainActivity.kt` dipindah dari hardcoded string ke `strings.xml` (lebih rapi & bisa diterjemahkan)
- **Catatan cakupan**: teks dialog Troubleshooting/Bantuan dan pesan error jaringan di `AiLogAnalyzer`
  masih Indonesia-only untuk saat ini (di luar prioritas batch ini)

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

## Fitur v1.1
- Tambah modul `scanner/` (belum terhubung ke UI):
  - `ZipLogExtractor.kt`: baca entri `.log`/`.txt` pertama langsung dari stream ZIP di memori (tanpa ekstrak ke disk), dibatasi ~200 KB teks agar aman dari OOM
  - `LogPromptBuilder.kt`: bungkus isi log ke template system prompt tetap ("Universal Log Parsing Engine") untuk dikirim ke API LLM, dengan sanitasi marker agar isi log tidak bisa memalsukan batas prompt (prompt-injection safe by construction)
- Catatan: modul ini hanya menyiapkan data & prompt secara lokal — belum ada pemanggilan API LLM aktual maupun tombol UI yang memicunya

## Fitur v1.2 — Analisis AI benar-benar berfungsi
- Dukungan buka file **`.zip`** langsung dari file picker (otomatis cari entri `.log`/`.txt` pertama via `ZipLogExtractor`, tanpa ekstrak ke disk)
- Tombol **"Analisis dengan AI"** aktif setelah file dimuat — memanggil **Claude (Anthropic Messages API)** sungguhan lewat OkHttp, dengan system prompt dari `LogPromptBuilder`
- Dialog input **API key pribadi** (sekali saja) — disimpan lokal di `SharedPreferences` privat aplikasi (`MODE_PRIVATE`), tidak pernah dibundel/hardcode, tidak dikirim ke server manapun selain `api.anthropic.com`
- Hasil analisis (JSON terstruktur: jenis log, status eksekusi, daftar error kritis, dsb.) ditampilkan rapi dalam dialog, bisa di-copy karena teks selectable
- Penanganan error: gagal koneksi, key salah (401 → key otomatis dihapus agar diminta ulang), respons tidak valid
- Permission baru: `INTERNET`, `ACCESS_NETWORK_STATE` (hanya dipakai saat tombol AI ditekan)
- Dependency baru: OkHttp 4.12.0

**Catatan:** butuh API key Anthropic milik Anda sendiri (dari console.anthropic.com), karena aplikasi tidak menyertakan key siapapun secara default.

## Fitur v1.3 — Analisis Offline (Gratis) + Bantuan/Troubleshooting
- **`LocalLogAnalyzer.kt`**: mesin analisis 100% on-device (regex/heuristik), tanpa API key, tanpa internet, **nol biaya**. Menghasilkan skema JSON yang sama persis dengan versi Cloud: jenis log terdeteksi, status eksekusi, daftar critical events, rentang timestamp, dan ringkasan metrik
- Tombol baru **"Analisis Offline (Gratis)"** berdampingan dengan **"Analisis AI (Cloud)"** — pengguna bisa memilih sesuai kebutuhan (instan & gratis vs. lebih kontekstual & berbayar token)
- Tombol **"Bantuan"** di pojok kanan atas — membuka dialog Troubleshooting berisi 7 masalah umum & solusinya: file gagal dibuka, ZIP tanpa log, hasil offline kurang detail, error 401, koneksi gagal, build APK gagal di CI, hingga `git push` ditolak
- Dialog hasil analisis (AI maupun Offline) sekarang memakai komponen yang sama (`showResultDialog`), lebih konsisten

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
