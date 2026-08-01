#!/data/data/com.termux/files/usr/bin/bash
# scripts/safe_update.sh
#
# Jaring pengaman update project — baca PROJECT_STATE.md untuk detail insiden 2026-08-01
# yang melatarbelakangi script ini. Jalankan ini untuk SETIAP update project, JANGAN
# pakai command manual delete+unzip+commit lagi.
#
# Cara pakai: bash ~/projects/PromptOpsLogParser/scripts/safe_update.sh

set -e

PROJECT_DIR="$HOME/projects/PromptOpsLogParser"
ZIP_PREFIX="PromptOpsLogParser"
DOWNLOAD_DIR="$HOME/storage/downloads"

LATEST_ZIP=$(ls -t "$DOWNLOAD_DIR"/${ZIP_PREFIX}*.zip 2>/dev/null | head -1)
if [ -z "$LATEST_ZIP" ]; then
  echo "❌ Tidak ada file ZIP ${ZIP_PREFIX}*.zip ditemukan di $DOWNLOAD_DIR"
  exit 1
fi
echo "📦 Pakai ZIP: $LATEST_ZIP"

# --- Cek 1: struktur ZIP harus dibungkus folder proyek yang benar, SEBELUM apapun dihapus ---
TOP_ENTRY=$(unzip -l "$LATEST_ZIP" | awk 'NR==4{print $4}')
case "$TOP_ENTRY" in
  ${ZIP_PREFIX}/*)
    echo "✅ Struktur ZIP OK ($TOP_ENTRY)"
    ;;
  *)
    echo "⚠️  DIBATALKAN: entry pertama ZIP bukan '${ZIP_PREFIX}/...' (dapat: '$TOP_ENTRY')."
    echo "⚠️  ZIP kemungkinan tidak dibungkus folder proyek yang benar."
    echo "⚠️  Tidak ada file project yang dihapus/diubah. Cek ulang ZIP-nya."
    exit 1
    ;;
esac

cd "$PROJECT_DIR"
BEFORE=$(find . -type f ! -path './.git/*' | wc -l)
echo "📊 Jumlah file sebelum update: $BEFORE"

find . -mindepth 1 -maxdepth 1 ! -name '.git' -exec rm -rf {} +
cd "$HOME/projects"
unzip -o "$LATEST_ZIP" -d "$HOME/projects/" > /dev/null

cd "$PROJECT_DIR"
AFTER=$(find . -type f ! -path './.git/*' | wc -l)
echo "📊 Jumlah file sesudah update: $AFTER"

# --- Cek 2: circuit breaker jumlah file ---
if [ "$BEFORE" -gt 0 ]; then
  THRESHOLD=$((BEFORE * 70 / 100))
  if [ "$AFTER" -lt "$THRESHOLD" ]; then
    echo ""
    echo "⚠️  PERINGATAN: jumlah file turun drastis ($BEFORE → $AFTER)."
    echo "⚠️  Kemungkinan ZIP rusak / struktur folder salah / ada yang tidak sengaja terhapus."
    echo "⚠️  COMMIT DIBATALKAN. File lama BELUM hilang permanen (belum di-commit)."
    echo "👉 Pulihkan working directory dengan:"
    echo "     git checkout -- . && git clean -fd"
    exit 1
  fi
fi

echo "✅ Structure & file-count check lolos."

# --- Update manifest otomatis ---
git ls-files > FILE_MANIFEST.txt

git add -A

echo ""
echo "📝 Ringkasan perubahan yang akan di-commit:"
git status --short | head -40
echo ""

read -p "Ketik pesan commit lalu Enter (kosongkan untuk batal): " COMMIT_MSG
if [ -z "$COMMIT_MSG" ]; then
  echo "❌ Pesan commit kosong, dibatalkan. (File sudah di-stage, jalankan 'git reset' kalau mau batal total.)"
  exit 1
fi

git commit -m "$COMMIT_MSG"
git push

echo ""
echo "🎉 Selesai. Cek tab Actions di GitHub untuk hasil build CI (jaring pengaman terakhir)."
