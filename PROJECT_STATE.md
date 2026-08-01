# PROJECT_STATE.md

> File ini untuk **Claude** (AI), bukan untuk dibaca manusia. Baca ini DULUAN setiap kali
> melanjutkan proyek ini di sesi baru manapun, sebelum menyentuh kode atau bertanya ulang
> ke user soal fitur apa yang sudah ada.
>
> Fitur & status singkat → `README.md`. History lengkap per versi → `CHANGELOG.md`.
> Daftar file terkini → `FILE_MANIFEST.txt`.

## Versi & Batch Terakhir
- Versi: **v1.7** (versionCode 8)
- Batch terakhir selesai: **2a** — Recent Files + Save/Share hasil analisis
- Batch berikutnya: **2b** (Regex search + Badge counter), lalu **2c** (Settings screen + Light mode)

## Insiden & Pelajaran (kronologis — JANGAN dihapus, ini riwayat pencegahan regresi)
- **2026-08-01**: ZIP v1.7 pertama dikirim TANPA dibungkus folder `PromptOpsLogParser/` di
  root ZIP-nya. Saat di-unzip ke `~/projects/`, isi nyebar langsung ke situ (bukan masuk ke
  `~/projects/PromptOpsLogParser/`), sementara folder proyek sudah dikosongkan duluan oleh
  langkah `rm -rf` di command update. `git add -A` mencatat semua file lama sebagai
  terhapus → ter-push → repo GitHub tampak kosong. Dipulihkan via `git revert`.
  **Sejak insiden ini**: semua ZIP WAJIB root-nya berupa folder `PromptOpsLogParser/` (bukan
  file lepas), dan update WAJIB lewat `scripts/safe_update.sh` (lihat di bawah), bukan
  command manual — script itu punya circuit breaker otomatis untuk mencegah insiden ini
  terulang.

## Keputusan Arsitektur yang Tidak Boleh Dilanggar
- AlertDialog native Android cuma dukung 3 tombol (positive/negative/neutral). Dialog yang
  butuh lebih dari 3 aksi (mis. dialog hasil analisis: Close/Copy/Share/Simpan/Ganti API Key)
  WAJIB pakai custom container (`LinearLayout` + `Button` manual di `actionRow`), BUKAN
  memaksakan aksi tambahan ke 3 slot native.
- `LogLevel` dan `LogClassifier` didefinisikan **di dalam** `LogEntry.kt`, bukan file terpisah.
- Semua kelas `*Store.kt` (`CustomKeywordStore`, `AnalysisHistoryStore`, `RecentFilesStore`)
  memakai pola yang sama: `SharedPreferences` + JSON array, 100% lokal, tanpa network.
  Ikuti pola ini untuk store baru, jangan reinvent.
- Fitur AI/Cloud (Anthropic API) sengaja tidak diprioritaskan — fokus ke mode Offline
  (keputusan user, alasan biaya token). Jangan usulkan roadmap ke arah situ kecuali diminta
  ulang secara eksplisit.
- App personal-use only, tidak akan rilis Play Store — skip item kesiapan rilis publik
  (privacy policy, aksesibilitas TalkBack) kecuali diminta ulang.
- Tema saat ini hardcode dark (`bg_dark` dst di `themes.xml`) walau parent theme
  `DayNight` — Light Mode BELUM benar-benar wired. Itu bagian dari Batch 2c nanti, jangan
  diasumsikan sudah ada.

## Struktur Package
- `com.fdzaki.promptopslogparser` (root): `MainActivity`, `LogEntry` (+`LogLevel`+
  `LogClassifier`), `LogAdapter`, `CustomKeywordStore`, `RecentFilesStore`,
  `PromptOpsApplication`
- `.ai/`: `AiLogAnalyzer`, `LocalLogAnalyzer`, `ApiKeyStore`, `AnalysisHistoryStore`
- `.diagnostics/`: `AppDiagnostics`, `CrashHandler`
- `.scanner/`: `ZipLogExtractor`, `LogPromptBuilder`

## Cara Update Project (Termux) — WAJIB pakai script, bukan command manual
```
bash ~/projects/PromptOpsLogParser/scripts/safe_update.sh
```
Script ini otomatis:
1. Cek entry pertama ZIP harus `PromptOpsLogParser/...` — kalau salah, berhenti, TIDAK ada
   file yang dihapus.
2. Hitung jumlah file sebelum & sesudah unzip — kalau turun >30%, commit dibatalkan
   (file lama masih bisa dipulihkan via `git checkout -- . && git clean -fd` karena belum
   di-commit).
3. Update `FILE_MANIFEST.txt` otomatis dari `git ls-files`.
4. Tampilkan `git status --short` sebelum minta konfirmasi pesan commit.
5. Push, lalu ingatkan cek tab Actions (CI) di GitHub.

## Checklist Sebelum Claude Mengirim ZIP Baru (self-check, jangan skip)
- [ ] `unzip -l` hasil ZIP → entry pertama adalah `PromptOpsLogParser/`
- [ ] Jumlah file tidak turun drastis dari versi sebelumnya (kecuali penghapusan diminta)
- [ ] `versionCode`/`versionName` di `app/build.gradle.kts` sudah dinaikkan
- [ ] `README.md` (status + tabel roadmap) dan `CHANGELOG.md` sudah diperbarui
- [ ] `PROJECT_STATE.md` ini diperbarui kalau ada keputusan arsitektur baru atau insiden baru
