# ArsipKu — Arsip Dokumen Keluarga & Manajemen Kendaraan (Android)

Package `com.alhafidz.app` · Nama aplikasi: **ArsipKu**

ArsipKu menggabungkan pengelolaan dokumen pribadi/keluarga dengan pencatatan kendaraan, servis, BBM, kilometer, dan laporan biaya dalam satu aplikasi Android.

## Fitur arsip dokumen
- Beranda: status keamanan, dokumen yang perlu diperpanjang, total dokumen, kategori.
- Cari: filter kategori & anggota keluarga, pencarian, status masa berlaku.
- Pengingat masa berlaku dokumen, notifikasi harian, dan tambah ke Kalender.
- Upload dari Galeri, Kamera, atau PDF; pratinjau foto dan PDF.
- Backup & restore berkas `.alhbak` terenkripsi kata sandi.
- Keamanan: PIN 6 digit + biometrik, kunci otomatis, blokir screenshot, enkripsi AES-256-GCM.

## Fitur kendaraan & garasi
- Multi profil pengguna dengan dialog nama saat pertama kali aplikasi dibuka dan sapaan sesuai waktu.
- Data kendaraan per profil: nama/tipe, nomor polisi, tahun, kilometer terakhir, dan jatuh tempo pajak.
- Pencatatan servis, BBM, suku cadang, pajak, bengkel/SPBU, nominal, tanggal, liter, kilometer, dan catatan.
- Riwayat transaksi per kendaraan serta pembaruan kilometer terakhir dari transaksi.
- Laporan bulanan, tahunan, semua waktu, dan rentang tanggal khusus; ringkasan biaya servis dan BBM.
- OCR nota berbasis Google ML Kit Text Recognition (model bundled): pemrosesan teks gambar secara lokal; mendeteksi indikasi BBM/servis dan nominal untuk membantu pengisian transaksi. Hasil OCR tetap dapat diperiksa dan diedit sebelum disimpan.
- Pengingat sederhana servis berkala (berdasarkan selisih 5.000 km dari catatan servis) dan pajak yang jatuh tempo dalam 30 hari.

## Privasi & jaringan
- Data kendaraan disimpan lokal dalam database terpisah dari database dokumen.
- OCR dilakukan di perangkat. Tidak ada unggahan nota ke server.
- Aplikasi tidak meminta izin INTERNET.

## Build APK lewat GitHub Actions
1. Upload isi folder ini ke repo GitHub (branch `main`).
2. Tab **Actions** > **Build APK ArsipKu** > *Run workflow* (atau otomatis saat push).
3. Unduh artifact **ArsipKu-APK** (`app-release.apk`).

### Tanda tangan (keystore)
Tanpa secrets, workflow membuat keystore baru tiap build dan menyimpannya di artifact `KEYSTORE-SIMPAN-INI`. APK dengan keystore berbeda tidak bisa menimpa/update instalasi lama. Untuk update mulus, simpan keystore dan konfigurasi secrets `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, dan `KEY_ALIAS` di GitHub Actions.

## Build lokal
Butuh JDK 17 + Android SDK 35 + Gradle 8.10+: `gradle :app:assembleRelease`.

## Struktur
`app/src/main/java/com/alhafidz/app/` — modul dokumen, keamanan, backup, pengingat, serta `VehicleModule.kt` untuk kendaraan, transaksi, laporan, dan OCR.
