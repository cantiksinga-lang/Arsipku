import 'dart:convert';
import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'models.dart';

/// Jembatan ke kode native Android (widget, notifikasi).
class Native {
  static const _ch = MethodChannel('garasiku/native');

  static Future<void> snapshot(String json) async {
    try {
      await _ch.invokeMethod('saveSnapshot', json);
    } catch (_) {}
  }

  static Future<void> requestNotif() async {
    try {
      await _ch.invokeMethod('requestNotifPermission');
    } catch (_) {}
  }

  static Future<bool> pinWidget() async {
    try {
      return (await _ch.invokeMethod<bool>('pinWidget')) ?? false;
    } catch (_) {
      return false;
    }
  }

  static Future<void> checkNow() async {
    try {
      await _ch.invokeMethod('checkNow');
    } catch (_) {}
  }
}

final Store store = Store();

class Store extends ChangeNotifier {
  late SharedPreferences _p;

  String? userName;
  int themeMode = 0; // 0 otomatis, 1 terang, 2 senja, 3 gelap
  bool notifEnabled = true;
  int oilWarn = 500;
  int serviceWarn = 500;
  int taxWarn = 14;
  int simWarn = 30;

  List<Vehicle> vehicles = [];
  List<Tx> txs = [];
  List<SimItem> sims = [];

  Future<void> load() async {
    _p = await SharedPreferences.getInstance();
    final raw = _p.getString('data');
    if (raw != null) {
      try {
        _apply(Map<String, dynamic>.from(jsonDecode(raw) as Map));
      } catch (_) {}
    }
    Native.snapshot(jsonEncode(snapshot()));
  }

  Map<String, dynamic> toMap() => {
        'user': userName,
        'theme': themeMode,
        'notif': notifEnabled,
        'oilWarn': oilWarn,
        'serviceWarn': serviceWarn,
        'taxWarn': taxWarn,
        'simWarn': simWarn,
        'vehicles': vehicles.map((e) => e.toJson()).toList(),
        'txs': txs.map((e) => e.toJson()).toList(),
        'sims': sims.map((e) => e.toJson()).toList(),
      };

  void _apply(Map<String, dynamic> j) {
    userName = j['user'] as String?;
    themeMode = (j['theme'] ?? 0) as int;
    notifEnabled = (j['notif'] ?? true) as bool;
    oilWarn = (j['oilWarn'] ?? 500) as int;
    serviceWarn = (j['serviceWarn'] ?? 500) as int;
    taxWarn = (j['taxWarn'] ?? 14) as int;
    simWarn = (j['simWarn'] ?? 30) as int;
    vehicles = ((j['vehicles'] ?? []) as List).map((e) => Vehicle.fromJson(Map<String, dynamic>.from(e as Map))).toList();
    txs = ((j['txs'] ?? []) as List).map((e) => Tx.fromJson(Map<String, dynamic>.from(e as Map))).toList();
    sims = ((j['sims'] ?? []) as List).map((e) => SimItem.fromJson(Map<String, dynamic>.from(e as Map))).toList();
  }

  String exportJson() => jsonEncode(toMap());

  Future<bool> importJson(String s) async {
    try {
      final m = jsonDecode(s);
      if (m is! Map) return false;
      _apply(Map<String, dynamic>.from(m));
      await commit();
      return true;
    } catch (_) {
      return false;
    }
  }

  Future<void> commit() async {
    await _p.setString('data', jsonEncode(toMap()));
    notifyListeners();
    Native.snapshot(jsonEncode(snapshot()));
  }

  // ---------- akses data ----------
  Vehicle? vehicleById(String? id) {
    for (final v in vehicles) {
      if (v.id == id) return v;
    }
    return null;
  }

  Future<void> setUser(String name) async {
    userName = name;
    await commit();
  }

  Future<void> saveVehicle(Vehicle v) async {
    final i = vehicles.indexWhere((e) => e.id == v.id);
    if (i >= 0) {
      vehicles[i] = v;
    } else {
      vehicles.add(v);
    }
    await commit();
  }

  Future<void> deleteVehicle(String id) async {
    vehicles.removeWhere((v) => v.id == id);
    txs.removeWhere((t) => t.vehicleId == id);
    await commit();
  }

