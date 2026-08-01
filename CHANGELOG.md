# Changelog — PromptOps LogParser

Semua perubahan per-versi dicatat di sini (terbaru di atas). Untuk gambaran fitur saat ini &
status roadmap, lihat `README.md`.

## v1.7 — Batch 2a: Recent Files + Save/Share Hasil
- `RecentFilesStore.kt`: hingga 10 file terakhir dibuka (nama + timestamp) disimpan lokal
  (SharedPreferences) — tombol baru **"Terkini"** di baris atas
  - `MainActivity.loadFile()` sekarang mengambil `takePersistableUriPermission` supaya URI tetap
    bisa dibuka lagi setelah aplikasi di-restart
  - Tap salah satu entri langsung membuka ulang file tsb; kalau izin akses sudah hilang
    (mis. file dipindah/dihapus), entri otomatis dibersihkan dari daftar dengan Toast penjelasan
- Dialog hasil Analisis (AI & Offline) kini punya dua tombol baru di baris aksi:
  - **Bagikan**: `ACTION_SEND` teks biasa ke aplikasi lain (WhatsApp/Email/dll)
  - **Simpan sebagai File**: `ACTION_CREATE_DOCUMENT` (SAF) menulis hasil sebagai `.json` ke lokasi
    pilihan pengguna — tidak butuh permission storage tambahan
- Tidak ada perubahan pada fondasi Copy ke Clipboard / Ganti API Key yang sudah ada

## v1.6 — Batch 4 (Troubleshooting Sejati) + Batch 3 (Offline Enhancement)
**Batch 4:**
- `AppDiagnostics.kt` + `CrashHandler.kt` + `PromptOpsApplication.kt`: crash handler global —
  setiap crash fatal otomatis dicatat ke file lokal (`diagnostics/last_crash.txt`), lalu tetap
  diteruskan ke handler sistem default (tidak menelan crash, cuma merekamnya)
- Kejadian non-fatal (gagal baca file/ZIP, error Analisis AI) sekarang juga tercatat ke
  `diagnostics/recent_events.log` (rolling, maks 30 baris)
- Dialog **Bantuan** sekarang dinamis: menampilkan crash terakhir & kejadian terbaru (dengan
  tombol Copy/Hapus) di atas FAQ statis 7 poin yang sudah ada
- Tombol **"Copy ke Clipboard"** ditambahkan di dialog hasil Analisis (AI maupun Offline)

**Batch 3:**
- `LogLevel.CUSTOM` baru: baris yang cocok kata kunci custom disorot **ungu** (`#BA68C8`),
  terpisah dari Error (merah) / Warning (kuning) bawaan
- `CustomKeywordStore.kt`: kata kunci custom disimpan lokal (SharedPreferences), dikelola lewat
  dialog baru — tombol **"Kata Kunci"** di baris atas
  - Reklasifikasi baris log berjalan instan tanpa perlu buka ulang file
  - Baris `CUSTOM` juga ikut ter-filter oleh toggle "Error/Exception saja"
- `AnalysisHistoryStore.kt`: riwayat 20 hasil analisis terakhir (offline maupun AI) tersimpan
  lokal — timestamp, sumber file, engine, status, jumlah error. Diakses lewat tombol **"Riwayat"**
- Row tombol atas (`Buka File`, `Kata Kunci`, `Riwayat`, `Bantuan`) dibungkus `HorizontalScrollView`
  agar tetap aman di layar sempit

## v1.5 — Batch 5: App Icon Final + Lokalisasi Inggris
- App icon baru: chevron terminal `>` + 4 baris log, baris ERROR disorot merah — konsisten di
  adaptive icon (Android 8+, vector) maupun legacy PNG fallback (Android 7.0–7.1 / API 24-25,
  di-generate untuk 5 densitas: mdpi/hdpi/xhdpi/xxhdpi/xxxhdpi)
- Lokalisasi Inggris (`values-en/strings.xml`): seluruh label UI & Toast utama otomatis
  mengikuti bahasa sistem HP
- Semua `Toast.makeText` di `MainActivity.kt` dipindah dari hardcoded string ke `strings.xml`
- Cakupan yang **belum** diterjemahkan (di luar prioritas batch ini): teks dialog
  Troubleshooting/Bantuan, pesan error jaringan di `AiLogAnalyzer.kt`

## v1.4 — Batch 1: Keamanan & Keandalan
- `ApiKeyStore.kt` di-upgrade ke **EncryptedSharedPreferences** (Jetpack Security,
  AES256-GCM, key dibungkus Android Keystore) — key tidak lagi plain text di disk
  - Migrasi otomatis satu-kali dari key plain text versi lama (kalau ada) ke penyimpanan terenkripsi
  - Fallback ke SharedPreferences biasa kalau Keystore perangkat bermasalah (hindari crash)
- Peringatan **persisten** (bukan cuma Toast sekilas) saat file besar (>200KB) terpotong —
  `tvTruncationWarning`, membedakan dua kasus: ZIP besar (tampilan ikut terpotong) vs.
  file teks biasa (tampilan lengkap, hanya kiriman ke AI yang terpotong)
- Dependency baru: `androidx.security:security-crypto:1.1.0-alpha06`

## v1.3 — Analisis Offline (Gratis) + Bantuan/Troubleshooting (versi awal)
- `LocalLogAnalyzer.kt`: mesin analisis 100% on-device (regex/heuristik), tanpa API key, tanpa
  internet, nol biaya. Skema JSON sama persis dengan versi Cloud
- Tombol **"Analisis Offline (Gratis)"** berdampingan dengan **"Analisis AI (Cloud)"**
- Tombol **"Bantuan"** — dialog Troubleshooting statis berisi 7 masalah umum & solusinya
- Dialog hasil analisis (AI & Offline) memakai komponen bersama (`showResultDialog`)

## v1.2 — Analisis AI Benar-Benar Berfungsi
- Dukungan buka file `.zip` langsung dari file picker (otomatis cari entri `.log`/`.txt`
  pertama via `ZipLogExtractor`, tanpa ekstrak ke disk)
- Tombol **"Analisis dengan AI"** — memanggil Claude (Anthropic Messages API) sungguhan lewat
  OkHttp, dengan system prompt dari `LogPromptBuilder`
- Dialog input API key pribadi (sekali saja), disimpan lokal
- Permission baru: `INTERNET`, `ACCESS_NETWORK_STATE`. Dependency baru: OkHttp 4.12.0

## v1.1
- Modul `scanner/` ditambahkan (`ZipLogExtractor.kt`, `LogPromptBuilder.kt`) — belum terhubung
  ke UI, baru menyiapkan fondasi untuk v1.2

## v1.0 — Initial Release
- Buka file log via Storage Access Framework (`.txt`, `.log`, file tanpa ekstensi)
- Parsing baris-per-baris 100% offline
- RecyclerView monospace, highlight Error (merah) / Warning (kuning)
- Filter kata kunci real-time, toggle "Error/Exception saja"
- Counter baris ditampilkan/total, empty state, tema gelap
