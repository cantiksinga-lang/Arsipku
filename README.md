# GarasiKu — Rawat Kendaraan & Status SIM

Aplikasi Android (Flutter + Kotlin native) · package `com.alhafidz.app`

## Cara build (GitHub Actions)
1. Upload seluruh isi folder ini ke repo GitHub (branch `main`).
2. Buka tab **Actions → Build GarasiKu APK** (otomatis jalan saat push, atau klik *Run workflow*).
3. Setelah selesai, unduh artifact **GarasiKu-apk** → `GarasiKu.apk` → install di HP.

Folder `android/` dibuat otomatis oleh workflow (`flutter create` + `android_overlay/`), jadi tidak perlu ada di repo.

### Agar APK baru bisa menimpa (update) yang lama
Tanpa ini tiap build memakai kunci tanda tangan berbeda. Sekali saja, di PC/HP Termux:
```
keytool -genkey -v -keystore debug.keystore -storepass android -alias androiddebugkey -keypass android -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Android Debug,O=Android,C=US"
base64 -w0 debug.keystore
```
Salin hasilnya ke **Settings → Secrets and variables → Actions → New secret** dengan nama `DEBUG_KEYSTORE_BASE64`.

## Fitur
- Beranda: sapaan sesuai jam + nama (dialog nama saat pertama install), ringkasan biaya (tahun/semua), kartu kendaraan (pajak, oli, servis, KM), tombol Isi BBM & Update KM, pencarian & tampilan ringkas/besar
- **OCR** (Google ML Kit, offline): scan kamera / galeri → nominal, nama sparepart/jasa, tanggal, liter BBM, KM, kategori terisi otomatis
- Laporan: Bulan / Tahun / Semua / Rentang, filter kendaraan, pencarian, rincian per kategori, grafik bulanan
- Tema otomatis: 05.00–14.59 terang, 15.00–17.59 senja, 18.00–04.59 gelap (bisa dipaksa manual di Pengaturan)
- Widget layar utama: info kendaraan, BBM bulan ini + km/L, sisa km ganti oli/servis, pajak; geser untuk ganti kendaraan; pilih kendaraan lewat opsi "Tampilkan di widget"
- Notifikasi: ganti oli / servis berdasarkan sisa KM (ambang bisa diatur), pajak, masa berlaku SIM
- Tab SIM Saya, cadangan/pemulihan data via clipboard
