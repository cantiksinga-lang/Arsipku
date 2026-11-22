#!/usr/bin/env bash
set -euo pipefail

if [ -f android/app/build.gradle.kts ]; then
  GRADLE=android/app/build.gradle.kts
elif [ -f android/app/build.gradle ]; then
  GRADLE=android/app/build.gradle
else
  echo "File gradle tidak ditemukan"; ls -R android/app | head -50; exit 1
fi
echo "Gradle file: $GRADLE"

# Paksa package = com.alhafidz.app dan minSdk 23 (ML Kit + widget)
sed -i -E 's/(namespace[[:space:]]*=?[[:space:]]*)"[^"]+"/\1"com.alhafidz.app"/' "$GRADLE"
sed -i -E 's/(applicationId[[:space:]]*=?[[:space:]]*)"[^"]+"/\1"com.alhafidz.app"/' "$GRADLE"
sed -i -E 's/minSdk[[:space:]]*=[[:space:]]*flutter\.minSdkVersion/minSdk = 23/' "$GRADLE"
sed -i -E 's/minSdkVersion[[:space:]]+flutter\.minSdkVersion/minSdkVersion 23/' "$GRADLE"
grep -nE 'namespace|applicationId|minSdk' "$GRADLE" || true

# Hapus MainActivity & icon bawaan, ganti dengan overlay
find android/app/src/main -name 'MainActivity.*' -delete
find android/app/src/main/res -name 'ic_launcher*' -delete
mkdir -p android/app/src/main/kotlin/com/alhafidz/app
cp -r android_overlay/. android/app/src/main/
echo "Overlay diterapkan"