  Future<void> updateKm(String id, int km) async {
    final v = vehicleById(id);
    if (v == null) return;
    v.odo = km;
    v.odoAt = DateTime.now();
    await commit();
  }

  Future<void> saveTx(Tx t) async {
    final i = txs.indexWhere((e) => e.id == t.id);
    final isNew = i < 0;
    if (isNew) {
      txs.add(t);
    } else {
      txs[i] = t;
    }
    final v = vehicleById(t.vehicleId);
    if (v != null && isNew) {
      if (t.km != null && t.km! > v.odo) {
        v.odo = t.km!;
        v.odoAt = DateTime.now();
      }
      final k = t.km ?? v.odo;
      if (t.cat == Cat.oli) v.lastOilKm = k;
      if (t.cat == Cat.servis) v.lastServiceKm = k;
    }
    await commit();
  }

  Future<void> deleteTx(String id) async {
    txs.removeWhere((t) => t.id == id);
    await commit();
  }

  Future<void> saveSim(SimItem s) async {
    final i = sims.indexWhere((e) => e.id == s.id);
    if (i >= 0) {
      sims[i] = s;
    } else {
      sims.add(s);
    }
    await commit();
  }

  Future<void> deleteSim(String id) async {
    sims.removeWhere((s) => s.id == id);
    await commit();
  }

  Future<void> setSettings({int? theme, bool? notif, int? oil, int? service, int? tax, int? sim}) async {
    if (theme != null) themeMode = theme;
    if (notif != null) notifEnabled = notif;
    if (oil != null) oilWarn = oil;
    if (service != null) serviceWarn = service;
    if (tax != null) taxWarn = tax;
    if (sim != null) simWarn = sim;
    await commit();
  }

  // ---------- statistik ----------
  int fuelMonth(String vid, DateTime now) {
    var s = 0;
    for (final t in txs) {
      if (t.vehicleId == vid && t.cat.isFuel && t.date.year == now.year && t.date.month == now.month) {
        s += t.amount;
      }
    }
    return s;
  }

  /// Rata-rata km/L (metode tangki penuh) dari catatan BBM yang punya KM & liter.
  double? fuelEff(String vid) {
    final l = txs
        .where((t) => t.vehicleId == vid && t.cat.isFuel && t.km != null && t.liters != null && t.liters! > 0)
        .toList()
      ..sort((a, b) => a.km!.compareTo(b.km!));
    if (l.length < 2) return null;
    final dist = l.last.km! - l.first.km!;
    if (dist <= 0) return null;
    final liters = l.skip(1).fold<double>(0, (s, t) => s + t.liters!);
    if (liters <= 0) return null;
    return dist / liters;
  }

  int alertCount() {
    var n = 0;
    for (final v in vehicles) {
      final td = v.taxDays;
      if ((v.oilInterval > 0 && v.oilLeft <= oilWarn) ||
          (v.serviceInterval > 0 && v.serviceLeft <= serviceWarn) ||
          (td != null && td <= taxWarn)) {
        n++;
      }
    }
    for (final s in sims) {
      if (s.daysLeft <= simWarn) n++;
    }
    return n;
  }

  Map<String, dynamic> snapshot() {
    final now = DateTime.now();
    return {
      'userName': userName,
      'notif': {
        'enabled': notifEnabled,
        'oilWarnKm': oilWarn,
        'serviceWarnKm': serviceWarn,
        'taxWarnDays': taxWarn,
        'simWarnDays': simWarn,
      },
      'vehicles': vehicles.map((v) {
        final m = <String, dynamic>{
          'id': v.id,
          'name': v.name,
          'plate': v.plate,
          'odo': v.odo,
          'widget': v.showWidget,
          'fuelMonth': fuelMonth(v.id, now),
        };
        if (v.oilInterval > 0) m['oilLeft'] = v.oilLeft;
        if (v.serviceInterval > 0) m['serviceLeft'] = v.serviceLeft;
        final d = v.taxDays;
        if (d != null) m['taxDays'] = d;
        final e = fuelEff(v.id);
        if (e != null) m['fuelEff'] = e;
        return m;
      }).toList(),
      'sims': sims.map((s) => {'id': s.id, 'label': '${s.type} ${s.name}'.trim(), 'days': s.daysLeft}).toList(),
    };
  }
}
