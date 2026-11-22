# GarasiKu v2

Aplikasi Android native Kotlin untuk pencatatan kendaraan dan biaya kepemilikan. Package: `com.alhafidz.app`; min Android 7.0 (API 24).

## Fitur v2
- Profil pengguna saat pertama kali membuka aplikasi dan sapaan berbasis waktu.
- Tema otomatis: 06:00–14:59 terang, 15:00–17:59 senja, 18:00–05:59 gelap; pilihan manual tersedia.
- Kendaraan: jenis, nomor polisi, odometer, foto, interval/odometer oli terakhir, interval/odometer servis terakhir, tanggal pajak, catatan.
- Transaksi: BBM, servis, sparepart, pajak, lainnya; nominal, tanggal, KM, liter, SPBU, catatan, dan foto nota.
- OCR on-device ML Kit dari foto nota (pilih foto/scan); hasil nominal, item, kategori adalah perkiraan dan dapat diedit.
- Laporan: bulan, tahun, semua, rentang tanggal ISO `YYYY-MM-DD`; pencarian transaksi, sparepart, catatan, kendaraan, dan plat.
- Edit/hapus kendaraan dan transaksi.
- Pengingat oli dan servis berkala melalui WorkManager; notifikasi saat mendekati (500 km) atau lewat.
- Widget layar utama dengan tombol kendaraan sebelumnya/berikutnya; menampilkan odometer dan sisa km oli/servis.
- Backup/restore JSON; media foto di-embed sebagai Base64 ketika dapat dibaca oleh aplikasi.
- Database lokal Room; fungsi utama offline.

## Build dengan GitHub Actions
1. Upload isi folder project ke repository GitHub.
2. Push ke `main`/`master` atau buka Actions → **GarasiKu Android Build** → Run workflow.
3. Download artifact `GarasiKu-APK`.

Workflow membangun `assembleDebug` dan `assembleRelease`. APK release tidak ditandatangani; APK debug untuk tes dapat di-install setelah diunduh.

## Catatan
- Reminder menggunakan odometer yang dimasukkan pengguna; aplikasi tidak membaca KM otomatis dari kendaraan.
- Widget dapat berpindah antar kendaraan dengan tombol. Widget refresh saat data kendaraan/transaksi disimpan; WorkManager reminder berjalan periodik, bukan alarm menit-ke-menit.
- OCR nota bersifat heuristik dan hasil wajib diperiksa.
- Backup JSON dapat menjadi besar karena foto di-embed. Foto yang URI-nya sudah tidak dapat diakses akan tetap tersimpan sebagai metadata URI tanpa data gambar.
- Versi ini disusun sebagai source project; build/test Android SDK belum dijalankan di lingkungan penyusunan.
