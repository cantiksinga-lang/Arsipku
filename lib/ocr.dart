import 'dart:math';
import 'package:google_mlkit_text_recognition/google_mlkit_text_recognition.dart';
import 'models.dart';

class OcrResult {
  int? amount;
  DateTime? date;
  String? title;
  double? liters;
  int? km;
  Cat? cat;
  String raw;
  OcrResult({this.amount, this.date, this.title, this.liters, this.km, this.cat, this.raw = ''});
}

class Ocr {
  static final TextRecognizer _rec = TextRecognizer(script: TextRecognitionScript.latin);

  static Future<OcrResult> scan(String path) async {
    final t = await _rec.processImage(InputImage.fromFilePath(path));
    return parse(t.text);
  }

  static const _partKeys = [
    'oli', 'filter', 'kampas', 'busi', 'ban ', 'ban\n', 'aki', 'accu', 'rantai', 'gir', 'v-belt', 'vbelt', 'belt',
    'roller', 'shock', 'lampu', 'kabel', 'kopling', 'minyak rem', 'coolant', 'radiator', 'servis', 'service',
    'jasa', 'tune up', 'cvt', 'bearing', 'wiper', 'karburator', 'injektor', 'spion', 'handle', 'cakram', 'piston',
    'seher', 'klep', 'noken', 'platina', 'cdi', 'kiprok', 'rem ', 'tromol', 'lager', 'per ', 'knalpot'
  ];

  /// Ambil semua angka rupiah dari satu baris teks.
  static List<int> nums(String s) {
    final out = <int>[];
    final re = RegExp(r'\d{1,3}(?:[.,]\d{3})+(?:[.,]\d{1,2})?|\d+(?:[.,]\d{1,2})?');
    for (final m in re.allMatches(s)) {
      var t = m.group(0)!;
      final dec = RegExp(r'[.,]\d{1,2}$').firstMatch(t);
      if (dec != null && !RegExp(r'^\d{1,3}(?:[.,]\d{3})+$').hasMatch(t)) {
        t = t.substring(0, dec.start);
      }
      final digits = t.replaceAll(RegExp(r'[.,]'), '');
      if (digits.isEmpty || digits.length > 9) continue;
      out.add(int.parse(digits));
    }
    return out;
  }

