# GarasiKu (com.alhafidz.app)

Rawat Kendaraan & Status SIM — Kotlin + Jetpack Compose, minSdk 26.

## Build lewat GitHub Actions (tanpa PC)
1. Buat repo GitHub, upload seluruh isi folder ini (termasuk folder `.github`).
2. Buka tab **Actions** → *Build GarasiKu APK* → **Run workflow** (otomatis jalan tiap push).
3. Setelah hijau, unduh artifact **GarasiKu-APK** → ekstrak → install `app-release.apk`.

Keystore `app/garasiku.jks` disertakan agar update APK bisa menimpa versi lama.
Ganti keystore + password di `app/build.gradle.kts` bila ingin merilis ke Play Store.

## Widget
Tekan lama layar utama → Widget → GarasiKu → pilih kendaraan (geser vertikal bila lebih dari satu).