  static OcrResult parse(String text) {
    final lines = text.split(RegExp(r'[\r\n]+')).map((e) => e.trim()).where((e) => e.isNotEmpty).toList();
    final low = lines.map((e) => e.toLowerCase()).toList();
    final joined = low.join('\n');

    // ---- nominal ----
    int? amount;
    final totalRe = RegExp(r'(grand\s*total|total\s*bayar|total|jumlah|tagihan|amount)');
    final skipRe = RegExp(r'(kembali|change|tunai|cash|sub\s*total|subtotal|diskon|ppn)');
    final cands = <int>[];
    for (var i = 0; i < low.length; i++) {
      if (totalRe.hasMatch(low[i]) && !skipRe.hasMatch(low[i])) {
        var n = nums(lines[i]);
        if (n.isEmpty && i + 1 < lines.length) n = nums(lines[i + 1]);
        n = n.where((e) => e >= 100).toList();
        if (n.isNotEmpty) cands.add(n.reduce(max));
      }
    }
    if (cands.isNotEmpty) {
      amount = cands.reduce(max);
    } else {
      final pool = <int>[];
      for (var i = 0; i < lines.length; i++) {
        if (RegExp(r'\d{1,2}[/\-.]\d{1,2}[/\-.]\d{2,4}').hasMatch(lines[i]) ||
            RegExp(r'(telp|tel\b|hp\b|wa\b|npwp|no\.)').hasMatch(low[i])) {
          continue;
        }
        pool.addAll(nums(lines[i]).where((e) => e >= 1000 && e < 100000000));
      }
      if (pool.isNotEmpty) amount = pool.reduce(max);
    }

    // ---- tanggal ----
    DateTime? date;
    final nowY = DateTime.now().year;
    final m1 = RegExp(r'(\d{1,2})[/\-.](\d{1,2})[/\-.](\d{2,4})').firstMatch(joined);
    if (m1 != null) {
      final d = int.parse(m1.group(1)!);
      final mo = int.parse(m1.group(2)!);
      var y = int.parse(m1.group(3)!);
      if (y < 100) y += 2000;
      if (d >= 1 && d <= 31 && mo >= 1 && mo <= 12 && y >= 2000 && y <= nowY + 1) date = DateTime(y, mo, d);
    }
    if (date == null) {
      const mons = ['jan', 'feb', 'mar', 'apr', 'mei', 'jun', 'jul', 'agu', 'sep', 'okt', 'nov', 'des'];
      final m2 = RegExp(r'(\d{1,2})\s*(jan|feb|mar|apr|mei|jun|jul|agu|ags|sep|okt|nov|des)[a-z]*\.?\s*(\d{2,4})')
          .firstMatch(joined);
      if (m2 != null) {
        var key = m2.group(2)!;
        if (key == 'ags') key = 'agu';
        final mo = mons.indexOf(key) + 1;
        var y = int.parse(m2.group(3)!);
        if (y < 100) y += 2000;
        final d = int.parse(m2.group(1)!);
        if (mo > 0 && d >= 1 && d <= 31 && y >= 2000 && y <= nowY + 1) date = DateTime(y, mo, d);
      }
    }

    // ---- liter & km ----
    double? liters;
    final ml = RegExp(r'(\d{1,3}(?:[.,]\d{1,3})?)\s*(?:l|ltr|lt|liter)\b').firstMatch(joined);
    if (ml != null) {
      final v = double.tryParse(ml.group(1)!.replaceAll(',', '.'));
      if (v != null && v > 0 && v < 200) liters = v;
    }
    int? km;
    final mk = RegExp(r'(?:odometer|odo|km)\s*[:=]\s*(\d[\d.,]*)').firstMatch(joined);
    if (mk != null) {
      final v = int.tryParse(mk.group(1)!.replaceAll(RegExp(r'[.,]'), ''));
      if (v != null && v >= 100 && v <= 2000000) km = v;
    }

    // ---- nama sparepart / jasa ----
    final items = <String>[];
    for (var i = 0; i < low.length; i++) {
      final l = low[i];
      if (RegExp(r'(total|kembali|tunai|bayar|jumlah|subtotal|npwp|alamat|telp)').hasMatch(l)) continue;
      if (_partKeys.any((k) => '$l '.contains(k))) {
        final s = lines[i]
            .replaceAll(RegExp(r'(rp\.?\s*)?\d[\d.,]*'), ' ')
            .replaceAll(RegExp(r'[^\w\s\-/&()]'), ' ')
            .replaceAll(RegExp(r'\s+'), ' ')
            .trim();
        if (s.length >= 3 && !items.contains(s)) items.add(s);
      }
    }

    // ---- kategori & judul ----
    Cat? cat;
    String? title;
    final fuelBrand = RegExp(r'(pertamax\s*(turbo|green)?|pertalite|dexlite|biosolar|solar|shell\s*\w+|revvo\s*\d*|pertamina|spbu)')
        .firstMatch(joined);
    if (fuelBrand != null || (liters != null && joined.contains('liter') && items.isEmpty)) {
      cat = Cat.bbm;
      final g = RegExp(r'(pertamax\s*(turbo|green)?|pertalite|dexlite|biosolar|solar|revvo\s*\d*)').firstMatch(joined);
      final grade = g?.group(0);
      title = grade == null ? 'BBM' : grade[0].toUpperCase() + grade.substring(1);
    } else {
      if (RegExp(r'\boli\b').hasMatch(joined)) {
        cat = Cat.oli;
      } else if (RegExp(r'(servis|service|jasa|tune up)').hasMatch(joined)) {
        cat = Cat.servis;
      } else if (items.isNotEmpty) {
        cat = Cat.sparepart;
      }
      if (items.isNotEmpty) {
        title = items.take(4).join(', ');
      } else if (lines.isNotEmpty && lines.first.length <= 40) {
        title = lines.first;
      }
    }

    return OcrResult(amount: amount, date: date, title: title, liters: liters, km: km, cat: cat, raw: text);
  }
}
